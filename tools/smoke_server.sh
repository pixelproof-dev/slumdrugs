#!/usr/bin/env bash
# Starts the NeoForge dedicated server with the mod, headless, and stops it once it reports
# ready. This is the test the Gradle build cannot do: registries, recipes, loot tables,
# advancements and attachments are all only checked when the game loads them. A data file
# that does not parse kills the server before "Done", and this script fails with it.
#
#   tools/smoke_server.sh            # after ./gradlew build
#
# It accepts the EULA for the throwaway run directory under neoforge/run, which is ignored by
# git. Exit status 0 means the server reached "Done" with no ERROR lines from the mod.
set -u
cd "$(dirname "$0")/.."

RUN=neoforge/run
LOG=${SMOKE_LOG:-build/smoke-server.log}
TIMEOUT=${SMOKE_TIMEOUT:-900}
mkdir -p "$RUN" build
rm -rf "$RUN/world"   # a fresh world each run, and no stale session lock
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<'EOF'
online-mode=false
level-seed=slumdrugs
max-tick-time=-1
view-distance=4
simulation-distance=4
EOF

timeout "$TIMEOUT" ./gradlew :neoforge:runServer --no-daemon -q > "$LOG" 2>&1 &
GRADLE=$!
ready=""
for _ in $(seq 1 $((TIMEOUT / 5))); do
    sleep 5
    if grep -q 'Done (' "$LOG" 2>/dev/null; then ready=yes; break; fi
    if grep -q 'FatalStartupException\|Failed to load datapacks' "$LOG" 2>/dev/null; then break; fi
    if ! kill -0 "$GRADLE" 2>/dev/null; then break; fi
done
# The server has no console here, so it is stopped the hard way; the world is throwaway. The
# server JVM is the one carrying the mod-folder flag; Gradle follows once it is gone.
pkill -f 'Dfml.modFolder[s]' 2>/dev/null
sleep 5
pkill -9 -f 'Dfml.modFolder[s]' 2>/dev/null
kill "$GRADLE" 2>/dev/null
wait "$GRADLE" 2>/dev/null

errors=$(grep -c '/ERROR\]' "$LOG" || true)
issues=$(grep -c 'Mods loaded with' "$LOG" || true)
echo "ready=${ready:-no} errors=$errors mod-issues=$issues log=$LOG"
if [ -z "$ready" ] || [ "$errors" != "0" ] || [ "$issues" != "0" ]; then
    grep -n 'ERROR\|Exception\|Caused by\|issues' "$LOG" | grep -v Ambiguity | head -40
    exit 1
fi
grep -n 'Done (' "$LOG"
