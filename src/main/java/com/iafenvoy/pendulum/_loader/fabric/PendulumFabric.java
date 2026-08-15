package com.iafenvoy.pendulum._loader.fabric;

//? if fabric {

import com.iafenvoy.pendulum.Pendulum;
import com.iafenvoy.pendulum.command.PendulumCommand;
import com.iafenvoy.pendulum.mcp.McpServer;
import com.iafenvoy.pendulum.script.ScriptEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class PendulumFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Pendulum.initialize();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(PendulumCommand.create()));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            ScriptEngine.getInstance().onClientTick();
            McpServer.onClientTick();
        });
    }
}

//?}
