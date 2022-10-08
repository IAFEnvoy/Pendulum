package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import net.minecraft.text.Text;

public class LogCommand implements VoidCommandEntry {
    @Override
    public String getPrefix() {
        return "log";
    }

    @Override
    public void execute(String command) {
        assert client.player != null;
        client.player.sendMessage(Text.of(command), false);
    }
}
