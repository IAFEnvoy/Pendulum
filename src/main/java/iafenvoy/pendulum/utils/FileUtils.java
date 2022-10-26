package iafenvoy.pendulum.utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
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

    public static String loadFileFromWeb(String url, String name) throws IOException {//download the file and return the save path
        HttpURLConnection httpUrl = (HttpURLConnection) new URL(url).openConnection();
        httpUrl.connect();
        InputStream ins = httpUrl.getInputStream();
        File file = new File("./pendulum/import/" + name + ".pendulum");
        OutputStream os = Files.newOutputStream(file.toPath());
        int bytesRead;
        int len = 1024;
        byte[] buffer = new byte[len];
        while ((bytesRead = ins.read(buffer, 0, len)) != -1)
            os.write(buffer, 0, bytesRead);
        os.close();
        ins.close();
        return "import/" + name;
    }
}
