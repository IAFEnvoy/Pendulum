package iafenvoy.pendulum.interpreter.util;

public class CommandLocation {
    private final String file;
    private final String def;

    public CommandLocation(String file, String def) {
        this.file = file;
        this.def = def;
    }

    public String getDef() {
        return def;
    }

    public String getFile() {
        return file;
    }

    public CommandLocation setDef(String def) {
        return new CommandLocation(this.file, def);
    }

    public CommandLocation setFile(String file) {
        return new CommandLocation(file, this.def);
    }
}
