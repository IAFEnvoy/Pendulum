package iafenvoy.pendulum.interpreter.util.entry;

import iafenvoy.pendulum.interpreter.util.CommandInterpretError;

public interface VoidCommandEntry extends CommandEntry{

    void execute(String command) throws CommandInterpretError;
}
