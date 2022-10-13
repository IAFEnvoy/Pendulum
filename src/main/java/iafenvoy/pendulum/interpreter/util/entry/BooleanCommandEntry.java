package iafenvoy.pendulum.interpreter.util.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;

public interface BooleanCommandEntry extends CommandEntry {

    boolean execute(PendulumInterpreter interpreter, String command);
}
