# Explorer's Journal

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1-green.svg)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A leather journal you fill with maps, sketches, drawings, notes and keepsakes from your travels. It wears in as it fills.

Open it and you get a real book: paper pages, ribbons, little tabs down the side, and your own writing on every page. Mark the spot you are standing on and it draws a map of the land around you. Hold it up to a view and it sketches what you see in ink. Tape in a flower from your first night out. Months later, the corners are rubbed, a letter is sticking out of the back, and the whole thing looks like it has been everywhere with you.

## What it does

**Mark a place.** The journal draws a map of the land around you, right on the page, the way a person would: rivers, coasts, trees, peaks, buildings and roads, with a red X on your spot. A small arrow under it always points the way back.

**Sketch the view.** A paper frame shows what the sketch will hold. Click, and the journal draws the blocks and mobs in front of you as ink lines, down the page as you watch.

**Draw by hand.** A small canvas on the page, with the 16 dyes as inks, a fill bucket, straight lines, an eraser and undo.

**Write notes.** Your writing drifts a little on the page, like real handwriting. Long notes run on to the next page. Write `see: River camp` on its own line to link to another entry.

**Tape things in.** Pick something from your bag and tape it on a page, with a short note beside it. Peel it off later and you get the exact same item back, name and enchantments and all.

**Photos.** With [Exposure](https://modrinth.com/mod/exposure) installed, your photos tape in too.

**It finds things for you.** The first page is a contents page that writes itself. A list of every place you have been shows how far each one is. Tabs down the side jump to places, sketches, the scrapbook and notes, and the red ribbon always opens the next blank page.

**It wears in.** The book has 5 stages of wear. As it fills, the corners rub, pages fold and go out of line, and things start poking out: a letter, the edge of one of your maps, the corner of a photo.

**Made for servers.** The pages live on the server, not in the item. Anyone holding a journal can read it, and only the owner can write in it. Lose yours, and whoever finds it can read about your travels.

## With Location Tooltip

[Location Tooltip](https://modrinth.com/mod/location-tooltip) 0.8.0 or newer links up with the journal by itself. Each mod still works alone.

- A place you mark takes the name Location Tooltip gives it: your own regions, villages and structures.
- Its map shows the nearest other places, with vanilla's map icons and their names.
- When a place is renamed, the journal crosses the old name out by hand and writes the new one beside it.
- Click the way back under a map to pin that place. A pill on your screen shows its name, how far it is, and vanilla's compass pointing the way.
- Give a village nobody has named its name, from the journal, if the server allows it.
- Name a camp in the wilderness yourself, and the place pill shows your name when you are near it. Only you see it.

## Supported versions

| Minecraft | Java |
|---|---|
| 1.20.1 | 17 |
| 1.21.1 | 21 |

Every version is a separate download. Pick the file that matches your game.

Fabric API is required. Location Tooltip and Exposure are optional.

## Getting started

1. Craft the journal from a Book and Quill and a Compass.
2. Right click to open it.
3. On the blank page, pick what to make: Mark this place, Sketch this view, Draw something, Write a note, or Paste something in.

## Settings

The game writes `config/explorersjournal.json` the first time it starts. On a server, the server's file sets the rules.

| Setting | Default | What it does |
|---|---|---|
| `textDrift` | `true` | Writing drifts a little, like handwriting. Turn it off for straight lines. This one is for your own screen. |
| `othersCanRead` | `true` | Anyone holding a journal can read it. Turn it off and journals open only for their owner. |
| `pasteCostsPaper` | `false` | Taping something in uses 1 Paper. |
| `maxEntries` | `500` | The most entries one journal can hold. |
| `wearAt` | `[10, 30, 75, 150]` | How many entries a journal has held when it reaches each stage of wear. |

`/journal reload` reads the file again without a restart.

## Commands

All of these need operator permission.

| Command | What it does |
|---|---|
| `/journal list <player> [page]` | Lists the entries in the journal that player carries |
| `/journal remove <player> <number>` | Takes one entry out of it. What was taped in goes back to that player. |
| `/journal wear <0-4>` | Shows the journal in your hand at that stage of wear |
| `/journal wear auto` | Lets it wear by itself again |
| `/journal reload` | Reads the config file again |

## Building

One source tree covers both versions, using [Stonecutter](https://stonecutter.kikugie.dev/) to switch the parts that differ.

```bash
./gradlew chiseledBuild
```

The jars land in `versions/<minecraft version>/build/libs/`.
