package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.util.VoidCommandEntry;

public class SayCommand implements VoidCommandEntry {
    @Override
    public String getPrefix() {
        return "say";
    }

    @Override
    public void execute(String command) {
        assert client.player != null;
        client.player.sendChatMessage(command);
    }
}
