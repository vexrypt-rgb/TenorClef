#!/bin/bash
# usage: tools/run-duo.sh leader|worker <logname>   (leader first; worker ~60s later, once the leader's world is up)
cd "$(dirname "$0")/.."
ROLE="$1"; LOG="logs/$2.log"
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
if [ "$ROLE" = leader ]; then
  powershell -c "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | ?{\$_.CommandLine -match 'KnotClient'} | %{ Stop-Process -Id \$_.ProcessId -Force }"
  export JAVA_TOOL_OPTIONS="-Dtenorclef.autorun.command=\"@swarm dlead SwarmB 3\" -Dtenorclef.swarm.log=true"
  ARGS="--quickPlaySingleplayer SigilTest --username SwarmA"
else
  export JAVA_TOOL_OPTIONS="-Dtenorclef.autorun.command=\"@swarm djoin SwarmA\" -Dtenorclef.swarm.tp=\"0 69 0\""
  ARGS="--quickPlayMultiplayer localhost:25599 --username SwarmB"
fi
rm -f logs/move-window.log
(nohup powershell -ExecutionPolicy Bypass -File tools/move-mc-window.ps1 >/dev/null 2>&1 &)
nohup ./gradlew :1.21.11:runClient --no-daemon -PnoTungsten --args="$ARGS" > "$LOG" 2>&1 &
echo launched $ROLE
