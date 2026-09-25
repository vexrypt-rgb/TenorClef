#!/usr/bin/env bash
# digest-run.sh — compact, deduplicated timeline of a sim run (for humans and for LLM context).
#
# Usage:
#   scripts/digest-run.sh                    # newest logs/sim-run-*.log
#   scripts/digest-run.sh logs/sim-run-X.log
#   scripts/digest-run.sh file.log.gz        # historical client logs work too
#   HB=30 scripts/digest-run.sh ...          # heartbeat interval in seconds (default 60)
#
# What it removes:
#   - ANSI colours; the [CHAT] and "T2 [HIST] ... FORCE/SOLVE" copies of every STDOUT event
#   - Baritone path stats, "FAR AWAY FROM PATH", region saves, "Blacklist RESET", stack frames
#   - T2 [NOW] heartbeats, except when phase or child changes, or every $HB seconds
# What it keeps: T2 codes, phase/child changes, deaths, harness (AUTOWORLD/AUTORUN/DEADMAN), resets.
# Consecutive repeats of the same event (ignoring numbers) collapse into one line with xN.

set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOGF="${1:-$(ls -1t "$ROOT"/logs/sim-run-*.log 2>/dev/null | head -1)}"
[ -f "$LOGF" ] || { echo "no log (pass one as \$1)" >&2; exit 1; }
HB="${HB:-60}"

case "$LOGF" in *.gz) CAT="zcat" ;; *) CAT="cat" ;; esac

$CAT "$LOGF" | sed 's/\x1b\[[0-9;]*m//g; s/§.//g' | awk -v hb="$HB" '
function flush() { if (prev != "") { print prev (n > 1 ? "  x" n : "") } prev = ""; n = 0 }
function emit(line,   key) {
    key = line; sub(/^\[[0-9:]+\] /, "", key); gsub(/-?[0-9]+(\.[0-9]+)?/, "#", key)
    if (key == pkey) { n++; return }
    flush(); prev = line; pkey = key; n = 1
}
{
    ts = ""; if (match($0, /^\[[0-9:]+\]/)) ts = substr($0, RSTART, RLENGTH)
    line = $0
    # Drop noise and duplicate channels.
    if (line ~ /\[CHAT\] \[TenorClef\]/) next
    if (line ~ /T2 \[HIST\] [0-9:.]+ (FORCE|SOLVE|SNAP|\[[ES][0-9])/) next
    if (line ~ /FAR AWAY FROM PATH|Blacklist RESET|Saving region|Saved region|World save took|movements considered|Open set size|PathNode map size|nodes per second|Path goes for|Saving chunks for level|^[ \t]+at |STDOUT\]:[ \t]+at /) next
    if (line ~ /FabricLoader\/Mixin\) Error loading class|Reloading ResourceManager/) next
    # Heartbeat: keep on phase/child change or every hb seconds of run clock.
    if (line ~ /T2 \[NOW\]/) {
        ph = line; sub(/.* ph=/, "", ph); sub(/ .*/, "", ph)
        ch = line; sub(/.* do=</, "", ch); sub(/[:>].*/, "", ch)
        clk = line; sub(/.*T2 \[NOW\] t=/, "", clk); sub(/ .*/, "", clk)
        split(clk, c, ":"); secs = c[1] * 60 + c[2]
        if (ph == lph && ch == lch && secs - lsecs < hb && secs >= lsecs) next
        lph = ph; lch = ch; lsecs = secs
        sub(/.*T2 \[NOW\] /, "", line); emit(ts " NOW " line); next
    }
    keep = 0
    if (line ~ /ALTO CLEF: WARNING: /) { sub(/.*ALTO CLEF: WARNING: /, "", line); keep = 1 }
    else if (line ~ /TENORCLEF: /) { sub(/.*TENORCLEF: /, "", line); keep = 1 }
    else if (line ~ /T2 \[HIST\] [0-9:.]+ (PHASE|CHILD|JUMP|DIM|WHY)/) { sub(/.*T2 \[HIST\] /, "HIST ", line); keep = 1 }
    else if (line ~ /\[Server thread\/INFO\].*(fell|was |drowned|burned|blew|tried to|suffocated|died|joined|left the game|Stopping)/) { sub(/.*\(Minecraft\) /, "", line); keep = 1 }
    else if (line ~ /RESPAWNING|TESRUN2|AUTOWORLD|AUTORUN|Loading Minecraft|FAILED|Exception|Crash/) { sub(/^\[[0-9:]+\] \[[^]]*\] (\([^)]*\) )?(\[CHAT\] |\[STDOUT\]: )?/, "", line); sub(/.*\[TenorClef\] /, "", line); keep = 1 }
    if (keep) emit(ts " " substr(line, 1, 220))
}
END { flush() }'
