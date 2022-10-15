package iafenvoy.pendulum.interpreter;

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
}
