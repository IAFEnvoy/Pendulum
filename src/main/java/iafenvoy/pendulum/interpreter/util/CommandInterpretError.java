package iafenvoy.pendulum.interpreter.util;

public class CommandInterpretError extends Exception{
    public CommandInterpretError(String message){
        super(message);
    }

    public CommandInterpretError(Exception e){
        super(e);
    }
}
