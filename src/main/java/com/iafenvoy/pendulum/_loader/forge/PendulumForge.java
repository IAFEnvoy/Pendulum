package com.iafenvoy.pendulum._loader.forge;

//? if forge {

/*import com.iafenvoy.pendulum.Pendulum;
import com.iafenvoy.pendulum.command.PendulumCommand;
import com.iafenvoy.pendulum.mcp.McpServer;
import com.iafenvoy.pendulum.script.ScriptEngine;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Pendulum.MOD_ID)
public final class PendulumForge {
    public PendulumForge() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        Pendulum.initialize();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(PendulumCommand.create());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ScriptEngine.getInstance().onClientTick();
            McpServer.onClientTick();
        }
    }
}

*///?}
