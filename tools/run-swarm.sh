#!/bin/bash
# usage: tools/run-swarm.sh host|join <username> <scriptfile> <logname> [extra -D opts]
# Launches one client that runs a swarm test script ("@swarm script"). Does not kill other clients.
cd "$(dirname "$0")/.."
ROLE="$1"; USER="$2"; SCRIPT="$3"; LOG="logs/$4.log"; EXTRA="$5"
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
export JAVA_TOOL_OPTIONS="-Dtenorclef.autorun.command=\"@swarm script $SCRIPT\" -Dtenorclef.swarm.log=true $EXTRA"
if [ "$ROLE" = host ]; then ARGS="--quickPlaySingleplayer SigilTest --username $USER"
else ARGS="--quickPlayMultiplayer localhost:25599 --username $USER"; fi
rm -f logs/move-window.log
(nohup powershell -ExecutionPolicy Bypass -File tools/move-mc-window.ps1 >/dev/null 2>&1 &)
nohup ./gradlew :1.21.11:runClient --no-daemon -PnoTungsten --args="$ARGS" > "$LOG" 2>&1 &
echo launched $ROLE $USER
