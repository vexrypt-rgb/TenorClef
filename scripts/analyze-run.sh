#!/usr/bin/env bash
# analyze-run.sh — one-shot health report for a finished (or in-flight) sim run.
#
# Built 2026-09-20 after several sessions of doing all of this by hand with ad-hoc
# greps. Every command here encodes a mistake that was actually made, so read the
# comments before "simplifying" anything.
#
# Usage:
#   scripts/analyze-run.sh                  # newest logs/sim-run-*.log
#   scripts/analyze-run.sh logs/sim-run-D.log
#
# Exit code 0 always (this is a report, not a test).

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

LOGF="${1:-}"
if [ -z "$LOGF" ]; then
    LOGF="$(ls -1t logs/sim-run-*.log 2>/dev/null | head -1)"
fi
if [ -z "$LOGF" ] || [ ! -f "$LOGF" ]; then
    echo "no sim-run log found (pass one as \$1)" >&2
    exit 0
fi

FAULTS="versions/1.16.1/run/altoclef/faults.log"

echo "==================================================================="
echo " run log : $LOGF  ($(wc -c <"$LOGF") bytes, last write $(date -r "$LOGF" '+%H:%M:%S' 2>/dev/null))"
echo " faults  : $FAULTS"
echo "==================================================================="

# ---------------------------------------------------------------- phase / progress
echo
echo "--- phases reached -------------------------------------------------"
grep -oE 'ph=[A-Z]+' "$LOGF" 2>/dev/null | sort -u | tr '\n' ' '; echo
# A single log can hold MORE THAN ONE @testrun2 session. `autoRunCommand` can re-fire
# and the driver re-enters with "onStart resume <PHASE> - pick in inv" + "TESRUN2 clock
# 0:00.0", continuing from the saved phase rather than restarting BOOTSTRAP. Run F had 2
# sessions (clock ran 0->3:01 then reset). Counting per-log without knowing this makes
# every total (faults, E70, S146) look wrong by the width of the earlier session.
NSESS="$(grep -cE 'TESRUN2 clock 0:00\.0' "$LOGF" 2>/dev/null)"
echo "sessions in this log: ${NSESS:-1}"
if [ "${NSESS:-1}" -gt 1 ]; then
    echo "  !! multiple sessions - counts below are for the WHOLE file."
    echo "     session starts at log lines: $(grep -nE 'TESRUN2 clock 0:00\.0' "$LOGF" | cut -d: -f1 | tr '\n' ' ')"
    echo "     resume lines: $(grep -oE 'onStart resume [A-Z]+' "$LOGF" | tr '\n' ' ')"
fi

echo
echo "--- last observed state -------------------------------------------"
grep -oE 'T2 \[NOW\].*' "$LOGF" 2>/dev/null | tail -1

echo
echo "--- inventory progression (pick / iron / buckets) ------------------"
# Read ONLY from `T2 [NOW]` lines. This is not cosmetic: `snapshot()` (the SNAP/CHILD
# lines) uses the SAME `iron=` label but used to mean IRON_INGOT + RAW_IRON, which the
# preprocessor rewrites to IRON_INGOT + IRON_ORE on 1.16.1. Grepping every line for
# `iron=` therefore picked up "iron=19" = nineteen UNMELTED ORE while the bot had zero
# ingots, which reads as "IRON phase healthy" when it is in fact stalled at 0.
# The [NOW] line is the authoritative inventory and is emitted from one place only.
NOW="$(grep -oE 'T2 \[NOW\].*' "$LOGF" 2>/dev/null | tail -1)"
echo "  (source: last [NOW] line)"
for k in woodpick stonepick pick iron buck; do
    v="$(echo "$NOW" | grep -oE "${k}=[0-9]+" | tail -1 | cut -d= -f2)"
    printf '  %-9s = %s\n' "$k" "${v:-?}"
done
# Raw ore is tracked separately in snapshot() as `ore=`.
ORE="$(grep -oE ' ore=[0-9]+' "$LOGF" 2>/dev/null | tail -1 | cut -d= -f2)"
printf '  %-9s = %s\n' "ore(raw)" "${ORE:-0}"

# ---------------------------------------------------------------- fault histogram
echo
echo "--- fault histogram -----------------------------------------------"
if [ ! -f "$FAULTS" ]; then
    echo "  (no faults.log)"
else
    # CRITICAL: faults.log is APPEND-ONLY and spans runs; the clock restarts at 0:00
    # each run. Isolate the LAST run before counting or the histogram mixes runs and
    # lies (this produced a bogus "E70 = 12" that was really run B's lines).
    LAST="$(grep -n '^FAULT 0:00' "$FAULTS" 2>/dev/null | tail -1 | cut -d: -f1)"
    STARTS="$(grep -c '^FAULT 0:00' "$FAULTS" 2>/dev/null)"
    if [ -n "$LAST" ]; then
        echo "  (faults.log contains $STARTS run-boundary marker(s); below is the LAST one only)"
        SEG="$(tail -n +"$LAST" "$FAULTS")"
    else
        SEG="$(cat "$FAULTS")"
    fi
    echo "$SEG" | grep -oE '^FAULT [0-9]+:[0-9]+\.[0-9] [ES][0-9]{2,3}[a-z]?' \
        | grep -oE '[ES][0-9]{2,3}[a-z]?$' | sort | uniq -c | sort -rn \
        | awk '{printf "  %-6s %s\n", $2, $1}'
fi

# ---------------------------------------------------------------- known-bad codes
echo
echo "--- watchlist (these should be 0 unless something regressed) -------"
if [ -f "$FAULTS" ]; then
    LAST="$(grep -n '^FAULT 0:00' "$FAULTS" 2>/dev/null | tail -1 | cut -d: -f1)"
    if [ -n "$LAST" ]; then SEG="$(tail -n +"$LAST" "$FAULTS")"; else SEG="$(cat "$FAULTS")"; fi
    for code in E132 E133 E134 E70 S143 S144 S145 S142 S146; do
        n="$(echo "$SEG" | grep -cE "^FAULT [0-9]+:[0-9]+\.[0-9] $code " 2>/dev/null)"
        note=""
        case "$code" in
            E132) note="shaft stuck x3+ (should be 0 — S142 unban should pre-empt it)";;
            E133) note="pillar ping-pong (should be 0)";;
            E134) note="wood latch expired 60s (0 = wood found in time)";;
            E70)  note="iron stall — any count is fine, but check the Y values differ";;
            S143) note="wood latch arm/release (expected 1-2 per run)";;
            S144) note="S100 escalation fired (expected 0-few)";;
            S145) note="boxed with no blocks — mine cobble (expected 0-few)";;
            S142) note="shaft ban lifted (expected 0-1; >2 means the pillar is looping)";;
            S146) note="iron target locked (EXACTLY 1 per IRON entry; >1 = still re-locking)";;
        esac
        printf '  %-5s %-4s %s\n' "$code" "$n" "$note"
    done
fi

# ---------------------------------------------------------------- IRON target proof
echo
echo "--- IRON target stability (the S146 regression test) ---------------"
# S146 fixed a moving iron target: `min(24,max(8,ironN+ore))` recomputed every tick made
# each distinct value a DIFFERENT CollectIronIngotTask, and ResourceTask.isEqualResource
# compares counts, so a change tore the running collection down and restarted it.
# PASS: exactly one distinct "Collecting N iron" value while in IRON.
IRONVALS="$(grep -oE 'Collecting [0-9]+ iron' "$LOGF" 2>/dev/null | sed 's/Collecting //;s/ iron//' | sort -u)"
if [ -z "$IRONVALS" ]; then
    echo "  (no iron collection lines seen — phase not reached, or all smelting)"
else
    echo "  distinct iron targets seen: $(echo "$IRONVALS" | tr '\n' ' ')"
    nv="$(echo "$IRONVALS" | wc -l)"
    if [ "$nv" -le 1 ]; then
        echo "  PASS — target is stable"
    else
        echo "  !! FAIL — $nv distinct targets: the collection is still being restarted"
    fi
    echo "  interleave check (target, occurrences, in log order — should not alternate):"
    grep -oE 'Collecting [0-9]+ iron' "$LOGF" 2>/dev/null \
        | uniq -c | awk '{printf "    %sx %s\n", $1, $2" "$3" "$4}' | head -12
fi

echo
echo "--- S146 lock lines -------------------------------------------------"
# Count the AUTHORITATIVE signal: the "[HIST] ... FORCE S146" ring-buffer marker, which
# T2Log.force emits exactly once per lock. Do NOT count raw "S146" occurrences: the same
# lock also prints to stdout and (on the re-lock path) as a bare "[S146]" chat line with
# no FORCE prefix, so a raw grep over-counts. Verified in run F: raw S146 = 6 lines,
# FORCE S146 = 3, and the true number of locks was 3.
#
# An IRON entry happens two ways, and both must lock once:
#   1. a phase transition   -> "PHASE <X> -> IRON" via setPhase()
#   2. a @testrun2 RE-ENTRY -> "onStart resume IRON", set directly WITHOUT a transition
#      line (run F resumed twice, at lines 1726 and 2160)
NIRON="$(grep -cE 'PHASE [A-Z]+ -> IRON|onStart resume IRON' "$LOGF" 2>/dev/null)"
NS146="$(grep -cE 'FORCE S146 ' "$LOGF" 2>/dev/null)"
echo "  IRON entries (transitions + resumes): $NIRON"
echo "  S146 locks (FORCE marker):            $NS146"
if [ "$NIRON" = "$NS146" ]; then
    echo "  MATCHED - one lock per IRON entry, as designed"
else
    echo "  NOTE: counts can differ by the E112 revert path (IRON->BOOTSTRAP->IRON within"
    echo "        ~1s, which re-locks). Check the event order before calling it a bug."
fi
grep -oE '\[HIST\][^§]*FORCE S146[^§]*' "$LOGF" 2>/dev/null | sed 's/^/    /' | head -6

# ---------------------------------------------------------------- E70 rungs
echo
echo "--- E70 stall-ladder breakdown -------------------------------------"
# E70 is an ESCALATING LADDER sharing one code across four rungs:
#   6s  clear blacklist + re-pick ore     (mild — often just a bad ore target)
#   12s walk                              (moderate)
#   50s wander                            (severe)
# plus the post-combat kick. "E70 = 3" is therefore ambiguous: three 6s rungs is
# benign, 6s+12s+50s means the collector is genuinely wedged. Always break it down.
if [ -f "$FAULTS" ]; then
    LAST="$(grep -n '^FAULT 0:00' "$FAULTS" 2>/dev/null | tail -1 | cut -d: -f1)"
    if [ -n "$LAST" ]; then SEG="$(tail -n +"$LAST" "$FAULTS")"; else SEG="$(cat "$FAULTS")"; fi
    echo "$SEG" | grep -E '^FAULT [0-9]+:[0-9]+\.[0-9] E70 ' | sed 's/.*E70 //' \
        | sed 's/ @.*//' | sort | uniq -c | sort -rn | sed 's/^/  /'
    echo "  (blank = no E70 this run)"
fi

# ---------------------------------------------------------------- movement health
echo
echo "--- movement health ------------------------------------------------"
# A low distinct-XZ count over a long run means the bot was pinned.
DISTINCT="$(grep -oE 'ph=[A-Z]+ @[-0-9]+,[0-9]+,[-0-9]+' "$LOGF" 2>/dev/null \
    | grep -oE '@[-0-9]+,[0-9]+,[-0-9]+' | sort -u | wc -l)"
echo "  distinct positions : $DISTINCT"
if [ "$DISTINCT" -lt 20 ]; then
    echo "  !! very low — the bot spent most of the run at one spot"
fi
# Where did it spend the most time?
echo "  top 5 dwell positions:"
grep -oE '@[-0-9]+,[0-9]+,[-0-9]+' "$LOGF" 2>/dev/null | sort | uniq -c | sort -rn \
    | head -5 | awk '{printf "    %-22s x%s\n", $2, $1}'

# ---------------------------------------------------------------- shaft digging
echo
echo "--- vertical shaft digging (the IRON-phase wall-clock killer) ------"
# WHY THIS METRIC EXISTS
# `CollectIronIngotTask` -> SmeltInFurnaceTask -> TaskCatalogue "raw_iron" ->
# MineAndCollectTask. That task DOES filter unreachable ore, but mining straight DOWN is
# honest progress: the bot never trips progressChecker, so requestBlockUnreachable is
# never called and the same column is re-targeted forever. Measured in run E: the bot
# descended @191,64,69 -> @191,58,69 one block per second, sat at the bottom ~59s, then
# pillared back up — 15 such columns out of 112 visited.
# A "shaft column" here = one (x,z) where the bot occupied >3 distinct Y values.
POSN="$(grep -oE 'ph=[A-Z]+ @[-0-9]+,[0-9]+,[-0-9]+' "$LOGF" 2>/dev/null | sed 's/.*@//')"
if [ -n "$POSN" ]; then
    COLS="$(echo "$POSN" | awk -F, '{print $1","$3}' | sort -u | wc -l)"
    echo "$POSN" | awk -F, '{print $1","$3" "$2}' | sort -u \
        | awk '{split($1,a,","); c[a[1]","a[2]]++}
               {if ($2+0 < miny || NR==1) miny=$2+0; if ($2+0 > maxy) maxy=$2+0}
               END {n=0
                    for (k in c) if (c[k]>3) {n++; if (n<=5) print "  shaft "k" -> "c[k]" distinct Y"}
                    printf "  shaft columns (>3 distinct Y) : %d of %d visited (%.0f%%)\n", n, length(c), 100*n/length(c)
                    printf "  Y range explored             : %d .. %d\n", miny, maxy}'
    echo "  (each shaft = a dig-down/pillar-up cycle; more shafts = worse)"
fi

# ---------------------------------------------------------------- S147 sticky ban
echo
echo "--- S147 sticky column ban (re-arm-at-one-column fix) --------------"
# WHY THIS METRIC EXISTS
# The ordinary banTicks ban clears on `manh >= 2`, which a pillar hop produces instantly,
# so E133's banTicks=400 never held. Run F armed S130 8x at -132,210 and 10x at -85,222.
# S147 refuses S130 at a column until the bot has genuinely left it.
# PASS = S147 arms a few times, S148 lifts them, and no column re-arms S130 repeatedly.
NS147="$(grep -c 'T2 \[S147\]' "$LOGF" 2>/dev/null)"
NS148="$(grep -c 'T2 \[S148\]' "$LOGF" 2>/dev/null)"
NREARM="$(grep -c 'T2 \[S147\] re-arm in sticky column' "$LOGF" 2>/dev/null)"
echo "  S147 arms      : $NS147   (a sticky ban was placed)"
echo "  S148 lifts     : $NS148   (an arm was lifted after a real departure)"
echo "  re-arms blocked: $NREARM   (S130 attempted inside a still-banned column)"
if [ -n "$NS147" ] && [ "$NS147" != "0" ]; then
    echo "  top sticky columns:"
    grep -oE 'T2 \[S147\] sticky ban arm @[-0-9]+,[-0-9]+' "$LOGF" 2>/dev/null \
        | sed 's/.*@//' | sort | uniq -c | sort -rn | head -5 \
        | awk '{printf "    %-16s x%s\n", $2, $1}'
    # The whole point: did any ONE column eat many arms after the fix?
    WORST="$(grep -oE 'sticky ban arm @[-0-9]+,[-0-9]+' "$LOGF" 2>/dev/null | sed 's/.*@//' \
        | sort | uniq -c | sort -rn | head -1 | awk '{print $1+0}')"
    if [ "${WORST:-0}" -le 2 ] 2>/dev/null; then
        echo "  VERDICT: PASS - worst column armed S147 ${WORST}x (was 10x before the fix)"
    else
        echo "  VERDICT: REVIEW - worst column still armed S147 ${WORST}x"
    fi
else
    echo "  VERDICT: N/A - no ping-pong column reached yet (fine for a short run)"
fi

# ---------------------------------------------------------------- S151 surface-bail downward fallback
echo
echo "--- S151 surface-bail downward fallback (void-fall fix) ------------"
NS151="$(grep -c 'T2 \[S151\]' "$LOGF" 2>/dev/null)"
echo "  S151 downward dest found: $NS151"
if [ -n "$NS151" ] && [ "$NS151" != "0" ]; then
    echo "  VERDICT: PASS - SurfaceBailTask found a downward escape at least once"
else
    echo "  VERDICT: N/A - bot never needed the downward fallback this run"
fi

# ---------------------------------------------------------------- last faults
echo
echo "--- last 10 fault lines (raw) --------------------------------------"
tail -10 "$FAULTS" 2>/dev/null | sed 's/^/  /'

echo
echo "--- last 6 chat lines ---------------------------------------------"
tail -60 "$LOGF" 2>/dev/null | grep -oE 'T2 \[(NOW|HIST)\].*' | tail -6 | sed 's/^/  /'

echo
echo "==================================================================="
