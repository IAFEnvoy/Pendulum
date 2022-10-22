package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.DataLoader;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import iafenvoy.pendulum.mixins.IMixinMinecraftClient;
import iafenvoy.pendulum.utils.ThreadUtils;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class BreakBlockCommand extends VoidCommandEntry implements HelpTextProvider {
    public BreakBlockCommand() {
        super("breakblock");
    }

    @Override
    public String getHelpText() {
        return "breakblock | Break the crosshair target block";
    }

    @Override
    public OptionalResult<Object> execute(PendulumInterpreter interpreter, String command) {
        if (DataLoader.currentPos != null || DataLoader.callback != null)
            return new OptionalResult<>("The pos is locked!");
        if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            DataLoader.currentPos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
            DataLoader.callback = () -> {
                ((IMixinMinecraftClient) client).setAttackCooldown(0);//因为mc会在打开各种界面的时候将这个值设置成10000来防止操作，所以要改掉
                ((IMixinMinecraftClient) client).invokeHandleBlockBreaking(true);
                ((IMixinMinecraftClient) client).setAttackCooldown(10000);
            };
            while (DataLoader.currentPos != null)
                ThreadUtils.sleep(50);
            DataLoader.callback = null;
        } else return new OptionalResult<>("The pos is not a block!");
        return new OptionalResult<>();
    }
}
