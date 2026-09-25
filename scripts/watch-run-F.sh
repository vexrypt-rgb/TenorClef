#!/usr/bin/env bash
# One-shot snapshot of the live run: phase, progress and the last few events.
# Safe to run at any time while a client is up - read-only.
LOG="${1:-logs/sim-run-F.log}"
if [ ! -f "$LOG" ]; then echo "no log at $LOG"; exit 1; fi

echo "=== $LOG  ($(wc -l < "$LOG") lines, $(du -h "$LOG" | cut -f1)) ==="
echo
echo "--- phase chain ---"
grep -oE "PHASE [A-Z]+ -> [A-Z]+" "$LOG" | uniq -c
echo
echo "--- samples per phase ---"
grep "T2 \[NOW\]" "$LOG" | grep -oE "ph=[A-Z]+" | sort | uniq -c
echo
echo "--- last sample ---"
grep "T2 \[NOW\]" "$LOG" | tail -1 | sed -E 's/.*\] //'
echo
echo "--- error codes (top 15) ---"
grep -oE "T2 \[[ES][0-9]+\]" "$LOG" | sort | uniq -c | sort -rn | head -15
echo
echo "--- last 8 history events ---"
grep "T2 \[HIST\]" "$LOG" | tail -8 | sed -E 's/.*T2 \[HIST\] [0-9:.]* //'
