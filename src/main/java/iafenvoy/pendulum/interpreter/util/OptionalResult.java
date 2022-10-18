package iafenvoy.pendulum.interpreter.util;

public class OptionalResult<T> {
    private InterpretResult result = InterpretResult.EMPTY;
    private T returnValue;

    public OptionalResult() {
    }

    public OptionalResult(String error) {
        this.result = new InterpretResult(error);
    }

    public OptionalResult(InterpretResult result) {
        this.result = result;
    }

    public OptionalResult(T returnValue) {
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
}
