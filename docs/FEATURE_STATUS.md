# Feature status

What each feature has been checked against. "Not yet tested in game" comes from the README's `*` marks. Automated coverage lists the unit tests that exercise the feature's logic. Game behaviour is not covered by the offline check, so a feature without an in-game note is unverified in game.

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
| Combat (Hypixel) | Stable | `macro/MobTest`-style helpers only |
| Slayer auto-start | Not yet tested in game | none |
| Wynncraft combat | Stable | `macro/wynncraft/WynnhcraftTest` |
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

"Stable" means the logic is covered by unit tests and the feature is not marked untested in the README. It does not mean every path has been run in game.
