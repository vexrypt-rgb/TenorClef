#Requires -Version 5.1
<#
.SYNOPSIS
  Launch ONE controlled 1.16.1 sim run and wait for the world to load.

.DESCRIPTION
  A deliberately minimal sibling to sim-loop.ps1. sim-loop is a keep-alive harness that
  restarts worlds forever; when validating a single code change you want exactly one run
  with a known start, and you want to be told the moment AUTOWORLD appears (or the moment
  the client dies), rather than polling by hand.

  What it does, in order:
    1. Refuses to start if a client is already running (two clients share run/ and corrupt
       the logs - this happened once and cost an hour).
    2. Rotates versions/1.16.1/run/logs/latest.log so the new run is unambiguous.
    3. Deletes versions/1.16.1/run/altoclef/faults.log. faults.log is APPEND-ONLY and its
       clock restarts at 0:00 per run, so a stale file makes every fault count wrong.
    4. Re-asserts BOTH settings files (the two-file trap; see docs/SIM_HARNESS.md).
    5. Starts `gradlew.bat :1.16.1:runClient --no-daemon` and streams to logs/sim-run-<tag>.log
    6. Blocks until AUTOWORLD appears, the T2 driver starts, or the timeout expires.
    7. Prints a one-line verdict and tells you to run scripts/analyze-run.sh.

.PARAMETER Tag
  Short label for this run (used in the log filename). e.g. F.

.PARAMETER TimeoutSec
  How long to wait for AUTOWORLD. Cold first-launch can take 6-8 min.

.PARAMETER MaxRunSec
  If >0, kill the client this long after AUTOWORLD was seen. 0 = leave it running.

.EXAMPLE
  powershell -File scripts\run-once.ps1 -Tag F
#>
[CmdletBinding()]
param(
  [string]$Repo = 'C:\Users\redfa\Documents\MinecraftDev\altoclef',
  [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot',
  [string]$Tag = 'run',
  [int]$TimeoutSec = 600,
  [int]$MaxRunSec = 0,
  [string]$Command = 'testrun2',
  [string]$Mover = 'baritone'
)

$ErrorActionPreference = 'Continue'
Set-Location $Repo

if (-not (Test-Path "$JavaHome\bin\java.exe")) {
  if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $JavaHome = $env:JAVA_HOME }
  else { throw "JAVA_HOME invalid: $JavaHome" }
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;" + $env:Path

$RunDir      = Join-Path $Repo 'versions\1.16.1\run'
$LogDir      = Join-Path $Repo 'logs'
$LatestLog   = Join-Path $RunDir 'logs\latest.log'
$FaultsPath  = Join-Path $RunDir 'altoclef\faults.log'
$TopSettings = Join-Path $RunDir 'altoclef_settings.json'
$WorldSettings = Join-Path $RunDir 'altoclef\altoclef_settings.json'
$RunLog      = Join-Path $LogDir ("sim-run-$Tag.log")
$RunErr      = Join-Path $LogDir ("sim-run-$Tag.err.log")

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

Write-Host "=== run-once: tag=$Tag ===" -ForegroundColor Cyan

# ---- 1. refuse to double-start -------------------------------------------------
$existing = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -match '1\.16\.1|fabric-loader|runClient' })
if ($existing.Count -gt 0) {
  Write-Host "ABORT: a 1.16.1 client is already running (pids: $($existing.ProcessId -join ','))" -ForegroundColor Red
  Write-Host "Two clients share versions\\1.16.1\\run and WILL corrupt the logs." -ForegroundColor Red
  Write-Host "Stop it first, or pass a different -Tag after stopping. Nothing was started." -ForegroundColor Red
  exit 2
}

# ---- 2. rotate latest.log ------------------------------------------------------
if (Test-Path $LatestLog) {
  $bak = Join-Path $LogDir ('latest-prestart-' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '.log')
  try { Move-Item $LatestLog $bak -Force } catch { try { Clear-Content $LatestLog } catch {} }
}

# ---- 3. clear faults.log (append-only across runs!) ----------------------------
if (Test-Path $FaultsPath) { Remove-Item $FaultsPath -Force -ErrorAction SilentlyContinue }
if (Test-Path $RunLog)   { Remove-Item $RunLog -Force -ErrorAction SilentlyContinue }
if (Test-Path $RunErr)   { Remove-Item $RunErr -Force -ErrorAction SilentlyContinue }

# ---- 4. re-assert BOTH settings files ------------------------------------------
foreach ($f in @($TopSettings, $WorldSettings)) {
  $dir = Split-Path $f
  if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
  $obj = $null
  if (Test-Path $f) { try { $obj = Get-Content -Raw $f | ConvertFrom-Json } catch { $obj = $null } }
  if (-not $obj) { $obj = [pscustomobject]@{} }
  foreach ($kv in @{ idleCommand=''; speedrunMoverPreference=$Mover; autoLoadWorld=$true; autoRunCommand=$Command }.GetEnumerator()) {
    if ($obj.PSObject.Properties.Name -contains $kv.Key) { $obj.$($kv.Key) = $kv.Value }
    else { $obj | Add-Member -NotePropertyName $kv.Key -NotePropertyValue $kv.Value -Force }
  }
  $enc = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($f, ($obj | ConvertTo-Json -Depth 40), $enc)
}
Write-Host "settings: both files set (autoLoadWorld=true, autoRunCommand=$Command, mover=$Mover)" -ForegroundColor DarkGray

# ---- 5. launch -----------------------------------------------------------------
Write-Host "launching :1.16.1:runClient (log -> $RunLog)" -ForegroundColor Cyan
$proc = Start-Process -FilePath (Join-Path $Repo 'gradlew.bat') `
  -ArgumentList @(':1.16.1:runClient', '--no-daemon') `
  -WorkingDirectory $Repo `
  -RedirectStandardOutput $RunLog -RedirectStandardError $RunErr `
  -PassThru -WindowStyle Normal
Write-Host "gradle pid=$($proc.Id)" -ForegroundColor DarkGray

# ---- 6. wait for AUTOWORLD -----------------------------------------------------
$deadline = (Get-Date).AddSeconds($TimeoutSec)
$worldAt  = $null
$t2At     = $null
while ((Get-Date) -lt $deadline) {
  if ($proc.HasExited) { break }
  if (Test-Path $RunLog) {
    $txt = ''
    try { $txt = Get-Content -Raw $RunLog -ErrorAction SilentlyContinue } catch {}
    if (-not $t2At -and $txt -match 'T2 \[NOW\]') { $t2At = Get-Date }
    if (-not $worldAt -and ($txt -match 'AUTOWORLD' -or $txt -match 'T2 \[NOW\]')) { $worldAt = Get-Date }
    if ($worldAt) { break }
  }
  Start-Sleep -Seconds 3
}

if ($proc.HasExited) {
  Write-Host "CLIENT EXITED EARLY (code $($proc.ExitCode)) - see $RunLog" -ForegroundColor Red
  if (Test-Path $RunErr) { Get-Content $RunErr -Tail 30 }
  exit 1
}
if (-not $worldAt) {
  Write-Host "TIMEOUT after ${TimeoutSec}s waiting for AUTOWORLD. Client is still up." -ForegroundColor Yellow
  Write-Host "Inspect $RunLog - if the window shows a title screen, the auto-world mixin did not fire." -ForegroundColor Yellow
  exit 3
}

# NOTE: '@' must be backtick-escaped inside a double-quoted Write-Host argument.
# Bare '@testrun2' in an argument list is parsed as a SPLAT, which breaks the whole
# statement and cascades into bogus errors on the closing braces below.
Write-Host "AUTOWORLD seen - world loaded, `@testrun2 driving." -ForegroundColor Green
Write-Host "pid=$($proc.Id); faults at $FaultsPath" -ForegroundColor DarkGray

# ---- 7. optionally time-box the run -------------------------------------------
if ($MaxRunSec -gt 0) {
  $killAt = (Get-Date).AddSeconds($MaxRunSec)
  while ((Get-Date) -lt $killAt -and -not $proc.HasExited) { Start-Sleep -Seconds 5 }
  if (-not $proc.HasExited) {
    Write-Host "MaxRunSec=$MaxRunSec reached - stopping client." -ForegroundColor Yellow
    try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch {}
    Start-Sleep -Seconds 3
  }
  Write-Host "run finished. Now: bash scripts/analyze-run.sh logs/sim-run-$Tag.log" -ForegroundColor Green
} else {
  Write-Host "Leaving the client running. Stop it with: Stop-Process -Id $($proc.Id) -Force" -ForegroundColor Green
  Write-Host "Then: bash scripts/analyze-run.sh logs/sim-run-$Tag.log" -ForegroundColor Green
}
exit 0
