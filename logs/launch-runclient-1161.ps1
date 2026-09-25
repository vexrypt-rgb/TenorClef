$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = 'C:/Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
Set-Location 'C:/Users/redfa/Documents/MinecraftDev/altoclef'
$out = 'C:/Users/redfa/Documents/MinecraftDev/altoclef/logs/runclient-1161.log'
$err = 'C:/Users/redfa/Documents/MinecraftDev/altoclef/logs/runclient-1161.err.log'
"LAUNCH $(Get-Date -Format o)" | Out-File -FilePath $out -Encoding utf8
& .\gradlew.bat :1.16.1:runClient --no-daemon *>> $out 2>> $err
"EXITCODE=$LASTEXITCODE $(Get-Date -Format o)" | Out-File -FilePath $out -Append -Encoding utf8
