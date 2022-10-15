package iafenvoy.pendulum.interpreter.util.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;

public interface BooleanCommandEntry extends CommandEntry {

    OptionalResult<Boolean> execute(PendulumInterpreter interpreter, String command);
}
