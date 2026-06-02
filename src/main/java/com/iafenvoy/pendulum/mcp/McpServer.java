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
                        "inventory (getAllItems/getItemInHand/hasItem/selectHotbar), " +
                        "GUI via mc.gui.* (isOpen/getTitle/close/getElements/click/clickButton/pressKey/typeText/clickSlot/craft/getAllItems/moveItem), " +
                        "player state (getPlayerHealth/getPlayerHunger/canReach/canSeeBlock/getAttackCooldown), " +
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

        // ---- GUI / Screen Interaction tools ----

        tools.add(makeTool("pendulum_click",
                "Click at the specified screen coordinates. Use after pendulum_screenshot to target UI elements.\n" +
                        "The screenshot includes a coordinate grid to help identify pixel positions.",
                jsonSchema("object",
                        jsonProperty("x", "integer", "X coordinate in screen pixels."),
                        jsonProperty("y", "integer", "Y coordinate in screen pixels."),
                        jsonProperty("button", "string", "Mouse button: \"left\" (default), \"right\", or \"middle\".", false))));

        tools.add(makeTool("pendulum_press_key",
                "Press a keyboard key. Supports named keys like \"W\", \"Enter\", \"ESC\", \"SPACE\", \"F3\", etc.",
                jsonSchema("object",
                        jsonProperty("key", "string", "Key name. E.g. \"key.keyboard.w\", \"Enter\", \"ESC\", \"SPACE\", \"F3\", \"A\"."),
                        jsonProperty("hold_seconds", "number", "Duration to hold the key in seconds. Default 0 (press and release).", false))));

        tools.add(makeTool("pendulum_type_text",
                "Type text into the currently focused text field, character by character.",
                jsonSchema("object",
                        jsonProperty("text", "string", "Text to type."),
                        jsonProperty("press_enter", "boolean", "Whether to press Enter after typing. Default false.", false))));

        tools.add(makeTool("pendulum_paste_text",
                "Type text quickly (same as type_text but intended for larger blocks of text).",
                jsonSchema("object",
                        jsonProperty("text", "string", "Text to paste."),
                        jsonProperty("press_enter", "boolean", "Whether to press Enter after. Default false.", false))));

        tools.add(makeTool("pendulum_scroll",
                "Scroll the mouse wheel.",
                jsonSchema("object",
                        jsonProperty("clicks", "integer", "Number of scroll clicks. Positive = up, negative = down."))));

        tools.add(makeTool("pendulum_hotkey",
                "Press a key combination. E.g. \"ctrl,s\" or \"shift,f3\".",
                jsonSchema("object",
                        jsonProperty("keys", "string", "Comma-separated key names. E.g. \"ctrl,s\", \"shift,f3\"."))));

        tools.add(makeTool("pendulum_mouse_drag",
                "Drag mouse from one point to another. Useful for moving items, selecting areas, etc.",
                jsonSchema("object",
                        jsonProperty("x_start", "integer", "Start X."),
                        jsonProperty("y_start", "integer", "Start Y."),
                        jsonProperty("x_end", "integer", "End X."),
                        jsonProperty("y_end", "integer", "End Y."),
                        jsonProperty("button", "string", "Button: \"left\" (default), \"right\", \"middle\".", false))));

        tools.add(makeTool("pendulum_screenshot_to_file",
                "Capture a screenshot and save it to a file on disk. Returns the file path and size.",
                jsonSchema("object",
                        jsonProperty("path", "string", "File path to save to. Defaults to pendulum/screenshots/ directory.", false))));

        tools.add(makeTool("pendulum_enumerate_widgets",
                "Recursively enumerate ALL GUI widgets on the current screen, including nested children.\n" +
                        "Returns [{type, text?, x, y, width, height, active?, focused?, children?}, ...].\n" +
                        "Use this to understand the full screen layout before clicking.",
                jsonSchema("object", jsonProperty("_", "string", "No parameters"))));

        tools.add(makeTool("pendulum_click_button",
                "Find a button/widget by text (substring match) or type name and click its center.\n" +
                        "Searches recursively through all widgets. Returns the widget info that was clicked.",
                jsonSchema("object",
                        jsonProperty("target", "string", "Text to match (substring, case-insensitive) or widget type name."))));

        tools.add(makeTool("pendulum_wait",
                "Wait for a specified duration. Useful for sequencing actions between clicks and screenshots.",
                jsonSchema("object",
                        jsonProperty("seconds", "number", "Seconds to wait. Default 1.0.", false))));

        tools.add(makeTool("pendulum_call_screen_method",
                "Call an arbitrary method on the current GUI screen via reflection.\n" +
                        "DANGEROUS — use only when standard tools are insufficient. The method is called with reflection;\n" +
                        "all exceptions are caught and returned as errors. Returns the method result or an error JSON.",
                jsonSchema("object",
                        jsonProperty("method", "string", "Method name to call on the current screen object."))));

        tools.add(makeTool("pendulum_select_list_item",
                "Select an item from a dropdown list widget on the current screen.\n" +
                        "Searches the widget tree for a list widget (e.g. ObjectSelectionList) and selects the item\n" +
                        "whose text contains the given substring (case-insensitive).",
                jsonSchema("object",
                        jsonProperty("text", "string", "Text substring to match for the list item."))));

        // ---- Video Frame Capture (experimental — prefer pendulum_screenshot) ----
        tools.add(makeTool("pendulum_video_start",
                "EXPERIMENTAL — Start continuous video frame capture. Captures ~10fps and caches the latest frame.\n" +
                        "WARNING: This reads the GPU framebuffer every 6 frames, which is expensive. Use sparingly.\n" +
                        "Prefer pendulum_screenshot for single screenshots. Use video mode only when you need\n" +
                        "to observe continuous motion (entity movement, falling items, animations).",
                jsonSchema("object", jsonProperty("_", "string", "No parameters"))));

        tools.add(makeTool("pendulum_video_stop",
                "Stop video frame capture and release resources.",
                jsonSchema("object", jsonProperty("_", "string", "No parameters"))));

        tools.add(makeTool("pendulum_video_frame",
                "Get the latest cached video frame as base64 PNG. Returns error if no recent frame (< 5s old).\n" +
                        "Call periodically while pendulum_video_start is active to observe motion.",
                jsonSchema("object", jsonProperty("_", "string", "No parameters"))));

        JsonObject result = new JsonObject();
        result.add("tools", tools);
        return jsonRpcResult(id, result);
    }

    private String handleToolsCall(Object id, JsonObject req) {
        JsonObject params = req.has("params") ? req.getAsJsonObject("params") : new JsonObject();
        String toolName = params.has("name") ? params.get("name").getAsString() : "";
        JsonObject arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

        long startMs = System.currentTimeMillis();
        boolean isError = false;
        String resultPreview = null;
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
                case "pendulum_click": {
                    int x = arguments.has("x") ? arguments.get("x").getAsInt() : 0;
                    int y = arguments.has("y") ? arguments.get("y").getAsInt() : 0;
                    String btn = arguments.has("button") ? arguments.get("button").getAsString() : "left";
                    int button = btn.equals("right") ? 1 : btn.equals("middle") ? 2 : 0;
                    ScreenInputHelper.clickAt(x, y, button);
                    content.add(textContent("Clicked at (" + x + ", " + y + ") with " + btn + " button."));
                    break;
                }
                case "pendulum_press_key": {
                    String key = arguments.has("key") ? arguments.get("key").getAsString() : "";
                    float hold = arguments.has("hold_seconds") ? arguments.get("hold_seconds").getAsFloat() : 0f;
                    if (key.isEmpty()) {
                        content.add(textContent("Error: 'key' parameter is required."));
                    } else {
                        ScreenInputHelper.pressKey(key, hold);
                        content.add(textContent("Pressed key: " + key + (hold > 0 ? " for " + hold + "s" : "")));
                    }
                    break;
                }
                case "pendulum_type_text": {
                    String text = arguments.has("text") ? arguments.get("text").getAsString() : "";
                    boolean pressEnter = arguments.has("press_enter") && arguments.get("press_enter").getAsBoolean();
                    if (text.isEmpty()) {
                        content.add(textContent("Error: 'text' parameter is required."));
                    } else {
                        ScreenInputHelper.typeText(text);
                        if (pressEnter) {
                            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                            ScreenInputHelper.pressKey("ENTER", 0f);
                        }
                        content.add(textContent("Typed " + text.length() + " characters." + (pressEnter ? " + Enter" : "")));
                    }
                    break;
                }
                case "pendulum_paste_text": {
                    String text = arguments.has("text") ? arguments.get("text").getAsString() : "";
                    boolean pressEnter = arguments.has("press_enter") && arguments.get("press_enter").getAsBoolean();
                    if (text.isEmpty()) {
                        content.add(textContent("Error: 'text' parameter is required."));
                    } else {
                        ScreenInputHelper.typeText(text);
                        if (pressEnter) {
                            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                            ScreenInputHelper.pressKey("ENTER", 0f);
                        }
                        content.add(textContent("Pasted " + text.length() + " characters." + (pressEnter ? " + Enter" : "")));
                    }
                    break;
                }
                case "pendulum_scroll": {
                    int clicks = arguments.has("clicks") ? arguments.get("clicks").getAsInt() : 0;
                    ScreenInputHelper.scroll(clicks);
                    content.add(textContent("Scrolled " + clicks + " clicks."));
                    break;
                }
                case "pendulum_hotkey": {
                    String keys = arguments.has("keys") ? arguments.get("keys").getAsString() : "";
                    if (keys.isEmpty()) {
                        content.add(textContent("Error: 'keys' parameter is required."));
                    } else {
                        String[] parts = keys.split(",");
                        for (String k : parts) ScreenInputHelper.injectKey(k.trim(), 1);
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                        for (int i = parts.length - 1; i >= 0; i--)
                            ScreenInputHelper.injectKey(parts[i].trim(), 0);
                        content.add(textContent("Pressed hotkey: " + keys));
                    }
                    break;
                }
                case "pendulum_mouse_drag": {
                    int x1 = arguments.has("x_start") ? arguments.get("x_start").getAsInt() : 0;
                    int y1 = arguments.has("y_start") ? arguments.get("y_start").getAsInt() : 0;
                    int x2 = arguments.has("x_end") ? arguments.get("x_end").getAsInt() : 0;
                    int y2 = arguments.has("y_end") ? arguments.get("y_end").getAsInt() : 0;
                    String btn = arguments.has("button") ? arguments.get("button").getAsString() : "left";
                    int button = btn.equals("right") ? 1 : btn.equals("middle") ? 2 : 0;
                    ScreenInputHelper.mouseDrag(x1, y1, x2, y2, button);
                    content.add(textContent("Dragged from (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")"));
                    break;
                }
                case "pendulum_screenshot_to_file": {
                    String b64 = takeScreenshotB64();
                    if (b64 == null) {
                        content.add(textContent("Failed to capture screenshot."));
                    } else {
                        String path = arguments.has("path") ? arguments.get("path").getAsString() : null;
                        if (path == null || path.isEmpty()) {
                            Files.createDirectories(Paths.get("pendulum", "screenshots"));
                            path = "pendulum/screenshots/pendulum_" + System.currentTimeMillis() + ".png";
                        }
                        Path fp = Paths.get(path);
                        Files.createDirectories(fp.getParent());
                        byte[] pngBytes = Base64.getDecoder().decode(b64);
                        Files.write(fp, pngBytes);
                        content.add(textContent("{\"file\":\"" + fp.toAbsolutePath().toString().replace("\\", "\\\\") + "\",\"size\":" + pngBytes.length + "}"));
                    }
                    break;
                }
                case "pendulum_enumerate_widgets": {
                    String json = enumerateAllWidgetsJson();
                    content.add(textContent(json != null ? json : "No GUI open or error reading widgets."));
                    break;
                }
                case "pendulum_click_button": {
                    String target = arguments.has("target") ? arguments.get("target").getAsString() : "";
                    if (target.isEmpty()) {
                        content.add(textContent("Error: 'target' parameter is required."));
                    } else {
                        String result = clickWidgetByText(target);
                        content.add(textContent(result != null ? result : "{\"error\":\"widget not found: " + target + "\"}"));
                    }
                    break;
                }
                case "pendulum_wait": {
                    double seconds = arguments.has("seconds") ? arguments.get("seconds").getAsDouble() : 1.0;
                    try { Thread.sleep((long)(seconds * 1000)); } catch (InterruptedException ignored) {}
                    content.add(textContent("Waited " + seconds + " seconds."));
                    break;
                }
                case "pendulum_call_screen_method": {
                    String methodName = arguments.has("method") ? arguments.get("method").getAsString() : "";
                    if (methodName.isEmpty()) {
                        content.add(textContent("Error: 'method' parameter is required."));
                    } else {
                        String res = callScreenMethod(methodName);
                        content.add(textContent(res != null ? res : "{\"error\":\"no screen\"}"));
                    }
                    break;
                }
                case "pendulum_select_list_item": {
                    String itemText = arguments.has("text") ? arguments.get("text").getAsString() : "";
                    if (itemText.isEmpty()) {
                        content.add(textContent("Error: 'text' parameter is required."));
                    } else {
                        String res = selectListItem(itemText);
                        content.add(textContent(res != null ? res : "{\"error\":\"no screen or list not found\"}"));
                    }
                    break;
                }
                case "pendulum_video_start": {
                    videoCaptureActive = true;
                    videoFrameCounter = 0;
                    videoFrameCache = null;
                    content.add(textContent("Video capture started (~10fps). Call pendulum_video_frame to get frames."));
                    break;
                }
                case "pendulum_video_stop": {
                    videoCaptureActive = false;
                    videoFrameCache = null;
                    content.add(textContent("Video capture stopped."));
                    break;
                }
                case "pendulum_video_frame": {
                    if (!videoCaptureActive) {
                        content.add(textContent("{\"error\":\"video capture not active, call pendulum_video_start first\"}"));
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
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(cached);
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

            // Add coordinate grid overlay
            pngBytes = addCoordinateGrid(pngBytes, w, h);

            return Base64.getEncoder().encodeToString(pngBytes);
        } catch (Exception e) {
            LOGGER.error("Screenshot failed", e);
            return null;
        } finally {
            if (tmp != null) tmp.delete();
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
        Class<?> clazz = widget.getClass();
        obj.addProperty("type", clazz.getSimpleName());

        try {
            java.lang.reflect.Field xF = findAccessibleField(clazz, "x", "getX", "field_22786");
            java.lang.reflect.Field yF = findAccessibleField(clazz, "y", "getY", "field_22787");
            java.lang.reflect.Field wF = findAccessibleField(clazz, "width", "getWidth", "field_22788");
            java.lang.reflect.Field hF = findAccessibleField(clazz, "height", "getHeight", "field_22789");
            if (xF != null) obj.addProperty("x", ((Number) xF.get(widget)).intValue());
            if (yF != null) obj.addProperty("y", ((Number) yF.get(widget)).intValue());
            if (wF != null) obj.addProperty("width", ((Number) wF.get(widget)).intValue());
            if (hF != null) obj.addProperty("height", ((Number) hF.get(widget)).intValue());
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Field msgF = findAccessibleField(clazz, "message", "getMessage", "field_22791");
            if (msgF != null) {
                Object msg = msgF.get(widget);
                obj.addProperty("text", msg instanceof net.minecraft.network.chat.Component c ? c.getString() : msg.toString());
            }
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Field activeF = findAccessibleField(clazz, "active", "isActive", "field_22792");
            if (activeF != null) obj.addProperty("active", activeF.getBoolean(widget));
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Field focusedF = findAccessibleField(clazz, "isFocused", "focused", "field_22801");
            if (focusedF != null) obj.addProperty("focused", focusedF.getBoolean(widget));
        } catch (Exception ignored) {
        }

        if (recurse) {
            try {
                java.lang.reflect.Field childrenF = findAccessibleField(clazz, "children", "renderables", "widgets");
                if (childrenF != null) {
                    Object children = childrenF.get(widget);
                    if (children instanceof java.util.List<?> list) {
                        JsonArray childArr = new JsonArray();
                        for (Object child : list) {
                            childArr.add(buildWidgetJson(child, true));
                        }
                        if (childArr.size() > 0) obj.add("children", childArr);
                    }
                }
            } catch (Exception ignored) {
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

            java.lang.reflect.Field xF = findAccessibleField(found.getClass(), "x", "getX");
            java.lang.reflect.Field yF = findAccessibleField(found.getClass(), "y", "getY");
            java.lang.reflect.Field wF = findAccessibleField(found.getClass(), "width", "getWidth");
            java.lang.reflect.Field hF = findAccessibleField(found.getClass(), "height", "getHeight");
            int wx = xF != null ? ((Number) xF.get(found)).intValue() : 0;
            int wy = yF != null ? ((Number) yF.get(found)).intValue() : 0;
            int ww = wF != null ? ((Number) wF.get(found)).intValue() : 0;
            int wh = hF != null ? ((Number) hF.get(found)).intValue() : 0;
            int cx = wx + ww / 2;
            int cy = wy + wh / 2;
            new Thread(() -> ScreenInputHelper.clickAt(cx, cy, 0)).start();
            return "{\"clicked\":true,\"widget\":\"" + found.getClass().getSimpleName() + "\",\"x\":" + cx + ",\"y\":" + cy + "}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static Object findWidgetByText(java.util.List<?> children, String target) {
        for (Object child : children) {
            Class<?> clazz = child.getClass();
            try {
                java.lang.reflect.Field msgF = findAccessibleField(clazz, "message", "getMessage", "field_22791");
                if (msgF != null) {
                    Object msg = msgF.get(child);
                    String text = msg instanceof net.minecraft.network.chat.Component c ? c.getString().toLowerCase() : msg.toString().toLowerCase();
                    if (text.contains(target)) return child;
                }
            } catch (Exception ignored) {
            }
            if (clazz.getSimpleName().toLowerCase().contains(target)) return child;
            try {
                java.lang.reflect.Field childrenF = findAccessibleField(clazz, "children", "renderables");
                if (childrenF != null) {
                    Object subChildren = childrenF.get(child);
                    if (subChildren instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> list = (java.util.List<Object>) subChildren;
                        Object found = findWidgetByText(list, target);
                        if (found != null) return found;
                    }
                }
            } catch (Exception ignored) {
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
                // Try to find children list or entries
                try {
                    java.lang.reflect.Field childrenF = findAccessibleField(clazz, "children", "entries", "items", "listEntries");
                    if (childrenF != null) {
                        Object entries = childrenF.get(child);
                        if (entries instanceof java.util.List<?> entryList) {
                            for (Object entry : entryList) {
                                // Check if entry text matches
                                try {
                                    java.lang.reflect.Field msgF = findAccessibleField(entry.getClass(), "message", "getMessage", "name", "getName");
                                    if (msgF != null) {
                                        Object msg = msgF.get(entry);
                                        String entryText = msg instanceof net.minecraft.network.chat.Component c ? c.getString() : msg.toString();
                                        if (entryText.toLowerCase().contains(target)) {
                                            // Click on this entry via its position
                                            java.lang.reflect.Field xF = findAccessibleField(entry.getClass(), "x", "getX");
                                            java.lang.reflect.Field yF = findAccessibleField(entry.getClass(), "y", "getY");
                                            java.lang.reflect.Field wF = findAccessibleField(entry.getClass(), "width", "getWidth");
                                            java.lang.reflect.Field hF = findAccessibleField(entry.getClass(), "height", "getHeight");
                                            int ex = xF != null ? ((Number) xF.get(entry)).intValue() : 0;
                                            int ey = yF != null ? ((Number) yF.get(entry)).intValue() : 0;
                                            int ew = wF != null ? ((Number) wF.get(entry)).intValue() : 0;
                                            int eh = hF != null ? ((Number) hF.get(entry)).intValue() : 0;
                                            new Thread(() -> ScreenInputHelper.clickAt(ex + ew / 2, ey + eh / 2, 0)).start();
                                            return "{\"selected\":true,\"text\":\"" + entryText.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"x\":" + (ex + ew / 2) + ",\"y\":" + (ey + eh / 2) + "}";
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Recurse into children
            try {
                java.lang.reflect.Field childrenF = findAccessibleField(clazz, "children", "renderables", "widgets");
                if (childrenF != null) {
                    Object subChildren = childrenF.get(child);
                    if (subChildren instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> list = (java.util.List<Object>) subChildren;
                        String result = selectListItemRecursive(list, target);
                        if (result != null && result.contains("\"selected\":true")) return result;
                    }
                }
            } catch (Exception ignored) {}
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
