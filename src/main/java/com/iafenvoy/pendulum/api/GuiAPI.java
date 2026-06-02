package com.iafenvoy.pendulum.api;

import com.iafenvoy.pendulum.script.ScriptEngine;
import com.iafenvoy.pendulum.util.ScreenInputHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.NativeArray;
import dev.latvian.mods.rhino.Scriptable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * Functions exposed on the "mc.gui" JS object.
 * Screen-level GUI interaction: inspect widgets, click by coordinate/id/index, keyboard input.
 * Also includes container operations (clickSlot, craft, getContainerAllItems, etc.).
 */
@SuppressWarnings("unused")
public final class GuiAPI {
    private static final Minecraft MC = Minecraft.getInstance();

    public static final List<String> FUNCTION_NAMES = Arrays.asList(
            // Screen info
            "isOpen", "getTitle",
            // Close/open
            "close", "openChat",
            // Widget enumeration
            "getElements",
            // Screen-level input (mouse / keyboard)
            "click", "clickButton", "pressKey", "typeText", "pasteText", "hotkey",
            "scroll", "mouseDrag",
            // Container slots
            "clickSlot", "clickSlotRight",
            "craft", "craftAll",
            "getSlotCount", "getSlotItem", "getAllItems", "getType",
            "moveItem", "quickMoveItem",
            // Control / Advanced
            "wait", "callMethod", "selectListItem"
    );

    // ==================== Screen Info ====================

    public static boolean isOpen(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> MC.screen != null);
    }

    public static String getTitle(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.screen == null) return "";
            Component title = MC.screen.getTitle();
            return title != null ? title.getString() : "";
        });
    }

    public static void close(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.submitToGameThread(() -> MC.setScreen(null));
    }

    public static void openChat(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.submitToGameThread(() -> {
            try {
                Class<?> chatScreenClass = Class.forName("net.minecraft.client.gui.screens.ChatScreen");
                Object chatScreen = chatScreenClass.getConstructor(String.class).newInstance("");
                for (java.lang.reflect.Method m : MC.getClass().getMethods()) {
                    if (m.getName().equals("setScreen") && m.getParameterCount() == 1) {
                        m.invoke(MC, chatScreen);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    // ==================== Widget Enumeration (recursive) ====================

    /**
     * getElements() - recursively enumerate all GUI widgets on the current screen.
     * Returns [{type, text?, x, y, width, height, id?, children?}, ...]
     */
    public static Scriptable getElements(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> {
            NativeArray results = (NativeArray) cx.newArray(thisObj, 0);
            if (MC.screen == null) return results;
            for (var child : MC.screen.children()) {
                Scriptable obj = buildWidgetObject(cx, thisObj, child, true);
                if (obj != null) results.put(cx, results.size(), results, obj);
            }
            return results;
        });
    }

    /**
     * Recursively build a widget info object.
     */
    static Scriptable buildWidgetObject(Context cx, Scriptable scope, Object widget, boolean recurse) {
        Scriptable obj = cx.newObject(scope);
        Class<?> clazz = widget.getClass();
        obj.put(cx, "type", obj, clazz.getSimpleName());

        // Position & size via reflection
        try {
            Field xF = findField(clazz, "x", "getX", "field_22786");
            Field yF = findField(clazz, "y", "getY", "field_22787");
            Field wF = findField(clazz, "width", "getWidth", "field_22788");
            Field hF = findField(clazz, "height", "getHeight", "field_22789");
            if (xF != null) obj.put(cx, "x", obj, ((Number) xF.get(widget)).intValue());
            if (yF != null) obj.put(cx, "y", obj, ((Number) yF.get(widget)).intValue());
            if (wF != null) obj.put(cx, "width", obj, ((Number) wF.get(widget)).intValue());
            if (hF != null) obj.put(cx, "height", obj, ((Number) hF.get(widget)).intValue());
        } catch (Exception ignored) {
        }

        // Text label
        try {
            Field msgF = findField(clazz, "message", "getMessage", "field_22791");
            if (msgF != null) {
                Object msg = msgF.get(widget);
                obj.put(cx, "text", obj, msg instanceof Component c ? c.getString() : msg.toString());
            }
        } catch (Exception ignored) {
        }

        // Enabled state
        try {
            Field activeF = findField(clazz, "active", "isActive", "field_22792");
            if (activeF != null) obj.put(cx, "active", obj, activeF.getBoolean(widget));
        } catch (Exception ignored) {
        }

        // Focused state
        try {
            Field focusedF = findField(clazz, "isFocused", "focused", "field_22801");
            if (focusedF != null) obj.put(cx, "focused", obj, focusedF.getBoolean(widget));
        } catch (Exception ignored) {
        }

        // Recurse into children (e.g. containers, panels)
        if (recurse) {
            try {
                Field childrenField = findField(clazz, "children", "renderables", "widgets");
                if (childrenField != null) {
                    Object children = childrenField.get(widget);
                    if (children instanceof List<?> list) {
                        NativeArray childArr = (NativeArray) cx.newArray(scope, 0);
                        for (Object child : list) {
                            Scriptable childObj = buildWidgetObject(cx, scope, child, true);
                            if (childObj != null) childArr.put(cx, childArr.size(), childArr, childObj);
                        }
                        if (childArr.size() > 0) obj.put(cx, "children", obj, childArr);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return obj;
    }

    // ==================== Screen-Level Input ====================

    /**
     * gui.click(x, y, button?) - click at screen coordinates.
     * button: "left" (default), "right", "middle"
     */
    public static void click(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int button = 0; // left
        if (args.length > 2) {
            String b = cx.toString(args[2]).toLowerCase();
            if (b.equals("right")) button = 1;
            else if (b.equals("middle")) button = 2;
        }
        int finalButton = button;
        new Thread(() -> ScreenInputHelper.clickAt(x, y, finalButton)).start();
    }

    /**
     * gui.clickButton(idOrText) - click a button by text (substring match) or by widget type.
     * Finds the first widget whose text contains the given string or whose type matches,
     * then clicks on its center. Returns the widget info that was clicked.
     */
    public static String clickButton(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String target = cx.toString(args[0]);
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.screen == null) return "{\"error\":\"no screen\"}";
            Object found = findWidgetByText(MC.screen.children(), target);
            if (found == null) return "{\"error\":\"widget not found: " + target + "\"}";

            try {
                Field xF = findField(found.getClass(), "x", "getX");
                Field yF = findField(found.getClass(), "y", "getY");
                Field wF = findField(found.getClass(), "width", "getWidth");
                Field hF = findField(found.getClass(), "height", "getHeight");
                int wx = xF != null ? ((Number) xF.get(found)).intValue() : 0;
                int wy = yF != null ? ((Number) yF.get(found)).intValue() : 0;
                int ww = wF != null ? ((Number) wF.get(found)).intValue() : 0;
                int wh = hF != null ? ((Number) hF.get(found)).intValue() : 0;
                int cx2 = wx + ww / 2;
                int cy2 = wy + wh / 2;
                new Thread(() -> ScreenInputHelper.clickAt(cx2, cy2, 0)).start();
                return "{\"clicked\":true,\"widget\":\"" + found.getClass().getSimpleName() + "\",\"x\":" + cx2 + ",\"y\":" + cy2 + "}";
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        });
    }

    private static Object findWidgetByText(List<? extends GuiEventListener> children, String target) {
        String lower = target.toLowerCase();
        for (Object child : children) {
            Class<?> clazz = child.getClass();
            // Check text
            try {
                Field msgF = findField(clazz, "message", "getMessage", "field_22791");
                if (msgF != null) {
                    Object msg = msgF.get(child);
                    String text = msg instanceof Component c ? c.getString().toLowerCase() : msg.toString().toLowerCase();
                    if (text.contains(lower)) return child;
                }
            } catch (Exception ignored) {
            }
            // Check type name
            if (clazz.getSimpleName().toLowerCase().contains(lower)) return child;
            // Recurse into children
            try {
                Field childrenF = findField(clazz, "children", "renderables");
                if (childrenF != null) {
                    Object subChildren = childrenF.get(child);
                    if (subChildren instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<GuiEventListener> list = (List<GuiEventListener>) subChildren;
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
     * gui.pressKey(key, holdSeconds?) - press a keyboard key.
     */
    public static void pressKey(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String key = cx.toString(args[0]);
        float hold = 0f;
        if (args.length > 1) hold = ((Number) args[1]).floatValue();
        float finalHold = hold;
        new Thread(() -> ScreenInputHelper.pressKey(key, finalHold)).start();
    }

    /**
     * gui.typeText(text, pressEnter?) - type text into the focused field.
     */
    public static void typeText(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String text = cx.toString(args[0]);
        boolean pressEnter = args.length > 1 && cx.toBoolean(args[1]);
        new Thread(() -> {
            ScreenInputHelper.typeText(text);
            if (pressEnter) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                ScreenInputHelper.pressKey("ENTER", 0f);
            }
        }).start();
    }

    /**
     * gui.pasteText(text, pressEnter?) - paste text (types quickly).
     */
    public static void pasteText(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String text = cx.toString(args[0]);
        boolean pressEnter = args.length > 1 && cx.toBoolean(args[1]);
        new Thread(() -> {
            ScreenInputHelper.typeText(text);
            if (pressEnter) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                ScreenInputHelper.pressKey("ENTER", 0f);
            }
        }).start();
    }

    /**
     * gui.hotkey(keys) - press a key combination. E.g. gui.hotkey("ctrl,s")
     */
    public static void hotkey(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String combo = cx.toString(args[0]);
        String[] keys = combo.split(",");
        new Thread(() -> {
            // Press all keys
            for (String k : keys) ScreenInputHelper.injectKey(k.trim(), 1);
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            // Release in reverse order
            for (int i = keys.length - 1; i >= 0; i--)
                ScreenInputHelper.injectKey(keys[i].trim(), 0);
        }).start();
    }

    /**
     * gui.scroll(clicks) - scroll mouse wheel.
     */
    public static void scroll(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int clicks = ((Number) args[0]).intValue();
        new Thread(() -> ScreenInputHelper.scroll(clicks)).start();
    }

    /**
     * gui.mouseDrag(x1, y1, x2, y2, button?) - drag from one point to another.
     */
    public static void mouseDrag(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x1 = ((Number) args[0]).intValue();
        int y1 = ((Number) args[1]).intValue();
        int x2 = ((Number) args[2]).intValue();
        int y2 = ((Number) args[3]).intValue();
        int button = 0;
        if (args.length > 4) {
            String b = cx.toString(args[4]).toLowerCase();
            if (b.equals("right")) button = 1;
            else if (b.equals("middle")) button = 2;
        }
        int finalButton = button;
        new Thread(() -> ScreenInputHelper.mouseDrag(x1, y1, x2, y2, finalButton)).start();
    }

    // ==================== Container Slots ====================

    public static void clickSlot(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int slotId = ((Number) args[0]).intValue();
        int button = args.length > 1 ? ((Number) args[1]).intValue() : 0;
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> screen)) return;
            if (MC.player == null || MC.gameMode == null) return;
            MC.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slotId, button,
                    net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    public static void clickSlotRight(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int slotId = ((Number) args[0]).intValue();
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> screen)) return;
            if (MC.player == null || MC.gameMode == null) return;
            MC.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slotId, 1,
                    net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    public static void craft(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> screen)) return;
            if (MC.player == null || MC.gameMode == null) return;
            MC.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 0, 0,
                    net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    public static void craftAll(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> screen)) return;
            if (MC.player == null || MC.gameMode == null) return;
            MC.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 0, 0,
                    net.minecraft.world.inventory.ClickType.QUICK_MOVE, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    public static double getSlotCount(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() ->
                (double) (MC.screen instanceof AbstractContainerScreen<?> s ? s.getMenu().slots.size() : 0));
    }

    public static Scriptable getSlotItem(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int slot = ((Number) args[0]).intValue();
        return ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> s)) return null;
            if (slot < 0 || slot >= s.getMenu().slots.size()) return null;
            return itemStackToObject(cx, thisObj, s.getMenu().getSlot(slot).getItem());
        });
    }

    public static Scriptable getAllItems(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> {
            NativeArray result = (NativeArray) cx.newArray(thisObj, 0);
            if (!(MC.screen instanceof AbstractContainerScreen<?> s)) return result;
            for (int i = 0; i < s.getMenu().slots.size(); i++) {
                ItemStack stack = s.getMenu().getSlot(i).getItem();
                if (!stack.isEmpty()) {
                    Scriptable obj = itemStackToObject(cx, thisObj, stack);
                    if (obj != null) {
                        obj.put(cx, "slot", obj, i);
                        result.put(cx, result.size(), result, obj);
                    }
                }
            }
            return result;
        });
    }

    public static String getType(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.screen == null) return "none";
            String cn = MC.screen.getClass().getName().toLowerCase();
            if (cn.contains("crafting")) return "crafting_table";
            if (cn.contains("furnace") || cn.contains("blast") || cn.contains("smoker")) return "furnace";
            if (cn.contains("chest") || cn.contains("shulker") || cn.contains("barrel")) return "chest";
            if (cn.contains("enchant")) return "enchanting";
            if (cn.contains("anvil")) return "anvil";
            if (cn.contains("inventory") || cn.contains("container")) return "container";
            return "unknown";
        });
    }

    public static void moveItem(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int from = ((Number) args[0]).intValue();
        int to = ((Number) args[1]).intValue();
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> s)) return;
            if (MC.player == null || MC.gameMode == null) return;
            int cid = s.getMenu().containerId;
            MC.gameMode.handleInventoryMouseClick(cid, from, 0, net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
            MC.gameMode.handleInventoryMouseClick(cid, to, 0, net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    public static void quickMoveItem(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int slot = ((Number) args[0]).intValue();
        ScriptEngine.submitToGameThread(() -> {
            if (!(MC.screen instanceof AbstractContainerScreen<?> s)) return;
            if (MC.player == null || MC.gameMode == null) return;
            MC.gameMode.handleInventoryMouseClick(s.getMenu().containerId, slot, 0,
                    net.minecraft.world.inventory.ClickType.QUICK_MOVE, MC.player);
        });
        ScriptEngine.waitTicks(1);
    }

    // ==================== Control / Advanced ====================

    /**
     * gui.wait(seconds) - pause script execution for N seconds.
     * Useful for sequencing screen actions.
     */
    public static void wait(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        double seconds = args.length > 0 ? ((Number) args[0]).doubleValue() : 1.0;
        long ms = (long) (seconds * 1000);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * gui.callMethod(name) - call an arbitrary method on the current screen.
     * HIGH RISK — reflection-based, wraps everything in try-catch.
     * Returns JSON string with result or error.
     */
    public static String callMethod(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String methodName = cx.toString(args[0]);
        return ScriptEngine.submitToGameThread(() -> {
            try {
                if (MC.screen == null) return "{\"error\":\"no screen open\"}";
                Object screen = MC.screen;
                Class<?> clazz = screen.getClass();

                java.lang.reflect.Method target = null;
                for (java.lang.reflect.Method m : clazz.getMethods()) {
                    if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                        target = m;
                        break;
                    }
                }
                if (target == null) {
                    for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                        if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                            target = m;
                            break;
                        }
                    }
                }
                if (target == null)
                    return "{\"error\":\"method not found: " + methodName + " on " + clazz.getSimpleName() + "\"}";

                target.setAccessible(true);
                Object result = target.invoke(screen);
                return "{\"called\":true,\"method\":\"" + methodName + "\",\"screen\":\"" + clazz.getSimpleName() + "\"}";
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                return "{\"error\":\"invocation failed: " + (cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : e.getMessage()).replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
            } catch (Exception e) {
                return "{\"error\":\"" + e.getClass().getSimpleName() + ": " + e.getMessage().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
            }
        });
    }

    /**
     * gui.selectListItem(text) - select an item in a dropdown/list widget.
     * Searches for list widgets and clicks matching entry by text substring.
     */
    public static String selectListItem(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String target = cx.toString(args[0]);
        String lower = target.toLowerCase();
        return ScriptEngine.submitToGameThread(() -> {
            if (MC.screen == null) return "{\"error\":\"no screen open\"}";
            return selectListItemRecursive(MC.screen.children(), lower);
        });
    }

    private static String selectListItemRecursive(java.util.List<?> children, String target) {
        for (Object child : children) {
            Class<?> clazz = child.getClass();
            String cn = clazz.getSimpleName().toLowerCase();
            if (cn.contains("list") || cn.contains("selectionlist") || cn.contains("entrylist") || cn.contains("choices")) {
                try {
                    Field childrenF = findField(clazz, "children", "entries", "items", "listEntries");
                    if (childrenF != null) {
                        Object entries = childrenF.get(child);
                        if (entries instanceof java.util.List<?> entryList) {
                            for (Object entry : entryList) {
                                try {
                                    Field msgF = findField(entry.getClass(), "message", "getMessage", "name", "getName");
                                    if (msgF != null) {
                                        Object msg = msgF.get(entry);
                                        String entryText = msg instanceof net.minecraft.network.chat.Component c ? c.getString() : msg.toString();
                                        if (entryText.toLowerCase().contains(target)) {
                                            Field xF = findField(entry.getClass(), "x", "getX");
                                            Field yF = findField(entry.getClass(), "y", "getY");
                                            Field wF = findField(entry.getClass(), "width", "getWidth");
                                            Field hF = findField(entry.getClass(), "height", "getHeight");
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
            try {
                Field childrenF = findField(clazz, "children", "renderables", "widgets");
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

    // ==================== Utility (shared with MinecraftAPI) ====================

    static Scriptable itemStackToObject(Context cx, Scriptable scope, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Scriptable obj = cx.newObject(scope);
        obj.put(cx, "id", obj, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        obj.put(cx, "count", obj, stack.getCount());
        obj.put(cx, "maxCount", obj, stack.getMaxStackSize());
        obj.put(cx, "durability", obj, stack.getDamageValue());
        obj.put(cx, "maxDurability", obj, stack.getMaxDamage());
        obj.put(cx, "name", obj, stack.getHoverName().getString());
        return obj;
    }

    static Field findField(Class<?> clazz, String... candidates) {
        for (String name : candidates) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        if (clazz.getSuperclass() != null) return findField(clazz.getSuperclass(), candidates);
        return null;
    }
}
