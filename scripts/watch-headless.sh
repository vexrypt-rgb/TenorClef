#!/usr/bin/env bash
LOG="C:/Users/redfa/Documents/MinecraftDev/altoclef/versions/1.16.1/run/logs/latest.log"
OUT="C:/Users/redfa/Documents/MinecraftDev/altoclef/logs/headless-markers.log"
: > "$OUT"
for i in $(seq 1 240); do
  if [ -f "$LOG" ]; then
    grep -h "AUTOWORLD\|AUTORUN\|TESRUN2\|T2 \[\|Parameters not allowed\|E9[0-9]\|joined the game\|CrashReport\|Exception\|crash" "$LOG" 2>/dev/null | tail -300 > "$OUT"
  fi
  sleep 5
done
