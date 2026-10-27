# Architecture

## Packages

| Package | Responsibility |
| --- | --- |
| `MinerMod`, `MinerCommands`, `MinerHud` | Entry point, `/sm` commands, HUD rendering. |
| `macro` | `Macro` runs the selected `Routine` and everything shared: failsafes, breaks, auto rejoin, stats, notifications. Each macro is one `Routine` in its own package. |
| `pathing` | `WorldMap` (indexed blocks), `Pathfinder` (travel-time planning), `PathWalker` (walking along a path). |
| `rotation` | `Rotator` and `MouseModel`: mouse-like aiming. Macros ask for a view, the rotator decides how it moves there. |
| `game` | Reads game state: server, SkyBlock time, sidebar, tab list, inventory helpers. |
| `helpers` | Side features that run beside any macro: info panel, chat filter, skill tracker, auction sniper, menu solvers. |
| `market` | Bazaar and auction data, flips, crafting costs, NBT reading. |
| `config` | `MinerConfig` (the saved JSON), `MacroSettings` (the menu and `/sm set` options), `MarketSettings`. |
| `gui` | ClickGUI, HUD editor, market screen, toasts, drawing helpers. |
| `script` | Userscripts: loads `.js` files and runs them as a routine with a limited API. |
| `update` | Self-update: checks releases and swaps the jar on restart. |

## Decisions

- **One running macro.** `Macro` has a single `running` flag and a single current `Routine`. Starting a macro stops the one already running, so two macros never drive the mouse at once.
- **Side features yield to macros.** The auction sniper and the menu solvers check `Macro.running()` and stay idle while a macro is active. Anything that clicks a screen or sends a command must do the same.
- **One source for options.** `MacroSettings` declares every option once. The menu and `/sm set <id> <value>` both read that list, and duplicate ids fail at startup.
- **Config is repaired, not rejected.** `MinerConfig.sanitize()` runs on every load and profile load. It fills nulls with defaults, clamps ranges, and replaces NaN. New numeric options should be clamped there.
- **Shaded libraries, not jar-in-jar.** Rhino is copied into the mod jar at build time (`shade` configuration). The loader never sees nested jars.
- **Offline check.** `devtools/stubcheck/` compiles the code and runs the tests without Minecraft or Maven. It needs `skyblock-miner-1.0.0.jar`, which is the API reference for the stubs.

## Adding a feature

1. Put the logic in its feature package and keep game calls in the `Routine` or helper.
2. Declare its options in `MacroSettings` and clamp numbers in `MinerConfig.sanitize()`.
3. Add unit tests for pure logic under `src/test/java` in the matching package.
4. Mark it in [FEATURE_STATUS.md](FEATURE_STATUS.md) until it has been tested in game.
