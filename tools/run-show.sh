#!/bin/bash
# usage: tools/run-show.sh "<show command>" <logname> [extra -D opts]
# Kills any KnotClient, launches a headless-autorun client on the secondary monitor, waits for SHOWRESULT/idle, then kills it.
cd "$(dirname "$0")/.."
CMD="$1"; LOG="logs/$2.log"; EXTRA="$3"
powershell -c "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | ?{\$_.CommandLine -match 'KnotClient'} | %{ Stop-Process -Id \$_.ProcessId -Force }"
rm -f logs/move-window.log
(nohup powershell -ExecutionPolicy Bypass -File tools/move-mc-window.ps1 >/dev/null 2>&1 &)
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
export JAVA_TOOL_OPTIONS="-Dtenorclef.autorun.command=\"$CMD\" $EXTRA"
nohup ./gradlew :1.21.11:runClient --no-daemon -PnoTungsten --args="--quickPlaySingleplayer SigilTest" > "$LOG" 2>&1 &
echo launched
