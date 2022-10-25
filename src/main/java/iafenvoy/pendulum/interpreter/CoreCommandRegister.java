package iafenvoy.pendulum.interpreter;

import iafenvoy.pendulum.interpreter.entry.*;

public class CoreCommandRegister {
    public static void register() {
        PendulumCommandManager.registerProviders(interpreter -> {
            //void
//            interpreter.register(new AttackCommand());
            interpreter.register(new BreakBlockCommand());
            interpreter.register(new CloseCommand());
            interpreter.register(new CraftCommand());
            interpreter.register(new HotBarCommand());
            interpreter.register(new LogCommand());
            interpreter.register(new SayCommand());
            interpreter.register(new UseCommand());
            //boolean
            interpreter.register(new HasCommand());
            interpreter.register(new FacingCommand());
        });
    }
}
