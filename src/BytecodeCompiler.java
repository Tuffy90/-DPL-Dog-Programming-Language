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

            // IF / ELSE
            if (trimmed.startsWith("if ")) {
                i = compileIf(lines, i, chunk);
                continue;
            }

            // import <module>
            if (trimmed.startsWith("import ")) {
                int col = indexOfNonSpace(raw) + 1;
                String mod = trimmed.substring("import ".length()).trim();
                if (mod.isEmpty()) {
                    throw DogException.at(line, col, raw, "Expected module name after import");
                }
                chunk.add(Instruction.importMod(mod, line, col, raw));
                continue;
            }

            // say <expr>
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
                p.parseExpression();
                p.finish();

                chunk.add(Instruction.simple(OpCode.PRINT, line, exprCol, raw));
                continue;
            }

            // let x = expr
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
                p.parseExpression();
                p.finish();

                chunk.add(Instruction.store(var, line, baseCol, raw));
                continue;
            }

            // x = expr (assignment without let)
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
                p.parseExpression();
                p.finish();

                chunk.add(Instruction.store(left, line, col, raw));
                continue;
            }

            // expression statement (must be call)
            compileExprStatement(raw, line, chunk);
        }

        return chunk;
    }

    // ============================================================
    // IF compiler (supports nested if, supports "} else {" same line)
    // ============================================================
    private int compileIf(List<String> lines, int i, Chunk chunk) {
        String rawIf = lines.get(i);
        int lineIf = i + 1;

        int ifIndex = rawIf.indexOf("if");
        int baseCol = (ifIndex >= 0 ? ifIndex + 1 : 1);

        // require '{' on same line (first version)
        int brace = rawIf.indexOf('{');
        if (brace < 0) {
            throw DogException.at(lineIf, baseCol, rawIf,
                    "Expected '{' after if condition (use: if cond { ... })");
        }

        String condExpr = rawIf.substring(ifIndex + 2, brace).trim();
        if (condExpr.isEmpty()) {
            throw DogException.at(lineIf, baseCol, rawIf, "Expected condition after 'if'");
        }

        // compile condition -> leaves Value on stack
        Parser p = new Parser(condExpr, lineIf, baseCol, rawIf, chunk);
        p.parseExpression();
        p.finish();

        // jump if false -> else start (patch later)
        int jFalseIndex = chunk.code().size();
        chunk.add(Instruction.jumpIfFalse(-1, lineIf, baseCol, rawIf));

        // compile THEN block
        int thenCloseLineIndex = compileThenBlock(lines, i, chunk);

        // detect ELSE:
        // 1) SAME line as then close: "} else {"
        // 2) NEXT line: "else {"
        int elseLineIndex = -1;

        String closeTrim = lines.get(thenCloseLineIndex).trim();
        if (closeTrim.startsWith("} else") || closeTrim.startsWith("else")) {
            elseLineIndex = thenCloseLineIndex;
        } else if (thenCloseLineIndex + 1 < lines.size()) {
            String nextTrim = lines.get(thenCloseLineIndex + 1).trim();
            if (nextTrim.startsWith("else") || nextTrim.startsWith("} else")) {
                elseLineIndex = thenCloseLineIndex + 1;
            }
        }

        if (elseLineIndex != -1) {
            // jump over else (patch later)
            int jEndIndex = chunk.code().size();
            chunk.add(Instruction.jump(-1, lineIf, baseCol, rawIf));

            // patch false jump -> else start (right after the JUMP we just emitted)
            chunk.code().get(jFalseIndex).jumpTarget = chunk.code().size();

            // compile ELSE block
            int elseCloseLineIndex = compileElseBlock(lines, elseLineIndex, chunk);

            // patch end jump -> after else
            chunk.code().get(jEndIndex).jumpTarget = chunk.code().size();

            return elseCloseLineIndex;
        }

        // no else: patch false jump -> after then
        chunk.code().get(jFalseIndex).jumpTarget = chunk.code().size();
        return thenCloseLineIndex;
    }

    // compile THEN block starting from "if ... {" line index
    // returns line index where THEN closes (line containing closing '}' maybe with
    // "else")
    private int compileThenBlock(List<String> lines, int ifLineIndex, Chunk chunk) {
        int depth = 0;

        for (int k = ifLineIndex; k < lines.size(); k++) {
            String raw = lines.get(k);
            String tr = raw.trim();

            if (k == ifLineIndex) {
                // header line "if ... {"
                depth += countChar(raw, '{');
                // ignore '}' on header
                continue;
            }

            // IMPORTANT:
            // In THEN block, if we meet "} else {", this line closes THEN.
            // Do NOT treat "else {" as inside THEN.
            if (tr.startsWith("} else") || tr.equals("} else {")) {
                // this line closes THEN
                depth -= 1; // the leading '}'
                if (depth == 0)
                    return k;
                // if depth not zero -> it means nested close, still return when 0
                continue;
            }

            depth += countChar(raw, '{');
            depth -= countChar(raw, '}');

            if (depth == 0) {
                // closing line of THEN (a plain "}")
                return k;
            }

            if (tr.isEmpty() || tr.startsWith("#"))
                continue;
            if (tr.equals("{") || tr.equals("}"))
                continue;
            if (tr.startsWith("}"))
                continue;

            // nested if
            if (tr.startsWith("if ")) {
                k = compileIf(lines, k, chunk);
                continue;
            }

            compileSingleLine(raw, k + 1, chunk);
        }

        throw DogException.at(ifLineIndex + 1, 1, lines.get(ifLineIndex), "Unclosed block: missing '}'");
    }

    // compile ELSE block starting from either "else {" or "} else {"
    // returns line index where ELSE closes
    private int compileElseBlock(List<String> lines, int elseLineIndex, Chunk chunk) {
        String rawElse = lines.get(elseLineIndex);

        int elsePos = rawElse.indexOf("else");
        if (elsePos < 0) {
            throw DogException.at(elseLineIndex + 1, 1, rawElse, "Expected 'else'");
        }

        int brace = rawElse.indexOf('{', elsePos);
        if (brace < 0) {
            throw DogException.at(elseLineIndex + 1, 1, rawElse, "Expected '{' after else");
        }

        // ELSE depth starts at 1 because we saw '{' after else
        int depth = 1;

        for (int k = elseLineIndex + 1; k < lines.size(); k++) {
            String raw = lines.get(k);
            String t = raw.trim();

            depth += countChar(raw, '{');
            depth -= countChar(raw, '}');

            if (depth == 0) {
                return k; // closing else "}"
            }

            if (t.isEmpty() || t.startsWith("#"))
                continue;
            if (t.equals("{") || t.equals("}"))
                continue;
            if (t.startsWith("}"))
                continue;

            if (t.startsWith("if ")) {
                k = compileIf(lines, k, chunk);
                continue;
            }

            compileSingleLine(raw, k + 1, chunk);
        }

        throw DogException.at(elseLineIndex + 1, 1, rawElse, "Unclosed else block: missing '}'");
    }

    // ============================================================
    // One-line compilation (used both at top-level and inside blocks)
    // ============================================================
    private void compileSingleLine(String raw, int line, Chunk chunk) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#"))
            return;

        if (trimmed.startsWith("if ")) {
            // allow if inside blocks
            // NOTE: we can’t advance outer loop index here, caller handles it.
            throw DogException.at(line, 1, raw, "Internal error: compileSingleLine called with 'if'.");
        }

        if (trimmed.startsWith("import ")) {
            int col = indexOfNonSpace(raw) + 1;
            String mod = trimmed.substring("import ".length()).trim();
            if (mod.isEmpty())
                throw DogException.at(line, col, raw, "Expected module name after import");
            chunk.add(Instruction.importMod(mod, line, col, raw));
            return;
        }

        if (trimmed.startsWith("say")) {
            int sayPos = raw.indexOf("say");
            int after = sayPos + 3;
            if (raw.substring(after).trim().isEmpty())
                throw DogException.at(line, after + 1, raw, "Expected expression after 'say'");

            int exprStart = findExprStartIndex(raw, after);
            int exprCol = exprStart + 1;
            String expr = raw.substring(exprStart);

            Parser p = new Parser(expr, line, exprCol, raw, chunk);
            p.parseExpression();
            p.finish();

            chunk.add(Instruction.simple(OpCode.PRINT, line, exprCol, raw));
            return;
        }

        if (trimmed.startsWith("let ")) {
            int letPos = raw.indexOf("let");
            int baseCol = letPos + 1;

            String rest = trimmed.substring(4).trim();
            int eq = rest.indexOf('=');
            if (eq < 0)
                throw DogException.at(line, baseCol, raw, "Expected '=' in let statement");

            String var = rest.substring(0, eq).trim();
            String expr = rest.substring(eq + 1).trim();

            if (!isIdent(var))
                throw DogException.at(line, baseCol, raw, "Bad variable name: " + var);
            if (expr.isEmpty())
                throw DogException.at(line, baseCol, raw, "Expected expression after '='");

            Parser p = new Parser(expr, line, baseCol, raw, chunk);
            p.parseExpression();
            p.finish();

            chunk.add(Instruction.store(var, line, baseCol, raw));
            return;
        }

        int assignPos = findTopLevelAssign(trimmed);
        if (assignPos >= 0) {
            String left = trimmed.substring(0, assignPos).trim();
            String right = trimmed.substring(assignPos + 1).trim();
            int col = indexOfNonSpace(raw) + 1;

            if (!isIdent(left))
                throw DogException.at(line, col, raw, "Bad assignment target: " + left);
            if (right.isEmpty())
                throw DogException.at(line, col, raw, "Expected expression after '='");

            Parser p = new Parser(right, line, col, raw, chunk);
            p.parseExpression();
            p.finish();

            chunk.add(Instruction.store(left, line, col, raw));
            return;
        }

        compileExprStatement(raw, line, chunk);
    }

    private void compileExprStatement(String raw, int line, Chunk chunk) {
        int exprStart = indexOfNonSpace(raw);
        int exprCol = exprStart + 1;
        String expr = raw.substring(exprStart);

        Parser p = new Parser(expr, line, exprCol, raw, chunk);
        p.parseExpression();
        p.finish();

        if (!p.didCall) {
            throw DogException.at(line, exprCol, raw,
                    "This line does nothing. Use 'say <expr>' or call something like io.print(...)");
        }
        chunk.add(Instruction.simple(OpCode.POP, line, exprCol, raw));
    }

    // ============================================================
    // Helpers
    // ============================================================
    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) == c)
                n++;
        return n;
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
        for (int i = 0; i < raw.length(); i++)
            if (!Character.isWhitespace(raw.charAt(i)))
                return i;
        return raw.length();
    }

    private static int findExprStartIndex(String raw, int afterIndex) {
        int i = afterIndex;
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i)))
            i++;
        return i;
    }

    // "x = y" but NOT "==" and NOT "!=" "<=" ">=" "<>"
    private static int findTopLevelAssign(String trimmed) {
        int eq = trimmed.indexOf('=');
        if (eq < 0)
            return -1;
        if (eq + 1 < trimmed.length() && trimmed.charAt(eq + 1) == '=')
            return -1;

        if (eq - 1 >= 0) {
            char prev = trimmed.charAt(eq - 1);
            if (prev == '!' || prev == '<' || prev == '>')
                return -1;
        }
        return eq;
    }

    // ============================================================
    // Expression Parser
    // ============================================================
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

        void parseExpression() {
            parseEquality();
        }

        void parseEquality() {
            parseCompare();
            while (true) {
                skipSpaces();
                if (match2("==")) {
                    parseCompare();
                    emit(OpCode.EQ);
                } else if (match2("!=") || match2("<>")) {
                    parseCompare();
                    emit(OpCode.NEQ);
                } else {
                    break;
                }
            }
        }

        void parseCompare() {
            parseAdd();
            while (true) {
                skipSpaces();

                // IMPORTANT: prevent "<>" being parsed as "<" then ">"
                if (peek() == '<' && peekNext() == '>')
                    break;

                if (match2(">=")) {
                    parseAdd();
                    emit(OpCode.GE);
                } else if (match2("<=")) {
                    parseAdd();
                    emit(OpCode.LE);
                } else if (match('>')) {
                    parseAdd();
                    emit(OpCode.GT);
                } else if (match('<')) {
                    parseAdd();
                    emit(OpCode.LT);
                } else {
                    break;
                }
            }
        }

        void parseAdd() {
            parseMul();
            while (true) {
                skipSpaces();
                if (match('+')) {
                    parseMul();
                    emit(OpCode.ADD);
                } else if (match('-')) {
                    parseMul();
                    emit(OpCode.SUB);
                } else
                    break;
            }
        }

        void parseMul() {
            parseUnary();
            while (true) {
                skipSpaces();
                if (match('*')) {
                    parseUnary();
                    emit(OpCode.MUL);
                } else if (match('/')) {
                    parseUnary();
                    emit(OpCode.DIV);
                } else
                    break;
            }
        }

        void parseUnary() {
            skipSpaces();

            if (match('!')) {
                parseUnary();
                emit(OpCode.NOT);
                return;
            }

            if (match('-')) {
                int start = pos;
                out.add(Instruction.constNum(-1.0, line, baseCol + start, fullLine));
                parseUnary();
                emit(OpCode.MUL);
                return;
            }

            parsePrimary();
        }

        void parsePrimary() {
            skipSpaces();

            if (peek() == '"') {
                int start = pos;
                String str = parseString();
                out.add(Instruction.constStr(str, line, baseCol + start, fullLine));
                return;
            }

            if (match('(')) {
                parseExpression();
                skipSpaces();
                if (!match(')'))
                    throw err("Missing ')'");
                return;
            }

            if (Character.isDigit(peek()) || peek() == '.') {
                int start = pos;
                double n = parseNumber();
                out.add(Instruction.constNum(n, line, baseCol + start, fullLine));
                return;
            }

            if (isIdentStart(peek())) {
                parseIdentOrQualifiedOrKeyword();
                return;
            }

            throw err("Unexpected token near: " + rest());
        }

        void parseIdentOrQualifiedOrKeyword() {
            int start = pos;
            String a = parseIdent();

            if (a.equals("true")) {
                out.add(Instruction.constBool(true, line, baseCol + start, fullLine));
                return;
            }
            if (a.equals("false")) {
                out.add(Instruction.constBool(false, line, baseCol + start, fullLine));
                return;
            }
            if (a.equals("nil")) {
                out.add(Instruction.constNil(line, baseCol + start, fullLine));
                return;
            }

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
                parseExpression();
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

        boolean match2(String two) {
            if (pos + 1 >= s.length())
                return false;
            if (s.charAt(pos) == two.charAt(0) && s.charAt(pos + 1) == two.charAt(1)) {
                pos += 2;
                return true;
            }
            return false;
        }

        char peek() {
            return isEnd() ? '\0' : s.charAt(pos);
        }

        char peekNext() {
            return (pos + 1 >= s.length()) ? '\0' : s.charAt(pos + 1);
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