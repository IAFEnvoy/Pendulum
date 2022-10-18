package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import net.minecraft.text.Text;

public class LogCommand extends VoidCommandEntry implements HelpTextProvider {
    public LogCommand() {
        super("log");
    }

    @Override
    public OptionalResult<Object> execute(PendulumInterpreter interpreter, String command) {
        assert client.player != null;
        client.player.sendMessage(Text.of(command), false);
        return new OptionalResult<>();
    }

    @Override
    public String getHelpText() {
        return "log <message> | Print the message to the chat hud (not send to server).";
    }
}
