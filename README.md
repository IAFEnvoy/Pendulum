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
- **`mc.*`** — 80+ API functions: `mc.player.forward()`, `mc.world.findBlocks()`, `mc.inv.hasItem()`, `mc.gui.click()`, and more
- **`br.*`** — 45 Baritone functions: pathfinding, mining, farming, building schematics

### 🤖 For AI Agents

- **MCP JSON-RPC 2.0 over TCP** — native AI agent integration
- **`pendulum_eval`** — execute arbitrary JS and return results
- **`pendulum_screenshot`** — capture the game view
- **`pendulum_gui_elements`** — read all visible GUI controls
- **`pendulum_click`** / **`pendulum_click_button`** — click GUI elements
- **`pendulum_enumerate_widgets`** — full recursive widget tree
- **`pendulum_type_text`** / **`pendulum_press_key`** — keyboard input
- **`pendulum_status`** / **`pendulum_abort`** — manage script state
- Compatible with VS Code Copilot, Claude Desktop, and any MCP client

## Quick Start

```js
// Walk forward 1 second
/pendulum execute mc.player.forward(20)

// Mine all pumpkins nearby
/pendulum execute for(let p of mc.world.findBlocks('minecraft:pumpkin',8)){ mc.player.breakBlockAt(p.x,p.y,p.z) }

// Run a script file
/pendulum file farm.js

// AI Agent workflow: screenshot + click
pendulum_screenshot  →  pendulum_click_button("Done")  →  pendulum_type_text("Hello", true)
```

## MCP Server

Start the MCP server for AI agent access:

```
/pendulum mcp start      # default port 25566
/pendulum mcp stop       # stop the server
/pendulum mcp status     # check if running
```

Configure your MCP client to connect to `localhost:25566` (default).
