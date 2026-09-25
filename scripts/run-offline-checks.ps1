$ErrorActionPreference = "Continue"
$Repo = "C:\Users\redfa\Documents\MinecraftDev\altoclef"
$JavaHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"
Set-Location $Repo
New-Item -ItemType Directory -Force -Path (Join-Path $Repo "logs") | Out-Null
$out = Join-Path $Repo "logs\offline-checks.log"
$json = Join-Path $Repo "logs\offline-checks.json"
$started = Get-Date
cmd /c "gradlew.bat :1.16.1:test --tests adris.altoclef.benchmark.* --no-daemon > logs\offline-checks.log 2>&1"
$code = $LASTEXITCODE
$tail = ""
if (Test-Path $out) { $tail = (Get-Content $out -Tail 30) -join "`n" }
$result = [ordered]@{
  updated = (Get-Date).ToString("o")
  exitCode = $code
  durationSec = [int]((Get-Date) - $started).TotalSeconds
  task = ":1.16.1:test --tests adris.altoclef.benchmark.*"
  ok = ($code -eq 0)
  tail = $tail
}
$enc = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($json, ($result | ConvertTo-Json -Depth 5), $enc)
Write-Output "offline-checks exit=$code"
exit $code
