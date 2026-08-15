# Pendulum

**Client-side JavaScript executor for Minecraft.** Automate player actions — movement, construction, combat, inventory,
world queries, and `Baritone` supported. Designed for both human players and AI agents.

This mod is partly inspired by `Playwright.js`, which allows agents to control web browsers via JavaScript. Pendulum
brings that same power to Minecraft, enabling complex automation and AI integration through a simple JS API.

## Version Support

| Minecraft | Fabric | Forge | NeoForge | Status          |
|-----------|--------|-------|----------|-----------------|
| 1.20.1    | ✅      | ✅     |          | Fully Supported |
| 1.21.1    | ✅      | ❌     | ✅        | Fully Supported |
| 26.1.2    | 🔨     | ❌     | 🔨       | In development  |

## Features

For detailed documentation, see the [Documentation](https://docs.iafenvoy.com/docs/mod/pendulum/)

### 🧑 For Players

- **`/pendulum execute`** — run JavaScript inline in chat
- **`/pendulum file`** — run `.js` scripts from `.minecraft/pendulum/`
- **`mc.*`** — 80+ API functions: `mc.player.forward()`, `mc.world.findBlocks()`, `mc.inv.hasItem()`, `mc.gui.click()`, and more. Log via `pendulum.log()`, `pendulum.warn()`, `pendulum.error()`.
- **`br.*`** — 45 Baritone functions: pathfinding, mining, farming, building schematics

### 🤖 For AI Agents 

There are 2 mods for agents to control Pendulum: Data Mode and Visual Mode. Data Mode provides JavaScript APIs for direct control, while Visual Mode allows agents to interact with the game through screenshots and simulated input.

## Quick Start

```js
// Walk forward 1 second
/pendulum execute mc.player.forward(20)

// Mine all pumpkins nearby
/pendulum execute for(let p of mc.world.findBlocks('minecraft:pumpkin',4)){ mc.player.breakBlockAt(p.x,p.y,p.z) }

// Run a script file
/pendulum file farm.js

// AI Agent workflow: screenshot + click
gui/screenshot  →  gui/clickButton("Done")  →  simulate/typeText("Hello", true)
```

## MCP Server

Start the MCP server for AI agent access:

```
/pendulum mcp start      # default port 25566
/pendulum mcp stop       # stop the server
/pendulum mcp status     # check if running
```

Configure your MCP client to connect to `localhost:25566` (default).
