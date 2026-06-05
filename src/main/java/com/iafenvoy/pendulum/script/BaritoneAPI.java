package com.iafenvoy.pendulum.script;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.Scriptable;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;

/**
 * All functions exposed on the JS global "baritone" (br) object.
 * Baritone is optional; calling any function when not loaded will warn and stop the script.
 */
@SuppressWarnings("unused")
public final class BaritoneAPI {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final List<String> FUNCTION_NAMES = Arrays.asList(
            // Pathing & Movement
            "mine", "follow", "farm", "explore",
            "getToBlock", "build", "tunnel", "come", "axis",
            "thisWay", "surface", "goal", "path",
            // Items & Interaction
            "pickup", "click",
            // Control
            "stop", "cancel", "forceCancel", "pause", "resume", "isActive", "isPaused", "paused",
            // Selection
            "select", "clearSelection", "selPos1", "selPos2",
            // Settings & Info
            "command", "setting", "find", "blacklist",
            "waypointSave", "waypointList", "waypointDelete",
            "sethome", "home",
            "proc", "eta", "version",
            // Tools
            "repack", "gc", "invert", "render",
            "reloadAll", "saveAll",
            // Elytra & Litematica
            "elytra", "litematica",
            // Help
            "help"
    );

    private static void requireBaritone() {
        if (!BaritoneHelper.isLoaded()) {
            throw new RuntimeException(
                    net.minecraft.client.resources.language.I18n.get("pendulum.baritone.not_installed"));
        }
    }

    private static Object getBaritone() {
        requireBaritone();
        return ScriptEngine.submitToGameThread(BaritoneHelper::getPrimaryBaritone);
    }

    // ==================== Movement/Pathing ====================

    /**
     * baritone.goto(x, y, z) - walk to the specified coordinates
     */
    public static void goto_(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x = ((Number) args[0]).intValue();
        int y = ((Number) args[1]).intValue();
        int z = ((Number) args[2]).intValue();
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "goto " + x + " " + y + " " + z));
    }

    // ==================== Mining ====================

    /**
     * baritone.mine(blockId, count?) - mine specified blocks
     */
    public static void mine(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String blockId = cx.toString(args[0]);
        int count = args.length > 1 ? ((Number) args[1]).intValue() : Integer.MAX_VALUE;
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object mineProcess = BaritoneHelper.getProcess(baritone, "getMineProcess");
            if (mineProcess == null) return;
            try {
                // IMineProcess.mineByName(int quantity, String... blocks)
                mineProcess.getClass().getMethod("mineByName", int.class, String[].class)
                        .invoke(mineProcess, count, new String[]{blockId});
            } catch (Exception e) {
                LOGGER.error("Failed to start mining", e);
            }
        });
    }

    // ==================== Follow ====================

    /**
     * baritone.follow(entityType?) - follow entities of specified type
     */
    public static void follow(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String entityType = args.length > 0 ? cx.toString(args[0]) : null;
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object followProcess = BaritoneHelper.getProcess(baritone, "getFollowProcess");
            if (followProcess == null) return;
            try {
                if (entityType != null) {
                    // follow(filter) - entityType is a simplified name filter
                    followProcess.getClass().getMethod("follow", java.util.function.Predicate.class)
                            .invoke(followProcess, (java.util.function.Predicate<Object>) e -> {
                                try {
                                    String key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                            .getKey((net.minecraft.world.entity.EntityType<?>)
                                                    e.getClass().getMethod("getType").invoke(e)).toString();
                                    return key.equals(entityType);
                                } catch (Exception ex) {
                                    return false;
                                }
                            });
                } else {
                    // Follow player
                    followProcess.getClass().getMethod("follow", java.util.function.Predicate.class)
                            .invoke(followProcess, (java.util.function.Predicate<Object>) e -> {
                                try {
                                    return e.getClass().getMethod("isLocalPlayer").invoke(e).equals(false)
                                            && e instanceof net.minecraft.world.entity.player.Player;
                                } catch (Exception ex) {
                                    return false;
                                }
                            });
                }
            } catch (Exception e) {
                LOGGER.error("Failed to start following", e);
            }
        });
    }

    // ==================== Farm ====================

    /**
     * baritone.farm(range?) - farm mode
     */
    public static void farm(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int range = args.length > 0 ? ((Number) args[0]).intValue() : 100;
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object farmProcess = BaritoneHelper.getProcess(baritone, "getFarmProcess");
            if (farmProcess == null) return;
            try {
                // IFarmProcess.farm(int range, BlockPos pos)
                farmProcess.getClass().getMethod("farm", int.class, BlockPos.class)
                        .invoke(farmProcess, range, Minecraft.getInstance().player.blockPosition());
            } catch (Exception e) {
                try {
                    farmProcess.getClass().getMethod("farm").invoke(farmProcess);
                } catch (Exception e2) {
                    LOGGER.error("Failed to start farming", e2);
                }
            }
        });
    }

    // ==================== Explore ====================

    /**
     * baritone.explore() - start exploring
     */
    public static void explore(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object exploreProcess = BaritoneHelper.getProcess(baritone, "getExploreProcess");
            if (exploreProcess == null) return;
            try {
                BlockPos pos = Minecraft.getInstance().player.blockPosition();
                exploreProcess.getClass().getMethod("explore", int.class, int.class)
                        .invoke(exploreProcess, pos.getX(), pos.getZ());
            } catch (Exception e) {
                LOGGER.error("Failed to start exploring", e);
            }
        });
    }

    // ==================== Move to Block ====================

    /**
     * baritone.getToBlock(blockId) - walk to nearest specified block
     */
    public static void getToBlock(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String blockId = cx.toString(args[0]);
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object process = BaritoneHelper.getProcess(baritone, "getGetToBlockProcess");
            if (process == null) return;
            try {
                //? if >=1.21 {
                /*Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
                *///?} else {
                Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation(blockId));
                //?}
                process.getClass().getMethod("getToBlock", Block.class).invoke(process, block);
            } catch (Exception e) {
                LOGGER.error("Failed to start getToBlock", e);
            }
        });
    }

    // ==================== Build ====================

    /**
     * baritone.build(schematicName, x, y, z) - build a schematic
     */
    public static void build(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String name = cx.toString(args[0]);
        int x = args.length > 1 ? ((Number) args[1]).intValue() : 0;
        int y = args.length > 2 ? ((Number) args[2]).intValue() : 0;
        int z = args.length > 3 ? ((Number) args[3]).intValue() : 0;
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object buildProcess = BaritoneHelper.getProcess(baritone, "getBuilderProcess");
            if (buildProcess == null) return;
            try {
                java.io.File schematicDir = new java.io.File(
                        Minecraft.getInstance().gameDirectory, "schematics");
                java.io.File file = new java.io.File(schematicDir, name + ".schematic");
                if (!file.exists()) file = new java.io.File(schematicDir, name);
                buildProcess.getClass().getMethod("build", String.class, java.io.File.class, net.minecraft.core.Vec3i.class)
                        .invoke(buildProcess, name, file, new net.minecraft.core.Vec3i(x, y, z));
            } catch (Exception e) {
                try {
                    buildProcess.getClass().getMethod("build", String.class,
                                    Class.forName("baritone.api.schematic.ISchematic"), net.minecraft.core.Vec3i.class)
                            .invoke(buildProcess, name, null, new net.minecraft.core.Vec3i(x, y, z));
                } catch (Exception ignored) {
                }
                LOGGER.error("Failed to start building", e);
            }
        });
    }

    // ==================== Tunnel / Pathing Helpers ====================

    /**
     * baritone.tunnel() - dig tunnel in current direction
     */
    public static void tunnel(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "tunnel"));
    }

    /**
     * baritone.come() - walk to player camera position
     */
    public static void come(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "come"));
    }

    /**
     * baritone.axis() - go to nearest axis (X=0 or Z=0)
     */
    public static void axis(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "axis"));
    }

    /**
     * baritone.thisWay() - keep going in current facing direction
     */
    public static void thisWay(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "thisway"));
    }

    /**
     * baritone.surface() - return to surface
     */
    public static void surface(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "surface"));
    }

    /**
     * baritone.goal(x, y, z) or goal(x, z) or goal(y) or goal() - set current goal
     */
    public static void goal(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            if (args.length == 0) {
                BaritoneHelper.executeCommand(baritone, "goal");
            } else if (args.length == 1) {
                BaritoneHelper.executeCommand(baritone, "goal " + ((Number) args[0]).intValue());
            } else if (args.length == 2) {
                BaritoneHelper.executeCommand(baritone, "goal " + ((Number) args[0]).intValue() + " " + ((Number) args[1]).intValue());
            } else {
                BaritoneHelper.executeCommand(baritone, "goal " + ((Number) args[0]).intValue() + " " + ((Number) args[1]).intValue() + " " + ((Number) args[2]).intValue());
            }
        });
    }

    /**
     * baritone.path() - start pathing to current goal
     */
    public static void path(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "path"));
    }

    // ==================== Items / Interaction ====================

    /**
     * baritone.pickup() - pick up nearby dropped items
     */
    public static void pickup(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "pickup"));
    }

    /**
     * baritone.click() - simulate click (must look at target first)
     */
    public static void click(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "click"));
    }

    // ==================== Control ====================

    /**
     * baritone.stop() - cancel all baritone actions
     */
    public static void stop(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> BaritoneHelper.cancelAll(baritone));
    }

    /**
     * baritone.pause() - pause baritone tasks
     */
    public static void pause(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "pause"));
    }

    /**
     * baritone.resume() - resume paused tasks
     */
    public static void resume(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "resume"));
    }

    /**
     * baritone.isActive() -> boolean - whether baritone is active
     */
    public static boolean isActive(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return false;
        return ScriptEngine.submitToGameThread(() -> BaritoneHelper.isPathing(baritone));
    }

    /**
     * baritone.isPaused() -> boolean - whether baritone is paused
     */
    public static boolean isPaused(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        requireBaritone();
        return ScriptEngine.submitToGameThread(() -> {
            try {
                Object baritone = BaritoneHelper.getPrimaryBaritone();
                if (baritone == null) return false;
                Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
                return (boolean) pathingBehavior.getClass().getMethod("isPaused").invoke(pathingBehavior);
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * baritone.paused() -> boolean - same as isPaused()
     */
    public static boolean paused(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        return isPaused(cx, thisObj, args, funObj);
    }

    /**
     * baritone.cancel() - cancel all baritone actions (same as stop())
     */
    public static void cancel(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        stop(cx, thisObj, args, funObj);
    }

    /**
     * baritone.forceCancel() - force cancel all actions
     */
    public static void forceCancel(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "forcecancel"));
    }

    /**
     * baritone.command(cmd) - execute a baritone command (e.g. "mine diamond_ore 64")
     */
    public static void command(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String cmd = cx.toString(args[0]);
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> BaritoneHelper.executeCommand(baritone, cmd));
    }

    /**
     * baritone.setting(key, value) - modify baritone settings
     */
    public static void setting(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String key = cx.toString(args[0]);
        Object value = args[1];
        Object baritone = getBaritone();
        if (baritone == null) return;
        Object settings = BaritoneHelper.getSettings();
        if (settings == null) return;
        ScriptEngine.submitToGameThread(() -> {
            Object converted;
            if (value instanceof Number) converted = ((Number) value).doubleValue();
            else if (value instanceof Boolean) converted = value;
            else converted = cx.toString(value);
            BaritoneHelper.setSetting(settings, key, converted);
        });
    }

    // ==================== Selection ====================

    /**
     * baritone.select(x1, y1, z1, x2, y2, z2) - set selection area
     */
    public static void select(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        int x1 = ((Number) args[0]).intValue(), y1 = ((Number) args[1]).intValue(), z1 = ((Number) args[2]).intValue();
        int x2 = ((Number) args[3]).intValue(), y2 = ((Number) args[4]).intValue(), z2 = ((Number) args[5]).intValue();
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            try {
                Object selectionManager = baritone.getClass().getMethod("getSelectionManager").invoke(baritone);
                selectionManager.getClass().getMethod("addSelection", BlockPos.class, BlockPos.class)
                        .invoke(selectionManager, new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2));
            } catch (Exception e) {
                LOGGER.error("Failed to set selection", e);
            }
        });
    }

    /**
     * baritone.clearSelection() - clear selection
     */
    public static void clearSelection(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            try {
                Object selectionManager = baritone.getClass().getMethod("getSelectionManager").invoke(baritone);
                selectionManager.getClass().getMethod("removeAllSelections").invoke(selectionManager);
            } catch (Exception e) {
                LOGGER.error("Failed to clear selection", e);
            }
        });
    }

    /**
     * baritone.selPos1(x?, y?, z?) - set selection pos1 (no args = player position)
     */
    public static void selPos1(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            if (args.length >= 3) {
                int x = ((Number) args[0]).intValue(), y = ((Number) args[1]).intValue(), z = ((Number) args[2]).intValue();
                BaritoneHelper.executeCommand(baritone, "sel pos1 " + x + " " + y + " " + z);
            } else {
                BaritoneHelper.executeCommand(baritone, "sel pos1");
            }
        });
    }

    /**
     * baritone.selPos2(x?, y?, z?) - set selection pos2
     */
    public static void selPos2(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() -> {
            if (args.length >= 3) {
                int x = ((Number) args[0]).intValue(), y = ((Number) args[1]).intValue(), z = ((Number) args[2]).intValue();
                BaritoneHelper.executeCommand(baritone, "sel pos2 " + x + " " + y + " " + z);
            } else {
                BaritoneHelper.executeCommand(baritone, "sel pos2");
            }
        });
    }

    // ==================== Info & Tools ====================

    /**
     * baritone.find(blockId) - search world for specified block positions (shown in chat)
     */
    public static void find(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String blockId = cx.toString(args[0]);
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "find " + blockId));
    }

    /**
     * baritone.blacklist() - blacklist the block at crosshair from pathing
     */
    public static void blacklist(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "blacklist"));
    }

    // ==================== Waypoints / Home ====================

    /**
     * baritone.waypointSave(name) - save current position as waypoint
     */
    public static void waypointSave(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String name = cx.toString(args[0]);
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "wp save " + name));
    }

    /**
     * baritone.waypointList() - list all waypoints
     */
    public static void waypointList(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "wp list"));
    }

    /**
     * baritone.waypointDelete(name) - delete specified waypoint
     */
    public static void waypointDelete(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String name = cx.toString(args[0]);
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "wp delete " + name));
    }

    /**
     * baritone.sethome(name) - set current position as home
     */
    public static void sethome(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "sethome"));
    }

    /**
     * baritone.home(name?) - go home
     */
    public static void home(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "home"));
    }

    // ==================== Process / Status ====================

    /**
     * baritone.proc() - show current process status
     */
    public static void proc(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "proc"));
    }

    /**
     * baritone.eta() - show estimated time of arrival
     */
    public static void eta(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "eta"));
    }

    /**
     * baritone.version() - show Baritone version
     */
    public static void version(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "version"));
    }

    // ==================== Utility Methods ====================

    /**
     * baritone.repack() - repack surrounding chunks
     */
    public static void repack(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "repack"));
    }

    /**
     * baritone.gc() - call System.gc() to free memory
     */
    public static void gc(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "gc"));
    }

    /**
     * baritone.invert() - invert current goal, run away from target
     */
    public static void invert(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "invert"));
    }

    /**
     * baritone.render() - fix chunk rendering issues (no reload needed)
     */
    public static void render(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "render"));
    }

    /**
     * baritone.reloadAll() - reload Baritone world cache
     */
    public static void reloadAll(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "reloadall"));
    }

    /**
     * baritone.saveAll() - save Baritone world cache
     */
    public static void saveAll(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "saveall"));
    }

    /**
     * baritone.elytra() - elytra flight mode
     */
    public static void elytra(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "elytra"));
    }

    /**
     * baritone.litematica() - build current Litematica schematic
     */
    public static void litematica(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        Object baritone = getBaritone();
        if (baritone == null) return;
        ScriptEngine.submitToGameThread(() ->
                BaritoneHelper.executeCommand(baritone, "litematica"));
    }

    // ==================== Help ====================

    public static void help(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String msg = """
                §6=== Baritone (br) Complete API ===
                §eMovement/Pathing:§r goto, goal, path, come, axis, thisWay, surface, elytra
                §eMining:§r mine, tunnel
                §eFollow:§r follow, pickup
                §eFarm:§r farm  Explore:§r explore  Invert:§r invert
                §eMoveToBlock:§r getToBlock
                §eBuild:§r build, litematica
                §eInteraction:§r click
                §eControl:§r stop, cancel, forceCancel, pause, resume, isActive, isPaused, paused
                §eCommand:§r command
                §eSettings:§r setting
                §eSelection:§r selPos1, selPos2, select, clearSelection
                §eInfo:§r find, proc, eta, version  Blacklist:§r blacklist
                §eWaypoints:§r waypointSave, waypointList, waypointDelete  Home:§r sethome, home
                §eTools:§r repack, gc, render, reloadAll, saveAll
                §cTip: Use Baritone (br.*) whenever possible! Path planning, mining, and building are far more efficient than per-block operations.""";
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(msg), false);
        }
    }
}
