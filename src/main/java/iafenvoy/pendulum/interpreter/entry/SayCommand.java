package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;

public class SayCommand implements VoidCommandEntry {
    @Override
    public String getPrefix() {
        return "say";
    }

    @Override
    public void execute(PendulumInterpreter interpreter, String command) {
        assert client.player != null;
        client.player.sendChatMessage(command);
    }
}
