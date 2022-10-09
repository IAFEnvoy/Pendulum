package iafenvoy.pendulum.utils;

public class NumberUtils {
    public static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
