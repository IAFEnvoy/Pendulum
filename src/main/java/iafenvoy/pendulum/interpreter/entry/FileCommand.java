package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.CommandInterpretError;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import iafenvoy.pendulum.utils.FileUtils;

import java.io.IOException;

public class FileCommand implements VoidCommandEntry {
    @Override
    public String getPrefix() {
        return "file";
    }

    @Override
    public void execute(String command) throws CommandInterpretError {
        try {
            String cmd = FileUtils.readFile("./pendulum/" + command + ".pendulum");
            PendulumInterpreter.InterpretResult result = new PendulumInterpreter().interpret(cmd.split("\n"));
            if (result != PendulumInterpreter.InterpretResult.EMPTY)
                throw new CommandInterpretError(result.getErrorMessage());
        } catch (IOException e) {
            throw new CommandInterpretError(e.getMessage());
        }
    }
}
