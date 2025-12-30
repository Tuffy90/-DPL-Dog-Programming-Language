
// File: DogVM.java
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DogVM {

    private final ArrayList<Value> stack = new ArrayList<Value>();
    private final Map<String, Value> globals = new HashMap<String, Value>();

    private static final class Frame {
        final Map<String, Value> locals = new HashMap<>();
        final Map<String, Value> closure; // captured values snapshot (mutable inside this frame)
        final int stackBase;

        Frame(Map<String, Value> closure, int stackBase) {
            this.closure = (closure == null) ? new HashMap<>() : closure;
            this.stackBase = stackBase;
        }
    }

    private final ArrayList<Frame> frames = new ArrayList<>();

    public Map<String, Value> globals() {
        return globals;
    }

    public void execute(Chunk chunk, DogContext ctx) {
        executeChunk(chunk, ctx, false, null);
    }

    private Value executeChunk(Chunk chunk, DogContext ctx, boolean isFunction, Frame frame) {
        List<Instruction> code = chunk.code();

        for (int ip = 0; ip < code.size(); ip++) {
            Instruction ins = code.get(ip);

            try {
                switch (ins.op) {

                    // ---- const ----
                    case CONST_INT:
                        stack.add(Value.ofInt(ins.intVal != null ? ins.intVal.intValue() : 0));
                        break;

                    case CONST_LONG:
                        stack.add(Value.ofLong(ins.longVal != null ? ins.longVal.longValue() : 0L));
                        break;

                    case CONST_DOUBLE:
                        stack.add(Value.ofDouble(ins.doubleVal != null ? ins.doubleVal.doubleValue() : 0.0));
                        break;

                    case CONST_BIGINT: {
                        String t = ins.text != null ? ins.text : "0";
                        stack.add(Value.ofBigInt(new BigInteger(t)));
                        break;
                    }

                    case CONST_STR:
                        stack.add(Value.str(ins.text != null ? ins.text : ""));
                        break;

                    case CONST_BOOL:
                        stack.add(Value.bool(ins.boolVal != null && ins.boolVal.booleanValue()));
                        break;

                    case CONST_NIL:
                        stack.add(Value.nil());
                        break;

                    // ---- functions ----
                    case CONST_FUNC: {
                        if (ins.funcIndex < 0 || ins.funcIndex >= chunk.functions().size()) {
                            throw DogException.at(ins.line, ins.col, ins.sourceLine,
                                    "Bad function index: " + ins.funcIndex);
                        }
                        FunctionProto proto = chunk.getFunction(ins.funcIndex);
                        Map<String, Value> cap = captureEnvSnapshot();
                        stack.add(Value.function(proto, cap));
                        break;
                    }

                    case CALL_VALUE: {
                        int n = ins.argCount;
                        if (n < 0)
                            throw DogException.at(ins.line, ins.col, ins.sourceLine, "Bad CALL_VALUE argCount");

                        ArrayList<Value> args = new ArrayList<>(n);
                        for (int i = 0; i < n; i++)
                            args.add(0, pop(ins)); // keep order
                        Value callee = pop(ins);

                        if (callee == null || !callee.isFunction()) {
                            throw DogException.at(ins.line, ins.col, ins.sourceLine,
                                    "Trying to call non-function: " + (callee == null ? "null" : callee.kind));
                        }

                        Value r = callUserFunction(callee.funcProto, callee.closure, args, ctx, ins);
                        stack.add(r);
                        break;
                    }

                    case RETURN: {
                        if (!isFunction) {
                            throw DogException.at(ins.line, ins.col, ins.sourceLine, "RETURN outside of function");
                        }
                        Value r = (stack.size() > frame.stackBase) ? pop(ins) : Value.nil();
                        while (stack.size() > frame.stackBase)
                            stack.remove(stack.size() - 1);
                        return r;
                    }

                    // ---- arrays ----
                    case ARRAY_NEW: {
                        int n = ins.argCount;
                        if (n < 0)
                            throw DogException.at(ins.line, ins.col, ins.sourceLine, "Bad ARRAY_NEW count");

                        ArrayList<Value> items = new ArrayList<Value>(n);
                        for (int i = 0; i < n; i++)
                            items.add(0, pop(ins));
                        stack.add(Value.array(items));
                        break;
                    }

                    case ARRAY_GET: {
                        Value idxV = pop(ins);
                        Value arrV = pop(ins);
                        if (!arrV.isArray()) {
                            throw DogException.at(ins.line, ins.col, ins.sourceLine, "Indexing works only for arrays");
                        }
                        int idx = requireIndexInt(idxV, ins);
                        if (idx < 0 || idx >= arrV.arrayVal.size()) {
                            throw DogException.at(ins.line, ins.col, ins.sourceLine,
                                    "Array index out of range: " + idx);
                        }
                        stack.add(arrV.arrayVal.get(idx));
                        break;
                    }

                    case ARRAY_SET: {
                        Value value = pop(ins);
                        Value idxV = pop(ins);
                        Value arrV = pop(ins);
                        if (!arrV.isArray()) {
                            throw DogException.at(ins.line, ins.col, ins.sourceLine, "Indexing works only for arrays");
                        }
                        int idx = requireIndexInt(idxV, ins);
                        if (idx < 0) {
                            throw DogException.at(ins.line, ins.col, ins.sourceLine,
                                    "Array index out of range: " + idx);
                        }
                        while (idx >= arrV.arrayVal.size())
                            arrV.arrayVal.add(Value.nil());
                        arrV.arrayVal.set(idx, value);
                        stack.add(value);
                        break;
                    }

                    // ---- arithmetic ----
                    case ADD: {
                        Value b = pop(ins);
                        Value a = pop(ins);

                        if (a.isString() || b.isString()) {
                            stack.add(Value.str(a.printable() + b.printable()));
                            break;
                        }

                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(numAdd(a, b));
                        break;
                    }

                    case SUB: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(numSub(a, b));
                        break;
                    }

                    case MUL: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(numMul(a, b));
                        break;
                    }

                    case DIV: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(Value.ofDouble(a.toDouble() / b.toDouble()));
                        break;
                    }

                    // ---- logic ----
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
                        stack.add(Value.bool(numCompare(a, b) < 0));
                        break;
                    }

                    case GT: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(Value.bool(numCompare(a, b) > 0));
                        break;
                    }

                    case LE: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(Value.bool(numCompare(a, b) <= 0));
                        break;
                    }

                    case GE: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        requireNumber(a, ins);
                        requireNumber(b, ins);
                        stack.add(Value.bool(numCompare(a, b) >= 0));
                        break;
                    }

                    // ---- vars ----
                    case LOAD: {
                        Value v = loadVar(ins.name);
                        if (v == null) {
                            throw DogException.at(ins.line, ins.col, ins.sourceLine,
                                    "Undefined variable '" + ins.name + "'");
                        }
                        stack.add(v);
                        break;
                    }

                    case STORE: {
                        Value v = pop(ins);
                        storeVar(ins.name, v);
                        break;
                    }

                    // ---- modules ----
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
                            for (int i = 0; i < n; i++)
                                args.add(0, pop(ins));
                            Value ret = m.call(ins.member, args, ctx, ins.line, ins.col, ins.sourceLine);
                            stack.add(ret);
                        }
                        break;
                    }

                    // ---- jumps ----
                    case JUMP:
                        checkJump(ins.jumpTarget, code.size(), ins);
                        ip = ins.jumpTarget - 1;
                        break;

                    case JUMP_IF_FALSE: {
                        Value cond = pop(ins);
                        if (!isTruthy(cond)) {
                            checkJump(ins.jumpTarget, code.size(), ins);
                            ip = ins.jumpTarget - 1;
                        }
                        break;
                    }

                    // ---- io ----
                    case PRINT: {
                        Value v = pop(ins);
                        System.out.println(v.printable());
                        break;
                    }

                    case POP:
                        pop(ins);
                        break;

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

        // function without explicit RETURN
        return isFunction ? Value.nil() : Value.nil();
    }

    private Value callUserFunction(FunctionProto proto,
            Map<String, Value> closure,
            List<Value> args,
            DogContext ctx,
            Instruction callIns) {

        int base = stack.size();
        Frame frame = new Frame(new HashMap<>(closure), base);
        frames.add(frame);

        for (int i = 0; i < proto.params.size(); i++) {
            String p = proto.params.get(i);
            Value v = (i < args.size()) ? args.get(i) : Value.nil();
            frame.locals.put(p, v);
        }

        try {
            return executeChunk(proto.body, ctx, true, frame);
        } finally {
            while (stack.size() > base)
                stack.remove(stack.size() - 1);
            frames.remove(frames.size() - 1);
        }
    }

    private Map<String, Value> captureEnvSnapshot() {
        HashMap<String, Value> env = new HashMap<>();
        env.putAll(globals);

        for (int i = 0; i < frames.size(); i++) {
            Frame f = frames.get(i);
            env.putAll(f.closure);
            env.putAll(f.locals);
        }
        return env;
    }

    private Value loadVar(String name) {
        for (int i = frames.size() - 1; i >= 0; i--) {
            Frame f = frames.get(i);
            if (f.locals.containsKey(name))
                return f.locals.get(name);
            if (f.closure.containsKey(name))
                return f.closure.get(name);
        }
        return globals.get(name);
    }

    private void storeVar(String name, Value v) {
        if (frames.isEmpty()) {
            globals.put(name, v);
            return;
        }

        for (int i = frames.size() - 1; i >= 0; i--) {
            Frame f = frames.get(i);
            if (f.locals.containsKey(name)) {
                f.locals.put(name, v);
                return;
            }
            if (f.closure.containsKey(name)) {
                f.closure.put(name, v);
                return;
            }
        }

        frames.get(frames.size() - 1).locals.put(name, v);
    }

    private Value pop(Instruction ins) {
        if (stack.isEmpty()) {
            throw DogException.at(ins.line, ins.col, ins.sourceLine, "Stack underflow");
        }
        return stack.remove(stack.size() - 1);
    }

    private void requireNumber(Value v, Instruction ins) {
        if (v == null || !v.isNumber()) {
            throw DogException.at(ins.line, ins.col, ins.sourceLine, "Expected number");
        }
    }

    private int requireIndexInt(Value v, Instruction ins) {
        if (v == null || !v.isNumber()) {
            throw DogException.at(ins.line, ins.col, ins.sourceLine, "Array index must be a number");
        }
        double d = v.toDouble();
        if (d != Math.rint(d)) {
            throw DogException.at(ins.line, ins.col, ins.sourceLine, "Array index must be an integer");
        }
        long L = (long) d;
        if (L < Integer.MIN_VALUE || L > Integer.MAX_VALUE) {
            throw DogException.at(ins.line, ins.col, ins.sourceLine, "Array index is too large");
        }
        return (int) L;
    }

    private void checkJump(int target, int size, Instruction ins) {
        if (target < 0 || target > size) {
            throw DogException.at(ins.line, ins.col, ins.sourceLine,
                    "Bad jump target: " + target + " (code size=" + size + ")");
        }
    }

    private boolean isTruthy(Value v) {
        if (v == null)
            return false;
        if (v.isNil())
            return false;
        if (v.isBool())
            return v.boolVal;
        if (v.isString())
            return v.stringVal != null && !v.stringVal.isEmpty();
        if (v.isArray())
            return true;
        if (v.isFunction())
            return true;

        if (v.isNumber()) {
            if (v.kind == Value.Kind.DOUBLE)
                return v.doubleVal != 0.0;
            if (v.kind == Value.Kind.INT)
                return v.intVal != 0;
            if (v.kind == Value.Kind.LONG)
                return v.longVal != 0L;
            if (v.kind == Value.Kind.BIGINT)
                return !v.bigIntVal.equals(BigInteger.ZERO);
        }
        return true;
    }

    private boolean isEqual(Value a, Value b) {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;

        if (a.isNil() && b.isNil())
            return true;

        if (a.isNumber() && b.isNumber()) {
            if (a.kind == Value.Kind.DOUBLE || b.kind == Value.Kind.DOUBLE) {
                return Double.compare(a.toDouble(), b.toDouble()) == 0;
            }
            return a.toBigInteger().equals(b.toBigInteger());
        }

        if (a.kind != b.kind)
            return false;

        if (a.isString())
            return a.stringVal.equals(b.stringVal);
        if (a.isBool())
            return a.boolVal == b.boolVal;
        if (a.isArray())
            return a.arrayVal == b.arrayVal;
        if (a.isFunction())
            return a.funcProto == b.funcProto;
        return false;
    }

    private int numCompare(Value a, Value b) {
        if (a.kind == Value.Kind.DOUBLE || b.kind == Value.Kind.DOUBLE) {
            return Double.compare(a.toDouble(), b.toDouble());
        }
        return a.toBigInteger().compareTo(b.toBigInteger());
    }

    private Value numAdd(Value a, Value b) {
        if (a.kind == Value.Kind.DOUBLE || b.kind == Value.Kind.DOUBLE) {
            return Value.ofDouble(a.toDouble() + b.toDouble());
        }
        return Value.fromBigInteger(a.toBigInteger().add(b.toBigInteger()));
    }

    private Value numSub(Value a, Value b) {
        if (a.kind == Value.Kind.DOUBLE || b.kind == Value.Kind.DOUBLE) {
            return Value.ofDouble(a.toDouble() - b.toDouble());
        }
        return Value.fromBigInteger(a.toBigInteger().subtract(b.toBigInteger()));
    }

    private Value numMul(Value a, Value b) {
        if (a.kind == Value.Kind.DOUBLE || b.kind == Value.Kind.DOUBLE) {
            return Value.ofDouble(a.toDouble() * b.toDouble());
        }
        return Value.fromBigInteger(a.toBigInteger().multiply(b.toBigInteger()));
    }
}