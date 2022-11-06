package iafenvoy.pendulum.interpreter.util;

import iafenvoy.pendulum.utils.ClientUtils;

import java.util.ArrayList;
import java.util.List;

public class OptionalResult<T> {
    private InterpretResult result = InterpretResult.EMPTY;
    private T returnValue;
    private final List<String> stackTrace = new ArrayList<>();

    public OptionalResult() {
    }

    public OptionalResult(String error) {
        this(new InterpretResult(error));
    }

    public OptionalResult(InterpretResult result) {
        this();
        this.result = result;
    }

    public OptionalResult(T returnValue) {
        this();
        this.returnValue = returnValue;
    }

    public boolean hasError() {
        return this.result != InterpretResult.EMPTY;
    }

    public T getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(T returnValue) {
        this.returnValue = returnValue;
    }

    public InterpretResult getResult() {
        return result;
    }

    public List<String> getStackTrace() {
        return stackTrace;
    }

    public void printStackTrace() {
        ClientUtils.sendMessage(this.result.getErrorMessage());
        for (String location : this.stackTrace)
            ClientUtils.sendMessage("at " + location);
    }

    public void addStackTrace(String location) {
        this.stackTrace.add(location);
    }

    public void addStackTrace(CommandLocation location, int line) {
        this.stackTrace.add(String.format("%s (in %s : %d)", location.getDef(), location.getFile(), line));
    }
}
