package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;

public class DropCommand extends VoidCommandEntry implements HelpTextProvider {
    public DropCommand() {
        super("drop");
    }

    @Override
    public String getHelpText() {
        return "drop <all=false> | drop selected stack's items";
    }

    @Override
    public OptionalResult<Object> execute(PendulumInterpreter interpreter, String command) {
        assert client.player != null;
        if (!client.player.isSpectator())
            client.player.dropSelectedItem(command.equals("true"));
        return null;
    }
}
