// File: Instruction.java
public final class Instruction {
    public final OpCode op;

    // payload
    public final Integer intVal;
    public final Long longVal;
    public final Double doubleVal;
    public final String text;
    public final Boolean boolVal;

    public final String name;
    public final String module;
    public final String member;

    public final int argCount;
    public final boolean isConst;

    public final int funcIndex; // CONST_FUNC

    public int jumpTarget;

    public final int line;
    public final int col;
    public final String sourceLine;

    private Instruction(
            OpCode op,
            Integer intVal,
            Long longVal,
            Double doubleVal,
            String text,
            Boolean boolVal,
            String name,
            String module,
            String member,
            int argCount,
            boolean isConst,
            int funcIndex,
            int jumpTarget,
            int line,
            int col,
            String sourceLine) {

        this.op = op;
        this.intVal = intVal;
        this.longVal = longVal;
        this.doubleVal = doubleVal;
        this.text = text;
        this.boolVal = boolVal;

        this.name = name;
        this.module = module;
        this.member = member;

        this.argCount = argCount;
        this.isConst = isConst;

        this.funcIndex = funcIndex;

        this.jumpTarget = jumpTarget;

        this.line = line;
        this.col = col;
        this.sourceLine = sourceLine;
    }

    // ---- CONST ----
    public static Instruction constInt(int n, int line, int col, String src) {
        return new Instruction(OpCode.CONST_INT, n, null, null, null, null, null, null, null, 0, false, -1, -1, line,
                col, src);
    }

    public static Instruction constLong(long n, int line, int col, String src) {
        return new Instruction(OpCode.CONST_LONG, null, n, null, null, null, null, null, null, 0, false, -1, -1, line,
                col, src);
    }

    public static Instruction constDouble(double n, int line, int col, String src) {
        return new Instruction(OpCode.CONST_DOUBLE, null, null, n, null, null, null, null, null, 0, false, -1, -1, line,
                col, src);
    }

    public static Instruction constBigInt(String nText, int line, int col, String src) {
        return new Instruction(OpCode.CONST_BIGINT, null, null, null, nText, null, null, null, null, 0, false, -1, -1,
                line, col, src);
    }

    public static Instruction constStr(String s, int line, int col, String src) {
        return new Instruction(OpCode.CONST_STR, null, null, null, s, null, null, null, null, 0, false, -1, -1, line,
                col, src);
    }

    public static Instruction constBool(boolean b, int line, int col, String src) {
        return new Instruction(OpCode.CONST_BOOL, null, null, null, null, Boolean.valueOf(b), null, null, null, 0,
                false, -1, -1, line, col, src);
    }

    public static Instruction constNil(int line, int col, String src) {
        return new Instruction(OpCode.CONST_NIL, null, null, null, null, null, null, null, null, 0, false, -1, -1, line,
                col, src);
    }

    // ---- functions ----
    public static Instruction constFunc(int funcIndex, int line, int col, String src) {
        return new Instruction(OpCode.CONST_FUNC, null, null, null, null, null, null, null, null, 0, false, funcIndex,
                -1, line, col, src);
    }

    public static Instruction callValue(int argCount, int line, int col, String src) {
        return new Instruction(OpCode.CALL_VALUE, null, null, null, null, null, null, null, null, argCount, false, -1,
                -1, line, col, src);
    }

    public static Instruction ret(int line, int col, String src) {
        return new Instruction(OpCode.RETURN, null, null, null, null, null, null, null, null, 0, false, -1, -1, line,
                col, src);
    }

    // ---- arrays ----
    public static Instruction arrayNew(int count, int line, int col, String src) {
        return new Instruction(OpCode.ARRAY_NEW, null, null, null, null, null, null, null, null, count, false, -1, -1,
                line, col, src);
    }

    public static Instruction arrayGet(int line, int col, String src) {
        return new Instruction(OpCode.ARRAY_GET, null, null, null, null, null, null, null, null, 0, false, -1, -1, line,
                col, src);
    }

    public static Instruction arraySet(int line, int col, String src) {
        return new Instruction(OpCode.ARRAY_SET, null, null, null, null, null, null, null, null, 0, false, -1, -1, line,
                col, src);
    }

    // ---- basic ----
    public static Instruction simple(OpCode op, int line, int col, String src) {
        return new Instruction(op, null, null, null, null, null, null, null, null, 0, false, -1, -1, line, col, src);
    }

    public static Instruction load(String var, int line, int col, String src) {
        return new Instruction(OpCode.LOAD, null, null, null, null, null, var, null, null, 0, false, -1, -1, line, col,
                src);
    }

    public static Instruction store(String var, int line, int col, String src) {
        return new Instruction(OpCode.STORE, null, null, null, null, null, var, null, null, 0, false, -1, -1, line, col,
                src);
    }

    public static Instruction importMod(String module, int line, int col, String src) {
        return new Instruction(OpCode.IMPORT, null, null, null, null, null, null, module, null, 0, false, -1, -1, line,
                col, src);
    }

    public static Instruction call(String module, String member, int argCount, boolean isConst, int line, int col,
            String src) {
        return new Instruction(OpCode.CALL, null, null, null, null, null, null, module, member, argCount, isConst, -1,
                -1, line, col, src);
    }

    public static Instruction jump(int target, int line, int col, String src) {
        return new Instruction(OpCode.JUMP, null, null, null, null, null, null, null, null, 0, false, -1, target, line,
                col, src);
    }

    public static Instruction jumpIfFalse(int target, int line, int col, String src) {
        return new Instruction(OpCode.JUMP_IF_FALSE, null, null, null, null, null, null, null, null, 0, false, -1,
                target, line, col, src);
    }
}