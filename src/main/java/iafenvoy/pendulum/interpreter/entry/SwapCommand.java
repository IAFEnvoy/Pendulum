package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class SwapCommand extends VoidCommandEntry implements HelpTextProvider {
    public SwapCommand() {
        super("swap");
    }

    @Override
    public String getHelpText() {
        return "swap | Swap the main hand and off hand items";
    }

    @Override
    public OptionalResult<Object> execute(PendulumInterpreter interpreter, String command) {
        assert client.getNetworkHandler() != null;
        assert client.player != null;
        if (!client.player.isSpectator())
            client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        return new OptionalResult<>();
    }
}
