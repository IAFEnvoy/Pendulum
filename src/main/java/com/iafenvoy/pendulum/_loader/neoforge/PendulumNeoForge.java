package com.iafenvoy.pendulum._loader.neoforge;

//? if neoforge {

/*import com.iafenvoy.pendulum.Pendulum;
import com.iafenvoy.pendulum.command.PendulumCommand;
import com.iafenvoy.pendulum.mcp.McpServer;
import com.iafenvoy.pendulum.script.ScriptEngine;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Pendulum.MOD_ID)
public final class PendulumNeoForge {
    public PendulumNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        Pendulum.initialize();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(PendulumCommand.create());
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Pre event) {
        ScriptEngine.getInstance().onClientTick();
        McpServer.onClientTick();
    }
}

*///?}
