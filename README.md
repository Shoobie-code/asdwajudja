# Skyblock Macro

A Fabric client mod for Hypixel SkyBlock (Minecraft 26.2) that automates mining, commissions, farming,
foraging, fishing and mob grinding. It has a ClickGUI, a HUD you can drag around, a loot tracker, notifications and
Discord alerts.

> Hypixel's rules forbid macros, so accounts that use them can be banned. Use this at your own risk.

## Features

**Macros**

| Category | Macros |
| --- | --- |
| Mining | Mithril (titanium first), Gemstone (pick gem types), Ore, Glacite Tunnels, Custom blocks, Route miner (etherwarp between saved points, with per-point walk and wait options), Powder (tunnels while mining), Commissions (picks, travels, mines, slays, claims, sells trash), Glacite Commissions\*, Fossil Excavator\* |
| Farming | Lane / S-shape farming with presets for vertical crops, melon/pumpkin, sugar cane, cactus, cocoa, mushroom and custom key patterns, or replay of a walk you recorded (`/sm echo record`) for any other design. It locks yaw and pitch, switches lanes when you stop moving, rewarps at a saved point, rewarps when stuck, and can react to pest spawns (notify, stop, or hunt nearby pests with the vacuum and rewarp) |
| Foraging | Plants saplings on free dirt, uses bone meal and chops with a Treecapitator, then repeats. With a tree route (Galatea, the Park, the Hub) it walks from tree to tree and chops instead |
| Fishing | Casts, reels in on the `!!!` bite marker and recasts on a timeout. It can fight sea creatures with a melee or right-click weapon |
| Combat | Can start the next slayer quest when one ends\*. Kills mobs whose name tag matches your list (ghosts, zealots, graveyard zombies, crypt ghouls, goblins...) within a radius of a saved spot, with a melee or right-click weapon, and walks back to the spot when the area is clear |

**Shared engine**
- Ore map: every mineable block in the chunks you have seen is indexed. When nothing is in reach, the block-mining macros walk to the nearest known vein, and spots that turn out unreachable are skipped for a minute.
- Path following cuts corners: the walker jumps ahead to the farthest of the next 10 waypoints it can walk to in a straight line (solid floor, head room, no hazards or drops).
- Failsafes: stops on teleports, forced rotations, bedrock cages, unexplained pushes, held-slot changes and players inside you. Also alerts on mentions, private messages and words like "macro".
- Player proximity alerts, with an option to stop.
- Auto rejoin after kicks, Limbo and server swaps. It then warps back and walks to your saved spot using the pathfinder.
- Scheduled breaks with varied lengths, and optional daily active hours (for example `08:00-23:00`; overnight windows work too).
- Panic key: a separate keybind that only ever stops the macro.
- Auto sell: when the inventory fills, sells items from your list through `/trades` and carries on. The hotbar is never sold.
- Heat and cold limits in the Crystal Hollows and Glacite Tunnels.
- Treasure chest solver.
- Menu solvers (work without the macro running): Ultrasequencer and Chronomatron in the Experimentation Table, Melody's Harp, and Forge auto-claim\*.
- Garden visitors\*: at each rewarp the farming macro can walk to the visitors and accept the offers you have items for.

\* Not yet tested in game. These click through SkyBlock menus by item name, and every name they look for is a setting, so a wrong
guess can be fixed in the GUI. When something isn't found they stop (or skip) and say what was missing.
- Discord webhook with failsafe pings, periodic status reports and a test button.

**Interface**
- ClickGUI (default key **Right Shift**). It has category pages, toggles, sliders, choice pickers, chips, text fields and search (`/` or Ctrl+F), plus a start/stop panel and seven accent colours.
- HUD editor: drag the status panel, loot tracker and notification area, with edge and centre snapping.
- Loot tracker: items gained this session with hourly rates, plus `[Sacks]` totals and a profit estimate at Bazaar instant-sell prices (from Hypixel's public API).
- Pop-up notifications for starts, stops, failsafes, breaks, pests and rejoins.
- Highlights the current target block and the farming rewarp point.

## Download

Every push is built by GitHub Actions. Take the jar from the repo's **Releases** page: **build-preview** for
branches, **build-latest** once merged into `main`. Put it in `.minecraft/mods` together with Fabric Loader
and Fabric API for Minecraft 26.2.

## Usage

Bind **Start / stop macro** under Controls → Skyblock Macro, or use `/sm`.

| Command | What it does |
| --- | --- |
| `/sm` | Start or stop the selected macro |
| `/sm gui` · `/sm hud` | Open the menu · edit the HUD layout |
| `/sm start [type]` · `/sm stop` · `/sm status` | Control the macro |
| `/sm type <type>` | Select a macro: `mithril gemstone ore tunnel custom route powder commissions glacite excavator farming foraging fishing combat` |
| `/sm set <setting> [value]` | View or change any GUI option, such as `/sm set farming.pitch 3` |
| `/sm settings` | List every setting id and its value |
| `/sm farm rewarp` · `/sm forage spot` · `/sm fish spot` · `/sm combat spot` | Save the spot you are standing on |
| `/sm forage add` · `/sm forage clearroute` | Add the spot you stand on to the tree route, or clear it |
| `/sm echo record` · `/sm echo stop` · `/sm echo clear` | Record a walk through your farm for the "Recorded movement" farm type |
| `/sm route walk <n> <true\|false>` · `/sm route wait <n> <ms>` | Always walk to route point n, or wait there before mining |
| `/sm visitors spot` | Save where the Garden visitors gather |
| `/sm route add\|insert\|remove\|clear\|list\|save\|load\|routes\|import\|export\|show` | Edit mining routes |
| `/sm goto <x> <y> <z>` | Walk somewhere with the pathfinder |

`/miner` and `/macro` work as aliases of `/sm`.

### Farming setup
1. Stand at the start of your farm facing along it, and pick the **Farm type** in the Farming page.
2. Walk to the last block of the farm and press **Rewarp point → Set here** (or run `/sm farm rewarp`).
3. In the Garden, use `/setspawn` at the start so `warp garden` brings you back there.
4. Start the macro. It aligns, farms lane by lane and rewarps at the end.

## Building

```
./gradlew build
```

This needs JDK 25. The jar is written to `build/libs/`. Versions live in `gradle.properties`.

### Offline check
`devtools/stubcheck/check.sh` compiles the mod and runs the unit tests without the Minecraft jar. It does
this against stubs generated from the API members the released 1.0.0 jar uses. Members the new code uses
that were **not** in the 1.0.0 jar are listed in `devtools/stubcheck/extra.txt`:
`Options.keyLeft/keyRight/keyDown/keyAttack/keyUse`, `ItemStack.getCount/getItem/hasFoil`, `BuiltInRegistries.ITEM` and `KeyMapping.isDown`. These are long-standing
vanilla names, and the CI Gradle build compiles against the real 26.2 game, which confirms they exist.

## Project layout

```
src/main/java/com/skyblockminer/
  MinerMod            entry point, keybinds, events
  Macro               runs the active Routine plus failsafes, breaks, rejoin, stats
  Routine             interface every macro implements
  BlockMining, RouteMiner, PowderMacro, Commissions      mining
  FarmingMacro, ForagingMacro, FishingMacro, CombatMacro other skills
  AutoSell, Schedule  selling on a full inventory, active hours
  Solvers             experiment and harp menu solvers
  Recording, Prices   recorded-movement farming, Bazaar profit estimate
  GlaciteCommissions, ExcavatorMacro, Visitors, SlayerStarter   menu-driven automation (untested in game)
  MacroSettings       every option (feeds the GUI and /sm set)
  Rotator, PathWalker, Pathfinder, WorldMap, TargetFinder, MiningEngine   movement and aiming
  gui/                ClickGuiScreen, HudEditorScreen, HudRenderer, Toasts, Setting, Theme, Draw, Input
```
