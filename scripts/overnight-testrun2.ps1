<#
.SYNOPSIS
  Unattended 1.16.1 runClient + @testrun2 overnight harness (Baritone preferred).

.NOTES
  Repo: C:\Users\redfa\Documents\MinecraftDev\altoclef
  JDK:  C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot
  Keep idleCommand empty; send @testrun2 via chat AFTER join.
  Prefer Baritone. No Tungsten movement work here.
#>
[CmdletBinding()]
param(
  [string]$Repo = "C:\Users\redfa\Documents\MinecraftDev\altoclef",
  [string]$JavaHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot",
  [int]$JoinTimeoutSec = 420,
  [int]$StallSec = 90,
  [int]$StatusEverySec = 30,
  [int]$MaxRunSec = 0,
  [switch]$MonitorOnly,
  [switch]$SkipWorldCreate,
  [switch]$ForceKillStale
)

$ErrorActionPreference = "Continue"
# NOTE: Set-StrictMode removed. Under StrictMode, `.Count` on a scalar or $null
# throws "The property 'Count' cannot be found on this object", which aborted the
# 2026-09-20 overnight run with FATAL during create-world. Array access below uses
# explicit @(...) wrapping instead.
Set-Location $Repo

$LogDir       = Join-Path $Repo "logs"
$RunLog       = Join-Path $LogDir "overnight-runclient.log"
$StatusPath   = Join-Path $LogDir "overnight-status.json"
$CrashDir     = Join-Path $LogDir "overnight-crashes"
$PidFile      = Join-Path $LogDir "overnight-gradle.pid"
$LatestLog    = Join-Path $Repo "versions\1.16.1\run\logs\latest.log"
$CrashReports = Join-Path $Repo "versions\1.16.1\run\crash-reports"
$SettingsPath = Join-Path $Repo "versions\1.16.1\run\altoclef_settings.json"
$AgentInbox   = Join-Path $Repo "versions\1.16.1\run\altoclef\agent\inbox.txt"

New-Item -ItemType Directory -Force -Path $LogDir, $CrashDir | Out-Null

function Write-Utf8NoBom([string]$Path, [string]$Content) {
  $enc = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($Path, $Content, $enc)
}

function Get-PtStamp {
  try { $tz = [TimeZoneInfo]::FindSystemTimeZoneById("America/Phoenix") }
  catch { $tz = [TimeZoneInfo]::FindSystemTimeZoneById("US Mountain Standard Time") }
  return [TimeZoneInfo]::ConvertTime([DateTimeOffset]::UtcNow, $tz).ToString("yyyy-MM-ddTHH:mm:sszzz")
}

$script:Status = [ordered]@{
  updated    = (Get-PtStamp)
  state      = "starting"
  pid        = $null
  world      = ""
  phase      = ""
  pick       = $null
  lastLogHit = ""
  blocker    = ""
  notes      = ""
  testrun2   = $false
  mover      = ""
}

function Save-Status {
  param(
    [string]$State,
    [string]$Notes,
    [string]$Blocker
  )
  if ($PSBoundParameters.ContainsKey("State") -and $State) { $script:Status.state = $State }
  if ($PSBoundParameters.ContainsKey("Notes")) { $script:Status.notes = $Notes }
  if ($PSBoundParameters.ContainsKey("Blocker")) { $script:Status.blocker = $Blocker }
  $script:Status.updated = Get-PtStamp
  Write-Utf8NoBom $StatusPath ($script:Status | ConvertTo-Json -Depth 6)
}

function Get-LogTail([int]$Lines = 250) {
  if (-not (Test-Path $LatestLog)) { return "" }
  try { return (Get-Content -Path $LatestLog -Tail $Lines -ErrorAction SilentlyContinue | Out-String) }
  catch { return "" }
}

function Test-JoinSignal([string]$text) {
  if ([string]::IsNullOrEmpty($text)) { return $false }
  # Title-screen init (TENORCLEF Global Init / Setting user) is NOT in-world.
  return ($text -match 'joined the game' -or
          $text -match 'TESRUN2 start' -or
          $text -match 'User Task Set:.*ModernSpeedrun' -or
          $text -match 'Loaded \d+ advancements' -or
          $text -match '\[CHAT\].*joined the game')
}

function Test-ClientUp([string]$text) {
  if ([string]::IsNullOrEmpty($text)) { return $false }
  return ($text -match 'Sound engine started' -or
          $text -match 'FabricLoader' -or
          $text -match 'AltoClef' -or
          $text -match 'TENORCLEF' -or
          $text -match 'Minecraft:')
}

function Update-FromLog {
  $tail = Get-LogTail 300
  if ($tail -match 'ph=([A-Z_]+)') { $script:Status.phase = $Matches[1] }
  elseif ($tail -match 'PHASE[=:]([A-Z_]+)') { $script:Status.phase = $Matches[1] }

  if ($tail -match 'pick=(\d+)') { $script:Status.pick = [int]$Matches[1] }
  elseif ($tail -match 'woodpick=(\d+)') { $script:Status.pick = [int]$Matches[1] }
  elseif ($tail -match 'stonepick=(\d+)') { $script:Status.pick = [int]$Matches[1] }

  if ($tail -match 'mover=([A-Za-z_]+)') { $script:Status.mover = $Matches[1] }

  if ($tail -match 'TESRUN2 start|User Task Set:.*ModernSpeedrun|ModernSpeedrunTask|T2 \[NOW\]|@testrun2') {
    $script:Status.testrun2 = $true
  }

  $patterns = @(
    @{ re = 'T2 \[E94\]';                         name = 'T2_E94' },
    @{ re = 'pick=0';                              name = 'pick0' },
    @{ re = 'PORTAL';                              name = 'PORTAL' },
    @{ re = 'TungstenGoto';                        name = 'TungstenGoto' },
    @{ re = 'FINISHED|User task FINISHED';         name = 'FINISHED' },
    @{ re = '(?i)hard_stuck|hard stuck';           name = 'hard_stuck' },
    @{ re = '(?i)You died|\bdeath\b';               name = 'death' },
    @{ re = '(?i)---- Minecraft Crash Report';      name = 'CRASH' },
    @{ re = '(?i)\bFATAL\b|Bootstrap error';       name = 'FATAL' }
  )
  foreach ($p in $patterns) {
    if ($tail -match $p.re) { $script:Status.lastLogHit = $p.name; break }
  }
  return $tail
}

function Ensure-BaritoneSettings {
  $dir = Split-Path $SettingsPath
  if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

  if (Test-Path $SettingsPath) {
    try {
      $obj = (Get-Content -Raw $SettingsPath) | ConvertFrom-Json
      $changed = $false
      foreach ($name in @("idleCommand","idle_command")) {
        if ($obj.PSObject.Properties.Name -contains $name) {
          if ("$($obj.$name)".Trim().Length -gt 0) { $obj.$name = ""; $changed = $true }
        }
      }
      if (-not ($obj.PSObject.Properties.Name -contains "idleCommand")) {
        $obj | Add-Member -NotePropertyName idleCommand -NotePropertyValue "" -Force
        $changed = $true
      } else {
        if ("$($obj.idleCommand)".Trim().Length -gt 0) { $obj.idleCommand = ""; $changed = $true }
      }
      if ($obj.PSObject.Properties.Name -contains "speedrunMoverPreference") {
        if ("$($obj.speedrunMoverPreference)" -ne "baritone") {
          $obj.speedrunMoverPreference = "baritone"; $changed = $true
        }
      } else {
        $obj | Add-Member -NotePropertyName speedrunMoverPreference -NotePropertyValue "baritone" -Force
        $changed = $true
      }
      if ($changed) {
        Write-Utf8NoBom $SettingsPath ($obj | ConvertTo-Json -Depth 30)
        Write-Host "OK patched altoclef_settings.json (idleCommand='', speedrunMoverPreference=baritone)"
      } else {
        Write-Host "OK settings already idle empty + baritone"
      }
    } catch {
      Write-Host "NOTE: settings patch failed: $_"
    }
  } else {
    $seed = @{ idleCommand = ""; speedrunMoverPreference = "baritone" } | ConvertTo-Json
    Write-Utf8NoBom $SettingsPath $seed
    Write-Host "OK wrote seed altoclef_settings.json"
  }
}

function Get-AltoclefJavaPids {
  $list = New-Object System.Collections.Generic.List[int]
  try {
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | ForEach-Object {
      $cl = $_.CommandLine
      if (-not $cl) { return }
      $hit = $false
      if ($cl -match '1\.16\.1') { $hit = $true }
      if ($cl -match 'MinecraftDev[\\/]+altoclef') { $hit = $true }
      if ($cl -match 'altoclef' -and $cl -match 'fabric-loader') { $hit = $true }
      if ($cl -match 'GradleWrapperMain' -and $cl -match 'runClient' -and $cl -match 'altoclef') { $hit = $true }
      if ($hit) { [void]$list.Add([int]$_.ProcessId) }
    }
  } catch {}
  return ($list | Select-Object -Unique)
}

function Stop-StaleAltoclef {
  $pids = Get-AltoclefJavaPids
  foreach ($procId in $pids) {
    Write-Host "Stopping stale altoclef-related java PID $procId"
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
  }
}

# --- UI helpers (screen automation; needs interactive session) ---
Add-Type @"
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class OvernightMcWin {
  public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr lp);
  [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr hWnd, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
}
"@ | Out-Null
Add-Type -AssemblyName System.Windows.Forms | Out-Null

function Find-McWindow {
  $found = New-Object System.Collections.Generic.List[object]
  try {
    [OvernightMcWin]::EnumWindows({
      param($hwnd, $lp)
      try {
        if (-not [OvernightMcWin]::IsWindowVisible($hwnd)) { return $true }
        $sb = New-Object System.Text.StringBuilder 512
        [void][OvernightMcWin]::GetWindowText($hwnd, $sb, $sb.Capacity)
        $title = $sb.ToString()
        if ([string]::IsNullOrWhiteSpace($title)) { return $true }
        if ($title -match 'Minecraft' -or $title -match '(?i)lwjgl' -or $title -match '1\.16\.1') {
          $procId = [uint32]0
          [void][OvernightMcWin]::GetWindowThreadProcessId($hwnd, [ref]$procId)
          $found.Add([pscustomobject]@{ Hwnd = $hwnd; Title = $title; Pid = $procId })
        }
      } catch {}
      return $true
    }, [IntPtr]::Zero) | Out-Null
  } catch {}
  # Also fall back to java MainWindowHandle with non-empty title
  try {
    Get-Process java,javaw -ErrorAction SilentlyContinue | ForEach-Object {
      if ($_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -and ($_.MainWindowTitle -match 'Minecraft|1\.16|lwjgl')) {
        $found.Add([pscustomobject]@{ Hwnd = ([IntPtr]$_.MainWindowHandle); Title = $_.MainWindowTitle; Pid = [uint32]$_.Id })
      }
    }
  } catch {}
  return ,@($found.ToArray())
}

function Focus-Mc {
  $wins = @(Find-McWindow)
  if ($wins.Length -eq 0) { return $false }
  $w = $wins | Select-Object -First 1
  [void][OvernightMcWin]::ShowWindow($w.Hwnd, 3)
  Start-Sleep -Milliseconds 200
  [void][OvernightMcWin]::SetForegroundWindow($w.Hwnd)
  Start-Sleep -Milliseconds 350
  return $true
}

function Send-KeysSafe([string]$keys) {
  [System.Windows.Forms.SendKeys]::SendWait($keys)
}

function Try-CreateSurvivalEasyWorld {
  Write-Host "Create-world automation (keyboard) - requires UI focus; never Hardcore; never load old save"
  if (-not (Focus-Mc)) {
    Save-Status -State "blocker" -Blocker "need_ui_focus" -Notes "MC window not focused for create-world"
    return $false
  }
  Send-KeysSafe "{ESC}"
  Start-Sleep -Milliseconds 500
  Send-KeysSafe "{ENTER}"   # Singleplayer (often first)
  Start-Sleep -Milliseconds 1000
  # World list: Tab toward Create New World (center = Play Selected - avoid)
  for ($i = 0; $i -lt 6; $i++) { Send-KeysSafe "{TAB}"; Start-Sleep -Milliseconds 120 }
  Send-KeysSafe "{ENTER}"
  Start-Sleep -Milliseconds 1000
  $name = "AutoRun_" + (Get-Date -Format "yyyyMMdd_HHmmss")
  $script:Status.world = $name
  Send-KeysSafe "^a"
  Start-Sleep -Milliseconds 100
  Send-KeysSafe $name
  Start-Sleep -Milliseconds 400
  # More World Options / difficulty cycling - fragile; document in SIM_HARNESS.md
  Send-KeysSafe "{TAB}{TAB}{ENTER}"
  Start-Sleep -Milliseconds 600
  for ($i = 0; $i -lt 4; $i++) { Send-KeysSafe "{TAB}"; Start-Sleep -Milliseconds 100 }
  for ($i = 0; $i -lt 3; $i++) { Send-KeysSafe "{ENTER}"; Start-Sleep -Milliseconds 250 }
  Send-KeysSafe "{TAB}{TAB}{ENTER}"
  Start-Sleep -Milliseconds 800
  Send-KeysSafe "{ENTER}"
  Save-Status -State "creating_world" -Notes "create-world keys sent name=$name (verify Easy Survival, not Hardcore)"
  return $true
}

function Send-Testrun2Chat {
  # idleCommand stays empty - chat after join only
  if (Focus-Mc) {
    Start-Sleep -Milliseconds 400
    Send-KeysSafe "t"
    Start-Sleep -Milliseconds 350
    # Prefer Baritone: settings already patched; command is plain @testrun2
    Send-KeysSafe "@testrun2"
    Start-Sleep -Milliseconds 200
    Send-KeysSafe "{ENTER}"
    $script:Status.testrun2 = $true
    Save-Status -State "running" -Notes "chat @testrun2 sent (SendKeys); idleCommand empty"
    Write-Host "Sent @testrun2 via chat"
    return $true
  }
  try {
    $dir = Split-Path $AgentInbox
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Add-Content -Path $AgentInbox -Value "testrun2" -Encoding utf8
    Save-Status -State "blocker" -Blocker "chat_focus_failed_inbox_written" -Notes "Needs @agent loop for inbox; prefer SendKeys"
    Write-Host "Focus failed; wrote agent inbox (may no-op)"
    return $false
  } catch {
    Save-Status -State "blocker" -Blocker "cannot_send_testrun2" -Notes "$_"
    return $false
  }
}

function Copy-NewCrashes {
  if (-not (Test-Path $CrashReports)) { return }
  Get-ChildItem $CrashReports -Filter "*.txt" -ErrorAction SilentlyContinue | ForEach-Object {
    $dest = Join-Path $CrashDir $_.Name
    if (-not (Test-Path $dest) -or $_.LastWriteTime -gt (Get-Item $dest).LastWriteTime) {
      Copy-Item $_.FullName $dest -Force
      Write-Host "Copied crash $($_.Name) -> overnight-crashes"
    }
  }
}

# ===================== main =====================
Write-Host "=== overnight-testrun2 ==="
Write-Host "Repo=$Repo  JAVA_HOME=$JavaHome"

if (-not (Test-Path "$JavaHome\bin\java.exe")) {
  Save-Status -State "blocker" -Blocker "missing_jdk21" -Notes "JAVA_HOME missing: $JavaHome"
  throw "JDK not found at $JavaHome"
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;" + $env:Path

# Archive stale latest.log so title-screen / prior T2 lines do not fake join/testrun2
if (Test-Path $LatestLog) {
  $bak = Join-Path $LogDir ("latest-prestart-" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".log")
  try { Move-Item $LatestLog $bak -Force } catch { try { Copy-Item $LatestLog $bak -Force; Clear-Content $LatestLog } catch {} }
}
Ensure-BaritoneSettings

$existing = @(Get-AltoclefJavaPids)
$tail0 = Update-FromLog
$alreadyT2 = [bool]$script:Status.testrun2

if ($MonitorOnly -or ($existing.Count -gt 0 -and $alreadyT2 -and -not $ForceKillStale)) {
  Write-Host "Monitor-only: mid-testrun2 (pids=$($existing -join ',')). Not restarting."
  if ($existing.Count -gt 0) { $script:Status.pid = $existing[0] }
  Save-Status -State "running" -Notes "monitor existing testrun2"
} else {
  if ($ForceKillStale -or ($existing.Count -gt 0 -and -not $alreadyT2)) {
    Stop-StaleAltoclef
    Start-Sleep -Seconds 2
  }
  if (Test-Path $RunLog) { Remove-Item $RunLog -Force -ErrorAction SilentlyContinue }
  Save-Status -State "starting" -Notes "launching :1.16.1:runClient"

  $gradle = Join-Path $Repo "gradlew.bat"
  if (-not (Test-Path $gradle)) { throw "gradlew.bat missing in $Repo" }

  $proc = Start-Process -FilePath $gradle `
    -ArgumentList @(":1.16.1:runClient", "--no-daemon") `
    -WorkingDirectory $Repo `
    -RedirectStandardOutput $RunLog `
    -RedirectStandardError ($RunLog + ".err") `
    -PassThru -WindowStyle Normal
  $proc.Id | Set-Content $PidFile
  $script:Status.pid = $proc.Id
  Save-Status -State "waiting_join" -Notes "gradle pid=$($proc.Id)"
  Write-Host "STARTED gradle PID=$($proc.Id)  tee=$RunLog"
}

$joined = $false
$deadline = (Get-Date).AddSeconds($JoinTimeoutSec)
$lastStatus = Get-Date
$attemptedCreate = $false

while ((Get-Date) -lt $deadline) {
  $tail = Update-FromLog
  if ((Get-Date) -ge $lastStatus.AddSeconds($StatusEverySec)) {
    Save-Status
    $lastStatus = Get-Date
  }
  if (Test-JoinSignal $tail) {
    $joined = $true
    Save-Status -State "in_world" -Notes "join signal seen"
    break
  }
  if (-not $SkipWorldCreate -and (Test-ClientUp $tail) -and -not (Test-JoinSignal $tail)) {
    if (-not $attemptedCreate) { Start-Sleep -Seconds 12 }
    try { $ok = Try-CreateSurvivalEasyWorld } catch { Write-Host "create-world error: $_"; $ok = $false }
    if ($ok) { $attemptedCreate = $true }
    elseif (-not $attemptedCreate) { $attemptedCreate = $false }
    # retry focus every ~20s until joined
    Start-Sleep -Seconds 8
  }
  if ($tail -match '(?i)---- Minecraft Crash Report|\bFATAL\b') {
    Copy-NewCrashes
    Save-Status -State "crashed" -Notes "crash during join wait"
    throw "Client crashed during join wait"
  }
  Start-Sleep -Seconds 5
}

if (-not $joined) {
  Save-Status -State "blocker" -Blocker "join_timeout" -Notes "No join within ${JoinTimeoutSec}s - create-world may need interactive UI (see docs/SIM_HARNESS.md)"
  Write-Host "JOIN TIMEOUT - client may still be on title; status blocker set"
} else {
  Start-Sleep -Seconds 5
  $tail = Update-FromLog
  if ($script:Status.testrun2) {
    Save-Status -State "running" -Notes "testrun2 already active after join"
  } else {
    [void](Send-Testrun2Chat)
  }
}

$progressAt = Get-Date
$lastFingerprint = ""
$runDeadline = if ($MaxRunSec -gt 0) { (Get-Date).AddSeconds($MaxRunSec) } else { [datetime]::MaxValue }

Write-Host "Monitor loop stall=${StallSec}s statusEvery=${StatusEverySec}s"
while ((Get-Date) -lt $runDeadline) {
  $tail = Update-FromLog
  Copy-NewCrashes

  $fp = ""
  if ($tail -match 'T2 \[NOW\]([^\r\n]+)') { $fp = $Matches[1].Trim() }
  elseif ($tail -match 'T2 \[HB\]([^\r\n]+)') { $fp = $Matches[1].Trim() }
  elseif ($tail -match 'ph=([A-Z_]+)') { $fp = "ph=" + $Matches[1] }
  elseif ($tail -match 'TESRUN2') { $fp = "TESRUN2" }

  if ($fp -and $fp -ne $lastFingerprint) {
    $lastFingerprint = $fp
    $progressAt = Get-Date
    $script:Status.lastLogHit = $fp.Substring(0, [Math]::Min(120, $fp.Length))
  }

  if ($script:Status.lastLogHit -in @("FATAL","CRASH") -or $tail -match '---- Minecraft Crash Report') {
    Copy-NewCrashes
    Save-Status -State "crashed" -Notes "FATAL/crash detected"
    break
  }
  if ($script:Status.lastLogHit -eq "FINISHED" -or $tail -match 'User task FINISHED') {
    Save-Status -State "finished" -Notes "task finished"
    break
  }
  if ($script:Status.testrun2 -and ((Get-Date) -gt $progressAt.AddSeconds($StallSec))) {
    Save-Status -State "stalled" -Notes "no T2 progress ~${StallSec}s (last=$lastFingerprint)"
  } elseif ($script:Status.testrun2) {
    Save-Status -State "running"
  }

  if ((Get-Date) -ge $lastStatus.AddSeconds($StatusEverySec)) {
    Save-Status
    $lastStatus = Get-Date
    Write-Host ("[{0}] state={1} phase={2} pick={3} hit={4}" -f (Get-PtStamp), $script:Status.state, $script:Status.phase, $script:Status.pick, $script:Status.lastLogHit)
  }
  Start-Sleep -Seconds 5
}

Save-Status
Write-Host "=== overnight-testrun2 exit state=$($script:Status.state) ==="
Write-Host "Status JSON: $StatusPath"
