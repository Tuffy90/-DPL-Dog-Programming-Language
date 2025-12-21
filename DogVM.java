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

                    case CONST_NUM:
                        stack.add(Value.num(ins.num));
                        break;

                    case CONST_STR:
                        stack.add(Value.str(ins.text));
                        break;

                    case ADD: {
                        Value b = pop(ins);
                        Value a = pop(ins);
                        if (a.isString() || b.isString()) {
                            stack.add(Value.str(a.printable() + b.printable()));
                        } else {
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

                    case PRINT: {
                        Value v = pop(ins);
                        System.out.println(v.printable());
                        break;
                    }

                    case POP: {
                        pop(ins);
                        break;
                    }
                }

            } catch (DogException e) {
                throw e;
            } catch (RuntimeException e) {
                throw DogException.at(ins.line, ins.col, ins.sourceLine,
                        "Runtime error: " + e.getMessage());
            }
        }
    }

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
}