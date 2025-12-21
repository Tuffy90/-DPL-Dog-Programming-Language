import java.util.List;

public final class BytecodeCompiler {

    public Chunk compile(List<String> lines) {
        Chunk chunk = new Chunk();

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            int line = i + 1;
            String trimmed = raw.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#"))
                continue;

            if (trimmed.startsWith("import ")) {
                int col = indexOfNonSpace(raw) + 1;
                String mod = trimmed.substring("import ".length()).trim();
                if (mod.isEmpty()) {
                    throw DogException.at(line, col, raw, "Expected module name after import");
                }
                chunk.add(Instruction.importMod(mod, line, col, raw));
                continue;
            }

            if (trimmed.startsWith("say")) {
                int sayPos = raw.indexOf("say");
                int after = sayPos + 3;
                if (raw.substring(after).trim().isEmpty()) {
                    throw DogException.at(line, after + 1, raw, "Expected expression after 'say'");
                }
                int exprStart = findExprStartIndex(raw, after);
                int exprCol = exprStart + 1;
                String expr = raw.substring(exprStart);
                Parser p = new Parser(expr, line, exprCol, raw, chunk);
                p.parseExpr();
                p.finish();
                chunk.add(Instruction.simple(OpCode.PRINT, line, exprCol, raw));
                continue;
            }

            if (trimmed.startsWith("let ")) {
                int letPos = raw.indexOf("let");
                int baseCol = letPos + 1;

                String rest = trimmed.substring(4).trim();
                int eq = rest.indexOf('=');
                if (eq < 0) {
                    throw DogException.at(line, baseCol, raw, "Expected '=' in let statement");
                }

                String var = rest.substring(0, eq).trim();
                String expr = rest.substring(eq + 1).trim();

                if (!isIdent(var)) {
                    throw DogException.at(line, baseCol, raw, "Bad variable name: " + var);
                }
                if (expr.isEmpty()) {
                    throw DogException.at(line, baseCol, raw, "Expected expression after '='");
                }

                Parser p = new Parser(expr, line, baseCol, raw, chunk);
                p.parseExpr();
                p.finish();

                chunk.add(Instruction.store(var, line, baseCol, raw));
                continue;
            }

            int assignPos = findTopLevelAssign(trimmed);
            if (assignPos >= 0) {
                String left = trimmed.substring(0, assignPos).trim();
                String right = trimmed.substring(assignPos + 1).trim();
                int col = indexOfNonSpace(raw) + 1;

                if (!isIdent(left)) {
                    throw DogException.at(line, col, raw, "Bad assignment target: " + left);
                }
                if (right.isEmpty()) {
                    throw DogException.at(line, col, raw, "Expected expression after '='");
                }

                Parser p = new Parser(right, line, col, raw, chunk);
                p.parseExpr();
                p.finish();

                chunk.add(Instruction.store(left, line, col, raw));
                continue;
            }

            int exprStart = indexOfNonSpace(raw);
            int exprCol = exprStart + 1;
            String expr = raw.substring(exprStart);

            Parser p = new Parser(expr, line, exprCol, raw, chunk);
            p.parseExpr();
            p.finish();

            if (!p.didCall) {
                throw DogException.at(line, exprCol, raw,
                        "This line does nothing. Use 'say <expr>' or call something like io.print(...)");
            }
            chunk.add(Instruction.simple(OpCode.POP, line, exprCol, raw));
        }

        return chunk;
    }

    private static boolean isIdent(String s) {
        if (s == null || s.isEmpty())
            return false;
        char c0 = s.charAt(0);
        if (!(Character.isLetter(c0) || c0 == '_'))
            return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_'))
                return false;
        }
        return true;
    }

    private static int indexOfNonSpace(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            if (!Character.isWhitespace(raw.charAt(i)))
                return i;
        }
        return raw.length();
    }

    private static int findExprStartIndex(String raw, int afterIndex) {
        int i = afterIndex;
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i)))
            i++;
        return i;
    }

    private static int findTopLevelAssign(String trimmed) {
        int eq = trimmed.indexOf('=');
        if (eq < 0)
            return -1;
        if (eq + 1 < trimmed.length() && trimmed.charAt(eq + 1) == '=')
            return -1;
        return eq;
    }

    private static final class Parser {
        private final String s;
        private final int line;
        private final int baseCol;
        private final String fullLine;
        private final Chunk out;

        int pos = 0;
        boolean didCall = false;

        Parser(String s, int line, int baseCol, String fullLine, Chunk out) {
            this.s = s;
            this.line = line;
            this.baseCol = baseCol;
            this.fullLine = fullLine;
            this.out = out;
        }

        void parseExpr() {
            parseTerm();
            while (true) {
                skipSpaces();
                if (match('+')) {
                    parseTerm();
                    emit(OpCode.ADD);
                } else if (match('-')) {
                    parseTerm();
                    emit(OpCode.SUB);
                } else
                    break;
            }
        }

        void parseTerm() {
            parseFactor();
            while (true) {
                skipSpaces();
                if (match('*')) {
                    parseFactor();
                    emit(OpCode.MUL);
                } else if (match('/')) {
                    parseFactor();
                    emit(OpCode.DIV);
                } else
                    break;
            }
        }

        void parseFactor() {
            skipSpaces();

            if (match('-')) {
                out.add(Instruction.constNum(-1.0, line, baseCol + pos, fullLine));
                parseFactor();
                emit(OpCode.MUL);
                return;
            }

            if (peek() == '"') {
                String str = parseString();
                out.add(Instruction.constStr(str, line, baseCol + pos, fullLine));
                return;
            }

            if (match('(')) {
                parseExpr();
                skipSpaces();
                if (!match(')')) {
                    throw err("Missing ')'");
                }
                return;
            }

            if (Character.isDigit(peek()) || peek() == '.') {
                double n = parseNumber();
                out.add(Instruction.constNum(n, line, baseCol + pos, fullLine));
                return;
            }

            if (isIdentStart(peek())) {
                parseIdentOrQualified();
                return;
            }

            throw err("Unexpected token near: " + rest());
        }

        void parseIdentOrQualified() {
            int start = pos;
            String a = parseIdent();
            skipSpaces();

            if (match('.')) {
                skipSpaces();
                if (!isIdentStart(peek()))
                    throw err("Expected identifier after '.'");
                String b = parseIdent();
                skipSpaces();

                if (match('(')) {
                    didCall = true;
                    int argCount = parseArgs();
                    out.add(Instruction.call(a, b, argCount, false, line, baseCol + start, fullLine));
                    return;
                }

                out.add(Instruction.call(a, b, 0, true, line, baseCol + start, fullLine));
                return;
            }

            out.add(Instruction.load(a, line, baseCol + start, fullLine));
        }

        int parseArgs() {
            skipSpaces();
            if (match(')'))
                return 0;

            int count = 0;
            while (true) {
                parseExpr();
                count++;
                skipSpaces();
                if (match(')'))
                    break;
                if (!match(','))
                    throw err("Expected ',' or ')' in arguments");
                skipSpaces();
            }
            return count;
        }

        String parseString() {
            if (!match('"'))
                throw err("Expected '\"'");
            StringBuilder sb = new StringBuilder();
            while (!isEnd()) {
                char c = s.charAt(pos++);
                if (c == '"')
                    return sb.toString();

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
            throw err("Unterminated string literal");
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
            String token = s.substring(start, pos);
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException e) {
                throw err("Bad number: " + token);
            }
        }

        String parseIdent() {
            int start = pos;
            if (!isIdentStart(peek()))
                throw err("Expected identifier");
            pos++;
            while (!isEnd() && isIdentPart(s.charAt(pos)))
                pos++;
            return s.substring(start, pos);
        }

        void finish() {
            skipSpaces();
            if (!isEnd())
                throw err("Bad expression near: " + rest());
        }

        void emit(OpCode op) {
            out.add(Instruction.simple(op, line, baseCol + pos, fullLine));
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

        boolean isIdentStart(char c) {
            return Character.isLetter(c) || c == '_';
        }

        boolean isIdentPart(char c) {
            return Character.isLetterOrDigit(c) || c == '_';
        }

        DogException err(String msg) {
            return DogException.at(line, baseCol + pos, fullLine, msg);
        }
    }
}