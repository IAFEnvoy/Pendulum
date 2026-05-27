# Pendulum

Client-side JavaScript script executor for Minecraft. Control your player with JS scripts — automate movement, block breaking, crafting, GUI interactions, and world queries.

## Install

- Requires **Fabric Loader** + **Fabric API** (1.20.1)
- Drop the jar into `mods/`

## Commands

All commands use `/pendulum` as root.

| Command | Usage | Description |
|---------|-------|-------------|
| `/pendulum` | — | Show API overview (same as `/pendulum help`) |
| `/pendulum help` | — | Show API function list |
| `/pendulum execute <code>` | `/pendulum execute minecraft.forward(); minecraft.jump()` | Run JS code inline |
| `/pendulum file <path>` | `/pendulum file mine_stone.js` | Run a script from `.minecraft/pendulum/<path>` |
| `/pendulum abort` | — | Stop the currently running script |
| `/pendulum status` | — | Show current script state and pending action |
| `/pendulum dir` | — | Print the absolute path of the script directory |

> Only one script can run at a time. Use `/pendulum abort` to stop the current one before starting a new one.

## Minecraft API (`minecraft.*`)

All functions live on the global `minecraft` object. You can also use `console.log(...)` for debug output to the game log.

### Movement

| Function | Description |
|----------|-------------|
| `forward()` / `back()` / `left()` / `right()` | Hold movement key (calls `stop()` internally first) |
| `stop()` | Release all movement keys |
| `jump(hold?)` | `jump()` = single jump, `jump(true)` = hold jump key |
| `sneak(hold?)` | `sneak()` = toggle on, `sneak(false)` = off |
| `sprint(hold?)` | `sprint()` = toggle on, `sprint(false)` = off |
| `stopSprint()` | Stop sprinting |
| `lookAt(x, y, z)` | Rotate camera to look at coordinates |
| `setYaw(y)` / `setPitch(p)` | Set yaw/pitch directly |
| `getYaw()` / `getPitch()` | Get current rotation |
| `getX()` / `getY()` / `getZ()` | Get player position |

```js
// Walk forward for 3 seconds, then jump
minecraft.forward();
// ...time passes...
minecraft.jump();
minecraft.stop();
```

### Interaction (Synchronous — returns when action completes)

| Function | Returns | Description |
|----------|---------|-------------|
| `use()` | — | Use held item / interact with block (waits 1 tick) |
| `attack()` | — | Attack entity / start breaking block (waits 2 ticks) |
| `breakBlock()` | `boolean` | Break the crosshair-targeted block. Blocks until broken or timeout (10s). Returns `true` on success. |
| `swapHands()` | — | Swap main/off hand items |
| `drop()` / `dropAll()` | — | Drop 1 / all of held stack |
| `pickBlock()` | — | Pick block (middle-click) |

```js
minecraft.lookAt(100, 64, 200);
var ok = minecraft.breakBlock();
if (ok) minecraft.log("Block broken!");
```

### Inventory

| Function | Description |
|----------|-------------|
| `selectHotbar(n)` | Switch to hotbar slot 1-9 |
| `getSelectedSlot()` | Get current hotbar slot (1-9) |
| `hasItem(id, count?)` | Check if inventory has item. `id` is `"minecraft:stone"` format |

```js
if (minecraft.hasItem("minecraft:cobblestone", 64)) {
    minecraft.log("Got a full stack!");
}
```

### GUI & Crafting (Synchronous)

| Function | Description |
|----------|-------------|
| `closeGui()` | Close current screen |
| `isGuiOpen()` → `boolean` | Is any GUI open |
| `getGuiTitle()` → `string` | Get current GUI title |
| `clickSlot(slotId, button?)` | Left-click a slot (button: 0=left, 1=right). Waits 1 tick. |
| `clickSlotRight(slotId)` | Right-click a slot. Waits 1 tick. |
| `craft()` | Craft once from workbench (click output slot). Waits 1 tick. |
| `craftAll()` | Craft all from workbench (shift-click output slot). Waits 1 tick. |

```js
// Open a chest and take the first item
minecraft.isGuiOpen();       // true
minecraft.clickSlot(0);      // left-click slot 0
minecraft.closeGui();
```

### World Query

| Function | Returns | Description |
|----------|---------|-------------|
| `getBlock(x, y, z)` | `string` | Get block ID at position |
| `isBlock(x, y, z, id)` | `boolean` | Check if block at position matches ID |
| `isBlockByTag(x, y, z, tag)` | `boolean` | Check if block at position matches tag |
| `findBlocks(id, radius?)` | `[{x,y,z}]` | Find blocks by ID in sphere around player (default radius 16) |
| `findBlocksByTag(tag, radius?)` | `[{x,y,z}]` | Find blocks by tag in sphere around player |
| `findBlocksInBox(x1,y1,z1, x2,y2,z2, id?)` | `[{x,y,z,block?}]` | Scan rectangular area. Omit `id` to return all non-air blocks (includes `block` field). |
| `getNearbyEntities(radius, type?)` | `[{name,type,x,y,z,distance}]` | Find entities in range. Optional type filter e.g. `"minecraft:creeper"`. |
| `getNearbyPlayers(radius)` | `[{name,x,y,z,distance}]` | Find nearby players (excludes self) |
| `rayTrace(maxDist?)` | `{type,x,y,z,...}` | Cast ray from player view. Returns `type: "block"|"entity"|"miss"`. |
| `facingBlock(id)` | `boolean` | Is crosshair pointing at given block |
| `facingEntity(id)` | `boolean` | Is crosshair pointing at given entity |
| `getFacingBlock()` | `string` | Get the block ID under crosshair |

```js
var ores = minecraft.findBlocks("minecraft:diamond_ore", 32);
minecraft.log("Found " + ores.length + " diamond ores");
for (var i = 0; i < ores.length; i++) {
    minecraft.log(ores[i].x + ", " + ores[i].y + ", " + ores[i].z);
}

var hit = minecraft.rayTrace(10);
if (hit.type === "block") {
    minecraft.log("Looking at: " + minecraft.getBlock(hit.x, hit.y, hit.z));
}

var creepers = minecraft.getNearbyEntities(32, "minecraft:creeper");
minecraft.log(creepers.length + " creepers nearby");
```

### Chat & Files

| Function | Description |
|----------|-------------|
| `say(msg)` | Send chat message to server |
| `log(msg)` | Print client-side message (not sent to server) |
| `execFile(path)` | Execute another script from `pendulum/` folder |
| `getScriptDir()` → `string` | Get absolute path of script directory |
| `help()` | Print API overview in chat |

```js
minecraft.log("Starting mining routine...");
minecraft.execFile("mining/pick_ores.js");
```

## Script Files

Place `.js` files in `.minecraft/pendulum/` and run with `/pendulum file <path>`.

Example: `.minecraft/pendulum/strip_mine.js`

```js
// Strip mine: break 20 blocks forward
for (var i = 0; i < 20; i++) {
    minecraft.lookAt(
        Math.floor(minecraft.getX()) + 1,
        Math.floor(minecraft.getY()),
        Math.floor(minecraft.getZ())
    );
    var ok = minecraft.breakBlock();
    if (!ok) { minecraft.log("Timeout!"); break; }
    minecraft.forward();
}
minecraft.log("Done!");
```
