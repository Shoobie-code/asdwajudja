# Roadmap 2: common mod features

A second roadmap, built from what popular Minecraft and Hypixel SkyBlock mods (SkyHanni, Skyblocker, Wurst, Meteor, Baritone, Litematica, Inventory Profiles, and similar) offer, scoped to this mod's macros and helpers. Each feature notes whether it can be checked offline (**offline**) or needs an in-game run (**in game**).

Ideas are taken from how these mods behave. No code is copied: the GPL-licensed references (V5, Giovanni) are for ideas only.

### Progress

- **Done, verified offline:** #6 event countdown list (`/sm events`), #19 reconnect back-off, #34 HUD layout presets, #42 command aliases, #45 config diff, #46 log export, #51 profile sharing codes, #53 one-time update notice, #54 log redaction (the redaction half; sending is still open), #59 stop-recovery hints.
- **Done, needs in-game testing** (built against the repo's existing 26.2 APIs, compiles, pure cores unit-tested, live behaviour unverified): #1 watched Bazaar price history (`/sm pricelog`), #2 `/sm price` Bazaar lookup, #7 custom chat alerts (`/sm alert`), #8 `/sm value` inventory worth, #9 `/sm accessories` duplicate finder, #11 coins/hour on the HUD (sidebar purse), #20 server lag detection (`/sm tps`, optional `pauseOnLag`), #33 world waypoints (`/sm waypoint`), #36 session history (`/sm history`), #49 per-module tick timings (`/sm perf`), #55 colour-blind status palette, #12/#13 location, dungeon-cleared and slayer readout (`/sm where`), #32 player ESP.
- **Already in the code:** #3 craft profit (`CraftCalc`), #16 stuck handling (`PathWalker`), #21 teleport verification (`RouteMiner` arrival check), #24 break length variance (`Macro.vary`), #47 version pin (`fabric.mod.json`).
- **From the original roadmap, done this pass too:** #20 per-module metrics (`/sm perf`), #28 reference-jar documented, #37 `/sm report`, #40 configurable notifications (`/sm alert`). (Earlier passes did #6, #26, #27, #29, #31, #32, #33, #39, #42, #44–48 of that list.)
- **Open, and need a client run** (rendering, in-game menus, live scoreboard/API data, or network): #4–5, #10, #22, #25, #26–31, #35, #37–38, #40–41, #43, #48, #50, #52, #56–58.

## Phase 1: Information and helpers (SkyBlock)

1. **Bazaar price history graph.** Plot a product's buy and sell price over the last day or week in the market screen. *offline (data), in game (fetch)*
2. **Auction price lookup.** Type an item name and see recent BIN sales and the median price. *offline (parsing), in game (data)*
3. **Profit calculator for crafts.** Show the cost of a recipe, the instant-sell value, and profit per craft, including all sub-ingredients. *offline*
4. **Cookie and Booster timer.** Countdown to the Booster Cookie expiring, in the info panel. *in game*
5. **Mayor and election tracker.** Show the current mayor, their perks and the next election. *in game (API)*
6. **Event timers.** Countdowns for Fishing Festival, Spooky Festival, Jacob's Contest, Dark Auction, and the Bank interest. *offline (time maths), in game (times)*
7. **Crimson Isle and Dragon's Nest event alerts.** Chat-driven alerts when a rare event spawns. *in game*
8. **Inventory value.** Show the total value of the bags and sacks in the loot tracker. *in game*
9. **Accessory bag helper.** Lists missing and upgradeable Talismans. *in game*
10. **Collection progress.** Show progress towards the next collection tier, with the rate per hour. *in game*
11. **Skill tier and XP panel.** Current level, XP to next, and an estimate of time to the next level. Extends the skill tracker. *offline (maths), in game (XP)*
12. **Dungeon score and split tracker.** Time per room and the score estimate in dungeon runs. *in game*
13. **Slayer boss tracker.** Count kills and boss HP per tier, and show the cost per kill. *in game*

## Phase 2: Macro logic (both Hypixel and Wynncraft)

14. **Adaptive mining speed.** Adjust the swing rate to the tool's mining speed rather than a fixed delay. *offline*
15. **Block break verification.** Confirm a block has broken (by block state, not by timing) before moving on. *offline (logic), in game (checks)*
16. **Stuck detection by progress.** Detect "no progress for N seconds" rather than only position changes. *offline*
17. **Per-macro tick budget.** Limit how much work each macro does per tick, to keep frame times steady. *offline*
18. **Smarter target choice.** Prefer the nearest reachable target by path cost, not straight-line distance. *offline*
19. **Reconnect back-off.** Exponential back-off between reconnect attempts, instead of a fixed wait. *offline*
20. **Lag detection.** Pause macros when server ticks slow down (for example, when TPS drops). *in game*
21. **Teleport verification.** Check the expected position after each teleport, and retry if it is wrong. *offline (logic)*
22. **Inventory full handling.** Sell or store items by rule when the inventory fills, with a per-item priority list. *in game*
23. **Macro chaining.** Run a list of macros in order (for example, mine, sell, then farm), with the handover between them. *offline (scheduler)*
24. **Break scheduler per macro.** Different break lengths for different macros. *offline*
25. **Pathing around players.** Route around other players and avoid walking through them. *in game*

## Phase 3: Wynncraft

26. **Class-aware combat rotations.** Spell rotations per class, with cooldown checks. *offline (rotation logic), in game (cooldowns)*
27. **Profession routes.** Saved routes for mining, woodcutting and fishing, with the same route editor as SkyBlock. *offline (route logic), in game (menus)*
28. **Quest helper.** Step through a quest's waypoints and dialogue. *in game*
29. **Loot and XP tracker.** Track items and XP per hour on Wynncraft. *in game*
30. **Territory and guild helpers.** Warn when a territory changes hands. *in game*
31. **Mana management.** Pause spells until mana recovers, with a per-spell threshold. Extends the current mana check. *offline*

## Phase 4: Rendering and HUD

32. **ESP for ores and mobs.** Highlight target blocks and mobs through walls, with a colour per type. *in game*
33. **Waypoints.** Named points in the world with distance and a direction arrow. *offline (maths), in game (render)*
34. **Custom HUD layout presets.** Save and switch between HUD positions. *offline*
35. **Ghost blocks.** Show the next block the macro intends to break. *in game*
36. **Damage and kill counters on screen.** Floating text for damage dealt, with a fade. *in game*
37. **Compact item tooltips.** Show profit or value on item tooltips. *in game*

## Phase 5: Inventory and interface

38. **Inventory profiles.** Named sets of hotbar and inventory layouts, with a one-click swap. *offline (data), in game (swap)*
39. **Item search in menus.** Search the current menu's items by name. *in game*
40. **Chat channels and filters.** Route chat to separate tabs (party, guild, DMs) with filters. *in game*
41. **Keybind profiles.** Different key sets per macro, switched with one key. *offline*
42. **Command aliases.** Short forms for common `/sm` commands, configurable. *offline*

## Phase 6: Tools and quality

43. **Replay recorder.** Record a run's inputs and state for debugging, then replay them. *offline (format), in game (capture)*
44. **Dry-run mode.** Run the macro logic without sending input, and log what it would do. *offline*
45. **Config diff.** Show what changed between two profiles or backups. *offline*
46. **Log export.** Save the last N minutes of macro log to a file for a bug report. *offline*
47. **Version check against the game.** Warn when the mod's target game version differs from the running one. *offline (already partly in fabric.mod.json)*
48. **Crash-safe state.** Save macro state each few minutes, so a crash can resume where it stopped. *offline*
49. **Performance overlay.** Frame time and tick time per module, on the HUD. *in game*
50. **Automated screenshots for docs.** A dev script that takes screenshots of each screen. *in game*

## Phase 7: Community and sharing

51. **Profile sharing.** Export and import profiles as a short text code. *offline*
52. **Shared route library.** Browse and import routes shared by other users. *in game (network)*
53. **Changelog in game.** Show the release notes on the first launch after an update. *offline*
54. **Feedback button.** Send a bug report with the log and config, with personal data removed. *offline (redaction), in game (send)*

## Phase 8: Accessibility and onboarding

55. **Colour-blind palettes** for all status colours. *offline*
56. **UI scale and font size options.** *in game*
57. **Keyboard-only navigation** of the menu. *in game*
58. **Tutorial overlay** for first-time users. *in game*
59. **Plain-language error messages** for every failsafe and macro stop. *offline*

## Notes

- Items marked **offline** can be built and tested with the existing offline check (`devtools/stubcheck/check.sh`).
- Items marked **in game** need a run in the Minecraft client before the behaviour is trusted.
- Items 1–13 and 26–31 depend on API data or in-game menus, so check the endpoints and menu names first.
- This is a second list. The original 48-item roadmap from earlier in this session is still the list of open work for the mod's own quality and structure, and it is not in the repo.
