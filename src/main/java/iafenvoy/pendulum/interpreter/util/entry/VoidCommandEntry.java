package iafenvoy.pendulum.interpreter.util.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;

public interface VoidCommandEntry extends CommandEntry {

    void execute(PendulumInterpreter interpreter, String command);
}
