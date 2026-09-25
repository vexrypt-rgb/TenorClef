#!/usr/bin/env bash
# run-F-and-report.sh — post-build checklist for validating the S146 iron-target lock.
#
# WHY A SCRIPT: the validation has four ordered steps and skipping any one produces a
# misleading result. Writing them down once is cheaper than getting it wrong.
#   1. confirm the build actually produced newer classes than sources
#   2. confirm the new symbols exist in the bytecode (a stale jar silently "passes")
#   3. launch ONE clean run
#   4. report, and check the S146 regression test explicitly
#
# Usage:  bash scripts/run-F-and-report.sh [tag]      (default tag: F)

set -uo pipefail
TAG="${1:-F}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CLASS=versions/1.16.1/build/classes/java/main/adris/altoclef/tasks/speedrun/testrun2/ModernSpeedrunTask.class
HIST=versions/1.16.1/build/classes/java/main/adris/altoclef/tasks/speedrun/testrun2/T2History.class
LOG=logs/tc-compile34.log

echo "==================================================================="
echo " STEP 1 - build verdict"
echo "==================================================================="
if grep -qE "BUILD SUCCESSFUL" "$LOG" 2>/dev/null; then
    echo "  BUILD SUCCESSFUL ($(grep -oE 'BUILD SUCCESSFUL in [0-9a-z ]+' "$LOG" | tail -1))"
elif grep -qE "BUILD FAILED" "$LOG" 2>/dev/null; then
    echo "  BUILD FAILED - stop here."
    grep -E "error:" "$LOG" | head -10 | sed 's/^/    /'
    echo "  Reminder: when a build fails in a file you just edited, read"
    echo "  versions/1.16.1/build/preprocessed/main/java/<path>.java FIRST - it"
    echo "  persists after a failed build and shows what javac actually saw."
    exit 1
else
    echo "  build still running (no verdict line yet) - wait, then re-run this script."
    exit 2
fi

echo
echo "==================================================================="
echo " STEP 2 - bytecode contains the new work"
echo "==================================================================="
for f in "$CLASS" "$HIST"; do
    if [ -f "$f" ]; then
        echo "  $(basename "$f"): mtime $(date -r "$f" '+%H:%M:%S')"
    else
        echo "  MISSING: $f"
    fi
done
if command -v javap >/dev/null 2>&1; then
    n=$(javap -p "$CLASS" 2>/dev/null | grep -c ironWant)
    echo "  ironWant occurrences in ModernSpeedrunTask.class : $n  (expect >=1)"
    o=$(javap -p "$HIST" 2>/dev/null | grep -c snapshot)
    echo "  snapshot present in T2History.class               : $o  (expect >=1)"
else
    echo "  (javap not on PATH - skipping bytecode check)"
fi

echo
echo "==================================================================="
echo " STEP 3 - launch"
echo "==================================================================="
echo "  Running: powershell -File scripts/run-once.ps1 -Tag $TAG -MaxRunSec 900"
echo "  (run-once refuses to double-start, clears faults.log, sets BOTH settings files,"
echo "   and waits for AUTOWORLD.) Ctrl-C to stop early; the client keeps running."
echo
echo "  THEN run:  bash scripts/analyze-run.sh logs/sim-run-$TAG.log"
echo
echo "==================================================================="
echo " STEP 4 - what to look for (the S146 pass/fail criteria)"
echo "==================================================================="
cat <<'CRITERIA'
  PASS if ALL of these hold:
    * "IRON target stability" section says PASS - exactly ONE distinct
      "Collecting N iron" value. Run E baseline was 4 (8/20/22/24).
    * exactly ONE "S146 iron target locked" line per IRON entry.
    * the bot reaches PORTAL (or finishes IRON) rather than sitting at iron=0.

  The signal that it is STILL broken: the interleave check in the report shows the
  target alternating, e.g. "106x 8 / 27x 20 / 51x 22 / 102x 24". Alternation means
  isEqualResource still sees a new task each tick.

  Do NOT judge this run by the E70 count alone. Every E70 in every archived run has
  been the mild 6s rung, including the runs where IRON was completely stalled - so a
  low E70 count is NOT evidence of health. Read the rung breakdown.
CRITERIA
echo "==================================================================="
