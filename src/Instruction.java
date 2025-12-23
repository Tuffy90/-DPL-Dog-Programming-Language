public final class Instruction {
    public final OpCode op;

    // const payloads
    public final Double num;
    public final String text;
    public final Boolean boolVal;

    // var payload
    public final String name;

    // module call payload
    public final String module;
    public final String member;
    public final int argCount;
    public final boolean isConst;

    // jump payload (absolute target ip)
    public int jumpTarget; // <- НЕ final, чтобы можно было "патчить" после компиляции

    // debug
    public final int line; // 1-based
    public final int col; // 1-based
    public final String sourceLine;

    private Instruction(
            OpCode op,
            Double num,
            String text,
            Boolean boolVal,
            String name,
            String module,
            String member,
            int argCount,
            boolean isConst,
            int jumpTarget,
            int line,
            int col,
            String sourceLine) {

        this.op = op;
        this.num = num;
        this.text = text;
        this.boolVal = boolVal;
        this.name = name;
        this.module = module;
        this.member = member;
        this.argCount = argCount;
        this.isConst = isConst;
        this.jumpTarget = jumpTarget;
        this.line = line;
        this.col = col;
        this.sourceLine = sourceLine;
    }

    // ---------- factories ----------
    public static Instruction constNum(double n, int line, int col, String src) {
        return new Instruction(OpCode.CONST_NUM, n, null, null, null, null, null, 0, false, -1, line, col, src);
    }

    public static Instruction constStr(String s, int line, int col, String src) {
        return new Instruction(OpCode.CONST_STR, null, s, null, null, null, null, 0, false, -1, line, col, src);
    }

    public static Instruction constBool(boolean b, int line, int col, String src) {
        return new Instruction(OpCode.CONST_BOOL, null, null, Boolean.valueOf(b), null, null, null, 0, false, -1, line,
                col, src);
    }

    public static Instruction constNil(int line, int col, String src) {
        return new Instruction(OpCode.CONST_NIL, null, null, null, null, null, null, 0, false, -1, line, col, src);
    }

    public static Instruction simple(OpCode op, int line, int col, String src) {
        return new Instruction(op, null, null, null, null, null, null, 0, false, -1, line, col, src);
    }

    public static Instruction load(String var, int line, int col, String src) {
        return new Instruction(OpCode.LOAD, null, null, null, var, null, null, 0, false, -1, line, col, src);
    }

    public static Instruction store(String var, int line, int col, String src) {
        return new Instruction(OpCode.STORE, null, null, null, var, null, null, 0, false, -1, line, col, src);
    }

    public static Instruction importMod(String module, int line, int col, String src) {
        return new Instruction(OpCode.IMPORT, null, null, null, null, module, null, 0, false, -1, line, col, src);
    }

    public static Instruction call(String module, String member, int argCount, boolean isConst, int line, int col,
            String src) {
        return new Instruction(OpCode.CALL, null, null, null, null, module, member, argCount, isConst, -1, line, col,
                src);
    }

    public static Instruction jump(int target, int line, int col, String src) {
        return new Instruction(OpCode.JUMP, null, null, null, null, null, null, 0, false, target, line, col, src);
    }

    public static Instruction jumpIfFalse(int target, int line, int col, String src) {
        return new Instruction(OpCode.JUMP_IF_FALSE, null, null, null, null, null, null, 0, false, target, line, col,
                src);
    }
}