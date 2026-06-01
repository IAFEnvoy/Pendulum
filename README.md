# Pendulum

**Client-side JavaScript executor for Minecraft.** Automate player actions — movement, construction, combat, inventory, world queries, and Baritone pathfinding. Designed for both human players and AI agents.

## Features

### 🧑 For Players
- **`/pendulum execute`** — run JavaScript inline in chat
- **`/pendulum file`** — run `.js` scripts from `.minecraft/pendulum/`
- **`mc.*`** — 80+ API functions: move, mine, build, craft, query the world
- **`br.*`** — 45 Baritone functions: pathfinding, mining, farming, building schematics

### 🤖 For AI Agents
- **MCP JSON-RPC 2.0 over TCP** — native AI agent integration
- **`pendulum_eval`** — execute arbitrary JS and return results
- **`pendulum_screenshot`** — capture the game view
- **`pendulum_gui_elements`** — read all visible GUI controls
- **`pendulum_status`** / **`pendulum_abort`** — manage script state
- Compatible with VS Code Copilot, Claude Desktop, and any MCP client

## Quick Start

```js
// Walk forward 1 second
/pendulum execute mc.forward(20)

// Mine all pumpkins nearby
/pendulum execute for(let p of mc.findBlocks('minecraft:pumpkin',8)){ mc.breakBlockAt(p.x,p.y,p.z) }

// Run a script file
/pendulum file farm.js
```

## API at a Glance

```
mc.forward(20)                   walk forward; args=ticks, omit to hold
mc.back/left/right(ticks?)       directional movement
mc.stop()                        release all movement keys
mc.jump(hold?) / mc.sneak(hold?) / mc.sprint(hold?)
mc.lookAt(x, y, z)               face a coordinate
mc.setYaw(y) / mc.setPitch(p)
mc.getX() / mc.getY() / mc.getZ()

mc.breakBlock()                  break block under crosshair (waits)
mc.breakBlockAt(x, y, z)         break specific coordinate (recommended)
mc.placeBlockAt(x, y, z)         place block precisely (no crosshair drift)
mc.jumpAndPlaceBelow()           jump up & place block under your feet
mc.useItem(32)                   hold right-click 32 ticks (eat/bow/shield)
mc.startUse() / mc.stopUse()     manual hold/release right-click
mc.use()                         single right-click
mc.attack()                      left-click attack
mc.swapHands() / mc.drop() / mc.dropAll()

mc.selectHotbar(1-9)             switch hotbar slot
mc.getItemInHand()               → {id, count, name, ...}
mc.getAllItems()                 → [{slot, id, count, ...}]
mc.hasItem('minecraft:dirt', 64)

mc.findBlocks('diamond_ore', 16) → [{x,y,z}]
mc.findBlocksInBox(x1,y1,z1,x2,y2,z2, id?)
mc.getNearbyPlayers(radius)      → [{name, x, y, z, dist}]
mc.rayTrace(maxDist?)            → {type, x, y, z, ...}

mc.say('hello') / mc.log('info')
mc.waitTick(ticks?)
mc.closeGui() / mc.isGuiOpen() / mc.getGuiTitle()
mc.clickSlot(id) / mc.quickMoveItem(fromSlot)
mc.getContainerAllItems() / mc.getContainerType()

// Baritone (requires Baritone mod)
br.goto(x, y, z)                 pathfind to coordinates
br.mine('diamond_ore', 64)       automated mining
br.surface()                     return to the surface
br.isActive() → boolean          check if Baritone is pathing
br.stop()                        cancel all Baritone tasks
br.command('baritone command')   execute any Baritone command

mc.help() / br.help()            show full API reference in chat
```

## Installation

- **Minecraft 1.20.1** + **Fabric Loader** + **Fabric API** (≥0.92.0)
- Drop `.jar` into `mods/`
- **Baritone** is optional → install to unlock `br.*` functions

## MCP Server

Start the MCP server for AI agent access:

```
/pendulum mcp start      # default port 25566
/pendulum mcp stop       # stop the server
/pendulum mcp status     # check if running
```

Configure your MCP client to connect to `localhost:25566` (default). See [wiki/agent-guide/AGENT.md](wiki/agent-guide/AGENT.md) for the complete API reference.

## Documentation

| Guide | For |
|-------|-----|
| [Getting Started](wiki/getting-started/installation.md) | Installation & first script |
| [Player Guide](wiki/player-guide/commands.md) | All player-facing features |
| [Agent Guide](wiki/agent-guide/AGENT.md) | Complete API reference for AI agents |
