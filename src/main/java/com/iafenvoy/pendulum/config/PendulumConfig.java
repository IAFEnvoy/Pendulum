package com.iafenvoy.pendulum.config;

import com.iafenvoy.jupiter.config.container.FileConfigContainer;
import com.iafenvoy.jupiter.config.entry.BooleanEntry;
import com.iafenvoy.jupiter.config.entry.DoubleEntry;
import com.iafenvoy.jupiter.config.entry.IntegerEntry;
import net.minecraft.resources.ResourceLocation;

public class PendulumConfig extends FileConfigContainer {
    public static final PendulumConfig INSTANCE = new PendulumConfig();

    public PendulumConfig() {
        //? if >=1.21 {
        /*super(ResourceLocation.fromNamespaceAndPath("pendulum", "config"), "pendulum.config.title", "./config/pendulum.json");
        *///?} else {
        super(new ResourceLocation("pendulum", "config"), "pendulum.config.title", "./config/pendulum.json");
        //?}
    }

    public final IntegerEntry breakTimeout = IntegerEntry.builder("pendulum.config.break_timeout", 200).tooltip("pendulum.config.break_timeout.tooltip").key("break_timeout").min(20).max(1200).build();
    public final DoubleEntry rayTraceDistance = DoubleEntry.builder("pendulum.config.ray_trace_distance", 5.0).tooltip("pendulum.config.ray_trace_distance.tooltip").key("ray_trace_distance").min(1.0).max(20.0).build();
    public final IntegerEntry tickIntervalMs = IntegerEntry.builder("pendulum.config.tick_interval_ms", 50).tooltip("pendulum.config.tick_interval_ms.tooltip").key("tick_interval_ms").min(10).max(200).build();
    public final BooleanEntry syncUseAttack = BooleanEntry.builder("pendulum.config.sync_use_attack", true).tooltip("pendulum.config.sync_use_attack.tooltip").key("sync_use_attack").build();
    public final BooleanEntry logJsErrors = BooleanEntry.builder("pendulum.config.log_js_errors", true).tooltip("pendulum.config.log_js_errors.tooltip").key("log_js_errors").build();
    public final BooleanEntry mcpEnabled = BooleanEntry.builder("pendulum.config.mcp_enabled", false).tooltip("pendulum.config.mcp_enabled.tooltip").key("mcp_enabled").build();
    public final IntegerEntry mcpPort = IntegerEntry.builder("pendulum.config.mcp_port", 25566).tooltip("pendulum.config.mcp_port.tooltip").key("mcp_port").min(1024).max(65535).build();
    public final BooleanEntry allowExecuteCommand = BooleanEntry.builder("pendulum.config.allow_execute_command", true).tooltip("pendulum.config.allow_execute_command.tooltip").key("allow_execute_command").build();
    public final BooleanEntry allowSay = BooleanEntry.builder("pendulum.config.allow_say", true).tooltip("pendulum.config.allow_say.tooltip").key("allow_say").build();
    public final BooleanEntry allowBreak = BooleanEntry.builder("pendulum.config.allow_break", true).tooltip("pendulum.config.allow_break.tooltip").key("allow_break").build();
    public final BooleanEntry allowPlace = BooleanEntry.builder("pendulum.config.allow_place", true).tooltip("pendulum.config.allow_place.tooltip").key("allow_place").build();
    public final BooleanEntry allowAttack = BooleanEntry.builder("pendulum.config.allow_attack", true).tooltip("pendulum.config.allow_attack.tooltip").key("allow_attack").build();

    @Override
    public void init() {
        this.createTab("general", "pendulum.config.category.general")
                .addEntry(this.breakTimeout)
                .addEntry(this.rayTraceDistance)
                .addEntry(this.tickIntervalMs)
                .addEntry(this.syncUseAttack)
                .addEntry(this.logJsErrors);
        this.createTab("mcp", "pendulum.config.category.mcp")
                .addEntry(this.mcpEnabled)
                .addEntry(this.mcpPort);
        this.createTab("permission", "pendulum.config.category.permission")
                .addEntry(this.allowExecuteCommand)
                .addEntry(this.allowSay)
                .addEntry(this.allowBreak)
                .addEntry(this.allowPlace)
                .addEntry(this.allowAttack);
    }
}
