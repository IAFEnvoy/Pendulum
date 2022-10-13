package iafenvoy.pendulum.interpreter;

import com.google.common.collect.Lists;
import iafenvoy.pendulum.interpreter.entry.*;
import iafenvoy.pendulum.interpreter.util.DataLoader;
import iafenvoy.pendulum.interpreter.util.entry.BooleanCommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.CommandEntry;
import iafenvoy.pendulum.interpreter.util.entry.VoidCommandEntry;
import iafenvoy.pendulum.utils.FileUtils;
import iafenvoy.pendulum.utils.NumberUtils;
import iafenvoy.pendulum.utils.ThreadUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PendulumInterpreter {
    private final HashMap<String, VoidCommandEntry> voidCommand = new HashMap<>();
    private final HashMap<String, BooleanCommandEntry> booleanCommand = new HashMap<>();

    public PendulumInterpreter() {
        register(new CloseCommand());
        register(new CraftCommand());
        register(new HotBarCommand());
        register(new LogCommand());
        register(new SayCommand());
        register(new UseCommand());

        register(new HasCommand());
    }

    private static List<String> rebuildCommand(List<String> cmd) {
        List<String> cmd2 = new ArrayList<>();
        for (String s : cmd)
            cmd2.add(rebuildCommand(s));
        return cmd2;
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

    private static int getEndIndex(List<String> command, int startIndex, String start, String end) {
        int stack = 0;
        for (int i = startIndex + 1; i < command.size(); i++) {
            String[] s = command.get(i).split(" ");
            if (s.length == 0) continue;
            String prefix = s[0];
            if (prefix.equals(start)) stack++;
            else if (prefix.equals(end))
                if (stack == 0) return i;
                else stack--;
        }
        return -1;
    }

    public void register(VoidCommandEntry entry) {
        voidCommand.put(entry.getPrefix(), entry);
    }

    public void register(BooleanCommandEntry entry) {
        booleanCommand.put(entry.getPrefix(), entry);
    }

    public InterpretResult interpret(String... command) {
        return interpret(Lists.newArrayList(command));
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
                    if (commandP.length <= 1) return InterpretResult.TOO_FEW_ARGUMENTS.setLine(i + 1);
                    String fileCmd = FileUtils.readByLines("./pendulum/" + commandP[1] + ".pendulum");
                    InterpretResult result = interpret(fileCmd.replace("\n", ";").split(";"));
                    if (result != InterpretResult.EMPTY)
                        return result;
                } catch (IOException e) {
                    e.printStackTrace();
                    return new InterpretResult("The file cannot be read!").setLine(i + 1);
                }
            } else if (prefix.equals("for")) {//for语句
                if (commandP.length == 1) return InterpretResult.TOO_FEW_ARGUMENTS.setLine(i + 1);
                int times = NumberUtils.parseInt(commandP[1]);
                int endIndex = getEndIndex(command, i, "for", "endfor");
                if (endIndex == -1) return InterpretResult.END_FLAG_NOT_FOUND;
                for (int j = 0; j < times; j++) {
                    InterpretResult result = interpret(command.subList(i + 1, endIndex));
                    if (result != InterpretResult.EMPTY)
                        return result;
                }
                i = endIndex;
            } else if (voidCommand.containsKey(prefix)) {//正常语句
                try {
                    VoidCommandEntry entry = voidCommand.get(prefix);
                    entry.execute(this, removePrefix(cmd, entry));
                } catch (Exception e) {
                    return new InterpretResult(e.getMessage());
                }
            } else if (booleanCommand.containsKey(prefix)) {//boolean语句
                try {
                    BooleanCommandEntry entry = booleanCommand.get(prefix);
                    boolean ret = entry.execute(this, removePrefix(cmd, entry));
                    assert MinecraftClient.getInstance().player != null;
                    MinecraftClient.getInstance().player.sendMessage(Text.of(String.valueOf(ret)), false);
                } catch (Exception e) {
                    return new InterpretResult(e.getMessage());
                }
            } else
                return InterpretResult.COMMAND_NOT_FOUND.setLine(i + 1);
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

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof InterpretResult)
                if (((InterpretResult) obj).errorMessage == null && this.errorMessage == null) return true;
                else return this.errorMessage.equals(((InterpretResult) obj).errorMessage);
            return false;
        }
    }
}
