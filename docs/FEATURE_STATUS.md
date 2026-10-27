# Feature status

What each feature has been checked against. "Not yet tested in game" comes from the README's `*` marks. Automated coverage lists the unit tests that exercise the feature's logic. The offline check cannot exercise game behaviour, so "Not yet tested in game" is the only status this page records; anything else is unverified in game.

| Feature | Status | Automated coverage |
| --- | --- | --- |
| Mining macros (Mithril, Gemstone, Ore, Tunnels, Custom) | Stable | `macro/mining/*Test` |
| Route miner | Stable | `macro/mining/RouteFlagsTest`, `pathing/PathingTest` |
| Powder and commissions | Stable | `macro/mining/MiningTest`, `macro/LogicTest` |
| Glacite commissions | Not yet tested in game | `macro/mining/MiningTest` (rules only) |
| Fossil Excavator | Not yet tested in game | none |
| Farming (lanes, presets, recording) | Stable | `macro/farming/FarmingTest`, `macro/farming/BuildPlanTest` |
| Farm builder | Stable | `macro/farming/BuildPlanTest` |
| Garden visitors | Not yet tested in game | none |
| Foraging (plant and chop, tree routes) | Stable | `macro/farming/FarmingTest` (shared helpers) |
| Fishing | Stable | none |
| Combat (Hypixel) | Not yet tested in game | none (mob matching is in `macro/combat/Mob`, untested) |
| Slayer auto-start | Not yet tested in game | none |
| Wynncraft combat | Stable | `macro/wynncraft/WynncraftTest` |
| Userscripts | Stable | `script/ScriptsTest`, `script/ScriptRunnerTest` |
| Auto sell | Stable | none |
| Menu solvers (Ultrasequencer, Chronomatron, Harp) | Stable | none (menu logic runs against live screens) |
| Menu solver: Forge auto-claim | Not yet tested in game | none |
| Auction sniper | Stable | `market/MarketTest` (pricing only) |
| Market and flips | Stable | `market/MarketTest` |
| Failsafes and alt alert | Stable | `macro/AltAlertTest` |
| Scheduling and active hours | Stable | `macro/ScheduleTest` |
| Settings and profiles | Stable | `gui/SettingTest`, `config/MinerConfigTest` |
| Skill tracker | Stable | `helpers/SkillTrackerTest` |
| Chat filter | Stable | `helpers/ChatFilterTest` |

"Stable" means the README does not mark it untested and unit tests cover some of its logic. It does not mean every path has been run in game.
