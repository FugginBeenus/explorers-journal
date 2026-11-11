#!/bin/zsh
# Makes a test world for a Minecraft version that has none, with the same seed as the 1.21.1 test world.
# A test server builds the ground around the village a few chunks at a time, then stops.
# Then the world goes to run-Dev/saves/TestWorld, set to creative, with commands on, and spawn at the village.
#   scripts/make-world.sh 1.20.1
# It will not replace a TestWorld that is already there. Move that one away first.
P="$(cd "$(dirname "$0")/.." && pwd)"
V=${1:?usage: scripts/make-world.sh <version>}
SEED=4444343672703342628
LOGS="$P/build/test-logs"
R="$P/versions/$V"
if [ -e "$R/run-Dev/saves/TestWorld" ]; then
  echo "$R/run-Dev/saves/TestWorld is already there. Move it away first."
  exit 1
fi
mkdir -p "$LOGS" "$R/run-server" "$R/run-Dev/saves"
cd "$P"
echo "eula=true" > "$R/run-server/eula.txt"
if [ ! -f "$R/run-server/server.properties" ]; then
  printf 'level-seed=%s\nonline-mode=false\nenforce-secure-profile=false\ngamemode=survival\ndifficulty=easy\nspawn-protection=0\nview-distance=8\nsimulation-distance=6\n' $SEED > "$R/run-server/server.properties"
elif ! grep -q "^level-seed=$SEED" "$R/run-server/server.properties"; then
  sed -i '' "s/^level-seed=.*/level-seed=$SEED/" "$R/run-server/server.properties"
fi
rm -rf "$R/run-server/world" "$R/run-server/ejtest.log"
./gradlew :${V}:runTestServer -Pejscript=pregen --console=plain > "$LOGS/$V-pregen.log" 2>&1 &
G=$!
for i in {1..1500}; do kill -0 $G 2>/dev/null || break; sleep 1; done
if kill -0 $G 2>/dev/null; then echo "still running after 1500 s, stopping it"; pkill -f "ej.script=pregen"; fi
wait $G
cat "$R/run-server/ejtest.log" 2>/dev/null
cp -R "$R/run-server/world" "$R/run-Dev/saves/TestWorld"
rm -rf "$R/run-Dev/saves/TestWorld/data/explorers_journal" "$R/run-Dev/saves/TestWorld/session.lock"
python3 "$P/scripts/world.py" "$R/run-Dev/saves/TestWorld/level.dat"
[ -f "$R/run-Dev/options.txt" ] || cp "$P/versions/1.21.1/run-Dev/options.txt" "$R/run-Dev/options.txt"
