public final class Instruction {
    public final OpCode op;
    public final Double num;
    public final String text;
    public final String name;
    public final String module;
    public final String member;
    public final int argCount;
    public final boolean isConst;
    public final int line; // 1-based
    public final int col; // 1-based
    public final String sourceLine;

    private Instruction(
            OpCode op,
            Double num,
            String text,
            String name,
            String module,
            String member,
            int argCount,
            boolean isConst,
            int line,
            int col,
            String sourceLine) {
        this.op = op;
        this.num = num;
        this.text = text;
        this.name = name;
        this.module = module;
        this.member = member;
        this.argCount = argCount;
        this.isConst = isConst;
        this.line = line;
        this.col = col;
        this.sourceLine = sourceLine;
    }

    public static Instruction constNum(double n, int line, int col, String src) {
        return new Instruction(OpCode.CONST_NUM, n, null, null, null, null, 0, false, line, col, src);
    }

    public static Instruction constStr(String s, int line, int col, String src) {
        return new Instruction(OpCode.CONST_STR, null, s, null, null, null, 0, false, line, col, src);
    }

    public static Instruction simple(OpCode op, int line, int col, String src) {
        return new Instruction(op, null, null, null, null, null, 0, false, line, col, src);
    }

    public static Instruction load(String var, int line, int col, String src) {
        return new Instruction(OpCode.LOAD, null, null, var, null, null, 0, false, line, col, src);
    }

    public static Instruction store(String var, int line, int col, String src) {
        return new Instruction(OpCode.STORE, null, null, var, null, null, 0, false, line, col, src);
    }

    public static Instruction importMod(String module, int line, int col, String src) {
        return new Instruction(OpCode.IMPORT, null, null, null, module, null, 0, false, line, col, src);
    }

    public static Instruction call(String module, String member, int argCount, boolean isConst, int line, int col,
            String src) {
        return new Instruction(OpCode.CALL, null, null, null, module, member, argCount, isConst, line, col, src);
    }
}