import java.util.List;

public class DogInterpreter {

    public void run(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw.trim();

            // empty / comments
            if (line.isEmpty() || line.startsWith("#"))
                continue;

            // command: say ...
            if (line.startsWith("say ")) {
                String expr = line.substring(4).trim();
                String output = evalToString(expr);
                System.out.println(output);
                continue;
            }

            throw new RuntimeException("Unknown command at line " + (i + 1) + ": " + raw);
        }
    }

    private String evalToString(String expr) {
        if (expr.startsWith("\"") && expr.endsWith("\"") && expr.length() >= 2) {
            return expr.substring(1, expr.length() - 1);
        }

        double val = evalMath(expr);
        if (val == Math.rint(val))
            return String.valueOf((long) val);
        return String.valueOf(val);
    }

    private double evalMath(String s) {
        Parser p = new Parser(s);
        double result = p.parseExpr();
        p.skipSpaces();
        if (!p.isEnd())
            throw new RuntimeException("Bad expression near: " + p.rest());
        return result;
    }

    private static final class Parser {
        private final String s;
        private int pos = 0;

        Parser(String s) {
            this.s = s;
        }

        double parseExpr() {
            double v = parseTerm();
            while (true) {
                skipSpaces();
                if (match('+'))
                    v += parseTerm();
                else if (match('-'))
                    v -= parseTerm();
                else
                    break;
            }
            return v;
        }

        double parseTerm() {
            double v = parseFactor();
            while (true) {
                skipSpaces();
                if (match('*'))
                    v *= parseFactor();
                else if (match('/'))
                    v /= parseFactor();
                else
                    break;
            }
            return v;
        }

        double parseFactor() {
            skipSpaces();

            if (match('-'))
                return -parseFactor();

            if (match('(')) {
                double v = parseExpr();
                skipSpaces();
                if (!match(')'))
                    throw new RuntimeException("Missing ')'");
                return v;
            }

            return parseNumber();
        }

        double parseNumber() {
            skipSpaces();
            int start = pos;
            boolean dot = false;

            while (!isEnd()) {
                char c = s.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    pos++;
                } else {
                    break;
                }
            }

            if (start == pos)
                throw new RuntimeException("Expected number near: " + rest());

            String token = s.substring(start, pos);
            return Double.parseDouble(token);
        }

        void skipSpaces() {
            while (!isEnd() && Character.isWhitespace(s.charAt(pos)))
                pos++;
        }

        boolean match(char c) {
            if (!isEnd() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        boolean isEnd() {
            return pos >= s.length();
        }

        String rest() {
            return s.substring(Math.min(pos, s.length()));
        }
    }
}