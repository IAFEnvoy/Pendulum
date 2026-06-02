package com.iafenvoy.pendulum._loader.fabric;

//? if fabric {

import com.iafenvoy.jupiter.render.screen.JupiterScreen;
import com.iafenvoy.pendulum.config.PendulumConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> JupiterScreen.getConfigScreen(parent, PendulumConfig.INSTANCE, true);
    }
}

//?}
