package iafenvoy.pendulum.interpreter.util.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;

public abstract class BooleanCommandEntry implements CommandEntry {
    private final String prefix;

    public BooleanCommandEntry(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getPrefix() {
        return this.prefix;
    }

    public abstract OptionalResult<Boolean> execute(PendulumInterpreter interpreter, String command);
}
