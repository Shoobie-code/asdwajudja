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
| Wynncraft | Wynn Combat\*: grinds mobs on Wynncraft by name (or any named mob) around a saved spot. It walks into range, mixes basic attacks with a spell rotation cast as click combos (R-L-R style, mirrored for archers), holds spells while mana is low and can cast a heal spell when health drops. Health and mana are read from Wynncraft's glyph-based action bar |

| Building | Farm builder: set two corners and it places a layout (crop lanes with water rows every N rows, walls, floor or full fill), walking between spots with the pathfinder |

**Money and progress**
- Market screen (`/sm market`): live bazaar order flips (profit after tax, limited by budget and trade volume), BIN auction flips (priced against the next listing after AH fees), craft flips and bazaar-to-NPC flips. Tables are sortable and searchable, and clicking a row opens `/bz` or `/viewauction`.
- Minion crafter: the full cost of crafting any minion tier, including lower tiers, with a shopping list. Recipes come from the NotEnoughUpdates item repository and are cached on disk.
- Auction sniper (opt-in): opens new flips as they are listed and can buy them, but only after checking that the price shown in game matches the flip.
- Skill tracker: XP per hour and time to the next level from the action bar, plus the best rate each macro has reached for each skill (`/sm skills`).

**Shared engine**
- Ore map: every mineable block in the chunks you have seen is indexed. When nothing is in reach, the block-mining macros walk to the nearest known vein, and spots that turn out unreachable are skipped for a minute.
- Safe paths: routes prefer to stay away from lava, fire, cactus and cliff edges when that costs little extra distance.
- Walking steers with W/A/S/D from where the camera actually points, so it keeps moving (strafing if needed) while a mouse-like turn catches up. It turns toward the next waypoint earlier at speed, brakes its sprint before sharp bends, and can look at something else while walking (combat watches the mob it is chasing). A new goal mid-walk is planned while it keeps walking the old path, long level stretches are sprint-jumped when there is room overhead (General → Movement), and when stuck it first jumps, then backs off to one side and tries again. **Show path** draws the route: blue to walk, orange for gap jumps, red for long drops.
- Path following cuts corners: the walker jumps ahead to the farthest of the next 10 waypoints it can walk to in a straight line (solid floor, head room, no hazards or drops).
- Pathfinder: plans by travel time at your measured sprint speed (it only turns greedy on very long searches). It sprint-jumps gaps of up to 2 blocks at a plain sprint and 3 from about 6.7 blocks/s, climbs ladders and vines, swims or wades when walking round is much longer, takes falls that cost at most the health above half (any height into water, less damage onto hay), avoids needless sharp turns, and routes around spots it got stuck at.
- Travel method (General → Movement): walk, or teleport along the path with **etherwarp** (Aspect of the Void or Etherwarp Conduit, sneak + use) and/or **Instant Transmission** (Aspect of the End or Void, used on flat straight stretches). Each etherwarp is checked against the real world before use; if teleports stop working (for example no mana) it walks the rest of the way.
- Server check: SkyBlock macros only start on Hypixel and Wynncraft macros only on Wynncraft (unknown servers such as singleplayer are allowed). Auto rejoin is SkyBlock only, so Wynncraft macros stop on a world change.
- Mouse-like aiming: every turn is a hand stroke on a mouse pad. Speed builds up and eases off, the path bows slightly, it usually lands a little past the target (sometimes short) and then corrects in smaller strokes, it pauses to react to new targets, splits wide turns with a mouse lift, never turns faster than the rotation speed allows, and moves in whole mouse counts for your sensitivity. Vertical moves are slower and looser than horizontal ones, the view wanders a few tenths of a degree while holding on a target, and each game session gets its own hand (how much it overshoots, how precise and quick it is, how wide its strokes are). Overshoot, reaction time, mouse lifts and idle drift are under General → Rotations.
- Failsafes: stops on teleports, forced rotations, bedrock cages, unexplained pushes, held-slot changes and players inside you. Also alerts on mentions, private messages and words like "macro".
- Alt alert: when a failsafe catches a macro check it can /msg your other account ("yo im getting macro checked" by default), with a cooldown so it only sends once per check.
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

**Helpers** (ideas from [SkyHanni](https://github.com/hannibal002/SkyHanni), written from scratch here)
- Info panel: SkyBlock date and time, and countdowns to Jacob's Contest, the Dark Auction, Cult of the Fallen Star, the Spooky Festival, Season of Jerry and the New Year. In the Garden it also shows pests (with infested plots) and visitors.
- Garden alerts when a visitor arrives or pests pile up.
- Chat filter (off by default): lobby joins, Watchdog reports, profile lines, ability spam, kill combos and your own words. Hidden lines still feed the alerts and trackers, and DMs or lines mentioning "macro" are never hidden.

**Interface**
- Menu (default key **Right Shift**) in the Skymacro overlay style: a gold title bar, an icon sidebar (Combat, Farms, Mining, Foraging, Fishing, Hunting, Misc, Settings) and a card per macro or module with an ON/OFF button, its keybind chip and a gear that opens its settings in place. Search with `/` or Ctrl+F. The Settings page has the master switch (off stops and blocks every macro), the menu key, panel opacity and the accent colour (amber, emerald or cyan).
- HUD editor: drag the status panel, loot tracker and notification area, with edge and centre snapping.
- Loot tracker: items gained this session with hourly rates, plus `[Sacks]` totals and a profit estimate at Bazaar instant-sell prices (from Hypixel's public API).
- Pop-up notifications for starts, stops, failsafes, breaks, pests and rejoins.
- Highlights the current target block and the farming rewarp point.
- Profiles: save and load named setting presets (General page or `/sm profile save|load <name>`).
- Skills panel on the HUD (movable in the HUD editor).

## Download

Take the jar from the repo's **Releases** page. Versioned releases (such as **v2.1.0**) are the stable downloads;
they are published when a `v*` tag is pushed, with notes from `release-notes/<tag>.md`. Every other push is also built:
**build-preview** for branches, **build-latest** once merged into `main`. Put it in `.minecraft/mods` together with Fabric Loader
and Fabric API for Minecraft 26.2.

### Loader (auto updates)
Instead of the macro jar you can put **`skymacro-loader-*.jar`** (attached to the same releases) in `.minecraft/mods`.
At every launch it checks this repo's releases, downloads the newest macro jar into `.minecraft/skymacro/` and loads it,
so you never have to swap jars by hand. If GitHub can't be reached it runs the copy it already has.

The title screen gets a small **Skymacro** panel in the top-left corner showing what is running, with two buttons:
- **Stable**: the newest `v*` release (until the first version is tagged, the `main` build).
- **Beta**: whichever is newest of the `main` build (`build-latest`) and `v*` prereleases such as `v2.2.0-beta.1`.
  Branch builds (`build-preview`) are never used.

Clicking a channel downloads it in the background; click **Quit game to apply** and start the game again to load it.
Clicking the channel you're on checks for an update. Settings are in `.minecraft/skymacro/loader.properties`
(`offline=true` stops update checks, `repo=` points at a fork). If the macro jar itself is also in `mods`, the loader
leaves it alone and does nothing.

Fabric only loads mods it finds at startup, so the loader adds the downloaded jar to the game itself (class path,
mixins and entrypoint) before Minecraft starts. A switch always needs a restart because the running macro's classes
can't be swapped out.

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

## Credits

The helper features are inspired by [SkyHanni](https://github.com/hannibal002/SkyHanni) (LGPL-2.1). No SkyHanni code is
included; the features were reimplemented from how the game behaves.

## Building

```
./gradlew build
```

This needs JDK 25. The jar is written to `build/libs/`. Versions live in `gradle.properties`.
The loader is a separate Gradle build: `gradle -p loader build` writes it to `loader/build/libs/`.

### Offline check
`devtools/stubcheck/check.sh` compiles the mod and runs the unit tests without the Minecraft jar. It does
this against stubs generated from the API members the released 1.0.0 jar uses. Members the new code uses
that were **not** in the 1.0.0 jar are listed in `devtools/stubcheck/extra.txt`:
`Options.keyLeft/keyRight/keyDown/keyAttack/keyUse`, `ItemStack.getCount/getItem/hasFoil`, `BuiltInRegistries.ITEM` and `KeyMapping.isDown`. These are long-standing
vanilla names, and the CI Gradle build compiles against the real 26.2 game, which confirms they exist.

## Project layout

```
src/main/java/com/skyblockminer/
  MinerMod, MinerCommands, MinerHud   entry point (keybinds, events), /sm commands, in-game overlay
  config/             MinerConfig (every saved setting), MacroSettings (the options behind the GUI and /sm set), MarketSettings
  macro/              Macro runs the active Routine plus failsafes, breaks, rejoin and stats; MacroType, Keys,
                      AutoSell, ItemTracker and Prices (loot tracker), Failsafes, AltAlert, Webhook, Schedule
  macro/mining/       block mining (MiningEngine, TargetFinder, OreIndex), route, powder, commissions,
                      Glacite commissions, Fossil Excavator, treasure chests
  macro/farming/      lane farming, recorded movement, Garden visitors, farm builder
  macro/foraging/, macro/fishing/, macro/combat/   tree farms, fishing, mob grinding and slayer auto-start
  macro/wynncraft/    Wynncraft combat, spell combos and the action-bar reader
  pathing/            Pathfinder, PathWalker, WarpPlanner (etherwarp and transmission), WorldMap (saved block map)
  rotation/           Rotator and MouseModel (mouse-like aiming)
  game/               reads the game: inventory and menus, tab list, sidebar, server, SkyBlock calendar
  helpers/            always-on helpers: info panel, chat filter, menu solvers, skill tracker, auction sniper
  gui/                ClickGuiScreen, Menu, HudEditorScreen, MarketScreen, HudRenderer, Toasts, Setting, Theme, Draw, Input
  market/             Bazaar and auction data, flip finders, minion crafting costs
  mixin/              client hooks (block changes, particles, knockback, attack key)
loader/src/main/java/com/skyblockminer/loader/
  LoaderPreLaunch     update check and download at startup, then Injector puts the jar on the class path
  Releases, Updater   GitHub release picking (stable / beta) and downloads
  TitleWidget         title screen channel switcher (drawn by mixin/TitleScreenMixin)
```
