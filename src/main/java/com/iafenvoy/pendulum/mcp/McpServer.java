package com.iafenvoy.pendulum.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.iafenvoy.pendulum.script.BaritoneHelper;
import com.iafenvoy.pendulum.script.ScriptEngine;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple MCP (Model Context Protocol) JSON-RPC 2.0 server over TCP.
 * Exposes Pendulum's JS execution capabilities as MCP tools for AI agents.
 */
public final class McpServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String SERVER_NAME = "Pendulum MCP";
    private static final String SERVER_VERSION = "1.0.0";

    private static final McpServer INSTANCE = new McpServer();

    private ServerSocket serverSocket;
    private final ExecutorService clientThreads = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Pendulum-MCP-Client");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private int currentPort;

    private McpServer() {
    }

    public static McpServer getInstance() {
        return INSTANCE;
    }

    public boolean isRunning() {
        return this.running.get();
    }

    public int getPort() {
        return this.currentPort;
    }

    public CompletableFuture<Boolean> start(int port) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (this.running.get()) {
            future.complete(false);
            return future;
        }
        new Thread(() -> {
            try {
                this.serverSocket = new ServerSocket(port);
                this.currentPort = port;
                this.running.set(true);
                LOGGER.info("MCP server started on port {}", port);
                future.complete(true);

                while (this.running.get()) {
                    try {
                        Socket client = this.serverSocket.accept();
                        this.clientThreads.submit(() -> this.handleClient(client));
                    } catch (IOException e) {
                        if (this.running.get()) {
                            LOGGER.error("MCP accept error", e);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to start MCP server", e);
                this.running.set(false);
                future.completeExceptionally(e);
            }
        }, "Pendulum-MCP-Accept").start();
        return future;
    }

    public void stop() {
        this.running.set(false);
        try {
            if (this.serverSocket != null && !this.serverSocket.isClosed()) {
                this.serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.warn("Error closing MCP server socket", e);
        }
        LOGGER.info("MCP server stopped.");
    }

    private void handleClient(Socket client) {
        try (client;
             BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null && this.running.get()) {
                if (line.isBlank()) continue;
                try {
                    String response = this.processMessage(line);
                    if (response != null) {
                        synchronized (writer) {
                            writer.write(response);
                            writer.newLine();
                            writer.flush();
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error processing MCP message", e);
                    this.sendError(writer, null, -32603, "Internal error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // Client disconnected
        }
    }

    private String processMessage(String jsonStr) {
        JsonObject req;
        try {
            req = GSON.fromJson(jsonStr, JsonObject.class);
        } catch (Exception e) {
            return jsonRpcError(null, -32700, "Parse error");
        }

        String method = req.has("method") ? req.get("method").getAsString() : null;
        JsonPrimitive idPrim = req.has("id") ? req.get("id").getAsJsonPrimitive() : null;
        Object id = idPrim != null ? (idPrim.isNumber() ? idPrim.getAsLong() : idPrim.getAsString()) : null;

        if (method == null) {
            return jsonRpcError(id, -32600, "Invalid Request");
        }

        switch (method) {
            case "initialize":
                return this.handleInitialize(id, req);
            case "notifications/initialized":
                return null; // No response for notifications
            case "tools/list":
                return this.handleToolsList(id);
            case "tools/call":
                return this.handleToolsCall(id, req);
            default:
                return jsonRpcError(id, -32601, "Method not found: " + method);
        }
    }

    private String handleInitialize(Object id, JsonObject req) {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", "2024-11-05");

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", SERVER_VERSION);

        // Meta: tell the agent whether Baritone is available
        JsonObject meta = new JsonObject();
        meta.addProperty("baritoneInstalled", BaritoneHelper.isLoaded());
        meta.addProperty("preferBaritone", "When baritone is installed, use br.* functions (br.goto, br.mine, br.follow, etc.) for pathfinding/mining/farming tasks instead of manual mc.forward/mc.breakBlock loops.");
        meta.addProperty("hint", "Baritone functions: br.goto(x,y,z), br.mine('blockId',count), br.follow('type'), br.stop(), br.isActive(), br.command('raw command'), br.help()");
        serverInfo.add("meta", meta);

        result.add("serverInfo", serverInfo);

        JsonObject capabilities = new JsonObject();
        JsonObject toolsCap = new JsonObject();
        capabilities.add("tools", toolsCap);
        result.add("capabilities", capabilities);

        return jsonRpcResult(id, result);
    }

    private String handleToolsList(Object id) {
        JsonArray tools = new JsonArray();

        tools.add(makeTool("pendulum_eval",
                "Execute JavaScript code in Minecraft using Pendulum. Key globals: mc/minecraft/game. " +
                        "API includes: movement (forward/back/left/right/stop with tick counts), " +
                        "block breaking (breakBlock/breakBlockAt), placement (placeBlock), world queries " +
                        "(findBlocks/findBlocksInBox/getBlockState/rayTrace/getLookingEntity), " +
                        "inventory (getAllItems/getItemInHand/hasItem/selectHotbar), GUI (isGuiOpen/" +
                        "getContainerAllItems/getContainerType/clickSlot/closeGui), player state " +
                        "(getPlayerHealth/getPlayerHunger/canReach/canSeeBlock/getAttackCooldown), " +
                        "environment (getBiomeAt/getLightLevel/getDifficulty/getDimension), and " +
                        "commands (executeCommand). All interaction functions are synchronous. " +
                        "BARITONE: If baritone is installed (check serverInfo.meta.baritoneInstalled), " +
                        "PREFER br.* functions: br.goto(x,y,z) instead of mc.forward loops, " +
                        "br.mine('blockId',count) instead of mc.findBlocks+mc.breakBlockAt, " +
                        "br.follow('entityType') to follow entities, br.stop() to cancel, " +
                        "br.isActive() to check status, br.command('raw') for any baritone command.",
                jsonSchema("object",
                        jsonProperty("code", "string", "JS code. E.g.: mc.forward(20); " +
                                "for(let{b} of mc.findBlocks('diamond_ore',16)) mc.breakBlockAt(b.x,b.y,b.z); " +
                                "JSON.stringify(mc.getAllItems()) for inventory. " +
                                "mc.rayTrace(5) to see what's ahead. " +
                                "If baritone installed: br.goto(100,64,200); br.mine('diamond_ore',64);"))));

        tools.add(makeTool("pendulum_screenshot",
                "Take a screenshot of the current Minecraft game window. Returns a base64-encoded PNG image.",
                jsonSchema("object", jsonProperty("_", "string", "No parameters"))));

        tools.add(makeTool("pendulum_gui_elements",
                "Get the list of non-slot GUI elements currently visible on screen (buttons, labels, etc.). " +
                        "Returns an array of objects with type, position, size, and text fields.",
                jsonSchema("object", jsonProperty("_", "string", "No parameters"))));

        tools.add(makeTool("pendulum_status",
                "Check if a script is currently running in Pendulum.",
                jsonSchema("object", jsonProperty("_", "string", "No parameters required"))));

        tools.add(makeTool("pendulum_abort",
                "Abort the currently running Pendulum script.",
                jsonSchema("object", jsonProperty("_", "string", "No parameters required"))));

        JsonObject result = new JsonObject();
        result.add("tools", tools);
        return jsonRpcResult(id, result);
    }

    private String handleToolsCall(Object id, JsonObject req) {
        JsonObject params = req.has("params") ? req.getAsJsonObject("params") : new JsonObject();
        String toolName = params.has("name") ? params.get("name").getAsString() : "";
        JsonObject arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

        try {
            JsonArray content = new JsonArray();
            switch (toolName) {
                case "pendulum_eval": {
                    String code = arguments.has("code") ? arguments.get("code").getAsString() : "";
                    if (code.isEmpty()) {
                        content.add(textContent("Error: 'code' parameter is required."));
                    } else {
                        synchronized (McpServer.class) {
                            CompletableFuture<String> resultFuture = new CompletableFuture<>();
                            ScriptEngine.getInstance().execWithCallback(code, resultFuture);
                            try {
                                String result = resultFuture.get(30, java.util.concurrent.TimeUnit.SECONDS);
                                content.add(textContent(result));
                            } catch (Exception e) {
                                content.add(textContent("Timeout or error: " + e.getMessage()));
                            }
                        }
                    }
                    break;
                }
                case "pendulum_status": {
                    boolean isRunning = ScriptEngine.getInstance().isRunning();
                    String status = ScriptEngine.getInstance().getStatus();
                    content.add(textContent(isRunning ? "Running: " + status : "Idle"));
                    break;
                }
                case "pendulum_abort": {
                    ScriptEngine.getInstance().abort();
                    content.add(textContent("Script aborted."));
                    break;
                }
                case "pendulum_screenshot": {
                    String b64 = takeScreenshotB64();
                    if (b64 != null) {
                        content.add(imageContent("data:image/png;base64," + b64, "image/png"));
                    } else {
                        content.add(textContent("Failed to capture screenshot."));
                    }
                    break;
                }
                case "pendulum_gui_elements": {
                    String json = getGuiElementsJson();
                    content.add(textContent(json != null ? json : "No GUI open or error reading elements."));
                    break;
                }
                default:
                    content.add(textContent("Unknown tool: " + toolName));
            }
            JsonObject result = new JsonObject();
            result.add("content", content);
            return jsonRpcResult(id, result);
        } catch (Exception e) {
            LOGGER.error("MCP tools/call error", e);
            return jsonRpcError(id, -32603, "Tool execution error: " + e.getMessage());
        }
    }

    // ---- External endpoint helpers ----

    private static String takeScreenshotB64() {
        File tmp = null;
        try {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getWidth();
            int h = mc.getWindow().getHeight();
            NativeImage img = new NativeImage(w, h, false);
            mc.getMainRenderTarget().bindRead();
            img.downloadTexture(0, false);
            img.flipY();
            tmp = File.createTempFile("pendulum_ss", ".png");
            img.writeToFile(tmp);
            img.close();
            byte[] pngBytes = Files.readAllBytes(tmp.toPath());
            return Base64.getEncoder().encodeToString(pngBytes);
        } catch (Exception e) {
            LOGGER.error("Screenshot failed", e);
            return null;
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static String getGuiElementsJson() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) return "[]";
            JsonArray arr = new JsonArray();
            for (var child : mc.screen.children()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("type", child.getClass().getSimpleName());
                try {
                    java.lang.reflect.Field xF = findAccessibleField(child.getClass(), "x", "getX", "field_22786");
                    java.lang.reflect.Field yF = findAccessibleField(child.getClass(), "y", "getY", "field_22787");
                    java.lang.reflect.Field wF = findAccessibleField(child.getClass(), "width", "getWidth", "field_22788");
                    java.lang.reflect.Field hF = findAccessibleField(child.getClass(), "height", "getHeight", "field_22789");
                    if (xF != null) obj.addProperty("x", ((Number) xF.get(child)).intValue());
                    if (yF != null) obj.addProperty("y", ((Number) yF.get(child)).intValue());
                    if (wF != null) obj.addProperty("width", ((Number) wF.get(child)).intValue());
                    if (hF != null) obj.addProperty("height", ((Number) hF.get(child)).intValue());
                } catch (Exception ignored) {
                }
                try {
                    java.lang.reflect.Field msgF = findAccessibleField(child.getClass(), "message", "getMessage", "field_22791");
                    if (msgF != null) {
                        Object msg = msgF.get(child);
                        obj.addProperty("text", msg instanceof net.minecraft.network.chat.Component c ? c.getString() : msg.toString());
                    }
                } catch (Exception ignored) {
                }
                arr.add(obj);
            }
            return GSON.toJson(arr);
        } catch (Exception e) {
            LOGGER.error("Failed to read GUI elements", e);
            return null;
        }
    }

    private static java.lang.reflect.Field findAccessibleField(Class<?> clazz, String... candidates) {
        for (String name : candidates) {
            try {
                java.lang.reflect.Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        if (clazz.getSuperclass() != null) return findAccessibleField(clazz.getSuperclass(), candidates);
        return null;
    }

    // ---- JSON-RPC helpers ----

    private static JsonObject makeTool(String name, String description, JsonObject inputSchema) {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", name);
        tool.addProperty("description", description);
        tool.add("inputSchema", inputSchema);
        return tool;
    }

    private static JsonObject jsonSchema(String type, JsonProperty... properties) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", type);
        JsonObject props = new JsonObject();
        JsonArray required = new JsonArray();
        for (JsonProperty p : properties) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", p.type);
            prop.addProperty("description", p.description);
            props.add(p.name, prop);
            if (p.required) required.add(p.name);
        }
        schema.add("properties", props);
        if (required.size() > 0) schema.add("required", required);
        return schema;
    }

    private static JsonObject textContent(String text) {
        JsonObject c = new JsonObject();
        c.addProperty("type", "text");
        c.addProperty("text", text);
        return c;
    }

    private static JsonObject imageContent(String data, String mimeType) {
        JsonObject c = new JsonObject();
        c.addProperty("type", "image");
        c.addProperty("data", data);
        c.addProperty("mimeType", mimeType);
        return c;
    }

    private static JsonProperty jsonProperty(String name, String type, String description) {
        return new JsonProperty(name, type, description, true);
    }

    private record JsonProperty(String name, String type, String description, boolean required) {
    }

    private static String jsonRpcResult(Object id, JsonObject result) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        if (id instanceof Number) resp.addProperty("id", (Number) id);
        else if (id instanceof String) resp.addProperty("id", (String) id);
        resp.add("result", result);
        return GSON.toJson(resp);
    }

    private static String jsonRpcError(Object id, int code, String message) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        if (id instanceof Number) resp.addProperty("id", (Number) id);
        else if (id instanceof String) resp.addProperty("id", (String) id);
        else resp.add("id", null);
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        resp.add("error", err);
        return GSON.toJson(resp);
    }

    private void sendError(BufferedWriter writer, Object id, int code, String message) {
        try {
            String resp = jsonRpcError(id, code, message);
            synchronized (writer) {
                writer.write(resp);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException ignored) {
        }
    }
}
