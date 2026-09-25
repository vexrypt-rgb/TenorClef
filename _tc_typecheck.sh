#!/usr/bin/env bash
# Cheap ~1-minute incremental typecheck of edited T2 files.
# See skill: mc-1161-mixin-api-verification.
#
# TRAP — a long `-cp` line in an @argfile is SILENTLY TRUNCATED by Windows javac.
# The first version of this script built a classpath by listing every sub-directory
# of the build output (74 entries, 8868 chars on one argfile line). javac dropped
# everything past the parser's line limit and reported "cannot find symbol: class
# AltoClef" for every single source — which looks EXACTLY like a broken classpath in
# the code and sent me bisecting the wrong thing for a while. Classpath entries are
# not additive here anyway: the base `classes/java/main` directory resolves every
# package beneath it, so the whole sub-directory list is redundant. Two entries only.
set -u
cd "C:/Users/redfa/Documents/MinecraftDev/altoclef" || exit 1

JDK="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin/javac.exe"
MCJAR="C:/Users/redfa/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.16.1-net.fabricmc.yarn.1_16_1.1.16.1+build.21-v2/minecraft-merged-1.16.1-net.fabricmc.yarn.1_16_1.1.16.1+build.21-v2.jar"
BASE="C:/Users/redfa/Documents/MinecraftDev/altoclef/versions/1.16.1/build/classes/java/main"
STAGE="C:/Users/redfa/Documents/MinecraftDev/altoclef/_tc_stage"
OUT="_tc_out"

# sponge-mixin + fabric-loader, so ClientTickMixin typechecks too (optional; skip if absent)
MIXIN=$(find ~/.gradle/caches -name "mixin-*.jar" 2>/dev/null | grep -v sources | head -1)
LOADER=$(find ~/.gradle/caches -name "fabric-loader-*.jar" 2>/dev/null | grep -v sources | head -1)
EXTRA=""
[ -n "$MIXIN" ] && EXTRA="$EXTRA;$(cygpath -m "$MIXIN" 2>/dev/null || echo "$MIXIN")"
[ -n "$LOADER" ] && EXTRA="$EXTRA;$(cygpath -m "$LOADER" 2>/dev/null || echo "$LOADER")"

# NOTE: do NOT use `rm -rf` on these directories. A safe-delete wrapper in this environment
# can fail the whole trash operation ("Some operations were aborted") and, because the script
# continues anyway, you get a typecheck that silently reuses the PREVIOUS run's errs.txt and
# reports 0 errors — a false green. Overwrite the individual files instead, and clear the
# classes dir file-by-file.
mkdir -p "$OUT/classes" "$STAGE"
: > "$OUT/sources.txt"
: > "$OUT/args.txt"
: > "$OUT/errs.txt"
find "$OUT/classes" -type f -name '*.class' -delete 2>/dev/null || true
find "$STAGE" -type f -name '*.java' -delete 2>/dev/null || true
# NOTE: any file importing baritone.* (T2Solve, GetOutOfWaterTask) cannot be fully
# typechecked here — baritone is not on this classpath, so javac reports
# "package baritone.api... does not exist" / "cannot access Baritone" at the lines that use
# it. Those are classpath artifacts, not code errors. Check that reported LINE NUMBERS fall
# outside your edit hunks (`git diff -U0 <file> | grep '^@@'`) rather than trusting a zero
# count. GetOutOfWaterTask is excluded entirely for this reason.
for f in \
  src/main/java/adris/altoclef/tasks/speedrun/testrun2/T2Deadman.java \
  src/main/java/adris/altoclef/tasks/speedrun/testrun2/RuinedPortalFinishTask.java \
  src/main/java/adris/altoclef/tasks/speedrun/testrun2/HolePillar.java \
  src/main/java/adris/altoclef/tasks/speedrun/testrun2/WaterBailTask.java \
  src/main/java/adris/altoclef/tasks/speedrun/testrun2/WaterBailTask.java \
  src/main/java/adris/altoclef/tasks/speedrun/testrun2/T2Codes.java \
  src/main/java/adris/altoclef/tasks/speedrun/testrun2/T2Fault.java \
  src/main/java/adris/altoclef/tasks/speedrun/testrun2/ModernSpeedrunTask.java \
  src/main/java/adris/altoclef/tasks/speedrun/testrun2/T2Solve.java \
  src/main/java/adris/altoclef/tasks/speedrun/testrun2/T2Probe.java \
  src/main/java/adris/altoclef/mixins/ClientTickMixin.java ; do
  [ -f "$f" ] || continue
  base=$(basename "$f")
  # mimic the preprocessor: it rewrites the non-1.16.1 entity position helpers.
  sed -e 's/\.getBlockX()/.getBlockPos().getX()/g' \
      -e 's/\.getBlockY()/.getBlockPos().getY()/g' \
      -e 's/\.getBlockZ()/.getBlockPos().getZ()/g' \
      "$f" > "$STAGE/$base"
  echo "$STAGE/$base" >> "$OUT/sources.txt"
done

{
  echo "-proc:none"
  echo "-nowarn"
  echo "-d"
  echo "C:/Users/redfa/Documents/MinecraftDev/altoclef/$OUT/classes"
  echo "-cp"
  echo "$MCJAR;$BASE$EXTRA"
  cat "$OUT/sources.txt"
} > "$OUT/args.txt"

"$JDK" "@$OUT/args.txt" > "$OUT/errs.txt" 2>&1

echo "----- errors by file:line -----"
grep -oP '[^\\/]+\.java:\d+(?=: error)' "$OUT/errs.txt" | sort -t: -k1,1 -k2,2n
echo "----- total -----"
grep -c ': error:' "$OUT/errs.txt"
echo "----- NOT in the known-preprocessor-error families -----"
# The `: error:` line does NOT contain the symbol name — javac prints it a few lines later
# as `  symbol:   ...` (with the offending source line and a caret in between). So a
# `grep -v RAW_IRON` on the error lines can never match, and the first version of this
# filter silently showed every known error as if it were new. Buffer each error line and
# decide when its `symbol:` line arrives.
awk '
  /: error:/ { err = $0; next }
  /^  symbol:/ {
      if (err != "") {
          if ($0 !~ /RAW_IRON/ && $0 !~ /jumpKey/ && $0 !~ /useKey/ &&
              $0 !~ /setPitch/ && $0 !~ /isSolid/ && $0 !~ /getStack/ &&
              $0 !~ /getInventory/) {
              print err
              print $0
          }
          err = ""
      }
      next
  }
' "$OUT/errs.txt"
echo "  (nothing above = only the known families remain)"
