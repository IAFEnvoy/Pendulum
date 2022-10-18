package iafenvoy.pendulum.interpreter.util.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;

public abstract class VoidCommandEntry implements CommandEntry {
    private final String prefix;

    public VoidCommandEntry(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getPrefix() {
        return this.prefix;
    }

    public abstract OptionalResult<Object> execute(PendulumInterpreter interpreter, String command);
}
