package iafenvoy.pendulum.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ClientUtils {
    private static final MinecraftClient client = MinecraftClient.getInstance();

    public static void sendMessage(String s) {
        assert client.player != null;
        client.player.sendMessage(Text.of(s), false);
    }
}
