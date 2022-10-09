package iafenvoy.pendulum.interpreter;

import com.google.common.collect.Lists;
import iafenvoy.pendulum.interpreter.entry.*;
import iafenvoy.pendulum.interpreter.util.DataLoader;
import iafenvoy.pendulum.interpreter.util.entry.BooleanCommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.CommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import iafenvoy.pendulum.utils.NumberUtils;
import iafenvoy.pendulum.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PendulumInterpreter {
    private final HashMap<String, VoidCommandEntry> voidCommand = new HashMap<>();
    private final HashMap<String, BooleanCommandEntry> booleanCommand = new HashMap<>();

    public void register(VoidCommandEntry entry) {
        voidCommand.put(entry.getPrefix(), entry);
    }

    public void register(BooleanCommandEntry entry) {
        booleanCommand.put(entry.getPrefix(), entry);
    }

    public InterpretResult interpret(String... command) {
        return interpret(Lists.newArrayList(command), 0, "");
    }

    public PendulumInterpreter() {
        register(new CloseCommand());
        register(new CraftCommand());
        register(new FileCommand());
        register(new HotBarCommand());
        register(new LogCommand());
        register(new SayCommand());
        register(new UseCommand());
    }

    public InterpretResult interpret(List<String> command, int startLine, String retString) {
        if (command.size() == 0) return InterpretResult.NO_COMMAND;
        for (int i = startLine; i < command.size(); i++) {
            String cmd = rebuildCommand(command.get(i));
            if (cmd.isEmpty()) continue;
            if (cmd.equals(retString)) return InterpretResult.EMPTY;
            String[] commandP = cmd.split(" ");
            String prefix = commandP[0];
            System.out.println(voidCommand.containsKey(prefix));
            if (prefix.equals("for")) {//for语句
                if (commandP.length == 1) return InterpretResult.TOO_FEW_ARGUMENTS.setLine(i + 1);
                int times = NumberUtils.parseInt(commandP[1]);
                for (int j = 0; j < times; j++) {
                    InterpretResult result = interpret(command, i + 1, "fend");
                    if (result != InterpretResult.EMPTY)
                        return result;
                }
                while (!rebuildCommand(command.get(i)).equals("fend")) {
                    i++;
                    if (i >= command.size()) return new InterpretResult("No fend found!");
                }
            } else if (voidCommand.containsKey(prefix)) {//正常语句
                VoidCommandEntry entry = voidCommand.get(prefix);
                try {
                    entry.execute(removePrefix(cmd, entry));
                } catch (Exception e) {
                    return new InterpretResult(e.getMessage()).setLine(i + 1);
                }
            } else
                return InterpretResult.COMMAND_NOT_FOUND.setLine(i + 1);
            ThreadUtils.sleep(DataLoader.sleepDelta);
        }
        if (!retString.isEmpty())
            return new InterpretResult("No fend found!");
        return InterpretResult.EMPTY;
    }

    private static String removePrefix(String command, CommandEntry entry) {
        if (command.length() <= entry.getPrefix().length()) return "";
        return command.substring(entry.getPrefix().length() + 1);
    }

    private static String rebuildCommand(String cmd) {
        List<String> l = new ArrayList<>();
        for (String s : cmd.split(" "))
            if (!s.isEmpty())
                l.add(s);
        return String.join(" ", l);
    }

    public static class InterpretResult {
        public static final InterpretResult EMPTY = new InterpretResult(null);
        public static final InterpretResult NO_COMMAND = new InterpretResult("There is no command to execute!");
        public static final InterpretResult COMMAND_NOT_FOUND = new InterpretResult("There is no such command!");
        public static final InterpretResult TOO_FEW_ARGUMENTS = new InterpretResult("There is too few arguments!");
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
