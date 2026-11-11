#!/bin/zsh
# Runs every check for one Minecraft version: single player, crashes, a real photo, the servers,
# and the Location Tooltip link. About 50 minutes. A game window opens and closes many times.
#   scripts/test-all.sh 1.21.1
# Parts whose game folder is missing are left out: run-Photo/mods, run-LT/mods, run-LT07/mods.
P="$(cd "$(dirname "$0")/.." && pwd)"
V=${1:-1.21.1}
R="$P/versions/$V"
T="$P/scripts/test.sh"

fresh() {
  rm -rf "$R/$1/saves/$2"
  mkdir -p "$R/$1/saves"
  cp -R "$R/run-Dev/saves/TestWorld" "$R/$1/saves/$2"
  rm -rf "$R/$1/saves/$2/data/explorers_journal"
}

# every run starts from the default settings
rm -f "$R/run-Dev/config/explorersjournal.json"
"$T" $V stage0 note noteAgain place placeAgain nav typing controls paste sketchSheet sketch sketchSky sketchMobs drawing drawingAgain layouts notes wear rules

# a crash must never leave an item in two places
fresh run-Dev CrashWorld
WORLD=CrashWorld "$T" $V crash1 crash2 crash3 crash4

if [ -d "$R/run-Photo/mods" ]; then EJUSER=Photo "$T" $V photo; fi

"$P/scripts/servers.sh" $V

if [ -d "$R/run-LT/mods" ]; then
  fresh run-LT LinkWorld
  EJUSER=LT WORLD=LinkWorld "$T" $V link
fi
if [ -d "$R/run-LT07/mods" ]; then
  fresh run-LT07 OldWorld
  EJUSER=LT07 WORLD=OldWorld "$T" $V linkOld
fi
