# Skyblock Macro

A Fabric client mod for Hypixel SkyBlock (Minecraft 26.2) that automates mining, commissions, farming,
foraging and fishing. It has a ClickGUI, a HUD you can drag around, a loot tracker, notifications and
Discord alerts.

> Hypixel's rules forbid macros, so accounts that use them can be banned. Use this at your own risk.

## Features

**Macros**

| Category | Macros |
| --- | --- |
| Mining | Mithril (titanium first), Gemstone (pick gem types), Ore, Glacite Tunnels, Custom blocks, Route miner (etherwarp between saved points), Powder (tunnels while mining), Commissions (picks, travels, mines, slays, claims, sells trash) |
| Farming | Lane / S-shape farming with presets for vertical crops, melon/pumpkin, sugar cane, cactus, cocoa, mushroom and custom key patterns. It locks yaw and pitch, switches lanes when you stop moving, rewarps at a saved point, rewarps when stuck, and can react to pest spawns |
| Foraging | Plants saplings on free dirt, uses bone meal and chops with a Treecapitator, then repeats |
| Fishing | Casts, reels in on the `!!!` bite marker and recasts on a timeout. It can fight sea creatures with a melee or right-click weapon |

| Building | Farm builder: set two corners and it places a layout (crop lanes with water rows every N rows, walls, floor or full fill), walking between spots with the pathfinder |

**Money and progress**
- Market screen (`/sm market`): live bazaar order flips (profit after tax, limited by budget and trade volume), BIN auction flips (priced against the next listing after AH fees), craft flips and bazaar-to-NPC flips. Tables are sortable and searchable, and clicking a row opens `/bz` or `/viewauction`.
- Minion crafter: the full cost of crafting any minion tier, including lower tiers, with a shopping list. Recipes come from the NotEnoughUpdates item repository and are cached on disk.
- Auction sniper (opt-in): opens new flips as they are listed and can buy them, but only after checking that the price shown in game matches the flip.
- Skill tracker: XP per hour and time to the next level from the action bar, plus the best rate each macro has reached for each skill (`/sm skills`).

**Shared engine**
- Ore map: every mineable block in the chunks you have seen is indexed. When nothing is in reach, the block-mining macros walk to the nearest known vein, and spots that turn out unreachable are skipped for a minute.
- Safe paths: routes prefer to stay away from lava, fire, cactus and cliff edges when that costs little extra distance.
- Path following cuts corners: the walker jumps ahead to the farthest of the next 10 waypoints it can walk to in a straight line (solid floor, head room, no hazards or drops).
- Failsafes: stops on teleports, forced rotations, bedrock cages, unexplained pushes, held-slot changes and players inside you. Also alerts on mentions, private messages and words like "macro".
- Player proximity alerts, with an option to stop.
- Auto rejoin after kicks, Limbo and server swaps. It then warps back and walks to your saved spot using the pathfinder.
- Scheduled breaks with varied lengths.
- Heat and cold limits in the Crystal Hollows and Glacite Tunnels.
- Treasure chest solver.
- Discord webhook with failsafe pings, periodic status reports and a test button.

**Interface**
- ClickGUI (default key **Right Shift**). It has category pages, toggles, sliders, choice pickers, chips, text fields and search (`/` or Ctrl+F), plus a start/stop panel and seven accent colours.
- HUD editor: drag the status panel, loot tracker and notification area, with edge and centre snapping.
- Loot tracker: items gained this session with hourly rates, plus `[Sacks]` totals.
- Pop-up notifications for starts, stops, failsafes, breaks, pests and rejoins.
- Highlights the current target block and the farming rewarp point.
- Profiles: save and load named setting presets (General page or `/sm profile save|load <name>`).
- Skills panel on the HUD (movable in the HUD editor).

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
| `/sm type <type>` | Select a macro: `mithril gemstone ore tunnel custom route powder commissions farming foraging fishing` |
| `/sm set <setting> [value]` | View or change any GUI option, such as `/sm set farming.pitch 3` |
| `/sm settings` | List every setting id and its value |
| `/sm farm rewarp` · `/sm forage spot` · `/sm fish spot` | Save the spot you are standing on |
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
`Options.keyLeft/keyRight/keyDown/keyAttack/keyUse` and `ItemStack.getCount`. These are long-standing
vanilla names, and the CI Gradle build compiles against the real 26.2 game, which confirms they exist.

## Project layout

```
src/main/java/com/skyblockminer/
  MinerMod            entry point, keybinds, events
  Macro               runs the active Routine plus failsafes, breaks, rejoin, stats
  Routine             interface every macro implements
  BlockMining, RouteMiner, PowderMacro, Commissions      mining
  FarmingMacro, ForagingMacro, FishingMacro             other skills
  MacroSettings       every option (feeds the GUI and /sm set)
  Rotator, PathWalker, Pathfinder, WorldMap, TargetFinder, MiningEngine   movement and aiming
  gui/                ClickGuiScreen, HudEditorScreen, HudRenderer, Toasts, Setting, Theme, Draw, Input
```
