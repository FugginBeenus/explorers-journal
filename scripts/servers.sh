#!/bin/zsh
# Runs the dedicated server scripts on one Minecraft version: Alice and Bob on a test server.
#   scripts/servers.sh 1.21.1
# The server uses versions/<version>/run-server, with online-mode=false.
P="$(cd "$(dirname "$0")/.." && pwd)"
V=${1:-1.21.1}
LOGS="$P/build/test-logs"
mkdir -p "$LOGS"
cd "$P"
R=versions/$V

waitfor() {
  local i=0
  while ! grep -q "$2" "$1" 2>/dev/null; do
    sleep 2
    i=$((i+2))
    if [ $i -ge $3 ]; then echo "timeout waiting for '$2' in $1"; return 1; fi
  done
}

server() {
  ./gradlew :${V}:runTestServer -Pejscript=$1 --console=plain > "$LOGS/$V-server-$1.log" 2>&1 &
  SRV=$!
  waitfor "$LOGS/$V-server-$1.log" 'Done (' 200
}

client() {
  ./gradlew :${V}:runTestClient -Puser=$1 -Pjoin=localhost:25565 -Pejscript=$2 --console=plain > "$LOGS/$V-$1-$2.log" 2>&1
  echo "$1 $2 exit $?"
}

clear_logs() {
  rm -f $R/run-server/ejtest.log $R/run-Alice/ejtest.log $R/run-Bob/ejtest.log
}

show() {
  for f in server Alice Bob; do
    echo "--- $f"
    cat $R/run-$f/ejtest.log 2>/dev/null
  done
}

# a note and a map, read by a second player, across a server restart
clear_logs
server mp1
client Alice alice1 &
A=$!
waitfor $R/run-Alice/ejtest.log 'waiting for bob' 200
client Bob bob1
wait $A
wait $SRV
server mp2
client Alice alice2
wait $SRV
server mp1b
client Alice alice3 &
A=$!
waitfor $R/run-Alice/ejtest.log 'alice marked a place' 200
client Bob bob3
wait $A
wait $SRV
show

# a slow server, about 6 ticks a second
clear_logs
server mp3
client Alice alice4 &
A=$!
waitfor $R/run-Alice/ejtest.log 'alice is waiting for bob' 280
client Bob bob4
wait $A
wait $SRV
echo "--- slow server: Alice $(grep -c PASS $R/run-Alice/ejtest.log) passed, $(grep -c FAIL $R/run-Alice/ejtest.log) failed; Bob $(grep -c PASS $R/run-Bob/ejtest.log) passed, $(grep -c FAIL $R/run-Bob/ejtest.log) failed"

# a sketch, a drawing and a note on a server
clear_logs
server mp1sketch
client Alice alice5 &
A=$!
waitfor $R/run-Alice/ejtest.log 'alice is waiting for bob' 280
client Bob bob5
wait $A
wait $SRV
show
echo "--- sketch on a server: Alice $(grep -c PASS $R/run-Alice/ejtest.log) passed, $(grep -c FAIL $R/run-Alice/ejtest.log) failed; Bob $(grep -c PASS $R/run-Bob/ejtest.log) passed, $(grep -c FAIL $R/run-Bob/ejtest.log) failed"
