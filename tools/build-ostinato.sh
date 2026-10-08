#!/bin/bash
# Builds the Ostinato 1.21.11 worktree and stages the unoptimized jar where the altoclef 1.21.11 build picks it up.
cd /c/Users/redfa/Documents/MinecraftDev/Ostinato-combat-1.21.11 || exit 1
./gradlew :fabric:build -Pavailable_loaders=fabric --offline -x test -q > /tmp/ost-build.log 2>&1
rc=$?
J=baritone-unoptimized-fabric-1.17.0-167-g25c42e6e-dirty.jar
if [ $rc -eq 0 ]; then
  cp dist/$J ../Ostinato/dist/ && touch ../Ostinato/dist/$J && echo "build ok"
else
  grep -a -E "error:|FAILED|What went wrong" -A3 /tmp/ost-build.log | head -30; echo "build FAILED rc=$rc"
fi
