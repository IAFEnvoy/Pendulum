package iafenvoy.pendulum.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {
    public static String readByLines(String path) throws IOException {
        InputStreamReader stream = new InputStreamReader(Files.newInputStream(Paths.get(path)));
        BufferedReader in = new BufferedReader(stream);
        StringBuilder buffer = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null)
            buffer.append(line).append("\n");
        return buffer.toString();
    }
}
