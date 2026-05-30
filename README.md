# Pendulum

Client-side JavaScript script executor for Minecraft. Automate player actions — movement, block breaking, inventory, world queries, and Baritone integration.

## Install

- **Minecraft 1.20.1** + **Fabric Loader** + **Fabric API** (`>=0.92.0`)
- Drop the jar into `mods/`
- **Baritone** is optional — install it to unlock `baritone.*` / `br.*` functions

## Commands

| Command | Description |
|---------|-------------|
| `/pendulum execute <code>` | Run JS inline |
| `/pendulum file <path>` | Run `.js` from `.minecraft/pendulum/` |
| `/pendulum abort` | Stop running script |
| `/pendulum status` | Show current script state |
| `/pendulum help` | Show API overview |

> Only one script runs at a time. `/pendulum abort` if needed.

## Objects

| Object | Aliases | Purpose |
|--------|---------|---------|
| `minecraft` | `mc`, `game` | Player control, world queries, inventory, GUI |
| `baritone` | `br` | Baritone automation (optional) |
| `console` | — | `console.log(...)` -> game log |

## Quick Example

```js
mc.forward(20);                       // walk forward 1 second

// Mine all pumpkins in range
for (let {x,y,z} of mc.findBlocks("minecraft:pumpkin", 8)) {
    mc.lookAt(x, y, z); mc.waitTick(2);
    mc.breakBlockAt(x, y, z);
}
```

## API Summary

**Movement** `forward(ticks?)` `back(ticks?)` `left(ticks?)` `right(ticks?)` `stop()` `jump(hold?)` `sneak(hold?)` `sprint(hold?)` `stopSprint()` `lookAt(x,y,z)` `setYaw(y)` `setPitch(p)` `getYaw()` `getPitch()` `getX()` `getY()` `getZ()`

**Interaction** `breakBlock()` `breakBlockAt(x,y,z)` `use()` `attack()` `swapHands()` `drop()` `dropAll()` `pickBlock()`

**Inventory & GUI** `selectHotbar(1-9)` `getSelectedSlot()` `hasItem(id,count?)` `closeGui()` `isGuiOpen()` `getGuiTitle()` `clickSlot(id,button?)` `clickSlotRight(id)` `craft()` `craftAll()`

**World Query** `getBlock(x,y,z)` `isBlock(x,y,z,id)` `isBlockByTag(x,y,z,tag)` `facingBlock(id)` `facingEntity(id)` `getFacingBlock()` `findBlocks(id,radius?)` `findBlocksByTag(tag,radius?)` `findBlocksInBox(x1,y1,z1,x2,y2,z2,id?)` `getNearbyEntities(radius,type?)` `getNearbyPlayers(radius)` `rayTrace(maxDist?)`

**Chat & Control** `say(msg)` `log(msg)` `waitTick(ticks?)` `execFile(path)` `getScriptDir()` `help()`

**Baritone (`br.*`)** `goto(x,y,z)` `mine(id,count?)` `tunnel()` `follow(type?)` `farm(range?)` `explore()` `getToBlock(id)` `build(schematic,x?,y?,z?)` `come()` `surface()` `axis()` `thisWay()` `pickup()` `click()` `stop()` `pause()` `resume()` `isActive()` `isPaused()` `command(cmd)` `setting(key,val)` `select(x1,y1,z1,x2,y2,z2)` `selPos1()` `selPos2()` `clearSelection()` `waypointSave/List/Delete()` `sethome()` `home()` `find(id)` `blacklist()` `proc()` `eta()` `help()`

## Documentation

Full API reference with examples: **[wiki/](wiki/docs/intro.md)**
