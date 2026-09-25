#Requires -Version 5.1
<#
.SYNOPSIS
  Real Minecraft 1.16.1 keep-alive loop for @testrun2.
  This IS the sim: Fabric :1.16.1:runClient. No mock physics.
#>
[CmdletBinding()]
param(
  [string]$Repo = 'C:\Users\redfa\Documents\MinecraftDev\altoclef',
  [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot',
  [int]$JoinTimeoutSec = 480,
  [int]$StallSec = 120,
  [int]$StatusEverySec = 30,
  [int]$MaxWorlds = 0,
  [switch]$MonitorOnly,
  [switch]$ForceKillStale
)

$ErrorActionPreference = 'Continue'
Set-Location $Repo

# NOTE: no Set-StrictMode here. Under StrictMode, `.Count` on a scalar or $null
# throws "The property 'Count' cannot be found on this object", which killed the
# previous overnight run with FATAL before create-world. All array access below
# uses @(...) explicitly instead.
function To-Array($v) {
  if ($null -eq $v) { return @() }
  return @($v)
}

if (-not $JavaHome -or -not (Test-Path "$JavaHome\bin\java.exe")) {
  $JavaHome = $env:JAVA_HOME
}
if (-not $JavaHome -or -not (Test-Path "$JavaHome\bin\java.exe")) {
  throw "JAVA_HOME invalid: $JavaHome"
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;" + $env:Path

$LogDir        = Join-Path $Repo 'logs'
$StatusPath    = Join-Path $LogDir 'overnight-status.json'
$RunLog        = Join-Path $LogDir 'sim-loop-runclient.log'
$PidFile       = Join-Path $LogDir 'sim-loop-gradle.pid'
$CrashDir      = Join-Path $LogDir 'overnight-crashes'
$SummaryPath   = Join-Path $LogDir 'MORNING_SUMMARY.md'
$LatestLog     = Join-Path $Repo 'versions\1.16.1\run\logs\latest.log'
$CrashReports  = Join-Path $Repo 'versions\1.16.1\run\crash-reports'
$SettingsPath  = Join-Path $Repo 'versions\1.16.1\run\altoclef_settings.json'
$FaultsPath    = Join-Path $Repo 'versions\1.16.1\run\altoclef\faults.log'

New-Item -ItemType Directory -Force -Path $LogDir, $CrashDir | Out-Null

$script:GradlePid = 0
$script:World = ''
$script:Phase = ''
$script:Pick = $null
$script:T2 = $false
$script:LastHit = ''
$script:WorldsDone = 0
$script:MixinHealth = ''
$script:LoopState = ''

function Write-Utf8NoBom([string]$Path, [string]$Text) {
  $enc = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($Path, $Text, $enc)
}

function Save-Status {
  param(
    [string]$State,
    [string]$Notes = '',
    [string]$Blocker = '',
    [string]$World = '',
    [string]$Phase = '',
    $Pick = $null,
    [bool]$Testrun2 = $false
  )
  if (-not $World) { $World = $script:World }
  if (-not $Phase) { $Phase = $script:Phase }
  if ($null -eq $Pick) { $Pick = $script:Pick }
  $obj = [ordered]@{
    updated         = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ssK')
    state           = $State
    pid             = $script:GradlePid
    world           = $World
    phase           = $Phase
    pick            = $Pick
    lastLogHit      = $script:LastHit
    blocker         = $Blocker
    notes           = $Notes
    testrun2        = [bool]$Testrun2
    mover           = 'baritone'
    mode            = '1.16.1-live-keepalive'
    worldsCompleted = $script:WorldsDone
    mcVersion       = '1.16.1'
    mixinHealth     = $script:MixinHealth
    loopState       = $script:LoopState
  }
  Write-Utf8NoBom $StatusPath ($obj | ConvertTo-Json -Depth 6)
}

function Append-Summary([string]$Line) {
  $stamp = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
  Add-Content -Path $SummaryPath -Value ("- [{0}] {1}" -f $stamp, $Line) -Encoding UTF8
}

function Set-JsonKey($obj, [string]$Key, $Value) {
  if ($obj.PSObject.Properties.Name -contains $Key) {
    $obj.$Key = $Value
  } else {
    $obj | Add-Member -NotePropertyName $Key -NotePropertyValue $Value -Force
  }
  return $obj
}

function Ensure-Settings {
  # There are TWO settings files and BOTH must agree. See docs/SIM_HARNESS.md.
  #   run\altoclef_settings.json          <- read by the title-screen mixin (pre-CWD-switch)
  #   run\altoclef\altoclef_settings.json <- read by Settings.load once in-world
  # Settings.SETTINGS_PATH is relative and the JVM CWD changes, so writing only one
  # file makes the harness silently do nothing (world loads, bot stands still).
  $worldFile = Join-Path (Join-Path $Repo 'versions\1.16.1\run') 'altoclef\altoclef_settings.json'
  $topFile   = Join-Path (Join-Path $Repo 'versions\1.16.1\run') 'altoclef_settings.json'

  foreach ($target in @($topFile, $worldFile)) {
    $dir = Split-Path $target
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $obj = $null
    if (Test-Path $target) {
      try { $obj = Get-Content -Raw $target | ConvertFrom-Json } catch { $obj = $null }
    }
    if (-not $obj) { $obj = [pscustomobject]@{} }

    $obj = Set-JsonKey $obj 'idleCommand' ''
    if ($obj.PSObject.Properties.Name -contains 'idle_command') { $obj.idle_command = '' }
    $obj = Set-JsonKey $obj 'speedrunMoverPreference' 'baritone'

    # Headless automation contract: create a fresh Easy world and run @testrun2.
    $obj = Set-JsonKey $obj 'autoLoadWorld' $true
    $obj = Set-JsonKey $obj 'autoRunCommand' 'testrun2'

    Write-Utf8NoBom $target ($obj | ConvertTo-Json -Depth 40)
  }

  # Sanity-check that both files now agree on the two keys the harness depends on.
  foreach ($f in @($topFile, $worldFile)) {
    try {
      $j = Get-Content -Raw $f | ConvertFrom-Json
      Append-Summary ("settings {0}: autoLoadWorld={1} autoRunCommand='{2}'" -f `
        (Split-Path $f -Leaf), $j.autoLoadWorld, $j.autoRunCommand)
    } catch {
      Append-Summary ("settings {0}: PARSE FAILED" -f (Split-Path $f -Leaf))
    }
  }
}

Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class SimMcWin {
  public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr lp);
  [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr hWnd, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
}
"@ | Out-Null
Add-Type -AssemblyName System.Windows.Forms | Out-Null

function Find-McWindow {
  $found = New-Object 'System.Collections.Generic.List[object]'
  try {
    [SimMcWin]::EnumWindows({
      param($hwnd, $lp)
      try {
        if (-not [SimMcWin]::IsWindowVisible($hwnd)) { return $true }
        $sb = New-Object System.Text.StringBuilder 512
        [void][SimMcWin]::GetWindowText($hwnd, $sb, $sb.Capacity)
        $title = $sb.ToString()
        if ([string]::IsNullOrWhiteSpace($title)) { return $true }
        if ($title -match 'Minecraft' -or $title -match '(?i)lwjgl' -or $title -match '1\.16\.1') {
          $procId = [uint32]0
          [void][SimMcWin]::GetWindowThreadProcessId($hwnd, [ref]$procId)
          $found.Add([pscustomobject]@{ Hwnd = $hwnd; Title = $title; Pid = $procId })
        }
      } catch {}
      return $true
    }, [IntPtr]::Zero) | Out-Null
  } catch {}
  try {
    Get-Process java,javaw -ErrorAction SilentlyContinue | ForEach-Object {
      if ($_.MainWindowHandle -ne [IntPtr]::Zero -and $_.MainWindowTitle -and ($_.MainWindowTitle -match 'Minecraft|1\.16|lwjgl')) {
        $found.Add([pscustomobject]@{ Hwnd = [IntPtr]$_.MainWindowHandle; Title = $_.MainWindowTitle; Pid = [uint32]$_.Id })
      }
    }
  } catch {}
  return ,@($found.ToArray())
}

function Focus-Mc {
  $wins = @(Find-McWindow)
  if ($wins.Length -eq 0) { return $false }
  $w = $wins | Select-Object -First 1
  [void][SimMcWin]::ShowWindow($w.Hwnd, 3)
  Start-Sleep -Milliseconds 200
  [void][SimMcWin]::SetForegroundWindow($w.Hwnd)
  Start-Sleep -Milliseconds 350
  return $true
}

function Send-KeysSafe([string]$Keys) {
  [System.Windows.Forms.SendKeys]::SendWait($Keys)
}

function Get-LogTail([int]$Lines = 300) {
  if (-not (Test-Path $LatestLog)) { return '' }
  try { return ((Get-Content -Path $LatestLog -Tail $Lines -ErrorAction SilentlyContinue) -join "`n") }
  catch { return '' }
}

function Test-JoinSignal([string]$Text) {
  if ([string]::IsNullOrEmpty($Text)) { return $false }
  # Title-screen AltoClef/TENORCLEF init is NOT join.
  return ($Text -match 'joined the game' -or
          $Text -match 'Loaded \d+ advancements' -or
          $Text -match 'TESRUN2 start' -or
          $Text -match 'User Task Set:.*(?:ModernSpeedrun|testrun2|Testrun2)')
}

function Test-ClientUp([string]$Text) {
  if ([string]::IsNullOrEmpty($Text)) { return $false }
  return ($Text -match 'Sound engine started' -or
          $Text -match 'FabricLoader' -or
          $Text -match 'Reloading ResourceManager' -or
          $Text -match 'Minecraft:')
}

function Update-FromLog {
  $tail = Get-LogTail 400

  # Fail loud if the mixin layer is dead. Checked once per poll; cheap.
  $mh = Test-MixinHealth $tail
  if (-not $mh.ok) {
    if ($script:MixinHealth -ne $mh.reason) {
      $script:MixinHealth = $mh.reason
      Write-Host ("FATAL MIXIN HEALTH: {0}" -f $mh.reason)
      Save-Status -State 'blocker' -Blocker ('mixin_' + $mh.reason) `
        -Notes 'altoclef mixin layer not loaded - check compatibilityLevel vs class file version'
      Append-Summary ("BLOCKER mixin health = {0}" -f $mh.reason)
    }
  } elseif ($script:MixinHealth -ne 'ok') {
    $script:MixinHealth = 'ok'
    Append-Summary 'mixin health OK (canary fired)'
  }

  if ($tail -match 'ph=([A-Z_]+)') { $script:Phase = $Matches[1] }
  elseif ($tail -match 'PHASE[=:]([A-Z_]+)') { $script:Phase = $Matches[1] }
  if ($tail -match 'pick=(\d+)') { $script:Pick = [int]$Matches[1] }
  if ($tail -match 'TESRUN2 start|User Task Set:.*(?:ModernSpeedrun|testrun2)|@testrun2') { $script:T2 = $true }

  # Loop health. A run that keeps S130-arming at one column is making no progress even
  # though nothing crashes - the wall-clock killer. Flag it so we notice in minutes.
  $lh = Test-LoopHealth
  if ($lh.stalled) {
    if ($script:LoopState -ne $lh.reason) {
      $script:LoopState = $lh.reason
      Write-Host ("WARN LOOP HEALTH: {0}" -f $lh.reason)
      Append-Summary ("LOOP {0} ({1})" -f $lh.reason, $lh.detail)
    }
  } elseif ($script:LoopState -ne 'ok') {
    $script:LoopState = 'ok'
  }

  $patterns = @(
    @{ re = 'pick=0.*PORTAL|PORTAL.*pick=0|E94|E\[94\]'; name = 'E94_pick0' },
    @{ re = 'E133'; name = 'E133_pillar_pingpong' },
    @{ re = '(?i)hard_stuck|hard stuck'; name = 'hard_stuck' },
    @{ re = '(?i)---- Minecraft Crash Report'; name = 'CRASH' },
    @{ re = '(?i)\bFATAL\b'; name = 'FATAL' },
    @{ re = 'User task FINISHED|(?i)task FINISHED'; name = 'FINISHED' },
    @{ re = '(?i)You died'; name = 'death' }
  )
  foreach ($p in $patterns) {
    if ($tail -match $p.re) { $script:LastHit = $p.name; break }
  }
  return $tail
}

function Test-LoopHealth {
  # Detect "no progress" by watching faults.log growth per fault CODE rather than by
  # position (the probe already proves the position repeats). Cheap: read the file tail.
  $out = [ordered]@{ stalled = $false; reason = ''; detail = '' }
  if (-not (Test-Path $FaultsPath)) { return $out }
  try {
    $lines = Get-Content $FaultsPath -Tail 400 -ErrorAction Stop
  } catch { return $out }

  # E133 is emitted by HolePillar.detectPingPong() the moment the pillar loop is proven.
  $ping = @($lines | Where-Object { $_ -match 'E133' })
  if ($ping.Count -ge 1) {
    $out.stalled = $true
    $out.reason = 'pillar_pingpong'
    $out.detail = ("{0} E133 in last 400 faults" -f $ping.Count)
    return $out
  }

  # Rate check: S130 arms per unit time. >4 arms in a 60s window means we are hopping
  # in place. S130 lines carry a mm:ss stamp as the first token after FAULT.
  $s130 = @($lines | Where-Object { $_ -match 'S130 \| START' })
  if ($s130.Count -ge 6) {
    $out.stalled = $true
    $out.reason = 'pillar_rearm_rate'
    $out.detail = ("{0} S130 START in last 400 fault lines" -f $s130.Count)
    return $out
  }
  return $out
}

function Test-InProcessCreateEnabled {
  # AutoWorldCreateMixin (altoclef) creates the fresh Survival/Easy world in-process and
  # AltoClef fires `autoRunCommand` on join. Works headless, where SendKeys cannot.
  # Gate on the same setting the mixin reads: autoLoadWorld in altoclef_settings.json.
  if (-not (Test-Path $SettingsPath)) { return $false }
  try {
    $j = Get-Content $SettingsPath -Raw | ConvertFrom-Json
    return [bool]$j.autoLoadWorld
  } catch { return $false }
}

function Test-MixinHealth([string]$Tail) {
  # Guard against the silent-mixin-death bug: if altoclef.mixins.json compatibilityLevel
  # is lower than the compiled class file version, Mixin drops EVERY altoclef mixin with
  # only a DEBUG line and no error. The mod then "runs" with nothing hooked.
  # See skills/mc-1161-mixin-api-verification (Step 3b).
  $out = [ordered]@{ ok = $true; reason = '' }

  # 1. Hard evidence: Mixin refused to load classes past the configured level.
  if ($Tail -match 'Class version \d+ required is higher than the class version supported') {
    $out.ok = $false
    $out.reason = 'mixin_class_version_rejected'
    return $out
  }

  # 2. Positive evidence: the ClientTickMixin canary fired.
  #    Absence is only meaningful once we know the client booted.
  if ($Tail -match 'TENORCLEF: Global Init' -and $Tail -notmatch 'TENORCLEF: MIXIN OK') {
    $out.ok = $false
    $out.reason = 'mixin_canary_missing'
    return $out
  }

  return $out
}

function Try-CreateSurvivalEasyWorld {
  if (Test-InProcessCreateEnabled) {
    # Client creates the world itself; nothing to send. Just wait for the join signal.
    Write-Host 'In-process create enabled (AutoWorldCreateMixin) - skipping SendKeys'
    $script:World = 'AutoRun_<in-process>'
    Save-Status -State 'creating_world' -World $script:World -Notes 'in-process create via mixin'
    Append-Summary "in-process create (mixin) - world name assigned by client"
    return $true
  }
  Write-Host 'Create-world keyboard - Survival Easy; never Hardcore; never load old save'
  if (-not (Focus-Mc)) {
    Save-Status -State 'blocker' -Blocker 'need_ui_focus' -Notes 'MC window not focused for create-world'
    return $false
  }
  $name = 'AutoRun_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
  $script:World = $name
  Send-KeysSafe '{ESC}'; Start-Sleep -Milliseconds 500
  Send-KeysSafe '{ESC}'; Start-Sleep -Milliseconds 500
  Send-KeysSafe '{ENTER}'; Start-Sleep -Milliseconds 1200
  for ($i = 0; $i -lt 6; $i++) { Send-KeysSafe '{TAB}'; Start-Sleep -Milliseconds 120 }
  Send-KeysSafe '{ENTER}'; Start-Sleep -Milliseconds 1000
  Send-KeysSafe '^a'; Start-Sleep -Milliseconds 100
  Send-KeysSafe $name; Start-Sleep -Milliseconds 400
  Send-KeysSafe '{TAB}{TAB}{ENTER}'; Start-Sleep -Milliseconds 600
  for ($i = 0; $i -lt 4; $i++) { Send-KeysSafe '{TAB}'; Start-Sleep -Milliseconds 100 }
  for ($i = 0; $i -lt 3; $i++) { Send-KeysSafe '{ENTER}'; Start-Sleep -Milliseconds 250 }
  Send-KeysSafe '{TAB}{TAB}{ENTER}'; Start-Sleep -Milliseconds 800
  Send-KeysSafe '{ENTER}'
  Save-Status -State 'creating_world' -World $name -Notes 'create-world keys sent'
  Append-Summary "create-world keys sent name=$name"
  return $true
}

function Send-Testrun2Chat {
  if (Test-InProcessCreateEnabled) {
    # AltoClef's autoRunCommand already fired `@testrun2` on join - do not double-start.
    Write-Host 'In-process autorun enabled - skipping chat @testrun2'
    $script:T2 = $true
    Save-Status -State 'running' -World $script:World -Phase $script:Phase -Pick $script:Pick -Testrun2 $true -Notes 'autorun via settings'
    Append-Summary "autorun @testrun2 via settings world=$($script:World)"
    return $true
  }
  if (-not (Focus-Mc)) {
    Save-Status -State 'blocker' -Blocker 'chat_focus_failed' -Notes 'could not focus MC for @testrun2'
    return $false
  }
  Start-Sleep -Milliseconds 400
  Send-KeysSafe 't'; Start-Sleep -Milliseconds 350
  Send-KeysSafe '@testrun2'; Start-Sleep -Milliseconds 200
  Send-KeysSafe '{ENTER}'
  $script:T2 = $true
  Save-Status -State 'running' -World $script:World -Phase $script:Phase -Pick $script:Pick -Testrun2 $true -Notes 'chat @testrun2 sent'
  Append-Summary "@testrun2 sent world=$($script:World)"
  return $true
}

function Copy-NewCrashes {
  if (-not (Test-Path $CrashReports)) { return }
  Get-ChildItem $CrashReports -Filter '*.txt' -ErrorAction SilentlyContinue | ForEach-Object {
    $dest = Join-Path $CrashDir $_.Name
    if (-not (Test-Path $dest) -or $_.LastWriteTime -gt (Get-Item $dest).LastWriteTime) {
      Copy-Item $_.FullName $dest -Force
      Append-Summary "copied crash $($_.Name)"
    }
  }
}

function Get-ClientJavaPids {
  $list = @()
  Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
    $cl = $_.CommandLine
    if (-not $cl) { return }
    if ($cl -match '1\.16\.1' -or ($cl -match 'altoclef' -and $cl -match 'fabric-loader') -or ($cl -match 'GradleWrapperMain' -and $cl -match 'runClient')) {
      $list += [int]$_.ProcessId
    }
  }
  return ,$list
}

function Start-ClientIfNeeded {
  $existing = @(Get-ClientJavaPids)
  if ($existing.Count -gt 0 -and -not $ForceKillStale) {
    $script:GradlePid = $existing[0]
    Save-Status -State 'waiting_join' -Notes ("reusing client pids=" + ($existing -join ','))
    Append-Summary ("reuse client pids=" + ($existing -join ','))
    return
  }
  if ($ForceKillStale -or $existing.Count -gt 0) {
    foreach ($p in $existing) { Stop-Process -Id $p -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 2
  }
  if (Test-Path $LatestLog) {
    $bak = Join-Path $LogDir ('latest-prestart-' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '.log')
    try { Move-Item $LatestLog $bak -Force } catch {
      try { Copy-Item $LatestLog $bak -Force; Clear-Content $LatestLog } catch {}
    }
  }
  Ensure-Settings
  if (Test-Path $RunLog) { Remove-Item $RunLog -Force -ErrorAction SilentlyContinue }
  Save-Status -State 'starting' -Notes 'launching :1.16.1:runClient'
  $gradle = Join-Path $Repo 'gradlew.bat'
  $proc = Start-Process -FilePath $gradle `
    -ArgumentList @(':1.16.1:runClient', '--no-daemon') `
    -WorkingDirectory $Repo `
    -RedirectStandardOutput $RunLog `
    -RedirectStandardError ($RunLog + '.err') `
    -PassThru -WindowStyle Normal
  $script:GradlePid = $proc.Id
  $proc.Id | Set-Content $PidFile
  Save-Status -State 'waiting_join' -Notes "gradle pid=$($proc.Id)"
  Append-Summary "started gradle pid=$($proc.Id)"
  Write-Host "STARTED gradle PID=$($proc.Id)"
}

# ---- main ----
if (-not (Test-Path $SummaryPath)) {
  Write-Utf8NoBom $SummaryPath "# Morning / 1.16.1 sim-loop summary`r`n`r`n"
}
Append-Summary 'sim-loop start (real 1.16.1 client)'
Write-Host "=== 1.16.1 sim-loop keepalive ==="
Write-Host "Repo=$Repo JAVA_HOME=$JavaHome"

if (-not $MonitorOnly) {
  Start-ClientIfNeeded
} else {
  $existing = @(Get-ClientJavaPids)
  if ($existing.Count -gt 0) { $script:GradlePid = $existing[0] }
  Save-Status -State 'running' -Notes 'monitor-only'
}

while ($true) {
  if ($MaxWorlds -gt 0 -and $script:WorldsDone -ge $MaxWorlds) {
    Save-Status -State 'done' -Notes 'MaxWorlds reached' -Testrun2 $script:T2
    break
  }

  $deadline = (Get-Date).AddSeconds($JoinTimeoutSec)
  $joined = $false
  $attemptedCreate = $false

  while ((Get-Date) -lt $deadline) {
    $clients = @(Get-ClientJavaPids)
    if ($clients.Count -eq 0 -and -not $MonitorOnly) {
      Append-Summary 'client dead - relaunching'
      Start-ClientIfNeeded
      $deadline = (Get-Date).AddSeconds($JoinTimeoutSec)
    }
    $tail = Update-FromLog
    if (Test-JoinSignal $tail) {
      $joined = $true
      Save-Status -State 'in_world' -World $script:World -Phase $script:Phase -Pick $script:Pick -Testrun2 $script:T2 -Notes 'join signal'
      break
    }
    if (-not $attemptedCreate -and (Test-ClientUp $tail)) {
      Start-Sleep -Seconds 10
      try { $ok = Try-CreateSurvivalEasyWorld } catch { Write-Host "create-world error: $_"; $ok = $false }
      if ($ok) { $attemptedCreate = $true }
      Start-Sleep -Seconds 8
    } elseif (-not $attemptedCreate) {
      $wins = @(Find-McWindow)
      if ($wins.Length -gt 0) {
        try { $ok = Try-CreateSurvivalEasyWorld } catch { $ok = $false }
        if ($ok) { $attemptedCreate = $true }
      }
    }
    if ($script:LastHit -in @('CRASH','FATAL')) {
      Copy-NewCrashes
      Append-Summary 'crash during join wait'
      break
    }
    Save-Status -State 'waiting_join' -World $script:World -Notes "waiting join attemptedCreate=$attemptedCreate"
    Start-Sleep -Seconds 5
  }

  if (-not $joined) {
    Save-Status -State 'blocker' -Blocker 'join_timeout' -Notes "No join within ${JoinTimeoutSec}s"
    Append-Summary 'join_timeout - retrying'
    Start-Sleep -Seconds 15
    continue
  }

  if (-not $script:T2) {
    Start-Sleep -Seconds 5
    [void](Send-Testrun2Chat)
  }

  $progressAt = Get-Date
  $lastFp = ''
  $lastStatus = Get-Date
  while ($true) {
    $clients = @(Get-ClientJavaPids)
    if ($clients.Count -eq 0) {
      Append-Summary 'client died mid-run'
      Save-Status -State 'crashed' -Notes 'client process gone'
      break
    }
    $tail = Update-FromLog
    Copy-NewCrashes
    $fp = ''
    if ($tail -match 'T2 \[NOW\]([^\r\n]+)') { $fp = $Matches[1].Trim() }
    elseif ($tail -match 'ph=([A-Z_]+)') { $fp = 'ph=' + $Matches[1] }
    elseif ($script:Phase) { $fp = 'ph=' + $script:Phase }
    if ($fp -and $fp -ne $lastFp) { $lastFp = $fp; $progressAt = Get-Date }

    if ($script:LastHit -in @('CRASH','FATAL')) {
      Append-Summary "crash hit=$($script:LastHit)"
      Save-Status -State 'crashed' -World $script:World -Phase $script:Phase -Pick $script:Pick -Testrun2 $script:T2
      break
    }
    if ($script:LastHit -eq 'FINISHED') {
      $script:WorldsDone++
      Append-Summary "FINISHED world=$($script:World) worldsDone=$($script:WorldsDone)"
      Save-Status -State 'finished' -World $script:World -Phase $script:Phase -Pick $script:Pick -Testrun2 $true
      break
    }
    if ($script:T2 -and ((Get-Date) -gt $progressAt.AddSeconds($StallSec))) {
      Append-Summary "STALL world=$($script:World) phase=$($script:Phase) pick=$($script:Pick) hit=$($script:LastHit)"
      Save-Status -State 'stalled' -World $script:World -Phase $script:Phase -Pick $script:Pick -Testrun2 $true -Notes "no progress ~${StallSec}s"
      break
    }
    if ((Get-Date) -ge $lastStatus.AddSeconds($StatusEverySec)) {
      Save-Status -State 'running' -World $script:World -Phase $script:Phase -Pick $script:Pick -Testrun2 $script:T2
      $lastStatus = Get-Date
      Write-Host ("[{0}] world={1} phase={2} pick={3} hit={4}" -f (Get-Date -Format 'HH:mm:ss'), $script:World, $script:Phase, $script:Pick, $script:LastHit)
    }
    Start-Sleep -Seconds 5
  }

  $script:T2 = $false
  $script:LastHit = ''
  $script:Phase = ''
  $script:Pick = $null
  if (Focus-Mc) {
    Send-KeysSafe '{ESC}'; Start-Sleep -Milliseconds 800
    Send-KeysSafe '{TAB}{TAB}{ENTER}'; Start-Sleep -Seconds 3
    Send-KeysSafe '{ENTER}'; Start-Sleep -Seconds 5
  }
  Append-Summary 'soft-reset toward title for next 1.16.1 world'
  Start-Sleep -Seconds 8
}