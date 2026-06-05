package com.iafenvoy.pendulum.util;

import com.mojang.logging.LogUtils;
import com.sun.jna.platform.win32.User32;
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
     * Inject a keyboard key via Minecraft's keyboard handler + Win32 PostMessage fallback.
     * NO AWT Robot — prevents keystroke leakage to other applications.
     * Forces Minecraft window focus before injection.
     */
    public static void injectKey(String keyName, int action) {
        int glfwKey = parseKeyName(keyName);
        if (glfwKey < 0) {
            LOGGER.warn("[Pendulum] Unknown key: {}", keyName);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        long handle = mc.getWindow().getWindow();

        // Force window focus first
        if (handle != 0) {
            try {
                Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
                glfw.getMethod("glfwFocusWindow", long.class).invoke(null, handle);
                try { Thread.sleep(30); } catch (InterruptedException ignored) {}
            } catch (Exception ignored) {}
        }

        // Method 1: MC keyboardHandler.keyPress (preferred)
        if (handle != 0) {
            try {
                Object kb = getKeyboardHandler(mc);
                if (kb != null) {
                    Method m = findKeyPressMethod(kb.getClass());
                    if (m != null) {
                        m.setAccessible(true);
                        m.invoke(kb, handle, glfwKey, 0, action, 0);
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Method 2: Win32 PostMessage (sends WM_KEYDOWN/UP to MC window only)
        if (handle != 0) {
            try {
                int vk = glfwToWin32Vk(glfwKey);
                if (vk > 0) {
                    Class<?> user32 = Class.forName("com.sun.jna.platform.win32.User32");
                    Object instance = user32.getField("INSTANCE").get(null);
                    user32.getMethod("PostMessage", long.class, int.class, long.class, long.class)
                            .invoke(instance, handle, action == 1 ? 0x0100 : 0x0101, (long) vk, 0L);
                    return;
                }
            } catch (Exception ignored) {}
        }

        LOGGER.warn("[Pendulum] Keyboard injection failed for key: {}", keyName);
    }

    private static int glfwToWin32Vk(int k) {
        if (k >= 65 && k <= 90) return k;
        if (k >= 48 && k <= 57) return k;
        return switch (k) {
            case 256 -> 0x1B;
            case 257 -> 0x0D;
            case 258 -> 0x09;
            case 259 -> 0x08;
            case 261 -> 0x2E;
            case 262 -> 0x27;
            case 263 -> 0x25;
            case 264 -> 0x28;
            case 265 -> 0x26;
            case 32 -> 0x20;
            case 340, 344 -> 0x10;
            case 341, 345 -> 0x11;
            case 342, 346 -> 0x12;
            default -> -1;
        };
    }

    /**
     * Press and release a key.
     */
    public static void pressKey(String keyName, float holdSeconds) {
        injectKey(keyName, 1);
        if (holdSeconds > 0) {
            try { Thread.sleep((long) (holdSeconds * 1000)); } catch (InterruptedException ignored) {}
        }
        injectKey(keyName, 0);
    }

    /** Type text via keyboard injection (no AWT Robot). */
    public static void typeText(String text) {
        for (char c : text.toCharArray()) typeChar(c);
    }

    private static void typeChar(char c) {
        // Map char to GLFW key code, inject press+release
        int glfwKey = charToGlfwKey(c);
        if (glfwKey > 0) {
            boolean shift = Character.isUpperCase(c) || "!@#$%^&*()_+{}|:\"<>?~".indexOf(c) >= 0;
            if (shift) injectKey("LEFT_SHIFT", 1);
            injectKeyRaw(glfwKey, 1);
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            injectKeyRaw(glfwKey, 0);
            if (shift) injectKey("LEFT_SHIFT", 0);
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
    }

    private static void injectKeyRaw(int glfwKey, int action) {
        Minecraft mc = Minecraft.getInstance();
        long handle = mc.getWindow().getWindow();
        if (handle != 0) {
            try {
                Object kb = getKeyboardHandler(mc);
                if (kb != null) {
                    Method m = findKeyPressMethod(kb.getClass());
                    if (m != null) { m.setAccessible(true); m.invoke(kb, handle, glfwKey, 0, action, 0); }
                }
            } catch (Exception ignored) {}
        }
    }

    private static int charToGlfwKey(char c) {
        if (c >= 'a' && c <= 'z') return (c - 'a') + 65;
        if (c >= 'A' && c <= 'Z') return (c - 'A') + 65;
        if (c >= '0' && c <= '9') return (c - '0') + 48;
        return switch (c) {
            case ' ' -> 32;
            case '-' -> 45;
            case '=' -> 61;
            case '[' -> 91;
            case ']' -> 93;
            case '\\' -> 92;
            case ';' -> 59;
            case '\'' -> 39;
            case ',' -> 44;
            case '.' -> 46;
            case '/' -> 47;
            default -> -1;
        };
    }

    // ==================== Direct access (Mojang mappings: mouseHandler/keyboardHandler are public) ====================

    private static Object getMouseHandler(Minecraft mc) {
        return mc.mouseHandler;
    }

    private static Object getKeyboardHandler(Minecraft mc) {
        return mc.keyboardHandler;
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
        return switch (glfwKey) {
            case 256 -> KeyEvent.VK_ESCAPE;
            case 257 -> KeyEvent.VK_ENTER;
            case 258 -> KeyEvent.VK_TAB;
            case 259 -> KeyEvent.VK_BACK_SPACE;
            case 261 -> KeyEvent.VK_DELETE;
            case 262 -> KeyEvent.VK_RIGHT;
            case 263 -> KeyEvent.VK_LEFT;
            case 264 -> KeyEvent.VK_DOWN;
            case 265 -> KeyEvent.VK_UP;
            case 32 -> KeyEvent.VK_SPACE;
            case 340 -> KeyEvent.VK_SHIFT;
            case 341 -> KeyEvent.VK_CONTROL;
            case 342 -> KeyEvent.VK_ALT;
            default -> -1;
        };
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
