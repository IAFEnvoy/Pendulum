package iafenvoy.pendulum.interpreter;

import com.google.common.collect.Lists;
import iafenvoy.pendulum.interpreter.entry.*;
import iafenvoy.pendulum.interpreter.util.DataLoader;
import iafenvoy.pendulum.interpreter.util.entry.BooleanCommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.CommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import iafenvoy.pendulum.utils.ThreadUtils;

import java.util.HashMap;
import java.util.List;

public class PendulumInterpreter {
    private static final HashMap<String, VoidCommandEntry> voidCommand = new HashMap<>();
    private static final HashMap<String, BooleanCommandEntry> booleanCommand = new HashMap<>();

    public static void register(VoidCommandEntry entry) {
        voidCommand.put(entry.getPrefix(), entry);
    }

    public static void register(BooleanCommandEntry entry) {
        booleanCommand.put(entry.getPrefix(), entry);
    }

    public static InterpretResult interpret(String... command) {
        return interpret(Lists.newArrayList(command));
    }

    public static void init() {
        register(new CloseCommand());
        register(new CraftCommand());
        register(new HotBarCommand());
        register(new LogCommand());
        register(new SayCommand());
        register(new UseCommand());
    }

    public static InterpretResult interpret(List<String> command) {
        if (command.size() == 0) return InterpretResult.NO_COMMAND;
        for (int i = 0; i < command.size(); i++) {
            String prefix = command.get(i).split(" ")[0];
            if (voidCommand.containsKey(prefix)) {
                VoidCommandEntry entry = voidCommand.get(prefix);
                try {
                    entry.execute(removePrefix(command.get(i), entry));
                } catch (Exception e) {
                    return new InterpretResult(e.getMessage()).setLine(i + 1);
                }
            } else
                return InterpretResult.COMMAND_NOT_FOUND.setLine(i + 1);
            ThreadUtils.sleep(DataLoader.sleepDelta);
        }
        return InterpretResult.EMPTY;
    }

    private static String removePrefix(String command, CommandEntry entry) {
        if (command.length() <= entry.getPrefix().length()) return "";
        return command.substring(entry.getPrefix().length() + 1);
    }

    public static class InterpretResult {
        public static final InterpretResult EMPTY = new InterpretResult(null);
        public static final InterpretResult NO_COMMAND = new InterpretResult("There is no command to execute!");
        public static final InterpretResult COMMAND_NOT_FOUND = new InterpretResult("There is no such command!");
        private final String errorMessage;
        private int line;

        public InterpretResult(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getLine() {
            return line;
        }

        public InterpretResult setLine(int line) {
            InterpretResult result = new InterpretResult(this.errorMessage);
            result.line = line;
            return result;
        }
    }
}
