package iafenvoy.pendulum.interpreter.util.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.CommandInterpretError;

public interface BooleanCommandEntry extends CommandEntry {

    boolean execute(PendulumInterpreter interpreter, String command) throws CommandInterpretError;
}
