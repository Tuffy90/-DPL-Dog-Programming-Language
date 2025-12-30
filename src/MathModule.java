import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class MathModule implements DogModule {

    private static final Random RNG = new Random();

    private interface Fn {
        Value run(List<Value> args, DogContext ctx, int line, int col, String fullLine);
    }

    private final Map<String, Fn> fns = new HashMap<String, Fn>();

    public MathModule() {
        fns.put("sqrt", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "sqrt", line, col, fullLine);
            double x = requireNumber(args.get(0), line, col, fullLine);
            if (x < 0)
                throw DogException.at(line, col, fullLine, "math.sqrt(x): x must be >= 0");
            return Value.ofDouble(Math.sqrt(x));
        });
        fns.put("pow", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "pow", line, col, fullLine);
            double a = requireNumber(args.get(0), line, col, fullLine);
            double b = requireNumber(args.get(1), line, col, fullLine);
            return Value.ofDouble(Math.pow(a, b));
        });
        fns.put("abs", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "abs", line, col, fullLine);
            double x = requireNumber(args.get(0), line, col, fullLine);
            return Value.ofDouble(Math.abs(x));
        });
        fns.put("floor", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "floor", line, col, fullLine);
            double x = requireNumber(args.get(0), line, col, fullLine);
            return Value.ofDouble(Math.floor(x));
        });
        fns.put("ceil", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "ceil", line, col, fullLine);
            double x = requireNumber(args.get(0), line, col, fullLine);
            return Value.ofDouble(Math.ceil(x));
        });
        fns.put("round", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "round", line, col, fullLine);
            double x = requireNumber(args.get(0), line, col, fullLine);
            return Value.ofLong(Math.round(x));
        });
        fns.put("min", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "min", line, col, fullLine);
            return Value.ofDouble(Math.min(
                    requireNumber(args.get(0), line, col, fullLine),
                    requireNumber(args.get(1), line, col, fullLine)));
        });
        fns.put("max", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "max", line, col, fullLine);
            return Value.ofDouble(Math.max(
                    requireNumber(args.get(0), line, col, fullLine),
                    requireNumber(args.get(1), line, col, fullLine)));
        });
        fns.put("clamp", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 3, "clamp", line, col, fullLine);
            double x = requireNumber(args.get(0), line, col, fullLine);
            double a = requireNumber(args.get(1), line, col, fullLine);
            double b = requireNumber(args.get(2), line, col, fullLine);
            double lo = Math.min(a, b);
            double hi = Math.max(a, b);
            if (x < lo)
                x = lo;
            if (x > hi)
                x = hi;
            return Value.ofDouble(x);
        });
        fns.put("sign", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "sign", line, col, fullLine);
            double x = requireNumber(args.get(0), line, col, fullLine);
            if (x > 0)
                return Value.ofInt(1);
            if (x < 0)
                return Value.ofInt(-1);
            return Value.ofInt(0);
        });
        fns.put("mod", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "mod", line, col, fullLine);
            double a = requireNumber(args.get(0), line, col, fullLine);
            double b = requireNumber(args.get(1), line, col, fullLine);
            if (b == 0.0)
                throw DogException.at(line, col, fullLine, "math.mod(a,b): b must be != 0");
            double r = a % b;
            return Value.ofDouble(r);
        });
        fns.put("rand", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 0, "rand", line, col, fullLine);
            return Value.ofDouble(RNG.nextDouble());
        });
        fns.put("randInt", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "randInt", line, col, fullLine);
            long a = (long) requireNumber(args.get(0), line, col, fullLine);
            long b = (long) requireNumber(args.get(1), line, col, fullLine);
            long lo = Math.min(a, b);
            long hi = Math.max(a, b);
            long bound = (hi - lo) + 1;
            if (bound <= 0) {
                throw DogException.at(line, col, fullLine, "math.randInt(min,max): range too large");
            }
            long v = lo + (Math.abs(RNG.nextLong()) % bound);
            if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE)
                return Value.ofInt((int) v);
            return Value.ofLong(v);
        });
        fns.put("toInt", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "toInt", line, col, fullLine);
            double x = requireNumber(args.get(0), line, col, fullLine);
            return Value.ofInt((int) x);
        });
        fns.put("toLong", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "toLong", line, col, fullLine);
            double x = requireNumber(args.get(0), line, col, fullLine);
            return Value.ofLong((long) x);
        });
        fns.put("toDouble", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "toDouble", line, col, fullLine);
            double x = requireNumber(args.get(0), line, col, fullLine);
            return Value.ofDouble(x);
        });
    }

    @Override
    public String name() {
        return "math";
    }

    @Override
    public Value call(String member, List<Value> args, DogContext ctx, int line, int col, String fullLine) {
        Fn fn = fns.get(member);
        if (fn == null) {
            throw DogException.at(line, col, fullLine,
                    "Unknown math function: " + member + ". Available: " + String.join(", ", fns.keySet()));
        }
        return fn.run(args, ctx, line, col, fullLine);
    }

    @Override
    public Value getConstant(String member, DogContext ctx, int line, int col, String fullLine) {
        if (member.equals("PI"))
            return Value.ofDouble(Math.PI);
        if (member.equals("E"))
            return Value.ofDouble(Math.E);
        if (member.equals("TAU"))
            return Value.ofDouble(Math.PI * 2.0);
        throw DogException.at(line, col, fullLine,
                "Unknown math constant: " + member + ". Available: PI, E, TAU");
    }

    private static void requireCount(List<Value> args, int n, String fn, int line, int col, String fullLine) {
        if (args.size() != n) {
            throw DogException.at(line, col, fullLine,
                    "math." + fn + "(...) expects " + n + " argument(s)");
        }
    }

    private static double requireNumber(Value v, int line, int col, String fullLine) {
        if (v == null || !v.isNumber()) {
            throw DogException.at(line, col, fullLine, "Expected a number argument");
        }
        return v.toDouble();
    }
}