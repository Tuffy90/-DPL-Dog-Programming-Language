import java.util.List;

public final class MathModule implements DogModule {

    @Override
    public String name() {
        return "math";
    }

    @Override
    public Value call(String member, List<Value> args, DogContext ctx, int line, int col, String fullLine) {
        if (member.equals("sqrt")) {
            requireCount(args, 1, member, line, col, fullLine);
            return Value.num(Math.sqrt(requireNumber(args.get(0), line, col, fullLine)));
        }
        if (member.equals("pow")) {
            requireCount(args, 2, member, line, col, fullLine);
            double a = requireNumber(args.get(0), line, col, fullLine);
            double b = requireNumber(args.get(1), line, col, fullLine);
            return Value.num(Math.pow(a, b));
        }
        if (member.equals("abs")) {
            requireCount(args, 1, member, line, col, fullLine);
            return Value.num(Math.abs(requireNumber(args.get(0), line, col, fullLine)));
        }
        if (member.equals("floor")) {
            requireCount(args, 1, member, line, col, fullLine);
            return Value.num(Math.floor(requireNumber(args.get(0), line, col, fullLine)));
        }
        if (member.equals("ceil")) {
            requireCount(args, 1, member, line, col, fullLine);
            return Value.num(Math.ceil(requireNumber(args.get(0), line, col, fullLine)));
        }
        if (member.equals("round")) {
            requireCount(args, 1, member, line, col, fullLine);
            return Value.num(Math.rint(requireNumber(args.get(0), line, col, fullLine)));
        }

        throw DogException.at(line, col, fullLine,
                "Unknown math function: " + member + ". Available: sqrt, pow, abs, floor, ceil, round");
    }

    @Override
    public Value getConstant(String member, DogContext ctx, int line, int col, String fullLine) {
        if (member.equals("PI"))
            return Value.num(Math.PI);
        if (member.equals("E"))
            return Value.num(Math.E);

        throw DogException.at(line, col, fullLine,
                "Unknown math constant: " + member + ". Available: PI, E");
    }

    private static void requireCount(List<Value> args, int n, String fn, int line, int col, String fullLine) {
        if (args.size() != n) {
            throw DogException.at(line, col, fullLine,
                    "math." + fn + "(...) expects " + n + " argument(s)");
        }
    }

    private static double requireNumber(Value v, int line, int col, String fullLine) {
        if (!v.isNumber()) {
            throw DogException.at(line, col, fullLine, "Expected a number argument");
        }
        return v.number;
    }
}