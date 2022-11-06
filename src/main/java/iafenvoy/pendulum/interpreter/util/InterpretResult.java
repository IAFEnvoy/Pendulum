package iafenvoy.pendulum.interpreter.util;

public class InterpretResult {
    public static final InterpretResult EMPTY = new InterpretResult(null);
    public static final InterpretResult COMMAND_NOT_FOUND = new InterpretResult("There is no such command!");
    public static final InterpretResult TOO_FEW_ARGUMENTS = new InterpretResult("There is too few arguments!");
    public static final InterpretResult END_FLAG_NOT_FOUND = new InterpretResult("End flag not found!");
    public static final InterpretResult INTERRUPTED = new InterpretResult("The interpreter is interrupted!");
    private final String errorMessage;

    public InterpretResult(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof InterpretResult)
            if (((InterpretResult) obj).errorMessage == null && this.errorMessage == null) return true;
            else return this.errorMessage.equals(((InterpretResult) obj).errorMessage);
        return false;
    }
}
