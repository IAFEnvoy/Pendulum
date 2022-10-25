package iafenvoy.pendulum.interpreter.entry;

import iafenvoy.pendulum.interpreter.PendulumInterpreter;
import iafenvoy.pendulum.interpreter.util.InterpretResult;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.BooleanCommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.utils.RegistryUtils;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class FacingCommand extends BooleanCommandEntry implements HelpTextProvider {
    public FacingCommand() {
        super("facing");
    }

    @Override
    public OptionalResult<Boolean> execute(PendulumInterpreter interpreter, String command) {
        String[] cp = command.split(" ");
        if (cp.length <= 1) return new OptionalResult<>(InterpretResult.TOO_FEW_ARGUMENTS);
        String type = cp[0], name = cp[1];
        assert client.crosshairTarget != null;
        assert client.world != null;
        HitResult.Type hitType = client.crosshairTarget.getType();
        if (type.equalsIgnoreCase(hitType.name())) {
            if (type.equals("block"))
                return new OptionalResult<>(client.world.getBlockState(((BlockHitResult) client.crosshairTarget).getBlockPos()).getBlock() == RegistryUtils.getBlockByName(name));
            else if (type.equals("entity"))
                return new OptionalResult<>(((EntityHitResult) client.crosshairTarget).getEntity().getType() == RegistryUtils.getEntityByName(name));
            else return new OptionalResult<>(false);
        } else
            return new OptionalResult<>(false);
    }

    @Override
    public String getHelpText() {
        return null;
    }
}
