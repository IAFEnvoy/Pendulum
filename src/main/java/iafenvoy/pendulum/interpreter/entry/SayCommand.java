package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;

public class SayCommand extends VoidCommandEntry implements HelpTextProvider {
    public SayCommand() {
        super("say");
    }

    @Override
    public OptionalResult<Object> execute(PendulumInterpreter interpreter, String command) {
        assert client.player != null;
        client.player.sendChatMessage(command);
        return new OptionalResult<>();
    }

    @Override
    public String getHelpText() {
        return "say <message> | Send message to the server.";
    }
}
