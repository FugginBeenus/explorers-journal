#!/bin/zsh
# Runs single player test scripts on one Minecraft version, in order, and prints the results.
#   scripts/test.sh 1.21.1 place nav
# Needs the test world at versions/<version>/run-Dev/saves/TestWorld.
# EJUSER and WORLD pick another game folder and world, like EJUSER=LT WORLD=LinkWorld.
P="$(cd "$(dirname "$0")/.." && pwd)"
V=$1
shift
if [ -z "$V" ] || [ $# -eq 0 ]; then
  echo "usage: scripts/test.sh <version> <script> [script...]"
  exit 1
fi
U=${EJUSER:-Dev}
W=${WORLD:-TestWorld}
LOGS="$P/build/test-logs"
mkdir -p "$LOGS"
cd "$P"
L=versions/$V/run-$U/ejtest.log
rm -f $L
for s in "$@"; do
  ./gradlew :${V}:runTestClient -Pejscript=$s -Puser=$U -Pworld=$W --console=plain > "$LOGS/$V-$s.log" 2>&1
  echo "$s exit $?"
  grep -E "Exception|Caused by|at com.fugginbeenus" "$LOGS/$V-$s.log" | grep -v "MalformedJsonException\|JsonSyntaxException" | head -8
done
grep -v "^screenshot" $L
echo "--- $V, $U: $(grep -c PASS $L) passed, $(grep -c FAIL $L) failed"
