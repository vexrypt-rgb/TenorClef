#!/usr/bin/env bash
# digest-baritone.sh — compact Baritone/Ostinato movement summary of a sim run.
# digest-run.sh drops all [Baritone] lines; use this when a stall or death needs movement detail.
#
# Usage:
#   scripts/digest-baritone.sh                          # newest logs/sim-run-*.log
#   scripts/digest-baritone.sh logs/sim-run-X.log
#   scripts/digest-baritone.sh logs/sim-run-X.log 23:08:30 23:09:00   # only this wall-clock window
#   WIN=10 scripts/digest-baritone.sh ...               # bucket size in seconds (default 30)
#
# Per bucket, one line of counters:
#   srch  path searches started      ok/np  found / "No path found"   ms  avg/max A* time
#   goals goal types searched (top 3)   unr  UNREACHABLE   slow  "movement has taken too long"
#   air   low-on-air surfacing   wy  Wrong Y coordinate   spr  sprinting unsafe   ahead  planning ahead
# Rare messages (anything not counted above) print verbatim under the bucket, numbers-insensitive
# duplicates collapsed to xN. Ostinato lines count as rare.

set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOGF="${1:-$(ls -1t "$ROOT"/logs/sim-run-*.log 2>/dev/null | head -1)}"
[ -f "$LOGF" ] || { echo "no log (pass one as \$1)" >&2; exit 1; }
FROM="${2:-00:00:00}"; TO="${3:-99:99:99}"; WIN="${WIN:-30}"
case "$LOGF" in *.gz) CAT="zcat" ;; *) CAT="cat" ;; esac

$CAT "$LOGF" | sed 's/\x1b\[[0-9;]*m//g; s/§.//g' \
  | grep -a -E '\[Baritone\]|Ostinato' | grep -av 'STDOUT\].*\[Baritone\]' \
  | awk -v from="$FROM" -v to="$TO" -v win="$WIN" '
function secs(t,  a) { split(t, a, ":"); return a[1] * 3600 + a[2] * 60 + a[3] }
function flush(   g, top, i, best, bk, out) {
    if (bucket == "") return
    out = ""
    for (i = 0; i < 3; i++) {
        best = 0; bk = ""
        for (g in goals) if (goals[g] > best) { best = goals[g]; bk = g }
        if (bk == "") break
        out = out (out == "" ? "" : ",") bk ":" best; delete goals[bk]
    }
    printf "%s srch=%d ok=%d np=%d ms=%d/%d", bucket, srch, ok, np, (nms ? tms / nms : 0), maxms
    if (out != "") printf " goals=%s", out
    if (unr) printf " unr=%d", unr; if (slow) printf " slow=%d", slow; if (air) printf " air=%d", air
    if (wy) printf " wy=%d", wy; if (spr) printf " spr=%d", spr; if (ahead) printf " ahead=%d", ahead
    printf "\n"
    for (i = 1; i <= nr; i++) print "    " rare[i] (rc[i] > 1 ? "  x" rc[i] : "")
    srch = ok = np = tms = nms = maxms = unr = slow = air = wy = spr = ahead = nr = 0
    delete goals; delete rkey
}
{
    if (!match($0, /^\[[0-9:]+\]/)) next
    t = substr($0, 2, RLENGTH - 2)
    if (t < from || t > to) next
    b = int(secs(t) / win) * win
    if (b != lastb) { flush(); lastb = b; bucket = sprintf("[%02d:%02d:%02d]", b / 3600, (b % 3600) / 60, b % 60) }
    m = $0; sub(/.*\[Baritone\] /, "", m); sub(/.*\[CHAT\] /, "", m); sub(/.*TENORCLEF: /, "", m)
    if (m ~ /^Starting to search for path/) {
        srch++; g = m; sub(/.* to /, "", g); sub(/[{@ ].*/, "", g); sub(/.*\./, "", g); sub(/\$.*/, "", g); goals[g]++
    } else if (m ~ /^Finished finding a path/) ok++
    else if (m ~ /^No path found/) np++
    else if (m ~ /^Took [0-9]+ms/) { v = m; sub(/^Took /, "", v); sub(/ms.*/, "", v); tms += v; nms++; if (v + 0 > maxms) maxms = v + 0 }
    else if (m ~ /UNREACHABLE/) unr++
    else if (m ~ /has taken too long/) slow++
    else if (m ~ /Low on air/) air++
    else if (m ~ /Wrong Y coordinate/) wy++
    else if (m ~ /Sprinting would be unsafe/) spr++
    else if (m ~ /Planning ahead/) ahead++
    else if (m ~ /Favoring size|Path ends within|A\* cost coefficient|Static cutoff|Found path segment|Skipping forward|movements considered|nodes considered|Open set size|PathNode map|nodes per second|Path goes for|FAR AWAY FROM PATH|Blacklist RESET|Death position saved/) next
    else {
        k = m; gsub(/-?[0-9]+(\.[0-9]+)?/, "#", k)
        if (k in rkey) { rc[rkey[k]]++; next }
        nr++; rare[nr] = substr(m, 1, 160); rc[nr] = 1; rkey[k] = nr
    }
}
END { flush() }'
