# Fix short dry-land @tgoto stall after "Serching for inputs!"
# Run on Windows machineId b7b8e2e7-d629-4a3e-9809-04f69b7bdd10
$ErrorActionPreference = "Stop"
$McDev = "C:\Users\redfa\Documents\MinecraftDev"
$Tenor = Join-Path $McDev "altoclef"
$Agent = if ($env:TUNGSTEN_DRYGOTO_AGENT_DIR) { $env:TUNGSTEN_DRYGOTO_AGENT_DIR } else { Join-Path $PSScriptRoot "tungsten-drygoto" }
if (-not (Test-Path $Agent)) { $Agent = $PSScriptRoot }
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

$files = @(
  "vendor\tungsten-1.16.1\src\main\java\kaptainwutax\tungsten\agent\AgentBlockCollisions.java",
  "vendor\tungsten-1.16.1\src\main\java\kaptainwutax\tungsten\path\Node.java",
  "vendor\tungsten-1.16.1\src\main\java\kaptainwutax\tungsten\path\PathFinder.java",
  "vendor\tungsten-1.16.1\src\main\java\kaptainwutax\tungsten\path\specialMoves\WalkToNode.java",
  "vendor\tungsten-1.16.1\src\main\java\kaptainwutax\tungsten\path\specialMoves\RunToNode.java",
  "vendor\tungsten-1.16.1\src\main\java\kaptainwutax\tungsten\path\specialMoves\EnterWaterAndSwimMove.java"
)

Set-Location $Tenor
foreach ($rel in $files) {
  $src = Join-Path $Agent $rel
  if (-not (Test-Path $src)) { $src = Join-Path $Agent ($rel -replace '\\','/') }
  if (-not (Test-Path $src)) { throw "Missing source $src" }
  $dst = Join-Path $Tenor $rel
  New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null
  Copy-Item -Force $src $dst
  Write-Host "OK $rel"
}

$JarSrc = Join-Path $Agent "tungsten-fabric-ALPHA-1.6.0-1.16.1-SNAPSHOT.jar"
if (-not (Test-Path $JarSrc)) { $JarSrc = Join-Path $PSScriptRoot "tungsten-fabric-ALPHA-1.6.0-1.16.1-SNAPSHOT.jar" }

# Prefer rebuild on Windows to match local toolchain
Set-Location (Join-Path $Tenor "vendor\tungsten-1.16.1")
& ..\..\gradlew.bat --stop 2>$null
& ..\..\gradlew.bat remapJar --no-daemon
if ($LASTEXITCODE -ne 0) { throw "remapJar failed" }

$Built = Get-ChildItem ".\build\libs\tungsten-fabric-ALPHA-1.6.0-1.16.1-SNAPSHOT.jar" | Select-Object -First 1
if (-not $Built) { throw "remapJar produced no jar" }
New-Item -ItemType Directory -Force -Path (Join-Path $Tenor "libs") | Out-Null
Copy-Item -Force $Built.FullName (Join-Path $Tenor "libs\tungsten-fabric-ALPHA-1.6.0-1.16.1-SNAPSHOT.jar")
Write-Host "OK jar staged $($Built.Length) bytes -> libs\"
Write-Host "Smoke: :1.16.1:runClient then @tgoto short flat XYZ; expect path found + walk (not Baritone fallback)."
