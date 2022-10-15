package iafenvoy.pendulum.interpreter;

import iafenvoy.pendulum.interpreter.util.DataLoader;
import iafenvoy.pendulum.interpreter.util.ExpressionUtils;
import iafenvoy.pendulum.interpreter.util.entry.BooleanCommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.CommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import iafenvoy.pendulum.utils.FileUtils;
import iafenvoy.pendulum.utils.NumberUtils;
import iafenvoy.pendulum.utils.ThreadUtils;

import java.io.IOException;
import java.util.*;

public class PendulumInterpreter {
    private final HashMap<String, VoidCommandEntry> voidCommand = new HashMap<>();
    private final HashMap<String, BooleanCommandEntry> booleanCommand = new HashMap<>();

    public PendulumInterpreter() {
        register(new BooleanCommandEntry() {
            @Override
            public boolean execute(PendulumInterpreter interpreter, String command) {
                String[] commandP = command.split(" ");
                String prefix = commandP[0];
                if (booleanCommand.containsKey(prefix)) {
                    BooleanCommandEntry entry = booleanCommand.get(prefix);
                    return !entry.execute(interpreter, removePrefix(command, entry));
                } else throw new IllegalArgumentException("there is no such command!");
            }

            @Override
            public String getPrefix() {
                return "not";
            }
        });
        PendulumCommandManager.doRegister(this);
    }

    private static List<String> rebuildCommand(List<String> cmd) {
        List<String> cmd2 = new ArrayList<>();
        for (String s : cmd)
            cmd2.add(rebuildCommand(s));
        return cmd2;
    }

    private static String removePrefix(String cmd, CommandEntry entry) {
        if (cmd.length() <= entry.getPrefix().length()) return "";
        return cmd.substring(entry.getPrefix().length() + 1);
    }

    private static String rebuildCommand(String cmd) {
        List<String> l = new ArrayList<>();
        for (String s : cmd.split(" "))
            if (!s.isEmpty())
                l.add(s);
        return String.join(" ", l);
    }

    private static int getEndIndex(List<String> cmd, int startIndex, String start, String end) {
        int stack = 0;
        for (int i = startIndex + 1; i < cmd.size(); i++) {
            String[] s = cmd.get(i).split(" ");
            if (s.length == 0) continue;
            String prefix = s[0];
            if (prefix.equals(start)) stack++;
            else if (prefix.equals(end))
                if (stack == 0) return i;
                else stack--;
        }
        return -1;
    }

    private static int getElseLocation(List<String> cmd) {
        int stack = 0;
        for (int i = 0; i < cmd.size(); i++) {
            String[] s = cmd.get(i).split(" ");
            if (s.length == 0) continue;
            String prefix = s[0];
            if (prefix.equals("if")) stack++;
            if (prefix.equals("endif")) stack--;
            if (prefix.equals("else") && stack == 0) return i;
        }
        return -1;
    }

    private boolean parseSuffixList(List<String> cmd) {
        Stack<Boolean> stack = new Stack<>();
        for (String s : cmd) {
            if (s.equals("true")) stack.push(true);
            else if (s.equals("false")) stack.push(false);
            else if (ExpressionUtils.isOperator(s)) {
                if (s.equals("and")) {
                    boolean b1 = stack.pop(), b2 = stack.pop();
                    stack.push(b1 & b2);
                }
                if (s.equals("or")) {
                    boolean b1 = stack.pop(), b2 = stack.pop();
                    stack.push(b1 | b2);
                }
            } else {
                if (s.isEmpty()) throw new RuntimeException();
                String[] commandP = s.split(" ");
                String prefix = commandP[0];
                if (booleanCommand.containsKey(prefix)) {
                    BooleanCommandEntry entry = booleanCommand.get(prefix);
                    boolean result = entry.execute(this, removePrefix(s, entry));
                    stack.push(result);
                } else throw new IllegalArgumentException("there is no such command!");
            }
        }
        return stack.peek();
    }

    public void register(VoidCommandEntry entry) {
        voidCommand.put(entry.getPrefix(), entry);
    }

    public void register(BooleanCommandEntry entry) {
        booleanCommand.put(entry.getPrefix(), entry);
    }

    public InterpretResult interpret(String... command) {
        return interpret(Arrays.asList(command));
    }

    public InterpretResult interpret(List<String> command) {
        if (command.size() == 0) return InterpretResult.NO_COMMAND;
        command = rebuildCommand(command);
        for (int i = 0; i < command.size(); i++) {
            String cmd = command.get(i);
            if (cmd.isEmpty()) continue;
            String[] commandP = cmd.split(" ");
            String prefix = commandP[0];
            if (prefix.equals("file")) {//file载入文件语句
                try {
                    if (commandP.length <= 1) return InterpretResult.TOO_FEW_ARGUMENTS;
                    String fileCmd = FileUtils.readByLines("./pendulum/" + commandP[1] + ".pendulum");
                    InterpretResult result = interpret(fileCmd.replace("\n", ";").split(";"));
                    if (result != InterpretResult.EMPTY)
                        return result;
                } catch (IOException e) {
                    e.printStackTrace();
                    return new InterpretResult("The file cannot be read!");
                }
            } else if (prefix.equals("for")) {//for语句
                if (commandP.length == 1) return InterpretResult.TOO_FEW_ARGUMENTS;
                int times = NumberUtils.parseInt(commandP[1]);
                int endIndex = getEndIndex(command, i, "for", "endfor");
                if (endIndex == -1) return InterpretResult.END_FLAG_NOT_FOUND;
                for (int j = 0; j < times; j++) {
                    InterpretResult result = interpret(command.subList(i + 1, endIndex));
                    if (result != InterpretResult.EMPTY)
                        return result;
                }
                i = endIndex;
            } else if (prefix.equals("while")) {
                List<String> expressions = ExpressionUtils.middleToSuffix(removePrefix(cmd, () -> "while"));
                int endIndex = getEndIndex(command, i, "while", "endwhile");
                if (endIndex == -1) return InterpretResult.END_FLAG_NOT_FOUND;
                try {
                    while (parseSuffixList(expressions)) {
                        InterpretResult result = interpret(command.subList(i + 1, endIndex));
                        if (result != InterpretResult.EMPTY)
                            return result;
                    }
                } catch (IllegalArgumentException e) {
                    return InterpretResult.COMMAND_NOT_FOUND;
                }
                i = endIndex;
            } else if (prefix.equals("if")) {
                List<String> expressions = ExpressionUtils.middleToSuffix(removePrefix(cmd, () -> "if"));
                boolean ifResult = parseSuffixList(expressions);
                int endIndex = getEndIndex(command, i, "if", "endif");
                if (endIndex == -1) return InterpretResult.END_FLAG_NOT_FOUND;
                List<String> ifObject = command.subList(i + 1, endIndex);
                int elseLocation = getElseLocation(ifObject);
                InterpretResult result = InterpretResult.EMPTY;
                if (elseLocation == -1) {
                    if (ifResult)
                        result = interpret(ifObject);
                } else {
                    if (ifResult) result = interpret(ifObject.subList(0, elseLocation));
                    else result = interpret(ifObject.subList(elseLocation + 1, ifObject.size()));
                }
                if (result != InterpretResult.EMPTY)
                    return result;
                i = endIndex;
            } else if (voidCommand.containsKey(prefix)) {//正常语句
                try {
                    VoidCommandEntry entry = voidCommand.get(prefix);
                    entry.execute(this, removePrefix(cmd, entry));
                } catch (Exception e) {
                    return new InterpretResult(e.getMessage());
                }
            } else
                return InterpretResult.COMMAND_NOT_FOUND;
            ThreadUtils.sleep(DataLoader.sleepDelta);
        }
        return InterpretResult.EMPTY;
    }

    public static class InterpretResult {
        public static final InterpretResult EMPTY = new InterpretResult(null);
        public static final InterpretResult NO_COMMAND = new InterpretResult("There is no command to execute!");
        public static final InterpretResult COMMAND_NOT_FOUND = new InterpretResult("There is no such command!");
        public static final InterpretResult TOO_FEW_ARGUMENTS = new InterpretResult("There is too few arguments!");
        public static final InterpretResult END_FLAG_NOT_FOUND = new InterpretResult("End flag not found!");
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
}
