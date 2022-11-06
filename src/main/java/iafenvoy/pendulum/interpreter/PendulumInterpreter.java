package iafenvoy.pendulum.interpreter;

import iafenvoy.pendulum.interpreter.util.*;
import iafenvoy.pendulum.interpreter.util.entry.*;
import iafenvoy.pendulum.utils.ClientUtils;
import iafenvoy.pendulum.utils.FileUtils;
import iafenvoy.pendulum.utils.NumberUtils;
import iafenvoy.pendulum.utils.ThreadUtils;

import java.io.IOException;
import java.util.*;

public class PendulumInterpreter {
    private final HashMap<String, VoidCommandEntry> voidCommand = new HashMap<>();
    private final HashMap<String, BooleanCommandEntry> booleanCommand = new HashMap<>();
    private final HashMap<String, IntegerCommandEntry> integerCommand = new HashMap<>();
    private final HashMap<String, DoubleCommandEntry> doubleCommand = new HashMap<>();
    private final HashMap<String, List<String>> defList = new HashMap<>();
    private final HashMap<String, HelpTextProvider> helpTextProviders = new HashMap<>();

    public PendulumInterpreter() {
        register(new BooleanCommandEntry("not") {
            @Override
            public OptionalResult<Boolean> execute(PendulumInterpreter interpreter, String command) {
                String[] commandP = command.split(" ");
                String prefix = commandP[0];
                if (booleanCommand.containsKey(prefix)) {
                    BooleanCommandEntry entry = booleanCommand.get(prefix);
                    OptionalResult<Boolean> optionalResult = entry.execute(interpreter, removePrefix(command, entry));
                    optionalResult.setReturnValue(!optionalResult.getReturnValue());
                    return optionalResult;
                } else throw new IllegalArgumentException("there is no such command!");
            }
        });
        register(new VoidCommandEntry("help") {
            @Override
            public OptionalResult<Object> execute(PendulumInterpreter interpreter, String command) {
                assert client.player != null;
                if (helpTextProviders.containsKey(command)) {
                    String help = helpTextProviders.get(command).getHelpText();
                    if (help == null) throw new IllegalArgumentException("Help text provider should not be null!");
                    else ClientUtils.sendMessage(help);
                } else
                    ClientUtils.sendMessage("Command help not found!");
                return new OptionalResult<>();
            }
        });
        PendulumCommandManager.doRegister(this);
    }

    private static List<String> rebuildCommand(List<String> cmd) {
        List<String> cmd2 = new ArrayList<>();
        for (String s : cmd)
            cmd2.addAll(Arrays.asList(rebuildCommand(s).split(";")));
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

    private OptionalResult<Boolean> parseSuffixList(List<String> cmd) {
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
                    OptionalResult<Boolean> result = entry.execute(this, removePrefix(s, entry));
                    if (result.hasError()) return result;
                    stack.push(result.getReturnValue());
                } else throw new IllegalArgumentException("there is no such command!");
            }
        }
        return new OptionalResult<>(stack.peek());
    }

    public void register(VoidCommandEntry entry) {
        voidCommand.put(entry.getPrefix(), entry);
        if (entry instanceof HelpTextProvider)
            this.registerHelpTextProvider(entry.getPrefix(), (HelpTextProvider) entry);
    }

    public void register(BooleanCommandEntry entry) {
        booleanCommand.put(entry.getPrefix(), entry);
        if (entry instanceof HelpTextProvider)
            this.registerHelpTextProvider(entry.getPrefix(), (HelpTextProvider) entry);
    }

    public void register(IntegerCommandEntry entry) {
        integerCommand.put(entry.getPrefix(), entry);
        if (entry instanceof HelpTextProvider)
            this.registerHelpTextProvider(entry.getPrefix(), (HelpTextProvider) entry);
    }

    public void register(DoubleCommandEntry entry) {
        doubleCommand.put(entry.getPrefix(), entry);
        if (entry instanceof HelpTextProvider)
            this.registerHelpTextProvider(entry.getPrefix(), (HelpTextProvider) entry);
    }

    public void registerHelpTextProvider(String cmd, HelpTextProvider provider) {
        helpTextProviders.put(cmd, provider);
    }

    public OptionalResult<Object> interpret(String... command) {
        return interpret(Arrays.asList(command), new CommandLocation("console", "main"));
    }

    public OptionalResult<Object> interpret(List<String> command, CommandLocation location) {
        command = rebuildCommand(command);
        for (int i = 0; i < command.size(); i++) {
            String cmd = command.get(i);
            if (cmd.isEmpty()) continue;
            String[] commandP = cmd.split(" ");
            String prefix = commandP[0];
            if (prefix.equals("file")) {//file载入文件语句
                try {
                    if (commandP.length <= 1) return new OptionalResult<>(InterpretResult.TOO_FEW_ARGUMENTS);
                    String fileCmd = FileUtils.readByLines("./pendulum/" + commandP[1] + ".pendulum");
                    OptionalResult<Object> result = interpret(Collections.singletonList(fileCmd.replace("\n", ";")), location.setFile(commandP[1]));
                    result.addStackTrace(location, i + 1);
                    if (result.hasError())
                        return result;
                } catch (IOException e) {
                    e.printStackTrace();
                    return new OptionalResult<>("The file cannot be read!");
                }
            } else if (prefix.equals("import")) {//import语句
                try {
                    if (commandP.length <= 2) return new OptionalResult<>(InterpretResult.TOO_FEW_ARGUMENTS);
                    OptionalResult<Object> result = interpret("file " + FileUtils.loadFileFromWeb(commandP[1], commandP[2]));
                    result.addStackTrace(location, i + 1);
                    if (result.hasError())
                        return result;
                } catch (IOException e) {
                    e.printStackTrace();
                    return new OptionalResult<>("The file cannot be download!");
                }
            } else if (prefix.equals("call")) {
                String name = removePrefix(cmd, () -> "call");
                if (defList.containsKey(name)) {
                    OptionalResult<Object> result = interpret(defList.get(name), location.setDef(name));
                    result.addStackTrace(location, i + 1);
                    if (result.hasError())
                        return result;
                } else return new OptionalResult<>(InterpretResult.COMMAND_NOT_FOUND);
            } else if (prefix.equals("def")) {
                String name = removePrefix(cmd, () -> "def");
                int endIndex = getEndIndex(command, i, "def", "enddef");
                if (endIndex == -1) return new OptionalResult<>(InterpretResult.END_FLAG_NOT_FOUND);
                this.defList.put(name, command.subList(i + 1, endIndex));
                i = endIndex;
            } else if (prefix.equals("for")) {//for语句
                if (commandP.length == 1) return new OptionalResult<>(InterpretResult.TOO_FEW_ARGUMENTS);
                int times = NumberUtils.parseInt(commandP[1]);
                int endIndex = getEndIndex(command, i, "for", "endfor");
                if (endIndex == -1) return new OptionalResult<>(InterpretResult.END_FLAG_NOT_FOUND);
                for (int j = 0; j < times; j++) {
                    OptionalResult<Object> result = interpret(command.subList(i + 1, endIndex), location.setDef("for loop block"));
                    result.addStackTrace(location, i + 1);
                    if (result.hasError())
                        return result;
                }
                i = endIndex;
            } else if (prefix.equals("while")) {
                List<String> expressions = ExpressionUtils.middleToSuffix(removePrefix(cmd, () -> "while"));
                int endIndex = getEndIndex(command, i, "while", "endwhile");
                if (endIndex == -1) return new OptionalResult<>(InterpretResult.END_FLAG_NOT_FOUND);
                while (true) {
                    OptionalResult<Boolean> optionalResult = parseSuffixList(expressions);
                    if (optionalResult.hasError()) return new OptionalResult<>(optionalResult.getResult());
                    if (!optionalResult.getReturnValue()) break;
                    OptionalResult<Object> result = interpret(command.subList(i + 1, endIndex), location.setDef("while loop block"));
                    result.addStackTrace(location, i + 1);
                    if (result.hasError())
                        return result;
                }
                i = endIndex;
            } else if (prefix.equals("if")) {
                List<String> expressions = ExpressionUtils.middleToSuffix(removePrefix(cmd, () -> "if"));
                OptionalResult<Boolean> ifResult = parseSuffixList(expressions);
                if (ifResult.hasError()) return new OptionalResult<>(ifResult.getResult());
                int endIndex = getEndIndex(command, i, "if", "endif");
                if (endIndex == -1) return new OptionalResult<>(InterpretResult.END_FLAG_NOT_FOUND);
                List<String> ifObject = command.subList(i + 1, endIndex);
                int elseLocation = getElseLocation(ifObject);
                OptionalResult<Object> result = new OptionalResult<>();
                if (elseLocation == -1) {
                    if (ifResult.getReturnValue())
                        result = interpret(ifObject, location.setDef("if block"));
                } else {
                    if (ifResult.getReturnValue())
                        result = interpret(ifObject.subList(0, elseLocation), location.setDef("if block"));
                    else
                        result = interpret(ifObject.subList(elseLocation + 1, ifObject.size()), location.setDef("else block"));
                }
                result.addStackTrace(location, i + 1);
                if (result.hasError())
                    return result;
                i = endIndex;
            } else if (voidCommand.containsKey(prefix)) {//正常语句
                try {
                    VoidCommandEntry entry = voidCommand.get(prefix);
                    entry.execute(this, removePrefix(cmd, entry));
                } catch (Exception e) {
                    return new OptionalResult<>(e.getMessage());
                }
            } else {
                OptionalResult<Object> result = new OptionalResult<>(InterpretResult.COMMAND_NOT_FOUND);
                result.addStackTrace(location, i);
                return result;
            }
            ThreadUtils.sleep(DataLoader.sleepDelta);
        }
        return new OptionalResult<>();
    }
}
