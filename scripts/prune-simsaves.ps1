#Requires -Version 5.1
<#
.SYNOPSIS
  Report (and optionally archive) accumulated SIM world saves for 1.16.1.

.DESCRIPTION
  Each headless sim cycle creates a fresh world named AutoRun_yyyyMMdd_HHmmss.
  Left alone these pile up in versions/1.16.1/run/saves and slow the world-list
  load. This script is SCAN-ONLY by default.

  It never deletes anything. With -Archive it MOVES old AutoRun_* worlds into
  logs/archived-saves/ (still on disk, just out of saves/). Even then it only
  touches folders matching the AutoRun_* pattern created by this harness — it
  will not touch 'New World*' or anything a human named.

.EXAMPLE
  # See what exists (default, safe)
  powershell -ExecutionPolicy Bypass -File .\scripts\prune-simsaves.ps1

.EXAMPLE
  # Move AutoRun_* worlds older than 1 day into the archive folder
  powershell -ExecutionPolicy Bypass -File .\scripts\prune-simsaves.ps1 -Archive -OlderThanDays 1
#>
[CmdletBinding()]
param(
  [string]$Repo = 'C:\Users\redfa\Documents\MinecraftDev\altoclef',
  [switch]$Archive,
  [int]$OlderThanDays = 0,
  [string]$ArchiveDir = ''
)

$ErrorActionPreference = 'Continue'
$SavesDir = Join-Path $Repo 'versions\1.16.1\run\saves'
if (-not $ArchiveDir) { $ArchiveDir = Join-Path $Repo 'logs\archived-saves' }

if (-not (Test-Path $SavesDir)) {
  Write-Host "No saves dir at $SavesDir"
  exit 0
}

$cutoff = (Get-Date).AddDays(-$OlderThanDays)

Write-Host "Saves dir : $SavesDir"
Write-Host "Mode      : $(if ($Archive) { 'ARCHIVE (move)' } else { 'SCAN ONLY (no changes)' })"
Write-Host "OlderThan : $OlderThanDays day(s)  (cutoff $($cutoff.ToString('s')))"
Write-Host ''

$all = @(Get-ChildItem -Path $SavesDir -Directory -ErrorAction SilentlyContinue)
# Only harness-created worlds are ever eligible.
$candidates = @($all | Where-Object { $_.Name -like 'AutoRun_*' })
$protected  = @($all | Where-Object { $_.Name -notlike 'AutoRun_*' })

Write-Host ("Harness worlds (AutoRun_*) : {0}" -f $candidates.Count)
Write-Host ("Other worlds (never touched): {0}" -f $protected.Count)
Write-Host ''

foreach ($w in $protected) {
  Write-Host ("  PROTECTED  {0,-28} lastWrite={1}" -f $w.Name, $w.LastWriteTime)
}
Write-Host ''

$eligible = @($candidates | Where-Object { $_.LastWriteTime -lt $cutoff })
$keep     = @($candidates | Where-Object { $_.LastWriteTime -ge $cutoff })

Write-Host ("Eligible to archive: {0}" -f $eligible.Count)
foreach ($w in $eligible) {
  Write-Host ("  ARCHIVE    {0,-28} lastWrite={1}" -f $w.Name, $w.LastWriteTime)
}
Write-Host ("Keeping            : {0}" -f $keep.Count)
foreach ($w in $keep) {
  Write-Host ("  KEEP       {0,-28} lastWrite={1}" -f $w.Name, $w.LastWriteTime)
}

if (-not $Archive) {
  Write-Host ''
  Write-Host 'SCAN ONLY — nothing was moved. Re-run with -Archive to move the listed worlds.'
  exit 0
}

if ($eligible.Count -eq 0) {
  Write-Host ''
  Write-Host 'Nothing eligible; no changes.'
  exit 0
}

New-Item -ItemType Directory -Force -Path $ArchiveDir | Out-Null
Write-Host ''
Write-Host "Archiving to $ArchiveDir"

foreach ($w in $eligible) {
  $dest = Join-Path $ArchiveDir $w.Name
  if (Test-Path $dest) {
    # Never overwrite an existing archive copy.
    Write-Host ("  SKIP (exists) {0}" -f $w.Name)
    continue
  }
  try {
    Move-Item -LiteralPath $w.FullName -Destination $dest -ErrorAction Stop
    Write-Host ("  MOVED  {0}" -f $w.Name)
  } catch {
    Write-Host ("  FAIL   {0} : {1}" -f $w.Name, $_)
  }
}

Write-Host ''
Write-Host 'Done. Worlds are in the archive folder, not deleted.'
