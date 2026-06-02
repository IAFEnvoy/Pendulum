package com.iafenvoy.pendulum.util;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Screen-level mouse/keyboard injection via GLFW (primary) with AWT Robot fallback.
 * Used by MCP GUI interaction tools and JS gui.click / gui.pressKey / gui.typeText.
 */
public final class ScreenInputHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Robot awtRobot;

    static {
        try {
            awtRobot = new Robot();
            awtRobot.setAutoDelay(5);
        } catch (AWTException e) {
            LOGGER.error("[Pendulum] AWT Robot unavailable", e);
        }
    }

    private ScreenInputHelper() {
    }

    // ==================== Mouse ====================

    /**
     * Inject a mouse button press/release at the OS level.
     *
     * @param button 0=left, 1=right, 2=middle (GLFW button codes)
     * @param action 1=press, 0=release
     */
    public static void injectMouseButton(int button, int action) {
        Minecraft mc = Minecraft.getInstance();
        long handle = mc.getWindow().getWindow();

        // Try GLFW direct injection first
        if (handle != 0) {
            try {
                Object mouseHandler = getMouseHandler(mc);
                if (mouseHandler != null) {
                    Method target = findMouseButtonMethod(mouseHandler.getClass());
                    if (target != null) {
                        target.setAccessible(true);
                        target.invoke(mouseHandler, handle, button, action, 0);
                        return;
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[Pendulum] GLFW mouse inject failed, falling back to AWT: {}", e.getMessage());
            }
        }

        // AWT Robot fallback
        Robot r = awtRobot;
        if (r == null) return;
        int mask = button == 1 ? InputEvent.BUTTON2_DOWN_MASK
                : button == 2 ? InputEvent.BUTTON3_DOWN_MASK
                : InputEvent.BUTTON1_DOWN_MASK;
        try {
            if (action == 1) r.mousePress(mask);
            else r.mouseRelease(mask);
        } catch (Exception e) {
            LOGGER.error("[Pendulum] AWT mouse inject failed", e);
        }
    }

    /**
     * Click at screen coordinates (moves cursor then injects press+release).
     *
     * @param x      screen pixel X
     * @param y      screen pixel Y
     * @param button 0=left, 1=right, 2=middle
     */
    public static void clickAt(int x, int y, int button) {
        Minecraft mc = Minecraft.getInstance();
        long handle = mc.getWindow().getWindow();

        // Move cursor via GLFW
        if (handle != 0) {
            try {
                Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
                Method setPos = glfwClass.getMethod("glfwSetCursorPos", long.class, double.class, double.class);
                setPos.invoke(null, handle, (double) x, (double) y);
            } catch (Exception e) {
                // Fall back to AWT
                Robot r = awtRobot;
                if (r != null) r.mouseMove(x, y);
            }
        }

        // Small delay for the game to process cursor move
        try {
            Thread.sleep(30);
        } catch (InterruptedException ignored) {
        }

        injectMouseButton(button, 1);
        try {
            Thread.sleep(30);
        } catch (InterruptedException ignored) {
        }
        injectMouseButton(button, 0);
    }

    /**
     * Drag mouse from (x1,y1) to (x2,y2).
     */
    public static void mouseDrag(int x1, int y1, int x2, int y2, int button) {
        clickAt(x1, y1, 0); // move to start
        injectMouseButton(button, 1);
        // Move in steps for smooth drag
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) / 5;
        if (steps < 5) steps = 5;
        for (int i = 1; i <= steps; i++) {
            int cx = x1 + (x2 - x1) * i / steps;
            int cy = y1 + (y2 - y1) * i / steps;
            Minecraft mc = Minecraft.getInstance();
            long handle = mc.getWindow().getWindow();
            if (handle != 0) {
                try {
                    Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
                    Method setPos = glfwClass.getMethod("glfwSetCursorPos", long.class, double.class, double.class);
                    setPos.invoke(null, handle, (double) cx, (double) cy);
                } catch (Exception ignored) {
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
        injectMouseButton(button, 0);
    }

    /**
     * Scroll mouse wheel.
     *
     * @param clicks positive=up, negative=down
     */
    public static void scroll(int clicks) {
        Minecraft mc = Minecraft.getInstance();
        long handle = mc.getWindow().getWindow();

        if (handle != 0) {
            try {
                Object mouseHandler = getMouseHandler(mc);
                if (mouseHandler != null) {
                    for (Method m : mouseHandler.getClass().getDeclaredMethods()) {
                        if (m.getName().equals("onScroll") && m.getParameterCount() == 3) {
                            m.setAccessible(true);
                            // GLFW scroll callback: onScroll(long window, double xoffset, double yoffset)
                            m.invoke(mouseHandler, handle, 0.0, (double) clicks);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[Pendulum] GLFW scroll failed: {}", e.getMessage());
            }
        }

        // AWT Robot scroll fallback
        Robot r = awtRobot;
        if (r != null) {
            int amount = clicks > 0 ? clicks : -clicks;
            for (int i = 0; i < amount; i++) {
                r.mouseWheel(clicks > 0 ? -1 : 1);
            }
        }
    }

    // ==================== Keyboard ====================

    /**
     * Inject a keyboard key press/release.
     *
     * @param keyName e.g. "key.keyboard.w", "key.keyboard.enter", "A", "ESC"
     * @param action  1=press, 0=release
     */
    public static void injectKey(String keyName, int action) {
        int glfwKey = parseKeyName(keyName);
        if (glfwKey < 0) {
            LOGGER.warn("[Pendulum] Unknown key: {}", keyName);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        long handle = mc.getWindow().getWindow();

        // Try GLFW direct injection
        if (handle != 0) {
            try {
                Object kbHandler = getKeyboardHandler(mc);
                if (kbHandler != null) {
                    Method keyPress = findKeyPressMethod(kbHandler.getClass());
                    if (keyPress != null) {
                        keyPress.setAccessible(true);
                        keyPress.invoke(kbHandler, handle, glfwKey, 0, action, 0);
                        return;
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[Pendulum] GLFW key inject failed: {}", e.getMessage());
            }
        }

        // AWT Robot fallback
        Robot r = awtRobot;
        if (r == null) return;
        int vk = glfwToAwtKey(glfwKey);
        if (vk < 0) return;
        try {
            if (action == 1) r.keyPress(vk);
            else r.keyRelease(vk);
        } catch (Exception e) {
            LOGGER.error("[Pendulum] AWT key inject failed", e);
        }
    }

    /**
     * Press and release a key.
     */
    public static void pressKey(String keyName, float holdSeconds) {
        injectKey(keyName, 1);
        if (holdSeconds > 0) {
            try {
                Thread.sleep((long) (holdSeconds * 1000));
            } catch (InterruptedException ignored) {
            }
        }
        injectKey(keyName, 0);
    }

    /**
     * Type a string character by character.
     */
    public static void typeText(String text) {
        for (char c : text.toCharArray()) {
            typeChar(c);
        }
    }

    private static void typeChar(char c) {
        Robot r = awtRobot;
        if (r == null) return;

        // Use AWT Robot for typing (handles Shift automatically)
        if (Character.isUpperCase(c)) {
            r.keyPress(KeyEvent.VK_SHIFT);
            r.keyPress(Character.toUpperCase(c));
            r.keyRelease(Character.toUpperCase(c));
            r.keyRelease(KeyEvent.VK_SHIFT);
        } else {
            // Map special characters
            int vk = charToAwtKey(c);
            if (vk >= 0) {
                if (Character.isUpperCase(c) || needsShift(c)) {
                    r.keyPress(KeyEvent.VK_SHIFT);
                    r.keyPress(vk);
                    r.keyRelease(vk);
                    r.keyRelease(KeyEvent.VK_SHIFT);
                } else {
                    r.keyPress(vk);
                    r.keyRelease(vk);
                }
            }
        }
    }

    // ==================== Reflection helpers ====================

    private static Object getMouseHandler(Minecraft mc) {
        try {
            Field f = mc.getClass().getDeclaredField("mouseHandler");
            f.setAccessible(true);
            return f.get(mc);
        } catch (Exception ignored) {
        }
        // Try getter method
        try {
            Method m = mc.getClass().getMethod("mouseHandler");
            return m.invoke(mc);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Object getKeyboardHandler(Minecraft mc) {
        try {
            Field f = mc.getClass().getDeclaredField("keyboardHandler");
            f.setAccessible(true);
            return f.get(mc);
        } catch (Exception ignored) {
        }
        try {
            Method m = mc.getClass().getMethod("keyboardHandler");
            return m.invoke(mc);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Method findMouseButtonMethod(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals("onPress") && m.getParameterCount() == 4) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt[0] == long.class && pt[1] == int.class && pt[2] == int.class && pt[3] == int.class) {
                    return m;
                }
            }
        }
        // Alternative name
        for (Method m : clazz.getDeclaredMethods()) {
            if ((m.getName().equals("mouseButtonCallback") || m.getName().contains("Button"))
                    && m.getParameterCount() == 4) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt[0] == long.class) return m;
            }
        }
        return null;
    }

    private static Method findKeyPressMethod(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals("keyPress") && m.getParameterCount() == 5) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt[0] == long.class && pt[1] == int.class) return m;
            }
        }
        return null;
    }

    // ==================== Key name parsing ====================

    /**
     * Parse various key name formats to GLFW key code.
     * Supports: "key.keyboard.w", "GLFW_KEY_W", "W", "Enter", "ESC", "SPACE", etc.
     */
    private static int parseKeyName(String name) {
        if (name == null || name.isEmpty()) return -1;

        String upper = name.toUpperCase().trim();

        // Strip "key.keyboard." prefix
        if (upper.startsWith("KEY.KEYBOARD.")) upper = upper.substring(13);
        if (upper.startsWith("GLFW_KEY_")) upper = upper.substring(9);

        // Common aliases
        switch (upper) {
            case "ESC":
            case "ESCAPE":
                return 256;
            case "ENTER":
            case "RETURN":
                return 257;
            case "TAB":
                return 258;
            case "BACKSPACE":
                return 259;
            case "DELETE":
                return 261;
            case "RIGHT":
                return 262;
            case "LEFT":
                return 263;
            case "DOWN":
                return 264;
            case "UP":
                return 265;
            case "PAGE_UP":
                return 266;
            case "PAGE_DOWN":
                return 267;
            case "HOME":
                return 268;
            case "END":
                return 269;
            case "SPACE":
                return 32;
            case "LEFT_SHIFT":
                return 340;
            case "LEFT_CONTROL":
            case "LEFT_CTRL":
                return 341;
            case "LEFT_ALT":
                return 342;
            case "RIGHT_SHIFT":
                return 344;
            case "RIGHT_CONTROL":
            case "RIGHT_CTRL":
                return 345;
            case "RIGHT_ALT":
                return 346;
            case "F1":
                return 290;
            case "F2":
                return 291;
            case "F3":
                return 292;
            case "F4":
                return 293;
            case "F5":
                return 294;
            case "F6":
                return 295;
            case "F7":
                return 296;
            case "F8":
                return 297;
            case "F9":
                return 298;
            case "F10":
                return 299;
            case "F11":
                return 300;
            case "F12":
                return 301;
        }

        // Single letter
        if (upper.length() == 1) {
            char c = upper.charAt(0);
            if (c >= 'A' && c <= 'Z') return c - 'A' + 65;
            if (c >= '0' && c <= '9') return c - '0' + 48;
        }

        // Try GLFW constant lookup via reflection
        try {
            Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            Field f = glfwClass.getField("GLFW_KEY_" + upper);
            return f.getInt(null);
        } catch (Exception ignored) {
        }

        return -1;
    }

    // ==================== GLFW to AWT key mapping ====================

    private static int glfwToAwtKey(int glfwKey) {
        if (glfwKey >= 65 && glfwKey <= 90) return glfwKey; // A-Z
        if (glfwKey >= 48 && glfwKey <= 57) return glfwKey; // 0-9
        switch (glfwKey) {
            case 256:
                return KeyEvent.VK_ESCAPE;
            case 257:
                return KeyEvent.VK_ENTER;
            case 258:
                return KeyEvent.VK_TAB;
            case 259:
                return KeyEvent.VK_BACK_SPACE;
            case 261:
                return KeyEvent.VK_DELETE;
            case 262:
                return KeyEvent.VK_RIGHT;
            case 263:
                return KeyEvent.VK_LEFT;
            case 264:
                return KeyEvent.VK_DOWN;
            case 265:
                return KeyEvent.VK_UP;
            case 32:
                return KeyEvent.VK_SPACE;
            case 340:
                return KeyEvent.VK_SHIFT;
            case 341:
                return KeyEvent.VK_CONTROL;
            case 342:
                return KeyEvent.VK_ALT;
            default:
                return -1;
        }
    }

    private static int charToAwtKey(char c) {
        switch (c) {
            case ' ':
                return KeyEvent.VK_SPACE;
            case '-':
                return KeyEvent.VK_MINUS;
            case '=':
                return KeyEvent.VK_EQUALS;
            case '[':
                return KeyEvent.VK_OPEN_BRACKET;
            case ']':
                return KeyEvent.VK_CLOSE_BRACKET;
            case '\\':
                return KeyEvent.VK_BACK_SLASH;
            case ';':
                return KeyEvent.VK_SEMICOLON;
            case '\'':
                return KeyEvent.VK_QUOTE;
            case ',':
                return KeyEvent.VK_COMMA;
            case '.':
                return KeyEvent.VK_PERIOD;
            case '/':
                return KeyEvent.VK_SLASH;
            default:
                if (c >= '0' && c <= '9') return c;
                if (c >= 'a' && c <= 'z') return Character.toUpperCase(c);
                if (c >= 'A' && c <= 'Z') return c;
                return -1;
        }
    }

    private static boolean needsShift(char c) {
        return "!@#$%^&*()_+{}|:\"<>?~".indexOf(c) >= 0;
    }
}
