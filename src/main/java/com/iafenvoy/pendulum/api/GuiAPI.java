package com.iafenvoy.pendulum.api;

import com.iafenvoy.pendulum.script.ScriptEngine;
import com.iafenvoy.pendulum.util.ScreenInputHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.NativeArray;
import dev.latvian.mods.rhino.Scriptable;

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
            return MC.screen.getTitle().getString();
        });
    }

    public static void close(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.submitToGameThread(() -> MC.setScreen(null));
    }

    public static void openChat(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ScriptEngine.submitToGameThread(() -> MC.setScreen(new ChatScreen("")));
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
                results.put(cx, results.size(), results, obj);
            }
            return results;
        });
    }

    /**
     * Recursively build a widget info object.
     */
    static Scriptable buildWidgetObject(Context cx, Scriptable scope, Object widget, boolean recurse) {
        Scriptable obj = cx.newObject(scope);
        obj.put(cx, "type", obj, widget.getClass().getSimpleName());

        // Position, size, text, state — all via AbstractWidget public getters (no reflection)
        if (widget instanceof AbstractWidget w) {
            obj.put(cx, "x", obj, w.getX());
            obj.put(cx, "y", obj, w.getY());
            obj.put(cx, "width", obj, w.getWidth());
            obj.put(cx, "height", obj, w.getHeight());
            Component msg = w.getMessage();
            obj.put(cx, "text", obj, msg.getString());
            obj.put(cx, "active", obj, w.active);
            obj.put(cx, "focused", obj, w.isFocused());
        }

        // Recurse into children (e.g. list widgets, containers)
        if (recurse) {
            java.util.List<?> list = tryGetChildren(widget);
            if (list != null) {
                NativeArray childArr = (NativeArray) cx.newArray(scope, 0);
                for (Object child : list) {
                    Scriptable childObj = buildWidgetObject(cx, scope, child, true);
                    childArr.put(cx, childArr.size(), childArr, childObj);
                }
                if (!childArr.isEmpty()) obj.put(cx, "children", obj, childArr);
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

            if (found instanceof AbstractWidget w) {
                int cx2 = w.getX() + w.getWidth() / 2;
                int cy2 = w.getY() + w.getHeight() / 2;
                new Thread(() -> ScreenInputHelper.clickAt(cx2, cy2, 0)).start();
                return "{\"clicked\":true,\"widget\":\"" + w.getClass().getSimpleName() + "\",\"x\":" + cx2 + ",\"y\":" + cy2 + "}";
            }
            return "{\"error\":\"widget has no position\"}";
        });
    }

    private static Object findWidgetByText(java.util.List<?> children, String target) {
        String lower = target.toLowerCase();
        for (Object child : children) {
            // Check text via AbstractWidget.getMessage()
            if (child instanceof AbstractWidget w) {
                Component msg = w.getMessage();
                if (msg.getString().toLowerCase().contains(lower)) return child;
            }
            // Check type name
            if (child.getClass().getSimpleName().toLowerCase().contains(lower)) return child;
            // Recurse into children
            java.util.List<?> sub = tryGetChildren(child);
            if (sub != null) {
                Object found = findWidgetByText(sub, target);
                if (found != null) return found;
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
        typeText(cx, thisObj, args, funObj);// FIXME::Duplicate API
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
            String cn = child.getClass().getSimpleName().toLowerCase();
            if (cn.contains("list") || cn.contains("selectionlist") || cn.contains("entrylist") || cn.contains("choices")) {
                java.util.List<?> entries = tryGetChildren(child);
                if (entries != null) {
                    for (Object entry : entries) {
                        if (entry instanceof AbstractWidget ew) {
                            Component msg = ew.getMessage();
                            String entryText = msg.getString();
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
            java.util.List<?> sub = tryGetChildren(child);
            if (sub != null) {
                String result = selectListItemRecursive(sub, target);
                if (result.contains("\"selected\":true")) return result;
            }
        }
        return "{\"error\":\"list item not found: " + target + "\"}";
    }

    // ==================== Utility (shared with MinecraftAPI) ====================

    static Scriptable itemStackToObject(Context cx, Scriptable scope, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Scriptable obj = cx.newObject(scope);
        obj.put(cx, "id", obj, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        obj.put(cx, "count", obj, stack.getCount());
        obj.put(cx, "maxCount", obj, stack.getMaxStackSize());
        obj.put(cx, "durability", obj, stack.getDamageValue());
        obj.put(cx, "maxDurability", obj, stack.getMaxDamage());
        obj.put(cx, "name", obj, stack.getHoverName().getString());
        return obj;
    }

    /**
     * Try to get children from a widget via public API only (no reflection).
     * Covers AbstractSelectionList (list widgets). Returns null if no children accessible.
     */
    public static List<?> tryGetChildren(Object widget) {
        if (widget instanceof AbstractSelectionList<?> list) {
            return list.children();
        }
        return null;
    }
}
