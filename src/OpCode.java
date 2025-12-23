public enum OpCode {
    // consts
    CONST_NUM,
    CONST_STR,
    CONST_BOOL,
    CONST_NIL,

    // arithmetic
    ADD, SUB, MUL, DIV,

    // logic/compare
    NOT,
    EQ, NEQ,
    LT, GT, LE, GE,

    // vars
    LOAD, STORE,

    // modules
    IMPORT, CALL,

    // output / stack
    PRINT, POP,

    // flow
    JUMP,
    JUMP_IF_FALSE
}