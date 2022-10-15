package iafenvoy.pendulum.interpreter.util;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class ExpressionUtils {
    public static List<String> middleToSuffix(String expression) {
        String[] words = expression.split(" ");
        List<String> list = new ArrayList<>(), temp = new ArrayList<>();
        for (String s : words) {
            if (s.isEmpty()) continue;
            if (ExpressionUtils.isOperator(s) || s.equals("(") || s.equals(")")) {
                list.add(String.join(" ", temp));
                list.add(s);
                temp.clear();
            } else temp.add(s);
        }
        if (!temp.isEmpty()) list.add(String.join(" ", temp));
        return middleToSuffix(list);
    }

    public static List<String> middleToSuffix(List<String> expression) {
        Stack<String> opStack = new Stack<>();
        List<String> suffixList = new ArrayList<>();
        for (String s : expression) {
            if (isOperator(s)) {
                if (opStack.isEmpty() || opStack.peek().equals("(") || priority(s) > priority(opStack.peek()))
                    opStack.push(s);
                else {
                    while (!opStack.isEmpty() && !opStack.peek().equals("("))
                        if (priority(s) <= priority(opStack.peek()))
                            suffixList.add(opStack.pop());
                    opStack.push(s);
                }
            } else if (s.equals("(")) opStack.push(s);
            else if (s.equals(")"))
                while (!opStack.isEmpty())
                    if (opStack.peek().equals("(")) {
                        opStack.pop();
                        break;
                    } else suffixList.add(opStack.pop());
            else suffixList.add(s);
        }
        while (!opStack.isEmpty())
            suffixList.add(opStack.pop());
        return suffixList;
    }

    public static boolean isOperator(String s) {
        return Lists.newArrayList("and", "or").contains(s);
    }

    private static int priority(String op) {
        if (op.equals("and")) return 2;
        if (op.equals("or")) return 1;
        throw new RuntimeException(op + " is not a valid operator!");
    }
}
