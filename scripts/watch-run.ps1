param(
    [string]$Repo = "C:\Users\redfa\Documents\MinecraftDev\altoclef",
    [int]$TailLines = 40
)

$ErrorActionPreference = 'Continue'

$runDir   = Join-Path $Repo 'versions\1.16.1\run'
$latest   = Join-Path $runDir 'logs\latest.log'
$debug    = Join-Path $runDir 'logs\debug.log'
$faults   = Join-Path $runDir 'altoclef\faults.log'
$lastRun  = Join-Path $runDir 'altoclef\last_run.log'
$settings = Join-Path $runDir 'altoclef\altoclef_settings.json'

function Age([string]$p) {
    if (-not (Test-Path $p)) { return '(missing)' }
    $t = (Get-Item $p).LastWriteTime
    $d = (Get-Date) - $t
    return ('{0:yyyy-MM-dd HH:mm:ss} ({1:N1}m ago)' -f $t, $d.TotalMinutes)
}

Write-Output "=== FILE FRESHNESS ==="
foreach ($f in @($latest, $debug, $faults, $lastRun, $settings)) {
    Write-Output ("  {0,-22} {1}" -f (Split-Path $f -Leaf), (Age $f))
}

Write-Output ""
Write-Output "=== MARKER CHAIN (latest.log) ==="
if (Test-Path $latest) {
    $marks = @(
        'TENORCLEF: Global Init',
        'AUTOWORLD: fresh-world create armed',
        'AUTOWORLD: creating fresh world',
        'AUTOWORLD: create call returned',
        'Player\d+ joined the game',
        'AUTORUN: armed',
        'AUTORUN: executing',
        'TESRUN2 start',
        'T2 \[I02\]',
        'T2 \[',
        'Baritone world data dir'
    )
    foreach ($m in $marks) {
        $hit = Select-String -Path $latest -Pattern $m -ErrorAction SilentlyContinue | Select-Object -Last 1
        if ($hit) {
            Write-Output ("  [OK ] {0,-38} {1}" -f $m, $hit.Line.Trim())
        } else {
            Write-Output ("  [   ] {0}" -f $m)
        }
    }
} else {
    Write-Output "  latest.log not found yet (client still booting?)"
}

Write-Output ""
Write-Output "=== CRASH SIGNATURES ==="
if (Test-Path $debug) {
    $bad = Select-String -Path $debug -Pattern 'minecraft:origin|Unregistered dimension type|ClassCastException|MixinApplyError|crash report' -ErrorAction SilentlyContinue | Select-Object -Last 5
    if ($bad) { $bad | ForEach-Object { Write-Output ("  ! " + $_.Line.Trim()) } }
    else { Write-Output "  (none)" }
}

Write-Output ""
Write-Output "=== faults.log (last $TailLines) ==="
if (Test-Path $faults) {
    Get-Content $faults -Tail $TailLines
} else {
    Write-Output "  (missing)"
}

Write-Output ""
Write-Output "=== latest.log tail ==="
if (Test-Path $latest) {
    Get-Content $latest -Tail 12 | ForEach-Object { Write-Output ("  " + $_) }
}
