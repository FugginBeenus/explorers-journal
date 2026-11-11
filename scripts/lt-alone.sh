#!/bin/zsh
# Starts Location Tooltip by itself, with no journal, in its own dev game on a copy of its own world.
# It checks that the game opens the world and runs for 45 seconds with no errors. Then it puts everything back.
#   scripts/lt-alone.sh
# LT is the Location Tooltip folder. By default it sits next to this one.
P="$(cd "$(dirname "$0")/.." && pwd)"
LT=${LT:-"$P/../locationtooltip-clean-start"}
LOGS="$P/build/test-logs"
R="$LT/versions/1.21.1/run"
mkdir -p "$LOGS"
rm -rf "$R/saves/EJ-LTAlone"
cp -R "$R/saves/New World (1)" "$R/saves/EJ-LTAlone"
cp "$R/options.txt" "$LOGS/lt-options-backup.txt"
if grep -q "^pauseOnLostFocus:" "$R/options.txt"; then sed -i '' 's/^pauseOnLostFocus:.*/pauseOnLostFocus:false/' "$R/options.txt"; else echo "pauseOnLostFocus:false" >> "$R/options.txt"; fi
cd "$LT"
./gradlew :1.21.1:runClient --args="--quickPlaySingleplayer EJ-LTAlone" --console=plain > "$LOGS/lt-alone.log" 2>&1 &
G=$!
joined=no
for i in {1..300}; do
  if grep -q "joined the game" "$LOGS/lt-alone.log"; then joined=yes; break; fi
  kill -0 $G 2>/dev/null || break
  sleep 1
done
echo "joined: $joined after ${i}s"
[ $joined = yes ] && sleep 45
alive=no; kill -0 $G 2>/dev/null && alive=yes
echo "still running 45s after joining: $alive"
pkill -f "quickPlaySingleplayer EJ-LTAlone"
wait $G
cp "$LOGS/lt-options-backup.txt" "$R/options.txt"
rm -rf "$R/saves/EJ-LTAlone"
echo "errors:"; grep -nE "Exception|ERROR|Caused by" "$LOGS/lt-alone.log" | grep -v "Realms\|realms\|telemetry\|narrator\|Narrator" | head -20
