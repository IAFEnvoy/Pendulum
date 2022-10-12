package iafenvoy.pendulum.utils;

import java.io.*;
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

    public static String readFile(String path) throws IOException {
        InputStream inputStream = Files.newInputStream(Paths.get(path));
        StringBuilder stringBuilder = new StringBuilder();
        int i;
        while ((i = inputStream.read()) != -1)
            stringBuilder.append((char) i);
        inputStream.close();
        return stringBuilder.toString();
    }

    public static void saveFile(String path, String content) throws IOException {
        File configFolder = new File("./config/fracture");
        if (!configFolder.exists())
            configFolder.mkdir();
        OutputStream outputStream = Files.newOutputStream(new File(path).toPath());
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
        bufferedWriter.write(content);
        bufferedWriter.close();
    }
}
