// mcp-bridge.js — TCP-to-stdio bridge for Pendulum MCP
// Usage: node mcp-bridge.js [host] [port]
// Env:   PENDULUM_HOST (default: 127.0.0.1)
//        PENDULUM_PORT (default: 25566)
const net = require("net");

const host = process.argv[2] || process.env.PENDULUM_HOST || "127.0.0.1";
const port = parseInt(process.argv[3] || process.env.PENDULUM_PORT || "25566", 10);

const client = net.createConnection({ host, port }, () => {
  console.error(`[mcp-bridge] Connected to Pendulum MCP at ${host}:${port}`);
});

process.stdin.pipe(client);
client.pipe(process.stdout);

client.on("error", (e) => {
  console.error(`[mcp-bridge] Connection error (${host}:${port}):`, e.message);
  process.exit(1);
});

client.on("close", () => {
  console.error("[mcp-bridge] Connection closed");
  process.exit(0);
});
