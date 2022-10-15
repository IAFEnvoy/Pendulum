package iafenvoy.pendulum.interpreter.util.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;

public interface VoidCommandEntry extends CommandEntry {

    OptionalResult<Object> execute(PendulumInterpreter interpreter, String command);
}
