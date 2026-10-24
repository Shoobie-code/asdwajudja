# Userscript API

Every `.js` file in this folder is a userscript. Pick one in the menu (**Scripts** tab) or run it with
`/sm script run <file>`; it then runs like any other macro, with the failsafes, breaks, panic key and HUD.
`/sm script reload` (or **Reload scripts** in the menu) picks up new and changed files, restarting a running script.

Scripts are JavaScript (ES6: `let`, `const`, arrow functions, template strings) run by Rhino. They are sandboxed:
there is no access to Java, files or the network, only the functions below. This file is rewritten by the mod on
every start, so don't edit it.

## Header

The comment lines at the top of a script describe it:

```js
// @name Pumpkin farm
// @description Walks the lanes and rewarps.
// @server hypixel      (hypixel, wynncraft or any; the script refuses to start on the other network)
```

## How a script runs

The script runs on its own thread, top to bottom. Waiting (`sleep`, `walkTo`, `waitForChat`, ...) never freezes the
game. Actions (`key`, `look`, `command`, ...) are carried out on the next game tick, in order.
When the top level ends the script finishes, unless it registered handlers with `on`; then it keeps running
(like a web page) until `stop()` or until every handler is removed with `off`.

While the macro pauses (a menu is open, a scheduled break, outside active hours) the script's actions wait, so
`waitTicks` and anything that reads from the game simply take longer. `sleep` still counts real time.

## Control

| Function | What it does |
| --- | --- |
| `sleep(ms)` / `sleep(min, max)` | Waits `ms` milliseconds, or a random time between `min` and `max`. |
| `waitTicks(n = 1)` | Waits `n` game ticks (20 per second). |
| `waitUntil(test, timeoutMs)` | Calls `test()` every tick until it returns true. Returns false on timeout (no timeout when left out). |
| `stop(reason)` | Ends the script and stops the macro with `reason` in chat. |
| `status(text)` | Sets the status line on the HUD. |
| `log(...values)` | Prints a chat message only you can see. |
| `toast(title, text)` | Shows a pop-up notification. |
| `random(min, max)` | A random number between `min` and `max`. |
| `pauseInMenus(false)` | Keeps running while a menu is open (needed to click through menus). `pauseInMenus(true)` restores the default. |

## Events

| Function | What it does |
| --- | --- |
| `on('chat', fn)` | Calls `fn(line)` for every chat line (formatting codes removed). |
| `on('tick', fn)` | Calls `fn()` once per game tick. A slow handler skips ticks instead of piling up. |
| `off(event, fn)` | Removes a handler. |
| `waitForChat(pattern, timeoutMs)` | Waits for a chat line containing `pattern` (a string, or a RegExp such as `/You earned (\d+)/`). Returns the line, or `null` after `timeoutMs` (no timeout when left out). |

Handlers run while the script waits (in `sleep`, `waitTicks`, `waitUntil`, `walkTo`, `waitForChat`, or after the
top level ends), never in the middle of other code, and never inside another handler.

## Player

`player` is read-only and updated every tick: `player.name`, `player.x`, `player.y`, `player.z`, `player.pos`
(`{x, y, z}`), `player.yaw`, `player.pitch`, `player.health`, `player.maxHealth`, `player.onGround`,
`player.slot` (1-9) and `player.heldItem` (item name).

## Movement and camera

| Function | What it does |
| --- | --- |
| `key(name, down = true)` | Holds (`true`) or releases (`false`) a key: `forward`, `back`, `left`, `right`, `jump`, `sneak`, `sprint`, `attack`, `use`. Held keys stay down until released, and are pressed again after a pause. Holding `attack` only breaks blocks while the game has the mouse; `mine(true)` works in the background. |
| `press(name, ms = 80)` | Holds a key for `ms` milliseconds. |
| `releaseKeys()` | Releases every key and stops walking and mining. |
| `look(yaw, pitch, speed)` | Turns the camera with the macro's mouse-like movement. `speed` is 0-1 (default: the rotation speed setting). |
| `lookAt(x, y, z, speed)` | Turns toward a point in the world. |
| `lookDone()` | True once the camera reached where it was sent. |
| `waitLook(timeoutMs = 3000)` | Waits until `lookDone()`. |
| `stopLooking()` | Lets go of the camera. |
| `walkTo(x, y, z, radius = 1)` | Walks there with the pathfinder (and etherwarp, if enabled in General → Movement). Returns true on arrival, false if no path was found. |
| `startWalk(x, y, z, radius = 1)` | Starts walking without waiting. |
| `walking()` | True while walking. |
| `stopWalking()` | Stops walking. |

## Actions

| Function | What it does |
| --- | --- |
| `attack()` | Left click once: hits the mob under the crosshair. |
| `use()` | Right clicks the held item. |
| `mine(true)` / `mine(false)` | Keeps breaking whatever block is under the crosshair, every tick, until turned off. Works with the window in the background. |
| `selectSlot(n)` | Selects hotbar slot 1-9. |
| `findItem(name)` | Hotbar slot (1-9) of the first item whose name contains `name`, or -1. |
| `chat(text)` | Sends a chat message to the server. |
| `command(cmd)` | Runs a server command, such as `command('warp garden')` (the leading `/` is optional). |

## Menus and inventory

Remember `pauseInMenus(false)` before working in menus.

| Function | What it does |
| --- | --- |
| `screen()` | Title of the open menu, or `null`. |
| `findSlot(name)` | Slot of the first item in the open menu (not your inventory) whose name contains `name`, or -1. |
| `slotName(slot)` | Item name in a slot of the open menu, or `''`. |
| `clickSlot(slot)` | Left clicks a slot of the open menu. |
| `closeScreen()` | Closes the open menu. |
| `inventoryFull()` | True when no inventory slot is free. |

## World and server

| Function | What it does |
| --- | --- |
| `block(x, y, z)` | Block id at a position, such as `'minecraft:stone'`. |
| `mobs(radius = 16, filter = '')` | Name-tagged mobs within `radius` whose tag contains `filter`, nearest first: `[{name, x, y, z, distance}]`. Positions are the name tag's, about 2 blocks above the mob's feet. |
| `sidebar()` | Scoreboard sidebar lines. |
| `tabList()` | Tab list lines. |
| `area()` | SkyBlock area from the tab list, such as `'Dwarven Mines'`, or `null`. |
| `server()` | `'hypixel'`, `'wynncraft'` or `'other'`. |

Reading from the game (`block`, `mobs`, `screen`, `sidebar`, ...) takes until the next tick, so call them a few
times per second rather than in a tight loop. `player` is instant.

## Example

```js
// @name Zombie slayer
// @server hypixel
status('Hunting');
selectSlot(findItem('Sword'));
while (true) {
    const target = mobs(12, 'Zombie')[0];
    if (!target) { sleep(500); continue; }
    if (target.distance > 3) walkTo(target.x, target.y - 2, target.z, 2);
    lookAt(target.x, target.y - 1, target.z);
    waitLook(1000);
    attack();
    sleep(150, 300);
}
```
