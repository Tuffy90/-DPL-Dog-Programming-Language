// File: OpCode.java
public enum OpCode {
    CONST_INT,
    CONST_LONG,
    CONST_DOUBLE,
    CONST_BIGINT,
    CONST_STR,
    CONST_BOOL,
    CONST_NIL,

    // functions
    CONST_FUNC, // funcIndex
    CALL_VALUE, // argCount; callee is on stack
    RETURN, // return from function

    ARRAY_NEW,
    ARRAY_GET,
    ARRAY_SET,

    ADD, SUB, MUL, DIV,
    NOT,
    EQ, NEQ,
    LT, GT, LE, GE,

    LOAD, STORE,

    IMPORT, CALL,

    PRINT, POP,

    JUMP,
    JUMP_IF_FALSE
}