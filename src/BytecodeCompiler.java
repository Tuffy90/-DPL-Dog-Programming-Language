import java.util.ArrayList;
import java.util.List;

public final class BytecodeCompiler {

    public Chunk compile(List<String> lines) {
        Chunk chunk = new Chunk();

        for (int i = 0; i < lines.size(); i++) {
            String raw0 = lines.get(i);
            int line0 = i + 1;

            String t0 = raw0.trim();
            if (t0.isEmpty() || t0.startsWith("#"))
                continue;

            // IF / ELSE
            if (t0.startsWith("if ")) {
                i = compileIf(lines, i, chunk);
                continue;
            }

            // WHILE
            if (t0.startsWith("while ")) {
                i = compileWhile(lines, i, chunk);
                continue;
            }

            // FUNCTION
            if (t0.startsWith("fn ")) {
                i = compileFn(lines, i, chunk);
                continue;
            }

            LogicalLine L = readLogicalLine(lines, i);
            i = L.endIndex;

            compileSingleLine(L.raw, line0, chunk);
        }

        return chunk;
    }

    // ============================================================
    // IF / ELSE
    // ============================================================
    private int compileIf(List<String> lines, int i, Chunk chunk) {
        String rawIf = lines.get(i);
        int lineIf = i + 1;

        int ifIndex = rawIf.indexOf("if");
        int baseCol = (ifIndex >= 0 ? ifIndex + 1 : 1);

        int brace = rawIf.indexOf('{');
        if (brace < 0) {
            throw DogException.at(lineIf, baseCol, rawIf,
                    "Expected '{' after if condition (use: if cond { ... })");
        }

        if (stripInlineComment(rawIf.substring(brace + 1)).trim().length() > 0) {
            throw DogException.at(lineIf, baseCol, rawIf,
                    "Put '{' at end of line. Body must be on next lines.");
        }

        String condExpr = rawIf.substring(ifIndex + 2, brace).trim();
        if (condExpr.isEmpty()) {
            throw DogException.at(lineIf, baseCol, rawIf, "Expected condition after 'if'");
        }

        Parser p = new Parser(condExpr, lineIf, baseCol, rawIf, chunk);
        p.parseExpression();
        p.finish();

        int jFalseIndex = chunk.code().size();
        chunk.add(Instruction.jumpIfFalse(-1, lineIf, baseCol, rawIf));

        int thenCloseLineIndex = compileThenBlock(lines, i, chunk);

        int elseLineIndex = -1;

        String closeTrim = lines.get(thenCloseLineIndex).trim();
        if (closeTrim.startsWith("} else")) {
            elseLineIndex = thenCloseLineIndex;
        } else if (thenCloseLineIndex + 1 < lines.size()) {
            String nextTrim = lines.get(thenCloseLineIndex + 1).trim();
            if (nextTrim.startsWith("else") || nextTrim.startsWith("} else")) {
                elseLineIndex = thenCloseLineIndex + 1;
            }
        }

        if (elseLineIndex != -1) {
            int jEndIndex = chunk.code().size();
            chunk.add(Instruction.jump(-1, lineIf, baseCol, rawIf));

            chunk.code().get(jFalseIndex).jumpTarget = chunk.code().size();

            int elseCloseLineIndex = compileElseBlock(lines, elseLineIndex, chunk);

            chunk.code().get(jEndIndex).jumpTarget = chunk.code().size();

            return elseCloseLineIndex;
        }

        chunk.code().get(jFalseIndex).jumpTarget = chunk.code().size();
        return thenCloseLineIndex;
    }

    private int compileThenBlock(List<String> lines, int ifLineIndex, Chunk chunk) {
        int depth = 0;

        for (int k = ifLineIndex; k < lines.size(); k++) {
            String raw = lines.get(k);
            String tr = raw.trim();

            if (k == ifLineIndex) {
                depth += braceDelta(raw);
                continue;
            }

            if (tr.startsWith("} else")) {
                depth -= 1;
                if (depth == 0)
                    return k;
                continue;
            }

            depth += braceDelta(raw);
            if (depth == 0)
                return k;

            if (tr.isEmpty() || tr.startsWith("#"))
                continue;
            if (tr.equals("{") || tr.equals("}"))
                continue;
            if (tr.startsWith("}"))
                continue;

            if (tr.startsWith("if ")) {
                int end = compileIf(lines, k, chunk);
                depth += braceDeltaRange(lines, k, end);
                k = end;
                continue;
            }
            if (tr.startsWith("while ")) {
                int end = compileWhile(lines, k, chunk);
                depth += braceDeltaRange(lines, k, end);
                k = end;
                continue;
            }
            if (tr.startsWith("fn ")) {
                int end = compileFn(lines, k, chunk);
                depth += braceDeltaRange(lines, k, end);
                k = end;
                continue;
            }

            LogicalLine L = readLogicalLine(lines, k);
            compileSingleLine(L.raw, k + 1, chunk);
            k = L.endIndex;
        }

        throw DogException.at(ifLineIndex + 1, 1, lines.get(ifLineIndex), "Unclosed block: missing '}'");
    }

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

        if (stripInlineComment(rawElse.substring(brace + 1)).trim().length() > 0) {
            throw DogException.at(elseLineIndex + 1, 1, rawElse,
                    "Put '{' at end of line. Else body must be on next lines.");
        }

        int depth = 1;

        for (int k = elseLineIndex + 1; k < lines.size(); k++) {
            String raw = lines.get(k);
            String t = raw.trim();

            depth += braceDelta(raw);
            if (depth == 0)
                return k;

            if (t.isEmpty() || t.startsWith("#"))
                continue;
            if (t.equals("{") || t.equals("}"))
                continue;
            if (t.startsWith("}"))
                continue;

            if (t.startsWith("if ")) {
                int end = compileIf(lines, k, chunk);
                depth += braceDeltaRange(lines, k, end);
                k = end;
                continue;
            }
            if (t.startsWith("while ")) {
                int end = compileWhile(lines, k, chunk);
                depth += braceDeltaRange(lines, k, end);
                k = end;
                continue;
            }
            if (t.startsWith("fn ")) {
                int end = compileFn(lines, k, chunk);
                depth += braceDeltaRange(lines, k, end);
                k = end;
                continue;
            }

            LogicalLine L = readLogicalLine(lines, k);
            compileSingleLine(L.raw, k + 1, chunk);
            k = L.endIndex;
        }

        throw DogException.at(elseLineIndex + 1, 1, rawElse, "Unclosed else block: missing '}'");
    }

    // ============================================================
    // WHILE
    // ============================================================
    private int compileWhile(List<String> lines, int i, Chunk chunk) {
        String rawWhile = lines.get(i);
        int lineWhile = i + 1;

        int whIndex = rawWhile.indexOf("while");
        int baseCol = (whIndex >= 0 ? whIndex + 1 : 1);

        int brace = rawWhile.indexOf('{');
        if (brace < 0) {
            throw DogException.at(lineWhile, baseCol, rawWhile,
                    "Expected '{' after while condition (use: while cond { ... })");
        }

        if (stripInlineComment(rawWhile.substring(brace + 1)).trim().length() > 0) {
            throw DogException.at(lineWhile, baseCol, rawWhile,
                    "Put '{' at end of line. Body must be on next lines.");
        }

        String condExpr = rawWhile.substring(whIndex + "while".length(), brace).trim();
        if (condExpr.isEmpty()) {
            throw DogException.at(lineWhile, baseCol, rawWhile, "Expected condition after 'while'");
        }

        int loopStartIp = chunk.code().size();

        Parser p = new Parser(condExpr, lineWhile, baseCol, rawWhile, chunk);
        p.parseExpression();
        p.finish();

        int jFalseIndex = chunk.code().size();
        chunk.add(Instruction.jumpIfFalse(-1, lineWhile, baseCol, rawWhile));

        int closeLine = compileWhileBlock(lines, i, chunk);

        chunk.add(Instruction.jump(loopStartIp, lineWhile, baseCol, rawWhile));
        chunk.code().get(jFalseIndex).jumpTarget = chunk.code().size();

        return closeLine;
    }

    private int compileWhileBlock(List<String> lines, int whileLineIndex, Chunk chunk) {
        int depth = 0;

        for (int k = whileLineIndex; k < lines.size(); k++) {
            String raw = lines.get(k);
            String tr = raw.trim();

            depth += braceDelta(raw);

            if (k == whileLineIndex) {
                continue;
            }

            if (depth == 0)
                return k;

            if (tr.isEmpty() || tr.startsWith("#"))
                continue;
            if (tr.equals("{") || tr.equals("}"))
                continue;
            if (tr.startsWith("}"))
                continue;

            if (tr.startsWith("if ")) {
                int end = compileIf(lines, k, chunk);
                depth += braceDeltaRange(lines, k, end);
                k = end;
                continue;
            }
            if (tr.startsWith("while ")) {
                int end = compileWhile(lines, k, chunk);
                depth += braceDeltaRange(lines, k, end);
                k = end;
                continue;
            }
            if (tr.startsWith("fn ")) {
                int end = compileFn(lines, k, chunk);
                depth += braceDeltaRange(lines, k, end);
                k = end;
                continue;
            }

            LogicalLine L = readLogicalLine(lines, k);
            compileSingleLine(L.raw, k + 1, chunk);
            k = L.endIndex;
        }

        throw DogException.at(whileLineIndex + 1, 1, lines.get(whileLineIndex), "Unclosed while block: missing '}'");
    }

    // ============================================================
    // FUNCTION: fn name(a,b) { ... }
    // ============================================================
   private int compileFn(List<String> lines, int i, Chunk chunk) {
    String rawFn = lines.get(i);
    int lineFn = i + 1;

    int fnPos = rawFn.indexOf("fn");
    int baseCol = (fnPos >= 0 ? fnPos + 1 : 1);

    int brace = rawFn.indexOf('{');
    if (brace < 0) {
        throw DogException.at(lineFn, baseCol, rawFn, "Expected '{' after fn header");
    }
    if (stripInlineComment(rawFn.substring(brace + 1)).trim().length() > 0) {
        throw DogException.at(lineFn, baseCol, rawFn, "Put '{' at end of line. Body must be on next lines.");
    }

    String header = rawFn.substring(fnPos + 2, brace).trim(); // after "fn"
    int lp = header.indexOf('(');
    int rp = header.lastIndexOf(')');
    if (lp < 0 || rp < 0 || rp < lp) {
        throw DogException.at(lineFn, baseCol, rawFn, "Bad fn header. Use: fn name(a,b) {");
    }

    String name = header.substring(0, lp).trim();
    String paramsText = header.substring(lp + 1, rp).trim();

    if (!isIdent(name)) {
        throw DogException.at(lineFn, baseCol, rawFn, "Bad function name: " + name);
    }

    ArrayList<String> params = new ArrayList<>();
    if (!paramsText.isEmpty()) {
        String[] parts = paramsText.split(",");
        for (String p : parts) {
            String id = p.trim();
            if (!isIdent(id)) {
                throw DogException.at(lineFn, baseCol, rawFn, "Bad parameter name: " + id);
            }
            params.add(id);
        }
    }

    Chunk body = new Chunk();

    int depth = 0;
    int closeLineIndex = -1;

    for (int k = i; k < lines.size(); k++) {
        String raw = lines.get(k);
        String tr = raw.trim();

        // учитываем скобки на текущей строке
        depth += braceDelta(raw);

        // первая строка "fn ... {" уже учтена, просто идём дальше
        if (k == i) {
            continue;
        }

        // если дошли до закрытия функции
        if (depth == 0) {
            closeLineIndex = k;
            break;
        }

        if (tr.isEmpty() || tr.startsWith("#")) continue;
        if (tr.equals("{") || tr.equals("}")) continue;
        if (tr.startsWith("}")) continue;

        // ВАЖНО: если мы прыгаем внутрь вложенного блока, надо докрутить depth
        if (tr.startsWith("if ")) {
            int end = compileIf(lines, k, body);
            depth += braceDeltaRange(lines, k, end); // компенсируем пропущенные строки
            k = end;
            continue;
        }
        if (tr.startsWith("while ")) {
            int end = compileWhile(lines, k, body);
            depth += braceDeltaRange(lines, k, end);
            k = end;
            continue;
        }
        if (tr.startsWith("fn ")) {
            int end = compileFn(lines, k, body);
            depth += braceDeltaRange(lines, k, end);
            k = end;
            continue;
        }

        LogicalLine L = readLogicalLine(lines, k);
        compileSingleLine(L.raw, k + 1, body);
        k = L.endIndex;
    }

    if (closeLineIndex == -1) {
        throw DogException.at(lineFn, baseCol, rawFn, "Unclosed fn block: missing '}'");
    }

    if (body.code().isEmpty() || body.code().get(body.code().size() - 1).op != OpCode.RETURN) {
        body.add(Instruction.constNil(lineFn, baseCol, rawFn));
        body.add(Instruction.ret(lineFn, baseCol, rawFn));
    }

    int fnIndex = chunk.addFunction(new FunctionProto(params, body));

    chunk.add(Instruction.constFunc(fnIndex, lineFn, baseCol, rawFn));
    chunk.add(Instruction.store(name, lineFn, baseCol, rawFn));

    return closeLineIndex;
    }

    // ============================================================
    // One logical statement
    // ============================================================
    private void compileSingleLine(String rawOriginal, int line, Chunk chunk) {
        String raw = stripInlineComment(rawOriginal);
        String trimmed = raw.trim();
        if (trimmed.isEmpty())
            return;

        // import <module>
        if (trimmed.startsWith("import ")) {
            int col = indexOfNonSpace(raw) + 1;
            String mod = trimmed.substring("import ".length()).trim();
            if (mod.isEmpty()) {
                throw DogException.at(line, col, rawOriginal, "Expected module name after import");
            }
            chunk.add(Instruction.importMod(mod, line, col, rawOriginal));
            return;
        }

        // return <expr?>
        if (trimmed.startsWith("return")) {
            int col = indexOfNonSpace(raw) + 1;
            String rest = trimmed.substring("return".length()).trim();

            if (rest.isEmpty()) {
                chunk.add(Instruction.constNil(line, col, rawOriginal));
                chunk.add(Instruction.ret(line, col, rawOriginal));
                return;
            }

            Parser p = new Parser(rest, line, col, rawOriginal, chunk);
            p.parseExpression();
            p.finish();

            chunk.add(Instruction.ret(line, col, rawOriginal));
            return;
        }

        // say <expr>
        if (trimmed.startsWith("say")) {
            int sayPos = raw.indexOf("say");
            int after = sayPos + 3;

            if (raw.substring(after).trim().isEmpty()) {
                throw DogException.at(line, after + 1, rawOriginal, "Expected expression after 'say'");
            }

            int exprStart = findExprStartIndex(raw, after);
            int exprCol = exprStart + 1;
            String expr = raw.substring(exprStart);

            Parser p = new Parser(expr, line, exprCol, rawOriginal, chunk);
            p.parseExpression();
            p.finish();

            chunk.add(Instruction.simple(OpCode.PRINT, line, exprCol, rawOriginal));
            return;
        }

        // let x = expr
        if (trimmed.startsWith("let ")) {
            int letPos = raw.indexOf("let");
            int baseCol = letPos + 1;

            String rest = trimmed.substring(4).trim();
            int eq = rest.indexOf('=');
            if (eq < 0) {
                throw DogException.at(line, baseCol, rawOriginal, "Expected '=' in let statement");
            }

            String var = rest.substring(0, eq).trim();
            String expr = rest.substring(eq + 1).trim();

            if (!isIdent(var)) {
                throw DogException.at(line, baseCol, rawOriginal, "Bad variable name: " + var);
            }
            if (expr.isEmpty()) {
                throw DogException.at(line, baseCol, rawOriginal, "Expected expression after '='");
            }

            Parser p = new Parser(expr, line, baseCol, rawOriginal, chunk);
            p.parseExpression();
            p.finish();

            chunk.add(Instruction.store(var, line, baseCol, rawOriginal));
            return;
        }

        // array set: a[expr] = expr
        if (tryCompileArraySet(raw, line, rawOriginal, chunk)) {
            return;
        }

        // x = expr
        int assignPos = findTopLevelAssign(trimmed);
        if (assignPos >= 0) {
            String left = trimmed.substring(0, assignPos).trim();
            String right = trimmed.substring(assignPos + 1).trim();
            int col = indexOfNonSpace(raw) + 1;

            if (!isIdent(left)) {
                throw DogException.at(line, col, rawOriginal, "Bad assignment target: " + left);
            }
            if (right.isEmpty()) {
                throw DogException.at(line, col, rawOriginal, "Expected expression after '='");
            }

            Parser p = new Parser(right, line, col, rawOriginal, chunk);
            p.parseExpression();
            p.finish();

            chunk.add(Instruction.store(left, line, col, rawOriginal));
            return;
        }

        // expression statement (must be call)
        compileExprStatement(raw, line, rawOriginal, chunk);
    }

    private boolean tryCompileArraySet(String raw, int line, String src, Chunk chunk) {
        String trimmed = raw.trim();
        int eq = findTopLevelAssign(trimmed);
        if (eq < 0)
            return false;

        String left = trimmed.substring(0, eq).trim();
        String right = trimmed.substring(eq + 1).trim();

        int lb = left.indexOf('[');
        int rb = left.lastIndexOf(']');
        if (lb < 0 || rb < 0 || rb < lb)
            return false;

        String name = left.substring(0, lb).trim();
        String idxExpr = left.substring(lb + 1, rb).trim();

        if (!isIdent(name))
            return false;

        if (idxExpr.isEmpty()) {
            throw DogException.at(line, 1, src, "Expected index expression inside []");
        }
        if (right.isEmpty()) {
            throw DogException.at(line, 1, src, "Expected expression after '='");
        }

        int col = indexOfNonSpace(raw) + 1;

        chunk.add(Instruction.load(name, line, col, src));

        Parser pIdx = new Parser(idxExpr, line, col, src, chunk);
        pIdx.parseExpression();
        pIdx.finish();

        Parser pVal = new Parser(right, line, col, src, chunk);
        pVal.parseExpression();
        pVal.finish();

        chunk.add(Instruction.arraySet(line, col, src));
        chunk.add(Instruction.simple(OpCode.POP, line, col, src));
        return true;
    }

    private void compileExprStatement(String raw, int line, String src, Chunk chunk) {
        int exprStart = indexOfNonSpace(raw);
        int exprCol = exprStart + 1;
        String expr = raw.substring(exprStart);

        Parser p = new Parser(expr, line, exprCol, src, chunk);
        p.parseExpression();
        p.finish();

        if (!p.didCall) {
            throw DogException.at(line, exprCol, src,
                    "This line does nothing. Use 'say <expr>' or call something like io.print(...)");
        }
        chunk.add(Instruction.simple(OpCode.POP, line, exprCol, src));
    }

    // ============================================================
    // Multiline logical line reader
    // ============================================================
    private static final class LogicalLine {
        final String raw;
        final int endIndex;

        LogicalLine(String raw, int endIndex) {
            this.raw = raw;
            this.endIndex = endIndex;
        }
    }

    private static LogicalLine readLogicalLine(List<String> lines, int startIndex) {
        String raw0 = lines.get(startIndex);
        String t0 = raw0.trim();

        if (isStructuralLine(t0)) {
            return new LogicalLine(raw0, startIndex);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(raw0);

        int i = startIndex;
        while (true) {
            if (isStatementComplete(sb.toString())) {
                return new LogicalLine(sb.toString(), i);
            }
            if (i + 1 >= lines.size()) {
                return new LogicalLine(sb.toString(), i);
            }
            i++;
            sb.append('\n').append(lines.get(i));
        }
    }

    private static boolean isStructuralLine(String trimmed) {
        if (trimmed == null)
            return false;
        if (trimmed.startsWith("if "))
            return true;
        if (trimmed.startsWith("while "))
            return true;
        if (trimmed.startsWith("fn "))
            return true;
        if (trimmed.startsWith("else"))
            return true;
        if (trimmed.startsWith("} else"))
            return true;
        if (trimmed.equals("{") || trimmed.equals("}"))
            return true;
        if (trimmed.startsWith("}"))
            return true;
        return false;
    }

    private static boolean isStatementComplete(String raw) {
        int par = 0, br = 0, cr = 0;
        boolean inStr = false;
        boolean esc = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);

            if (inStr) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }

            if (c == '#')
                break;

            if (c == '"') {
                inStr = true;
                continue;
            }

            if (c == '(')
                par++;
            else if (c == ')')
                par = Math.max(0, par - 1);

            else if (c == '[')
                br++;
            else if (c == ']')
                br = Math.max(0, br - 1);

            else if (c == '{')
                cr++;
            else if (c == '}')
                cr = Math.max(0, cr - 1);
        }

        return !inStr && par == 0 && br == 0 && cr == 0;
    }

    private static String stripInlineComment(String raw) {
        if (raw == null)
            return null;

        boolean inStr = false;
        boolean esc = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);

            if (inStr) {
                if (esc)
                    esc = false;
                else if (c == '\\')
                    esc = true;
                else if (c == '"')
                    inStr = false;
                continue;
            }

            if (c == '"') {
                inStr = true;
                continue;
            }

            if (c == '#') {
                return raw.substring(0, i);
            }
        }
        return raw;
    }

    private static int braceDelta(String raw) {
        if (raw == null)
            return 0;

        boolean inStr = false;
        boolean esc = false;
        int d = 0;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);

            if (inStr) {
                if (esc)
                    esc = false;
                else if (c == '\\')
                    esc = true;
                else if (c == '"')
                    inStr = false;
                continue;
            }

            if (c == '#')
                break;
            if (c == '"') {
                inStr = true;
                continue;
            }

            if (c == '{')
                d++;
            else if (c == '}')
                d--;
        }
        return d;
    }

    private static int braceDeltaRange(List<String> lines, int fromExclusive, int toInclusive) {
        int d = 0;
        int a = fromExclusive + 1;
        int b = Math.min(toInclusive, lines.size() - 1);
        for (int i = a; i <= b; i++) {
            d += braceDelta(lines.get(i));
        }
        return d;
    }

    // ============================================================
    // Helpers
    // ============================================================
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

    private static int findTopLevelAssign(String s) {
        if (s == null || s.isEmpty())
            return -1;

        int paren = 0;
        int bracket = 0;
        int brace = 0;
        boolean inQuotes = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '"') {
                boolean escaped = (i > 0 && s.charAt(i - 1) == '\\');
                if (!escaped)
                    inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes)
                continue;

            if (c == '#')
                break;

            if (c == '(')
                paren++;
            else if (c == ')' && paren > 0)
                paren--;
            else if (c == '[')
                bracket++;
            else if (c == ']' && bracket > 0)
                bracket--;
            else if (c == '{')
                brace++;
            else if (c == '}' && brace > 0)
                brace--;

            if (paren != 0 || bracket != 0 || brace != 0)
                continue;

            if (c == '=') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '=')
                    continue;

                if (i - 1 >= 0) {
                    char prev = s.charAt(i - 1);
                    if (prev == '!' || prev == '<' || prev == '>')
                        continue;
                }
                return i;
            }
        }
        return -1;
    }

    // ============================================================
    // Expression Parser -> emits bytecode into Chunk
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
                } else {
                    break;
                }
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
                } else {
                    break;
                }
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
                emitConstInt(0, start);
                parseUnary();
                emit(OpCode.SUB);
                return;
            }

            parsePrimary();
            parsePostfix();
        }

        // postfix: calls and indexing AFTER any primary (including lambda/grouping)
        void parsePostfix() {
            while (true) {
                skipSpaces();

                // call: expr(...)
                if (match('(')) {
                    didCall = true;
                    int argc = parseArgsAfterOpenParen();
                    out.add(Instruction.callValue(argc, line, baseCol + pos, fullLine));
                    continue;
                }

                // indexing: expr[...]
                if (match('[')) {
                    skipSpaces();
                    parseExpression();
                    skipSpaces();
                    if (!match(']'))
                        throw err("Missing ']'");
                    out.add(Instruction.arrayGet(line, baseCol + pos, fullLine));
                    continue;
                }

                break;
            }
        }

        int parseArgsAfterOpenParen() {
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

        void parsePrimary() {
            skipSpaces();

            // array literal
            if (match('[')) {
                skipSpaces();
                int count = 0;
                if (match(']')) {
                    out.add(Instruction.arrayNew(0, line, baseCol + pos, fullLine));
                    return;
                }
                while (true) {
                    parseExpression();
                    count++;
                    skipSpaces();
                    if (match(']'))
                        break;
                    if (!match(','))
                        throw err("Expected ',' or ']' in array literal");
                    skipSpaces();
                }
                out.add(Instruction.arrayNew(count, line, baseCol + pos, fullLine));
                return;
            }

            // string
            if (peek() == '"') {
                int start = pos;
                String str = parseString();
                out.add(Instruction.constStr(str, line, baseCol + start, fullLine));
                return;
            }

            // lambda OR grouping: (a,b)=>expr OR (expr)
            if (peek() == '(') {
                int saved = pos;

                LambdaHeader lh = tryParseLambdaHeader(saved);
                if (lh != null) {
                    skipSpaces();
                    if (matchArrow()) {
                        skipSpaces();
                        int bodyStart = pos;

                        // IMPORTANT: slice stops BEFORE ',' ')' ']' '}' on top level
                        String bodyExpr = readLambdaBodySlice();
                        if (bodyExpr.trim().isEmpty())
                            throw err("Expected expression after lambda arrow");

                        Chunk body = new Chunk();
                        Parser p2 = new Parser(bodyExpr, line, baseCol + bodyStart, fullLine, body);
                        p2.parseExpression();
                        p2.finish();
                        body.add(Instruction.ret(line, baseCol + bodyStart, fullLine));

                        int fnIndex = out.addFunction(new FunctionProto(lh.params, body));
                        out.add(Instruction.constFunc(fnIndex, line, baseCol + saved, fullLine));

                        // move pos to end of lambda body (delimiter not consumed)
                        pos = bodyStart + bodyExpr.length();
                        return;
                    } else {
                        // not a lambda, rollback and parse as grouping
                        pos = saved;
                    }
                }

                // grouping
                match('(');
                parseExpression();
                skipSpaces();
                if (!match(')'))
                    throw err("Missing ')'");
                return;
            }

            // number
            if (Character.isDigit(peek()) || peek() == '.') {
                int start = pos;
                emitNumberLiteral(parseNumberToken(), start);
                return;
            }

            // ident / module.member / keywords
            if (isIdentStart(peek())) {
                parseIdentOrQualifiedOrKeyword();
                return;
            }

            throw err("Unexpected token near: " + rest());
        }

        boolean matchArrow() {
            // support both => and ->
            if (match2("=>"))
                return true;
            if (match2("->"))
                return true;
            return false;
        }

        // Try parse lambda header: (a,b) OR ()
        // If parsed, leaves pos after ')'. If not - returns null and does not change
        // pos.
        LambdaHeader tryParseLambdaHeader(int savedPos) {
            int p = savedPos;
            if (p >= s.length() || s.charAt(p) != '(')
                return null;
            p++; // after '('

            ArrayList<String> params = new ArrayList<>();

            while (p < s.length() && Character.isWhitespace(s.charAt(p)))
                p++;

            // empty: ()
            if (p < s.length() && s.charAt(p) == ')') {
                p++;
                pos = p;
                return new LambdaHeader(params);
            }

            // ident (, ident)*
            while (true) {
                while (p < s.length() && Character.isWhitespace(s.charAt(p)))
                    p++;
                if (p >= s.length())
                    return null;

                char c = s.charAt(p);
                if (!(Character.isLetter(c) || c == '_'))
                    return null;

                int start = p;
                p++;
                while (p < s.length()) {
                    char cc = s.charAt(p);
                    if (Character.isLetterOrDigit(cc) || cc == '_')
                        p++;
                    else
                        break;
                }
                params.add(s.substring(start, p));

                while (p < s.length() && Character.isWhitespace(s.charAt(p)))
                    p++;
                if (p >= s.length())
                    return null;

                if (s.charAt(p) == ')') {
                    p++;
                    pos = p;
                    return new LambdaHeader(params);
                }
                if (s.charAt(p) == ',') {
                    p++;
                    continue;
                }
                return null;
            }
        }

        // read until end-of-lambda-expression slice (same expression),
        // IMPORTANT: stops BEFORE delimiters at TOP LEVEL so that apply(..., (x)=>x*x)
        // works
        String readLambdaBodySlice() {
            int i = pos;
            int par = 0, br = 0, cr = 0;
            boolean inStr = false;
            boolean esc = false;

            while (i < s.length()) {
                char c = s.charAt(i);

                if (inStr) {
                    if (esc)
                        esc = false;
                    else if (c == '\\')
                        esc = true;
                    else if (c == '"')
                        inStr = false;
                    i++;
                    continue;
                }

                if (c == '"') {
                    inStr = true;
                    i++;
                    continue;
                }

                if (c == '#')
                    break;

                // stop at delimiters on TOP LEVEL
                if (par == 0 && br == 0 && cr == 0) {
                    if (c == ',' || c == ')' || c == ']' || c == '}') {
                        break;
                    }
                }

                if (c == '(')
                    par++;
                else if (c == ')' && par > 0)
                    par--;
                else if (c == '[')
                    br++;
                else if (c == ']' && br > 0)
                    br--;
                else if (c == '{')
                    cr++;
                else if (c == '}' && cr > 0)
                    cr--;

                i++;
            }

            return s.substring(pos, i);
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
                    int argCount = parseArgsAfterOpenParen();
                    out.add(Instruction.call(a, b, argCount, false, line, baseCol + start, fullLine));
                    return;
                }

                // property get: module.member
                out.add(Instruction.call(a, b, 0, true, line, baseCol + start, fullLine));
                return;
            }

            out.add(Instruction.load(a, line, baseCol + start, fullLine));
        }

        // ---- numbers / strings ----

        void emitConstInt(int v, int startPos) {
            out.add(Instruction.constInt(v, line, baseCol + startPos, fullLine));
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
                } else {
                    sb.append(c);
                }
            }
            throw err("Unterminated string literal");
        }

        String parseNumberToken() {
            int start = pos;
            boolean dot = false;
            boolean exp = false;

            while (!isEnd()) {
                char c = s.charAt(pos);

                if (Character.isDigit(c)) {
                    pos++;
                    continue;
                }

                if (c == '.' && !dot && !exp) {
                    dot = true;
                    pos++;
                    continue;
                }

                if ((c == 'e' || c == 'E') && !exp) {
                    exp = true;
                    pos++;
                    if (!isEnd()) {
                        char sgn = s.charAt(pos);
                        if (sgn == '+' || sgn == '-')
                            pos++;
                    }
                    continue;
                }
                break;
            }
            return s.substring(start, pos);
        }

        void emitNumberLiteral(String token, int startPos) {
            if (token == null || token.isEmpty())
                throw err("Bad number");

            if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
                try {
                    double d = Double.parseDouble(token);
                    out.add(Instruction.constDouble(d, line, baseCol + startPos, fullLine));
                } catch (NumberFormatException e) {
                    throw err("Bad number: " + token);
                }
                return;
            }

            try {
                java.math.BigInteger bi = new java.math.BigInteger(token);

                if (bi.bitLength() <= 31) {
                    out.add(Instruction.constInt(bi.intValue(), line, baseCol + startPos, fullLine));
                    return;
                }
                if (bi.bitLength() <= 63) {
                    out.add(Instruction.constLong(bi.longValue(), line, baseCol + startPos, fullLine));
                    return;
                }

                out.add(Instruction.constBigInt(bi.toString(), line, baseCol + startPos, fullLine));
            } catch (NumberFormatException e) {
                throw err("Bad number: " + token);
            }
        }

        // ---- finish / utils ----

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

        String parseIdent() {
            int start = pos;
            if (!isIdentStart(peek()))
                throw err("Expected identifier");
            pos++;
            while (!isEnd() && isIdentPart(s.charAt(pos)))
                pos++;
            return s.substring(start, pos);
        }

        DogException err(String msg) {
            return DogException.at(line, baseCol + pos, fullLine, msg);
        }

        static final class LambdaHeader {
            final ArrayList<String> params;

            LambdaHeader(ArrayList<String> params) {
                this.params = params;
            }
        }
    }
}