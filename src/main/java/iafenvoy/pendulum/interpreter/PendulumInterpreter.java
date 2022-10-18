package iafenvoy.pendulum.interpreter;

import iafenvoy.pendulum.interpreter.util.DataLoader;
import iafenvoy.pendulum.interpreter.util.ExpressionUtils;
import iafenvoy.pendulum.interpreter.util.InterpretResult;
import iafenvoy.pendulum.interpreter.util.OptionalResult;
import iafenvoy.pendulum.interpreter.util.entry.BooleanCommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.CommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.HelpTextProvider;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import iafenvoy.pendulum.utils.FileUtils;
import iafenvoy.pendulum.utils.NumberUtils;
import iafenvoy.pendulum.utils.ThreadUtils;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.*;

public class PendulumInterpreter {
    private final HashMap<String, VoidCommandEntry> voidCommand = new HashMap<>();
    private final HashMap<String, BooleanCommandEntry> booleanCommand = new HashMap<>();
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
                if (helpTextProviders.containsKey(command))
                    client.player.sendMessage(Text.of(helpTextProviders.get(command).getHelpText()), false);
                else
                    client.player.sendMessage(Text.of("Command help not found!"), false);
                return new OptionalResult<>();
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
            helpTextProviders.put(entry.getPrefix(), (HelpTextProvider) entry);
    }

    public void register(BooleanCommandEntry entry) {
        booleanCommand.put(entry.getPrefix(), entry);
        if (entry instanceof HelpTextProvider)
            helpTextProviders.put(entry.getPrefix(), (HelpTextProvider) entry);
    }

    public OptionalResult<Object> interpret(String... command) {
        return interpret(Arrays.asList(command));
    }

    public OptionalResult<Object> interpret(List<String> command) {
        if (command.size() == 0) return new OptionalResult<>(InterpretResult.NO_COMMAND);
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
                    OptionalResult<Object> result = interpret(fileCmd.replace("\n", ";").split(";"));
                    if (result.hasError())
                        return result;
                } catch (IOException e) {
                    e.printStackTrace();
                    return new OptionalResult<>("The file cannot be read!");
                }
            } else if (prefix.equals("for")) {//for语句
                if (commandP.length == 1) return new OptionalResult<>(InterpretResult.TOO_FEW_ARGUMENTS);
                int times = NumberUtils.parseInt(commandP[1]);
                int endIndex = getEndIndex(command, i, "for", "endfor");
                if (endIndex == -1) return new OptionalResult<>(InterpretResult.END_FLAG_NOT_FOUND);
                for (int j = 0; j < times; j++) {
                    OptionalResult<Object> result = interpret(command.subList(i + 1, endIndex));
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
                    OptionalResult<Object> result = interpret(command.subList(i + 1, endIndex));
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
                        result = interpret(ifObject);
                } else {
                    if (ifResult.getReturnValue()) result = interpret(ifObject.subList(0, elseLocation));
                    else result = interpret(ifObject.subList(elseLocation + 1, ifObject.size()));
                }
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
            } else
                return new OptionalResult<>(InterpretResult.COMMAND_NOT_FOUND);
            ThreadUtils.sleep(DataLoader.sleepDelta);
        }
        return new OptionalResult<>();
    }

}
