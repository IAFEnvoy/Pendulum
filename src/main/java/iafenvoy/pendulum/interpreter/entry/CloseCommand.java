package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;

public class CloseCommand implements VoidCommandEntry {
    @Override
    public String getPrefix() {
        return "close";
    }

    @Override
    public void execute(String command) {
        if (client.currentScreen != null)
            client.currentScreen.keyPressed(256, 0, 0);
    }
}
