package iafenvoy.pendulum.interpreter;

import iafenvoy.pendulum.interpreter.entry.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PendulumCommandManager {
    private static final List<PendulumRegistryProvider> providerList = new ArrayList<>();

    public static void registerProviders(PendulumRegistryProvider... providers) {
        providerList.addAll(Arrays.asList(providers));
    }

    public static void doRegister(PendulumInterpreter interpreter) {
        for (PendulumRegistryProvider provider : providerList)
            provider.doRegister(interpreter);
    }

    public static void init() {
        PendulumCommandManager.registerProviders(interpreter -> {
            //void
            interpreter.register(new BreakBlockCommand());
            interpreter.register(new CloseCommand());
            interpreter.register(new CraftCommand());
            interpreter.register(new DropCommand());
            interpreter.register(new HotBarCommand());
            interpreter.register(new LogCommand());
            interpreter.register(new SayCommand());
            interpreter.register(new SwapCommand());
            interpreter.register(new UseCommand());
            //boolean
            interpreter.register(new HasCommand());
            interpreter.register(new FacingCommand());
        });
    }
}
