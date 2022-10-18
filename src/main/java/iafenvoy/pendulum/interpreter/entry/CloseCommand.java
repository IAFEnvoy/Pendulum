package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;

public class CloseCommand extends VoidCommandEntry implements HelpTextProvider {
    public CloseCommand() {
        super("close");
    }

    @Override
    public OptionalResult<Object> execute(PendulumInterpreter interpreter, String command) {
        if (client.currentScreen != null)
            client.currentScreen.keyPressed(256, 0, 0);
        return new OptionalResult<>();
    }

    @Override
    public String getHelpText() {
        return "close | Close current gui.";
    }
}
