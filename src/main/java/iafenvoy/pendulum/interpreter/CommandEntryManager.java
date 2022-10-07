package iafenvoy.pendulum.interpreter;

import iafenvoy.pendulum.interpreter.entry.*;

public class CommandEntryManager {
    public static void initialize() {
        PendulumInterpreter.register(new SayCommand());
        PendulumInterpreter.register(new LogCommand());
        PendulumInterpreter.register(new HotBarCommand());
        PendulumInterpreter.register(new UseCommand());
        PendulumInterpreter.register(new CraftCommand());
    }
}
