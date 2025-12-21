import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class DogInterpreter {

    private final Set<String> imported = new HashSet<String>();

    public void run(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            int lineNumber = i + 1;

            String trimmed = raw.trim();

            // empty / comments
            if (trimmed.isEmpty() || trimmed.startsWith("#"))
                continue;

            // import <module>
            if (trimmed.startsWith("import ")) {
                int kwIndex = indexOfNonSpace(raw);
                int moduleStartCol = kwIndex + "import ".length() + 1; // 1-based
                String module = trimmed.substring("import ".length()).trim();

                if (module.isEmpty()) {
                    throw DogException.at(lineNumber, moduleStartCol, raw, "Expected module name after import");
                }

                if (!module.equals("io") && !module.equals("math")) {
                    throw DogException.at(lineNumber, moduleStartCol, raw,
                            "Unknown module '" + module + "'. Available: io, math");
                }

                imported.add(module);
                continue;
            }

            // say <expr>
            if (trimmed.startsWith("say")) {
                int sayPos = raw.indexOf("say");
                int afterSay = sayPos + 3; // index after 'say'
                // must have at least one space or expression after
                String rest = raw.substring(afterSay);
                if (rest.trim().isEmpty()) {
                    throw DogException.at(lineNumber, afterSay + 1, raw, "Expected expression after 'say'");
                }

                int exprIndex0 = findExprStartIndex(raw, afterSay);
                int exprBaseCol = exprIndex0 + 1; // 1-based column where expression starts

                String expr = raw.substring(exprIndex0).trim();
                try {
                    String output = evalToString(expr, lineNumber, exprBaseCol, raw);
                    System.out.println(output);
                } catch (DogException e) {
                    throw e; // already has line/col
                }
                continue;
            }

            // expression statement (mostly for io.print / io.println)
            int exprStart = indexOfNonSpace(raw);
            int exprBaseCol = exprStart + 1;
            String exprStmt = raw.substring(exprStart);

            try {
                evalExpressionStatement(exprStmt, lineNumber, exprBaseCol, raw);
            } catch (DogException e) {
                throw e;
            }
        }
    }

    private void evalExpressionStatement(String exprStmt, int line, int baseCol, String fullLine) {
        Parser p = new Parser(exprStmt, line, baseCol, fullLine, this);
        // Only allow module calls as statements:
        // io.print(...), io.println(...)
        // (math.* without using result is allowed too, but useless — we’ll allow it)
        Value v = p.parseExprValue();
        p.skipSpaces();
        if (!p.isEnd()) {
            throw DogException.at(line, baseCol + p.pos, fullLine, "Unexpected token near: " + p.rest());
        }
        // If it's just a number/string and not a call, it's probably a mistake:
        // We'll warn as an error to keep language strict.
        if (!p.didCallFunction) {
            throw DogException.at(line, baseCol, fullLine,
                    "This line does nothing. Use 'say <expr>' or call something like io.print(...)");
        }
        // ignore returned value
    }

    private String evalToString(String expr, int line, int baseCol, String fullLine) {
        // string literal: "hello"
        String tr = expr.trim();
        if (tr.startsWith("\"") && tr.endsWith("\"") && tr.length() >= 2) {
            return unescape(tr.substring(1, tr.length() - 1));
        }

        Parser p = new Parser(expr, line, baseCol, fullLine, this);
        Value v = p.parseExprValue();
        p.skipSpaces();
        if (!p.isEnd()) {
            throw DogException.at(line, baseCol + p.pos, fullLine, "Bad expression near: " + p.rest());
        }

        if (v.kind == ValueKind.STRING)
            return v.s;
        double val = v.n;

        if (val == Math.rint(val))
            return String.valueOf((long) val);
        return String.valueOf(val);
    }

    private String unescape(String s) {
        // минимально полезные экранирования
        return s.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static int indexOfNonSpace(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            if (!Character.isWhitespace(raw.charAt(i)))
                return i;
        }
        return raw.length();
    }

    private static int findExprStartIndex(String raw, int afterSayIndex) {
        // skip spaces after 'say'
        int i = afterSayIndex;
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i)))
            i++;
        return i;
    }

    // =========================
    // Errors
    // =========================
    public static class DogException extends RuntimeException {
        public final int line;
        public final int column; // 1-based
        public final String sourceLine;

        private DogException(int line, int column, String sourceLine, String message) {
            super(message);
            this.line = line;
            this.column = column;
            this.sourceLine = sourceLine;
        }

        public static DogException at(int line, int column, String sourceLine, String message) {
            return new DogException(line, Math.max(1, column), sourceLine, message);
        }
    }

    // =========================
    // Values
    // =========================
    private enum ValueKind {
        NUMBER, STRING
    }

    private static final class Value {
        final ValueKind kind;
        final double n;
        final String s;

        private Value(double n) {
            this.kind = ValueKind.NUMBER;
            this.n = n;
            this.s = null;
        }

        private Value(String s) {
            this.kind = ValueKind.STRING;
            this.s = s;
            this.n = 0;
        }

        static Value num(double n) {
            return new Value(n);
        }

        static Value str(String s) {
            return new Value(s);
        }
    }

    // =========================
    // Parser (math + calls)
    // =========================
    private static final class Parser {
        private final String s;
        private final int line;
        private final int baseCol;
        private final String fullLine;
        private final DogInterpreter host;

        int pos = 0;
        boolean didCallFunction = false;

        Parser(String s, int line, int baseCol, String fullLine, DogInterpreter host) {
            this.s = s;
            this.line = line;
            this.baseCol = baseCol;
            this.fullLine = fullLine;
            this.host = host;
        }

        // expr -> term ((+|-) term)*
        Value parseExprValue() {
            Value v = parseTerm();
            while (true) {
                skipSpaces();
                if (match('+')) {
                    Value r = parseTerm();
                    v = add(v, r);
                } else if (match('-')) {
                    Value r = parseTerm();
                    v = sub(v, r);
                } else
                    break;
            }
            return v;
        }

        // term -> factor ((*|/) factor)*
        Value parseTerm() {
            Value v = parseFactor();
            while (true) {
                skipSpaces();
                if (match('*')) {
                    Value r = parseFactor();
                    v = mul(v, r);
                } else if (match('/')) {
                    Value r = parseFactor();
                    v = div(v, r);
                } else
                    break;
            }
            return v;
        }

        // factor -> STRING | NUMBER | (expr) | -factor | qualifiedCallOrConst
        Value parseFactor() {
            skipSpaces();

            if (match('-')) {
                Value v = parseFactor();
                if (v.kind != ValueKind.NUMBER) {
                    throw DogException.at(line, baseCol + pos, fullLine, "Unary '-' works only for numbers");
                }
                return Value.num(-v.n);
            }

            if (peek() == '"') {
                return parseString();
            }

            if (match('(')) {
                Value v = parseExprValue();
                skipSpaces();
                if (!match(')')) {
                    throw DogException.at(line, baseCol + pos, fullLine, "Missing ')'");
                }
                return v;
            }

            if (isIdentStart(peek())) {
                return parseQualified();
            }

            // number
            if (Character.isDigit(peek()) || peek() == '.') {
                return Value.num(parseNumber());
            }

            throw DogException.at(line, baseCol + pos, fullLine, "Unexpected token near: " + rest());
        }

        Value parseQualified() {
            int startPos = pos;
            String a = parseIdent();

            skipSpaces();
            if (match('.')) {
                skipSpaces();
                if (!isIdentStart(peek())) {
                    throw DogException.at(line, baseCol + pos, fullLine, "Expected identifier after '.'");
                }
                String b = parseIdent();

                skipSpaces();
                if (match('(')) {
                    // function call: a.b(...)
                    didCallFunction = true;
                    Value[] args = parseArgs();
                    requireModuleImported(a, startPos);
                    return call(a, b, args, startPos);
                } else {
                    // constant: a.b
                    requireModuleImported(a, startPos);
                    return constant(a, b, startPos);
                }
            }

            // single identifier not supported yet (variables later)
            throw DogException.at(line, baseCol + startPos, fullLine,
                    "Unknown identifier '" + a + "'. Did you mean a module call like io.print(...) or math.sqrt(...)?");
        }

        Value[] parseArgs() {
            // after '(' already consumed
            skipSpaces();
            if (match(')'))
                return new Value[0];

            java.util.ArrayList<Value> list = new java.util.ArrayList<Value>();
            while (true) {
                Value v = parseExprValue();
                list.add(v);

                skipSpaces();
                if (match(')'))
                    break;
                if (!match(',')) {
                    throw DogException.at(line, baseCol + pos, fullLine, "Expected ',' or ')' in arguments");
                }
                skipSpaces();
            }
            return list.toArray(new Value[0]);
        }

        private void requireModuleImported(String module, int moduleStartPos) {
            if (!host.imported.contains(module)) {
                throw DogException.at(line, baseCol + moduleStartPos, fullLine,
                        "Module '" + module + "' is not imported. Add: import " + module);
            }
        }

        private Value call(String module, String name, Value[] args, int callStartPos) {
            if (module.equals("io")) {
                if (name.equals("print") || name.equals("println")) {
                    // io.print / io.println accepts 1 arg (string or number)
                    if (args.length != 1) {
                        throw DogException.at(line, baseCol + callStartPos, fullLine,
                                "io." + name + "(...) expects exactly 1 argument");
                    }
                    String out = toPrintable(args[0]);
                    if (name.equals("print"))
                        System.out.print(out);
                    else
                        System.out.println(out);
                    return Value.num(0);
                }
                throw DogException.at(line, baseCol + callStartPos, fullLine,
                        "Unknown io function: " + name + ". Available: print, println");
            }

            if (module.equals("math")) {
                // math functions expect numbers
                if (name.equals("sqrt")) {
                    checkArgCount(name, args, 1, callStartPos);
                    return Value.num(Math.sqrt(requireNumber(args[0], callStartPos)));
                }
                if (name.equals("pow")) {
                    checkArgCount(name, args, 2, callStartPos);
                    return Value
                            .num(Math.pow(requireNumber(args[0], callStartPos), requireNumber(args[1], callStartPos)));
                }
                if (name.equals("abs")) {
                    checkArgCount(name, args, 1, callStartPos);
                    return Value.num(Math.abs(requireNumber(args[0], callStartPos)));
                }
                if (name.equals("floor")) {
                    checkArgCount(name, args, 1, callStartPos);
                    return Value.num(Math.floor(requireNumber(args[0], callStartPos)));
                }
                if (name.equals("ceil")) {
                    checkArgCount(name, args, 1, callStartPos);
                    return Value.num(Math.ceil(requireNumber(args[0], callStartPos)));
                }
                if (name.equals("round")) {
                    checkArgCount(name, args, 1, callStartPos);
                    return Value.num(Math.rint(requireNumber(args[0], callStartPos)));
                }

                throw DogException.at(line, baseCol + callStartPos, fullLine,
                        "Unknown math function: " + name + ". Available: sqrt, pow, abs, floor, ceil, round");
            }

            throw DogException.at(line, baseCol + callStartPos, fullLine, "Unknown module: " + module);
        }

        private Value constant(String module, String name, int startPos) {
            if (module.equals("math")) {
                if (name.equals("PI"))
                    return Value.num(Math.PI);
                if (name.equals("E"))
                    return Value.num(Math.E);
                throw DogException.at(line, baseCol + startPos, fullLine,
                        "Unknown math constant: " + name + ". Available: PI, E");
            }

            throw DogException.at(line, baseCol + startPos, fullLine,
                    "Module '" + module + "' has no constants (yet)");
        }

        private void checkArgCount(String fn, Value[] args, int expected, int startPos) {
            if (args.length != expected) {
                throw DogException.at(line, baseCol + startPos, fullLine,
                        "math." + fn + "(...) expects " + expected + " argument(s)");
            }
        }

        private double requireNumber(Value v, int startPos) {
            if (v.kind != ValueKind.NUMBER) {
                throw DogException.at(line, baseCol + startPos, fullLine, "Expected a number argument");
            }
            return v.n;
        }

        private String toPrintable(Value v) {
            if (v.kind == ValueKind.STRING)
                return v.s;
            double val = v.n;
            if (val == Math.rint(val))
                return String.valueOf((long) val);
            return String.valueOf(val);
        }

        // string literal "..."
        Value parseString() {
            int start = pos;
            if (!match('"')) {
                throw DogException.at(line, baseCol + pos, fullLine, "Expected '\"'");
            }
            StringBuilder sb = new StringBuilder();
            while (!isEnd()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return Value.str(sb.toString());
                }
                if (c == '\\' && !isEnd()) {
                    char n = s.charAt(pos++);
                    if (n == 'n')
                        sb.append('\n');
                    else if (n == 't')
                        sb.append('\t');
                    else if (n == '"')
                        sb.append('"');
                    else if (n == '\\')
                        sb.append('\\');
                    else
                        sb.append(n);
                } else {
                    sb.append(c);
                }
            }
            throw DogException.at(line, baseCol + start, fullLine, "Unterminated string literal");
        }

        double parseNumber() {
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

            if (start == pos) {
                throw DogException.at(line, baseCol + pos, fullLine, "Expected number near: " + rest());
            }

            String token = s.substring(start, pos);
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException e) {
                throw DogException.at(line, baseCol + start, fullLine, "Bad number: " + token);
            }
        }

        String parseIdent() {
            int start = pos;
            if (!isIdentStart(peek())) {
                throw DogException.at(line, baseCol + pos, fullLine, "Expected identifier");
            }
            pos++;
            while (!isEnd() && isIdentPart(s.charAt(pos)))
                pos++;
            return s.substring(start, pos);
        }

        boolean isIdentStart(char c) {
            return Character.isLetter(c) || c == '_';
        }

        boolean isIdentPart(char c) {
            return Character.isLetterOrDigit(c) || c == '_';
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

        char peek() {
            if (isEnd())
                return '\0';
            return s.charAt(pos);
        }

        boolean isEnd() {
            return pos >= s.length();
        }

        String rest() {
            return s.substring(Math.min(pos, s.length()));
        }

        // operations
        Value add(Value a, Value b) {
            if (a.kind == ValueKind.STRING || b.kind == ValueKind.STRING) {
                return Value.str(toPrintable(a) + toPrintable(b));
            }
            return Value.num(a.n + b.n);
        }

        Value sub(Value a, Value b) {
            if (a.kind != ValueKind.NUMBER || b.kind != ValueKind.NUMBER) {
                throw DogException.at(line, baseCol + pos, fullLine, "Subtraction works only for numbers");
            }
            return Value.num(a.n - b.n);
        }

        Value mul(Value a, Value b) {
            if (a.kind != ValueKind.NUMBER || b.kind != ValueKind.NUMBER) {
                throw DogException.at(line, baseCol + pos, fullLine, "Multiplication works only for numbers");
            }
            return Value.num(a.n * b.n);
        }

        Value div(Value a, Value b) {
            if (a.kind != ValueKind.NUMBER || b.kind != ValueKind.NUMBER) {
                throw DogException.at(line, baseCol + pos, fullLine, "Division works only for numbers");
            }
            return Value.num(a.n / b.n);
        }
    }
}