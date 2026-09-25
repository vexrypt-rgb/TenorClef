$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
Set-Location 'C:\Users\redfa\Documents\MinecraftDev\altoclef'
# single-instance lock
$lock = 'C:\Users\redfa\Documents\MinecraftDev\altoclef\logs\overnight.lock'
if (Test-Path $lock) {
  $old = Get-Content $lock -EA SilentlyContinue
  if ($old -and (Get-Process -Id $old -EA SilentlyContinue)) {
    Write-Host "Another overnight already running pid=$old — exit"
    exit 0
  }
}
$PID | Set-Content $lock
Start-Transcript -Path 'C:\Users\redfa\Documents\MinecraftDev\altoclef\logs\overnight-harness-transcript.log' -Force
try {
  & 'C:\Users\redfa\Documents\MinecraftDev\altoclef\scripts\overnight-testrun2.ps1'
} catch {
  Write-Host "FATAL: $_"
  exit 1
} finally {
  Stop-Transcript
  Remove-Item $lock -Force -EA SilentlyContinue
}