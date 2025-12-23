import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DogVM {

    private final ArrayList<Value> stack = new ArrayList<Value>();
    private final Map<String, Value> globals = new HashMap<String, Value>();

    public Map<String, Value> globals() {
        return globals;
    }

    public void execute(Chunk chunk, DogContext ctx) {
        List<Instruction> code = chunk.code();

        for (int ip = 0; ip < code.size(); ip++) {
            Instruction ins = code.get(ip);

            try {
                switch (ins.op) {

                    // ======================
                    // CONST
                    // ======================
                    case CONST_NUM:
                        stack.add(Value.num(ins.num != null ? ins.num.doubleValue() : 0.0));
                        break;

                    case CONST_STR:
                        stack.add(Value.str(ins.text != null ? ins.text : ""));
                        break;

                    case CONST_BOOL:
                        stack.add(Value.bool(ins.boolVal != null && ins.boolVal.booleanValue()));
                        break;

                    case CONST_NIL:
                        stack.add(Value.nil());
                        break;

                    // ======================
                    // ARITH / CONCAT
                    // ======================
                    case ADD: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        if (a.isString() || b.isString()) {
                            stack.add(Value.str(a.printable() + b.printable()));
                        } else {
                            requireNumber(a, ins);
                            requireNumber(b, ins);
                            stack.add(Value.num(a.number + b.number));
                        }
                        break;
                    }

                    case SUB: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(Value.num(a.number - b.number));
                        break;
                    }

                    case MUL: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(Value.num(a.number * b.number));
                        break;
                    }

                    case DIV: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(Value.num(a.number / b.number));
                        break;
                    }

                    // ======================
                    // COMPARE / LOGIC
                    // ======================
                    case NOT: {
                        Value a = pop(ins);
                        stack.add(Value.bool(!isTruthy(a)));
                        break;
                    }

                    case EQ: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        stack.add(Value.bool(isEqual(a, b)));
                        break;
                    }

                    case NEQ: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        stack.add(Value.bool(!isEqual(a, b)));
                        break;
                    }

                    case LT: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(Value.bool(a.number < b.number));
                        break;
                    }

                    case GT: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(Value.bool(a.number > b.number));
                        break;
                    }

                    case LE: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(Value.bool(a.number <= b.number));
                        break;
                    }

                    case GE: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(Value.bool(a.number >= b.number));
                        break;
                    }

                    // ======================
                    // VARS
                    // ======================
                    case LOAD: {
                        Value v = globals.get(ins.name);
                        if (v == null) {
                            throw DogException.at(ins.line, ins.col, ins.sourceLine,
                                    "Undefined variable '" + ins.name + "'");
                        }
                        stack.add(v);
                        break;
                    }

                    case STORE: {
                        Value v = pop(ins);
                        globals.put(ins.name, v);
                        break;
                    }

                    // ======================
                    // MODULES
                    // ======================
                    case IMPORT: {
                        ctx.importModule(ins.module, ins.line, ins.col, ins.sourceLine);
                        break;
                    }

                    case CALL: {
                        ctx.requireImported(ins.module, ins.line, ins.col, ins.sourceLine);

                        DogModule m = ctx.registry().get(ins.module);
                        if (m == null) {
                            throw DogException.at(ins.line, ins.col, ins.sourceLine,
                                    "Unknown module: " + ins.module);
                        }

                        if (ins.isConst) {
                            Value v = m.getConstant(ins.member, ctx, ins.line, ins.col, ins.sourceLine);
                            stack.add(v);
                        } else {
                            int n = ins.argCount;
                            if (n < 0) {
                                throw DogException.at(ins.line, ins.col, ins.sourceLine, "Bad CALL argCount");
                            }
                            ArrayList<Value> args = new ArrayList<Value>(n);
                            for (int i = 0; i < n; i++) {
                                args.add(0, pop(ins));
                            }
                            Value ret = m.call(ins.member, args, ctx, ins.line, ins.col, ins.sourceLine);
                            stack.add(ret);
                        }
                        break;
                    }

                    // ======================
                    // FLOW CONTROL
                    // ======================
                    case JUMP:
                        checkJump(ins.jumpTarget, code.size(), ins);
                        ip = ins.jumpTarget - 1; // -1 because loop does ip++
                        break;

                    case JUMP_IF_FALSE: {
                        // ✅ must POP condition, иначе стек будет засоряться
                        Value cond = pop(ins);
                        if (!isTruthy(cond)) {
                            checkJump(ins.jumpTarget, code.size(), ins);
                            ip = ins.jumpTarget - 1;
                        }
                        break;
                    }

                    // ======================
                    // PRINT / STACK
                    // ======================
                    case PRINT: {
                        Value v = pop(ins);
                        System.out.println(v.printable());
                        break;
                    }

                    case POP: {
                        pop(ins);
                        break;
                    }

                    default:
                        throw DogException.at(ins.line, ins.col, ins.sourceLine,
                                "Unknown opcode: " + ins.op);
                }

            } catch (DogException e) {
                throw e;
            } catch (RuntimeException e) {
                throw DogException.at(ins.line, ins.col, ins.sourceLine,
                        "Runtime error: " + e.getMessage());
            }
        }
    }

    // ======================
    // Stack helpers
    // ======================
    private Value pop(Instruction ins) {
        if (stack.isEmpty()) {
            throw DogException.at(ins.line, ins.col, ins.sourceLine, "Stack underflow");
        }
        return stack.remove(stack.size() - 1);
    }

    private void requireNumber(Value v, Instruction ins) {
        if (!v.isNumber()) {
            throw DogException.at(ins.line, ins.col, ins.sourceLine, "Expected number");
        }
    }

    private void checkJump(int target, int size, Instruction ins) {
        if (target < 0 || target >= size) {
            throw DogException.at(ins.line, ins.col, ins.sourceLine,
                    "Bad jump target: " + target + " (code size=" + size + ")");
        }
    }

    // ======================
    // Truthy + Equality
    // ======================
    private boolean isTruthy(Value v) {
        if (v.isNil())
            return false;
        if (v.isBool())
            return v.bool;
        if (v.isNumber())
            return v.number != 0.0;
        if (v.isString())
            return v.string != null && !v.string.isEmpty();
        return true;
    }

    private boolean isEqual(Value a, Value b) {
        if (a.isNil() && b.isNil())
            return true;
        if (a.kind != b.kind)
            return false;

        if (a.isNumber())
            return a.number == b.number;
        if (a.isString())
            return a.string.equals(b.string);
        if (a.isBool())
            return a.bool == b.bool;
        return false;
    }
}