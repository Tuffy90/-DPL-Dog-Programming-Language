import java.util.ArrayList;
import java.util.List;

public class DogInterpreter {

    public void run(List<String> lines, DogContext ctx) {
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            int lineNumber = i + 1;

            String trimmed = raw.trim();
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
                ctx.importModule(module, lineNumber, moduleStartCol, raw);
                continue;
            }

            // say <expr>
            if (trimmed.startsWith("say")) {
                int sayPos = raw.indexOf("say");
                int afterSay = sayPos + 3;

                String rest = raw.substring(afterSay);
                if (rest.trim().isEmpty()) {
                    throw DogException.at(lineNumber, afterSay + 1, raw, "Expected expression after 'say'");
                }

                int exprIndex0 = findExprStartIndex(raw, afterSay);
                int exprBaseCol = exprIndex0 + 1;

                String expr = raw.substring(exprIndex0).trim();
                String out = evalToString(expr, ctx, lineNumber, exprBaseCol, raw);
                System.out.println(out);
                continue;
            }

            // expression statement
            int exprStart = indexOfNonSpace(raw);
            int exprBaseCol = exprStart + 1;
            String exprStmt = raw.substring(exprStart);

            Parser p = new Parser(exprStmt, ctx, lineNumber, exprBaseCol, raw);
            Value v = p.parseExprValue();
            p.skipSpaces();

            if (!p.isEnd()) {
                throw DogException.at(lineNumber, exprBaseCol + p.pos, raw, "Unexpected token near: " + p.rest());
            }
            if (!p.didCallFunction) {
                throw DogException.at(lineNumber, exprBaseCol, raw,
                        "This line does nothing. Use 'say <expr>' or call something like io.print(...)");
            }
        }
    }

    private String evalToString(String expr, DogContext ctx, int line, int baseCol, String fullLine) {
        String tr = expr.trim();
        if (tr.startsWith("\"") && tr.endsWith("\"") && tr.length() >= 2) {
            return unescape(tr.substring(1, tr.length() - 1));
        }

        Parser p = new Parser(expr, ctx, line, baseCol, fullLine);
        Value v = p.parseExprValue();
        p.skipSpaces();

        if (!p.isEnd()) {
            throw DogException.at(line, baseCol + p.pos, fullLine, "Bad expression near: " + p.rest());
        }

        return v.printable();
    }

    private String unescape(String s) {
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
        int i = afterSayIndex;
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i)))
            i++;
        return i;
    }

    private static final class Parser {
        private final String s;
        private final DogContext ctx;
        private final int line;
        private final int baseCol;
        private final String fullLine;

        int pos = 0;
        boolean didCallFunction = false;

        Parser(String s, DogContext ctx, int line, int baseCol, String fullLine) {
            this.s = s;
            this.ctx = ctx;
            this.line = line;
            this.baseCol = baseCol;
            this.fullLine = fullLine;
        }

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

        Value parseFactor() {
            skipSpaces();

            if (match('-')) {
                Value v = parseFactor();
                if (!v.isNumber()) {
                    throw DogException.at(line, baseCol + pos, fullLine, "Unary '-' works only for numbers");
                }
                return Value.num(-v.number);
            }

            if (peek() == '"')
                return parseString();

            if (match('(')) {
                Value v = parseExprValue();
                skipSpaces();
                if (!match(')'))
                    throw DogException.at(line, baseCol + pos, fullLine, "Missing ')'");
                return v;
            }

            if (isIdentStart(peek()))
                return parseQualified();

            if (Character.isDigit(peek()) || peek() == '.')
                return Value.num(parseNumber());

            throw DogException.at(line, baseCol + pos, fullLine, "Unexpected token near: " + rest());
        }

        Value parseQualified() {
            int startPos = pos;
            String module = parseIdent();

            skipSpaces();
            if (!match('.')) {
                throw DogException.at(line, baseCol + startPos, fullLine,
                        "Unknown identifier '" + module + "'. Use module.member(...) like io.println(...)");
            }

            skipSpaces();
            if (!isIdentStart(peek())) {
                throw DogException.at(line, baseCol + pos, fullLine, "Expected identifier after '.'");
            }
            String member = parseIdent();

            ctx.requireImported(module, line, baseCol + startPos, fullLine);

            DogModule m = ctx.registry().get(module);
            if (m == null) {
                throw DogException.at(line, baseCol + startPos, fullLine, "Unknown module: " + module);
            }

            skipSpaces();
            if (match('(')) {
                didCallFunction = true;
                List<Value> args = parseArgs();
                return m.call(member, args, ctx, line, baseCol + startPos, fullLine);
            }
            return m.getConstant(member, ctx, line, baseCol + startPos, fullLine);
        }

        List<Value> parseArgs() {
            skipSpaces();
            if (match(')'))
                return new ArrayList<Value>();

            ArrayList<Value> list = new ArrayList<Value>();
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
            return list;
        }

        Value parseString() {
            int start = pos;
            if (!match('"'))
                throw DogException.at(line, baseCol + pos, fullLine, "Expected '\"'");
            StringBuilder sb = new StringBuilder();
            while (!isEnd()) {
                char c = s.charAt(pos++);
                if (c == '"')
                    return Value.str(sb.toString());

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
                } else
                    sb.append(c);
            }
            throw DogException.at(line, baseCol + start, fullLine, "Unterminated string literal");
        }

        double parseNumber() {
            int start = pos;
            boolean dot = false;
            while (!isEnd()) {
                char c = s.charAt(pos);
                if (Character.isDigit(c))
                    pos++;
                else if (c == '.' && !dot) {
                    dot = true;
                    pos++;
                } else
                    break;
            }
            if (start == pos)
                throw DogException.at(line, baseCol + pos, fullLine, "Expected number near: " + rest());

            String token = s.substring(start, pos);
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException e) {
                throw DogException.at(line, baseCol + start, fullLine, "Bad number: " + token);
            }
        }

        String parseIdent() {
            int start = pos;
            if (!isIdentStart(peek()))
                throw DogException.at(line, baseCol + pos, fullLine, "Expected identifier");
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
            return isEnd() ? '\0' : s.charAt(pos);
        }

        boolean isEnd() {
            return pos >= s.length();
        }

        String rest() {
            return s.substring(Math.min(pos, s.length()));
        }

        // ops
        Value add(Value a, Value b) {
            if (a.isString() || b.isString())
                return Value.str(a.printable() + b.printable());
            return Value.num(a.number + b.number);
        }

        Value sub(Value a, Value b) {
            if (!a.isNumber() || !b.isNumber())
                throw DogException.at(line, baseCol + pos, fullLine, "Subtraction works only for numbers");
            return Value.num(a.number - b.number);
        }

        Value mul(Value a, Value b) {
            if (!a.isNumber() || !b.isNumber())
                throw DogException.at(line, baseCol + pos, fullLine, "Multiplication works only for numbers");
            return Value.num(a.number * b.number);
        }

        Value div(Value a, Value b) {
            if (!a.isNumber() || !b.isNumber())
                throw DogException.at(line, baseCol + pos, fullLine, "Division works only for numbers");
            return Value.num(a.number / b.number);
        }
    }
}
