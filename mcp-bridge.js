// mcp-bridge.js — TCP-to-stdio bridge for Pendulum MCP
const net = require("net");
const client = net.createConnection({ host: "127.0.0.1", port: 25566 });
process.stdin.pipe(client);
client.pipe(process.stdout);
client.on("error", (e) => {
  console.error("MCP bridge error:", e.message);
  process.exit(1);
});
