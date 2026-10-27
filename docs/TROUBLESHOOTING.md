# Troubleshooting

Start with the chat messages and the toast, which name the reason a macro did not start or stopped. The log is the Minecraft `logs/latest.log` file; mod messages are logged under "Skyblock Macro".

## A macro does not start

| You see | Cause | Fix |
| --- | --- | --- |
| "Macros are switched off" | The master switch is off. | Menu → Settings → Master switch. |
| "Wrong server" | SkyBlock macros only start on Hypixel, Wynncraft macros only on Wynncraft. | Join the right server. Singleplayer is allowed for both. |
| Nothing happens and no message | You are not in a world yet, or the player is not loaded. | Wait for the world to load, then try again. |
| A solver does nothing | Solvers stay idle while a macro is running, so they never click against it. | Stop the macro first, then open the menu. |
| Macro outside the active window does not run | Active hours (for example `08:00-23:00`) are set. | Clear the Active hours setting, or widen the window. |

## A macro stops by itself

- **FAILSAFE** stops are reported as "FAILSAFE: <reason>". The failsafes stop on teleports, forced rotations, bedrock cages, unexplained pushes, held-slot changes and players inside you. Check the reason in the chat, then restart when it's safe.
- **Scheduled break**: the scheduler stops the macro for the configured break length and restarts it after.
- **Auto rejoin** gives up after 5 rejoins in one session. Restart the macro to reset the count.

## Settings did not save or reverted

- The mod reads `config/skyblockminer.json` on launch. If the file cannot be parsed, the log says "Could not read skyblockminer.json, using defaults" and the defaults are used. Fix the JSON syntax or remove the file, and the mod writes a fresh one.
- Out-of-range numbers are clamped on load (for example a hotbar slot above 8 becomes 8). A value that was NaN becomes the minimum. Nothing is lost silently: the value you see in the GUI is the one that will be used.
- Profile loads replace every setting. Save the current profile first if you want to keep it.

## Menu clicks miss

- Menu features click by item name. Every name they look for is a setting. Check the name in the GUI, then update it to match the item shown in game.
- Features marked "not yet tested in game" are listed in [FEATURE_STATUS.md](FEATURE_STATUS.md).

## Discord or alt alerts do not arrive

- Discord has a test button, and alt alerts have a test command. Use them to check that messages go out.
- Alt alerts use a cooldown, so a second check within the cooldown sends nothing. Lower the cooldown if that's not wanted.

## Still stuck

Open an issue with the chat lines around the problem, the matching lines from `latest.log`, and the mod version from the title screen.
