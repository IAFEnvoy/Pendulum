package com.iafenvoy.pendulum.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.iafenvoy.pendulum.script.BaritoneHelper;
import com.iafenvoy.pendulum.script.ScriptEngine;
import com.iafenvoy.pendulum.util.ScreenInputHelper;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

    // ---- MCP Call Logging (replaces SSE + Web panel) ----
    private static final Path LOG_DIR = Paths.get("pendulum", "logs");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
    private static final Object logLock = new Object();

    // ---- Video Frame Capture (experimental, use sparingly) ----
    private static volatile boolean videoCaptureActive = false;
    private static volatile byte[] videoFrameCache = null;
    private static volatile long videoFrameTime = 0;
    private static int videoFrameCounter = 0;
    private static final int VIDEO_FRAME_SKIP = 6; // capture every 6th frame (~10fps @ 60fps)

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
                ScriptEngine.getInstance().setMcpConnected(true);
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
        ScriptEngine.getInstance().setMcpConnected(false);
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

        return switch (method) {
            case "initialize" -> this.handleInitialize(id, req);
            case "notifications/initialized" -> null; // No response for notifications
            case "tools/list" -> this.handleToolsList(id);
            case "tools/call" -> this.handleToolsCall(id, req);
            default -> jsonRpcError(id, -32601, "Method not found: " + method);
        };
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

        // ---- Core ----
        tools.add(makeTool("script/eval",
                I18n.get("pendulum.mcp.tool.eval"),
                jsonSchema("object",
                        jsonProperty("code", "string", I18n.get("pendulum.mcp.tool.eval.param.code")))));

        tools.add(makeTool("script/evalAsync",
                I18n.get("pendulum.mcp.tool.eval_async"),
                jsonSchema("object",
                        jsonProperty("code", "string", I18n.get("pendulum.mcp.tool.eval_async.param.code")))));

        tools.add(makeTool("script/status",
                I18n.get("pendulum.mcp.tool.status"),
                jsonSchema("object", jsonProperty("_", "string", I18n.get("pendulum.mcp.tool.no_params")))));

        tools.add(makeTool("script/abort",
                I18n.get("pendulum.mcp.tool.abort"),
                jsonSchema("object", jsonProperty("_", "string", I18n.get("pendulum.mcp.tool.no_params")))));

        tools.add(makeTool("health",
                I18n.get("pendulum.mcp.tool.health"),
                jsonSchema("object", jsonProperty("_", "string", I18n.get("pendulum.mcp.tool.no_params")))));

        // ---- Visual ----
        tools.add(makeTool("gui/screenshot",
                I18n.get("pendulum.mcp.tool.screenshot"),
                jsonSchema("object",
                        jsonProperty("path", "string", I18n.get("pendulum.mcp.tool.screenshot.param.path"), false))));

        // ---- Simulate Input ----
        tools.add(makeTool("simulate/click",
                I18n.get("pendulum.mcp.tool.click"),
                jsonSchema("object",
                        jsonProperty("x", "integer", I18n.get("pendulum.mcp.tool.click.param.x")),
                        jsonProperty("y", "integer", I18n.get("pendulum.mcp.tool.click.param.y")),
                        jsonProperty("button", "string", I18n.get("pendulum.mcp.tool.click.param.button"), false))));

        // ---- GUI / Screen Interaction ----
        tools.add(makeTool("gui/clickButton",
                I18n.get("pendulum.mcp.tool.click_button"),
                jsonSchema("object",
                        jsonProperty("target", "string", I18n.get("pendulum.mcp.tool.click_button.param.target")))));

        tools.add(makeTool("gui/enumerateWidgets",
                I18n.get("pendulum.mcp.tool.enumerate_widgets"),
                jsonSchema("object", jsonProperty("_", "string", I18n.get("pendulum.mcp.tool.no_params")))));

        tools.add(makeTool("gui/guiElements",
                I18n.get("pendulum.mcp.tool.gui_elements"),
                jsonSchema("object", jsonProperty("_", "string", I18n.get("pendulum.mcp.tool.no_params")))));

        tools.add(makeTool("simulate/pressKey",
                I18n.get("pendulum.mcp.tool.press_key"),
                jsonSchema("object",
                        jsonProperty("key", "string", I18n.get("pendulum.mcp.tool.press_key.param.key")),
                        jsonProperty("holdSeconds", "number", I18n.get("pendulum.mcp.tool.press_key.param.hold"), false))));

        tools.add(makeTool("simulate/typeText",
                I18n.get("pendulum.mcp.tool.type_text"),
                jsonSchema("object",
                        jsonProperty("text", "string", I18n.get("pendulum.mcp.tool.type_text.param.text")),
                        jsonProperty("pressEnter", "boolean", I18n.get("pendulum.mcp.tool.type_text.param.enter"), false))));

        tools.add(makeTool("simulate/pasteText",
                I18n.get("pendulum.mcp.tool.paste_text"),
                jsonSchema("object",
                        jsonProperty("text", "string", I18n.get("pendulum.mcp.tool.paste_text.param.text")),
                        jsonProperty("pressEnter", "boolean", I18n.get("pendulum.mcp.tool.paste_text.param.enter"), false))));

        tools.add(makeTool("simulate/scroll",
                I18n.get("pendulum.mcp.tool.scroll"),
                jsonSchema("object",
                        jsonProperty("clicks", "integer", I18n.get("pendulum.mcp.tool.scroll.param.clicks")))));

        tools.add(makeTool("simulate/hotkey",
                I18n.get("pendulum.mcp.tool.hotkey"),
                jsonSchema("object",
                        jsonProperty("keys", "string", I18n.get("pendulum.mcp.tool.hotkey.param.keys")))));

        tools.add(makeTool("simulate/mouseDrag",
                I18n.get("pendulum.mcp.tool.mouse_drag"),
                jsonSchema("object",
                        jsonProperty("xStart", "integer", I18n.get("pendulum.mcp.tool.mouse_drag.param.x1")),
                        jsonProperty("yStart", "integer", I18n.get("pendulum.mcp.tool.mouse_drag.param.y1")),
                        jsonProperty("xEnd", "integer", I18n.get("pendulum.mcp.tool.mouse_drag.param.x2")),
                        jsonProperty("yEnd", "integer", I18n.get("pendulum.mcp.tool.mouse_drag.param.y2")),
                        jsonProperty("button", "string", I18n.get("pendulum.mcp.tool.click.param.button"), false))));

        tools.add(makeTool("simulate/callScreenMethod",
                I18n.get("pendulum.mcp.tool.call_screen_method"),
                jsonSchema("object",
                        jsonProperty("method", "string", I18n.get("pendulum.mcp.tool.call_screen_method.param.method")))));

        tools.add(makeTool("simulate/selectListItem",
                I18n.get("pendulum.mcp.tool.select_list_item"),
                jsonSchema("object",
                        jsonProperty("text", "string", I18n.get("pendulum.mcp.tool.select_list_item.param.text")))));

        // ---- Utility ----
        tools.add(makeTool("wait",
                I18n.get("pendulum.mcp.tool.wait"),
                jsonSchema("object",
                        jsonProperty("seconds", "number", I18n.get("pendulum.mcp.tool.wait.param.seconds"), false))));

        // ---- Video Frame Capture (experimental) ----
        tools.add(makeTool("video/start",
                I18n.get("pendulum.mcp.tool.video_start"),
                jsonSchema("object", jsonProperty("_", "string", I18n.get("pendulum.mcp.tool.no_params")))));

        tools.add(makeTool("video/stop",
                I18n.get("pendulum.mcp.tool.video_stop"),
                jsonSchema("object", jsonProperty("_", "string", I18n.get("pendulum.mcp.tool.no_params")))));

        tools.add(makeTool("video/frame",
                I18n.get("pendulum.mcp.tool.video_frame"),
                jsonSchema("object", jsonProperty("_", "string", I18n.get("pendulum.mcp.tool.no_params")))));

        JsonObject result = new JsonObject();
        result.add("tools", tools);
        return jsonRpcResult(id, result);
    }

    private String handleToolsCall(Object id, JsonObject req) {
        JsonObject params = req.has("params") ? req.getAsJsonObject("params") : new JsonObject();
        String toolName = params.has("name") ? params.get("name").getAsString() : "";
        JsonObject arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();
        String traceId = "trace-" + System.currentTimeMillis() + "-" + (int)(Math.random()*10000);

        long startMs = System.currentTimeMillis();
        boolean isError = false;
        String resultPreview = null;
        try {
            JsonArray content = new JsonArray();
            switch (toolName) {
                case "script/eval": {
                    String code = arguments.has("code") ? arguments.get("code").getAsString() : "";
                    if (code.isEmpty()) {
                        content.add(textContent("Error: 'code' parameter is required."));
                    } else {
                        synchronized (McpServer.class) {
                            CompletableFuture<String> resultFuture = new CompletableFuture<>();
                            ScriptEngine.getInstance().execWithCallback(code, resultFuture);
                            try {
                                String result = resultFuture.get(120, java.util.concurrent.TimeUnit.SECONDS);
                                content.add(textContent("[trace:" + traceId + "] " + result));
                            } catch (Exception e) {
                                content.add(textContent("Timeout or error: " + e.getMessage()));
                            }
                        }
                    }
                    break;
                }
                case "script/evalAsync": {
                    String code = arguments.has("code") ? arguments.get("code").getAsString() : "";
                    if (code.isEmpty()) {
                        content.add(textContent("Error: 'code' parameter is required."));
                    } else {
                        ScriptEngine.getInstance().execWithCallback(code, new CompletableFuture<>());
                        content.add(textContent("Script submitted. Use status to check. trace: " + traceId));
                    }
                    break;
                }
                case "script/status": {
                    boolean running = ScriptEngine.getInstance().isRunning();
                    String result = ScriptEngine.getInstance().lastEvalResult;
                    JsonObject s = new JsonObject();
                    s.addProperty("running", running);
                    if (result != null) s.addProperty("lastResult", result);
                    content.add(textContent(GSON.toJson(s)));
                    break;
                }
                case "script/abort": {
                    ScriptEngine.getInstance().abort();
                    content.add(textContent("Script aborted."));
                    break;
                }
                case "health": {
                    JsonObject h = new JsonObject();
                    h.addProperty("scriptRunning", ScriptEngine.getInstance().isRunning());
                    h.addProperty("mcpServer", isRunning());
                    h.addProperty("baritone", BaritoneHelper.isLoaded());
                    try {
                        String test = takeScreenshotB64();
                        h.addProperty("screenshot", test != null && test.length() > 100);
                    } catch (Exception e) {
                        h.addProperty("screenshot", false);
                        h.addProperty("screenshotError", e.getMessage());
                    }
                    h.addProperty("keyboardInjection", "GLFW only (Minecraft window focus required)");
                    content.add(textContent(GSON.toJson(h)));
                    break;
                }
                case "gui/screenshot": {
                    String b64 = takeScreenshotB64();
                    if (b64 == null) {
                        content.add(textContent("Failed to capture screenshot."));
                    } else {
                        // Always return base64 image
                        content.add(imageContent(b64, "image/png"));
                        // Optionally save to disk
                        String path = arguments.has("path") ? arguments.get("path").getAsString() : null;
                        if (path != null && !path.isEmpty()) {
                            Path fp = Paths.get(path);
                            Files.createDirectories(fp.getParent());
                            byte[] pngBytes = Base64.getDecoder().decode(b64);
                            Files.write(fp, pngBytes);
                            content.add(textContent("{\"saved\":\"" + fp.toAbsolutePath().toString().replace("\\", "\\\\") + "\",\"size\":" + pngBytes.length + "}"));
                        }
                    }
                    break;
                }
                case "simulate/click": {
                    int x = arguments.has("x") ? arguments.get("x").getAsInt() : 0;
                    int y = arguments.has("y") ? arguments.get("y").getAsInt() : 0;
                    String btn = arguments.has("button") ? arguments.get("button").getAsString() : "left";
                    int button = btn.equals("right") ? 1 : btn.equals("middle") ? 2 : 0;
                    ScreenInputHelper.clickAt(x, y, button);
                    content.add(textContent("Clicked (" + x + "," + y + ") " + btn));
                    break;
                }
                case "gui/clickButton": {
                    String target = arguments.has("target") ? arguments.get("target").getAsString() : "";
                    if (target.isEmpty()) {
                        content.add(textContent("Error: 'target' is required."));
                    } else {
                        String r = clickWidgetByText(target);
                        content.add(textContent(r != null ? r : "{\"error\":\"widget not found: " + target + "\"}"));
                    }
                    break;
                }
                case "gui/enumerateWidgets": {
                    String json = enumerateAllWidgetsJson();
                    content.add(textContent(json != null ? json : "No GUI open."));
                    break;
                }
                case "gui/guiElements": {
                    String json = getGuiElementsJson();
                    content.add(textContent(json != null ? json : "No GUI open."));
                    break;
                }
                case "simulate/pressKey": {
                    String key = arguments.has("key") ? arguments.get("key").getAsString() : "";
                    float hold = arguments.has("holdSeconds") ? arguments.get("holdSeconds").getAsFloat() : 0f;
                    if (key.isEmpty()) {
                        content.add(textContent("Error: 'key' is required."));
                    } else {
                        ScreenInputHelper.pressKey(key, hold);
                        content.add(textContent("Pressed " + key + (hold > 0 ? " for " + hold + "s" : "")));
                    }
                    break;
                }
                case "simulate/typeText": {
                    String text = arguments.has("text") ? arguments.get("text").getAsString() : "";
                    boolean enter = arguments.has("pressEnter") && arguments.get("pressEnter").getAsBoolean();
                    if (text.isEmpty()) {
                        content.add(textContent("Error: 'text' is required."));
                    } else {
                        ScreenInputHelper.typeText(text);
                        if (enter) { try { Thread.sleep(50); } catch (InterruptedException ignored) {} ScreenInputHelper.pressKey("ENTER", 0f); }
                        content.add(textContent("Typed " + text.length() + " chars" + (enter ? " + Enter" : "")));
                    }
                    break;
                }
                case "simulate/pasteText": {
                    String text = arguments.has("text") ? arguments.get("text").getAsString() : "";
                    boolean enter = arguments.has("pressEnter") && arguments.get("pressEnter").getAsBoolean();
                    if (text.isEmpty()) {
                        content.add(textContent("Error: 'text' is required."));
                    } else {
                        ScreenInputHelper.typeText(text);
                        if (enter) { try { Thread.sleep(50); } catch (InterruptedException ignored) {} ScreenInputHelper.pressKey("ENTER", 0f); }
                        content.add(textContent("Pasted " + text.length() + " chars" + (enter ? " + Enter" : "")));
                    }
                    break;
                }
                case "simulate/scroll": {
                    int clicks = arguments.has("clicks") ? arguments.get("clicks").getAsInt() : 0;
                    ScreenInputHelper.scroll(clicks);
                    content.add(textContent("Scrolled " + clicks));
                    break;
                }
                case "simulate/hotkey": {
                    String keys = arguments.has("keys") ? arguments.get("keys").getAsString() : "";
                    if (keys.isEmpty()) {
                        content.add(textContent("Error: 'keys' is required."));
                    } else {
                        String[] parts = keys.split(",");
                        for (String k : parts) ScreenInputHelper.injectKey(k.trim(), 1);
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                        for (int i = parts.length - 1; i >= 0; i--) ScreenInputHelper.injectKey(parts[i].trim(), 0);
                        content.add(textContent("Hotkey: " + keys));
                    }
                    break;
                }
                case "simulate/mouseDrag": {
                    int x1 = arguments.has("xStart") ? arguments.get("xStart").getAsInt() : 0;
                    int y1 = arguments.has("yStart") ? arguments.get("yStart").getAsInt() : 0;
                    int x2 = arguments.has("xEnd") ? arguments.get("xEnd").getAsInt() : 0;
                    int y2 = arguments.has("yEnd") ? arguments.get("yEnd").getAsInt() : 0;
                    String btn = arguments.has("button") ? arguments.get("button").getAsString() : "left";
                    int button = btn.equals("right") ? 1 : btn.equals("middle") ? 2 : 0;
                    ScreenInputHelper.mouseDrag(x1, y1, x2, y2, button);
                    content.add(textContent("Dragged (" + x1 + "," + y1 + ")→(" + x2 + "," + y2 + ")"));
                    break;
                }
                case "simulate/callScreenMethod": {
                    String methodName = arguments.has("method") ? arguments.get("method").getAsString() : "";
                    if (methodName.isEmpty()) {
                        content.add(textContent("Error: 'method' is required."));
                    } else {
                        String res = callScreenMethod(methodName);
                        content.add(textContent(res != null ? res : "{\"error\":\"no screen\"}"));
                    }
                    break;
                }
                case "simulate/selectListItem": {
                    String itemText = arguments.has("text") ? arguments.get("text").getAsString() : "";
                    if (itemText.isEmpty()) {
                        content.add(textContent("Error: 'text' is required."));
                    } else {
                        String res = selectListItem(itemText);
                        content.add(textContent(res != null ? res : "{\"error\":\"no screen or list not found\"}"));
                    }
                    break;
                }
                case "wait": {
                    double seconds = arguments.has("seconds") ? arguments.get("seconds").getAsDouble() : 1.0;
                    try { Thread.sleep((long)(seconds * 1000)); } catch (InterruptedException ignored) {}
                    content.add(textContent("Waited " + seconds + "s."));
                    break;
                }
                case "video/start": {
                    videoCaptureActive = true;
                    videoFrameCounter = 0;
                    videoFrameCache = null;
                    content.add(textContent("Video capture started (~10fps)."));
                    break;
                }
                case "video/stop": {
                    videoCaptureActive = false;
                    videoFrameCache = null;
                    content.add(textContent("Video capture stopped."));
                    break;
                }
                case "video/frame": {
                    if (!videoCaptureActive) {
                        content.add(textContent("{\"error\":\"video capture not active\"}"));
                    } else {
                        String b64 = getVideoFrame();
                        if (b64.startsWith("{\"error\"")) {
                            content.add(textContent(b64));
                        } else {
                            content.add(imageContent(b64, "image/png"));
                        }
                    }
                    break;
                }
                default:
                    content.add(textContent("Unknown tool: " + toolName));
            }
            JsonObject result = new JsonObject();
            result.add("content", content);
            resultPreview = GSON.toJson(result);
            if (resultPreview != null && resultPreview.length() > 500)
                resultPreview = resultPreview.substring(0, 500) + "...(truncated)";
            return jsonRpcResult(id, result);
        } catch (Exception e) {
            isError = true;
            resultPreview = e.getMessage();
            LOGGER.error("MCP tools/call error", e);
            return jsonRpcError(id, -32603, "Tool execution error: " + e.getMessage());
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            logCall(toolName, arguments, resultPreview, isError, durationMs);
        }
    }

    // ---- Call Logging (JSON Lines, replaces SSE + Web panel) ----

    /**
     * Log an MCP tool call to pendulum/logs/mcp_calls.jsonl.
     * Format: one JSON object per line: {timestamp, tool, duration_ms, error, params?, result_preview?}
     * For post-hoc review of AI agent activity.
     */
    private static void logCall(String toolName, JsonObject arguments, String resultPreview, boolean isError, long durationMs) {
        try {
            Files.createDirectories(LOG_DIR);
            Path logFile = LOG_DIR.resolve("mcp_calls.jsonl");

            JsonObject entry = new JsonObject();
            entry.addProperty("timestamp", TS_FMT.format(Instant.now()));
            entry.addProperty("tool", toolName);
            entry.addProperty("duration_ms", durationMs);
            entry.addProperty("error", isError);
            if (arguments != null && arguments.size() > 0) {
                String paramsStr = GSON.toJson(arguments);
                if (paramsStr.length() > 500) paramsStr = paramsStr.substring(0, 500) + "...(truncated)";
                entry.addProperty("params", paramsStr);
            }
            if (resultPreview != null) {
                String preview = resultPreview.length() > 500 ? resultPreview.substring(0, 500) + "...(truncated)" : resultPreview;
                entry.addProperty("result_preview", preview);
            }

            String line = GSON.toJson(entry) + "\n";
            synchronized (logLock) {
                Files.write(logFile, line.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to write MCP call log: {}", e.getMessage());
        }
    }

    // ---- Video Frame Capture (experimental, use sparingly) ----

    /**
     * Called from the Fabric/Forge client tick event when video capture is active.
     * Captures a screenshot every VIDEO_FRAME_SKIP frames (~10fps @ 60fps).
     */
    public static void onClientTick() {
        if (!videoCaptureActive) return;
        videoFrameCounter++;
        if (videoFrameCounter % VIDEO_FRAME_SKIP != 0) return;

        try {
            byte[] frame = takeScreenshotB64Raw();
            if (frame != null) {
                videoFrameCache = frame;
                videoFrameTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            LOGGER.warn("Video frame capture failed: {}", e.getMessage());
        }
    }

    public static boolean isVideoCaptureActive() {
        return videoCaptureActive;
    }

    private static String getVideoFrame() {
        byte[] cached = videoFrameCache;
        if (cached == null || System.currentTimeMillis() - videoFrameTime > 5000) {
            return "{\"error\":\"no recent frame available, is video capture active?\"}";
        }
        // Return raw base64 — the caller (imageContent) already wraps it in a data URI
        return Base64.getEncoder().encodeToString(cached);
    }

    // ---- External endpoint helpers ----

    /** Screenshot without grid overlay (for video frames — grid is added later if needed) */
    private static byte[] takeScreenshotB64Raw() {
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
            return Files.readAllBytes(tmp.toPath());
        } catch (Exception e) {
            LOGGER.warn("Screenshot raw failed: {}", e.getMessage());
            return null;
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static String takeScreenshotB64() {
        File tmp = null;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() == null) return null;
            int w = mc.getWindow().getWidth();
            int h = mc.getWindow().getHeight();
            if (w <= 0 || h <= 0) return null;

            // Capture on the game thread so GPU framebuffer read works.
            // Falls back to AWT Robot only if the game thread itself can't read
            // the framebuffer (e.g. window minimized).
            byte[] pngBytes = ScriptEngine.submitToGameThread(() -> {
                File localTmp = null;
                try {
                    // 1) GPU framebuffer (preferred — only captures Minecraft window)
                    try {
                        NativeImage img = new NativeImage(w, h, false);
                        mc.getMainRenderTarget().bindRead();
                        img.downloadTexture(0, false);
                        img.flipY();
                        localTmp = File.createTempFile("pendulum_ss", ".png");
                        img.writeToFile(localTmp);
                        img.close();
                        return Files.readAllBytes(localTmp.toPath());
                    } catch (Exception e) {
                        LOGGER.warn("GPU screenshot failed, trying AWT Robot fallback: {}", e.getMessage());
                    }

                    // 2) AWT Robot fallback (window minimized / headless)
                    try {
                        java.awt.Robot robot = new java.awt.Robot();
                        java.awt.Rectangle bounds = new java.awt.Rectangle(
                                mc.getWindow().getX(), mc.getWindow().getY(), w, h);
                        java.awt.image.BufferedImage awtImg = robot.createScreenCapture(bounds);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(awtImg, "png", baos);
                        byte[] bytes = baos.toByteArray();
                        LOGGER.info("AWT Robot screenshot captured {} bytes", bytes.length);
                        return bytes;
                    } catch (Exception e2) {
                        LOGGER.error("AWT Robot fallback also failed: {}", e2.getMessage());
                        return null;
                    }
                } finally {
                    if (localTmp != null) localTmp.delete();
                }
            });

            if (pngBytes == null || pngBytes.length < 100) return null;

            // Add coordinate grid overlay (CPU-only, safe on any thread)
            pngBytes = addCoordinateGrid(pngBytes, w, h);
            return Base64.getEncoder().encodeToString(pngBytes);
        } catch (Exception e) {
            LOGGER.error("Screenshot failed", e);
            return null;
        }
    }

    /**
     * Overlay a coordinate grid on the screenshot PNG.
     * Draws grid lines every 100px with small labels, plus edge rulers.
     */
    private static byte[] addCoordinateGrid(byte[] pngBytes, int width, int height) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(pngBytes));
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int gridSpacing = 100;
            Color gridColor = new Color(255, 255, 255, 40);      // semi-transparent white
            Color labelColor = new Color(255, 255, 100, 180);     // bright yellow
            Color rulerBg = new Color(0, 0, 0, 100);              // dark translucent
            Font font = new Font("Monospaced", Font.PLAIN, 10);
            g.setFont(font);

            // Grid lines
            g.setColor(gridColor);
            for (int x = gridSpacing; x < width; x += gridSpacing) {
                g.drawLine(x, 0, x, height);
            }
            for (int y = gridSpacing; y < height; y += gridSpacing) {
                g.drawLine(0, y, width, y);
            }

            // Axis labels along top and left edges
            for (int x = gridSpacing; x < width; x += gridSpacing) {
                String label = String.valueOf(x);
                int labelW = g.getFontMetrics().stringWidth(label);
                g.setColor(rulerBg);
                g.fillRect(x - labelW / 2 - 2, 0, labelW + 4, 12);
                g.setColor(labelColor);
                g.drawString(label, x - labelW / 2, 10);
            }
            for (int y = gridSpacing; y < height; y += gridSpacing) {
                String label = String.valueOf(y);
                g.setColor(rulerBg);
                g.fillRect(0, y - 6, g.getFontMetrics().stringWidth(label) + 6, 12);
                g.setColor(labelColor);
                g.drawString(label, 3, y + 3);
            }

            // Corner label
            g.setColor(rulerBg);
            g.fillRect(0, 0, 35, 12);
            g.setColor(labelColor);
            g.drawString(width + "x" + height, 3, 10);

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            LOGGER.warn("Failed to add coordinate grid to screenshot: {}", e.getMessage());
            return pngBytes; // Return unadorned screenshot as fallback
        }
    }

    /**
     * Recursively enumerate all GUI widgets as JSON.
     */
    private static String enumerateAllWidgetsJson() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) return "[]";
            JsonArray arr = new JsonArray();
            for (var child : mc.screen.children()) {
                arr.add(buildWidgetJson(child, true));
            }
            return GSON.toJson(arr);
        } catch (Exception e) {
            LOGGER.error("Failed to enumerate widgets", e);
            return null;
        }
    }

    private static JsonObject buildWidgetJson(Object widget, boolean recurse) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", widget.getClass().getSimpleName());

        if (widget instanceof AbstractWidget w) {
            obj.addProperty("x", w.getX());
            obj.addProperty("y", w.getY());
            obj.addProperty("width", w.getWidth());
            obj.addProperty("height", w.getHeight());
            net.minecraft.network.chat.Component msg = w.getMessage();
            if (msg != null) obj.addProperty("text", msg.getString());
            obj.addProperty("active", w.active);
            obj.addProperty("focused", w.isFocused());
        }

        if (recurse) {
            java.util.List<?> list = com.iafenvoy.pendulum.api.GuiAPI.tryGetChildren(widget);
            if (list != null) {
                JsonArray childArr = new JsonArray();
                for (Object child : list) {
                    childArr.add(buildWidgetJson(child, true));
                }
                if (childArr.size() > 0) obj.add("children", childArr);
            }
        }

        return obj;
    }

    /**
     * Find a widget by text substring and click its center. Returns JSON result.
     */
    private static String clickWidgetByText(String target) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) return "{\"error\":\"no screen\"}";
            Object found = findWidgetByText(mc.screen.children(), target.toLowerCase());
            if (found == null) return "{\"error\":\"widget not found\"}";

            if (found instanceof AbstractWidget w) {
                int cx = w.getX() + w.getWidth() / 2;
                int cy = w.getY() + w.getHeight() / 2;
                new Thread(() -> ScreenInputHelper.clickAt(cx, cy, 0)).start();
                return "{\"clicked\":true,\"widget\":\"" + w.getClass().getSimpleName() + "\",\"x\":" + cx + ",\"y\":" + cy + "}";
            }
            return "{\"error\":\"widget has no position\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static Object findWidgetByText(java.util.List<?> children, String target) {
        for (Object child : children) {
            if (child instanceof AbstractWidget w) {
                net.minecraft.network.chat.Component msg = w.getMessage();
                if (msg != null && msg.getString().toLowerCase().contains(target)) return child;
            }
            if (child.getClass().getSimpleName().toLowerCase().contains(target)) return child;
            java.util.List<?> sub = com.iafenvoy.pendulum.api.GuiAPI.tryGetChildren(child);
            if (sub != null) {
                Object found = findWidgetByText(sub, target);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Call an arbitrary method on the current screen via reflection (HIGH RISK).
     * All exceptions are caught and returned as error JSON.
     */
    private static String callScreenMethod(String methodName) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) return "{\"error\":\"no screen open\"}";
            Object screen = mc.screen;
            Class<?> clazz = screen.getClass();

            // Find the method (no-arg first, then try other arities)
            java.lang.reflect.Method target = null;
            for (java.lang.reflect.Method m : clazz.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                // Try declared methods too
                for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                        target = m;
                        break;
                    }
                }
            }
            if (target == null) {
                return "{\"error\":\"method not found: " + methodName + " on " + clazz.getSimpleName() + "\"}";
            }

            target.setAccessible(true);
            Object result = target.invoke(screen);
            return "{\"called\":true,\"method\":\"" + methodName + "\",\"screen\":\"" + clazz.getSimpleName() + "\",\"result\":\"" + (result != null ? result.toString().replace("\\", "\\\\").replace("\"", "\\\"") : "null") + "\"}";
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            return "{\"error\":\"invocation failed: " + (cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : e.getMessage()).replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getClass().getSimpleName() + ": " + e.getMessage().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        }
    }

    /**
     * Select an item in a dropdown/list widget by text substring match.
     * Searches the widget tree for list widgets and finds matching items.
     */
    private static String selectListItem(String text) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) return "{\"error\":\"no screen open\"}";
            String lower = text.toLowerCase();

            // Walk all children looking for list widgets
            return selectListItemRecursive(mc.screen.children(), lower);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        }
    }

    private static String selectListItemRecursive(java.util.List<?> children, String target) {
        for (Object child : children) {
            Class<?> clazz = child.getClass();
            String cn = clazz.getSimpleName().toLowerCase();

            // Check if this is a list-like widget
            if (cn.contains("list") || cn.contains("selectionlist") || cn.contains("entrylist") || cn.contains("choices")) {
                java.util.List<?> entries = com.iafenvoy.pendulum.api.GuiAPI.tryGetChildren(child);
                if (entries != null) {
                    for (Object entry : entries) {
                        if (entry instanceof AbstractWidget ew) {
                            net.minecraft.network.chat.Component msg = ew.getMessage();
                            String entryText = msg != null ? msg.getString() : "";
                            if (entryText.toLowerCase().contains(target)) {
                                int cx = ew.getX() + ew.getWidth() / 2;
                                int cy = ew.getY() + ew.getHeight() / 2;
                                new Thread(() -> ScreenInputHelper.clickAt(cx, cy, 0)).start();
                                return "{\"selected\":true,\"text\":\"" + entryText.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"x\":" + cx + ",\"y\":" + cy + "}";
                            }
                        }
                    }
                }
            }

            // Recurse into children
            java.util.List<?> sub = com.iafenvoy.pendulum.api.GuiAPI.tryGetChildren(child);
            if (sub != null) {
                String result = selectListItemRecursive(sub, target);
                if (result != null && result.contains("\"selected\":true")) return result;
            }
        }
        return "{\"error\":\"list item not found: " + target + "\"}";
    }

    private static String getGuiElementsJson() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) return "[]";
            JsonArray arr = new JsonArray();
            for (var child : mc.screen.children()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("type", child.getClass().getSimpleName());
                if (child instanceof AbstractWidget w) {
                    obj.addProperty("x", w.getX());
                    obj.addProperty("y", w.getY());
                    obj.addProperty("width", w.getWidth());
                    obj.addProperty("height", w.getHeight());
                    net.minecraft.network.chat.Component msg = w.getMessage();
                    if (msg != null) obj.addProperty("text", msg.getString());
                }
                arr.add(obj);
            }
            return GSON.toJson(arr);
        } catch (Exception e) {
            LOGGER.error("Failed to read GUI elements", e);
            return null;
        }
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

    private static JsonProperty jsonProperty(String name, String type, String description, boolean required) {
        return new JsonProperty(name, type, description, required);
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
