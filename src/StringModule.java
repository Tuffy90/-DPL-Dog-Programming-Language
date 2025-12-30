import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StringModule implements DogModule {

    private interface Fn {
        Value run(List<Value> args, DogContext ctx, int line, int col, String fullLine);
    }

    private final Map<String, Fn> fns = new HashMap<String, Fn>();

    public StringModule() {
        fns.put("len", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "len", line, col, fullLine);
            String s = requireString(args.get(0), line, col, fullLine);
            return Value.ofInt(s.length());
        });

        fns.put("upper", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "upper", line, col, fullLine);
            return Value.str(requireString(args.get(0), line, col, fullLine).toUpperCase());
        });

        fns.put("lower", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "lower", line, col, fullLine);
            return Value.str(requireString(args.get(0), line, col, fullLine).toLowerCase());
        });

        fns.put("trim", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "trim", line, col, fullLine);
            return Value.str(requireString(args.get(0), line, col, fullLine).trim());
        });

        fns.put("contains", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "contains", line, col, fullLine);
            String s = requireString(args.get(0), line, col, fullLine);
            String sub = requireString(args.get(1), line, col, fullLine);
            return Value.bool(s.contains(sub));
        });

        fns.put("replace", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 3, "replace", line, col, fullLine);
            String s = requireString(args.get(0), line, col, fullLine);
            String a = requireString(args.get(1), line, col, fullLine);
            String b = requireString(args.get(2), line, col, fullLine);
            return Value.str(s.replace(a, b));
        });

        // str.split("a,b,c", ",") -> ["a","b","c"]
        fns.put("split", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "split", line, col, fullLine);
            String s = requireString(args.get(0), line, col, fullLine);
            String sep = requireString(args.get(1), line, col, fullLine);
            String[] parts = sep.isEmpty() ? s.split("") : s.split(java.util.regex.Pattern.quote(sep), -1);
            ArrayList<Value> out = new ArrayList<Value>(parts.length);
            for (String p : parts)
                out.add(Value.str(p));
            return Value.array(out);
        });

        // str.join(["a","b"], "-") -> "a-b"
        fns.put("join", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "join", line, col, fullLine);
            Value arr = args.get(0);
            String sep = requireString(args.get(1), line, col, fullLine);
            if (arr == null || !arr.isArray()) {
                throw DogException.at(line, col, fullLine, "str.join(arr, sep): arr must be an array");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.arrayVal.size(); i++) {
                if (i > 0)
                    sb.append(sep);
                sb.append(arr.arrayVal.get(i).printable());
            }
            return Value.str(sb.toString());
        });

        // str.sub("hello", 1, 4) -> "ell"
        fns.put("sub", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 3, "sub", line, col, fullLine);
            String s = requireString(args.get(0), line, col, fullLine);
            int a = requireInt(args.get(1), line, col, fullLine);
            int b = requireInt(args.get(2), line, col, fullLine);
            if (a < 0 || b < 0 || a > s.length() || b > s.length() || a > b) {
                throw DogException.at(line, col, fullLine, "str.sub(s,a,b): bad range");
            }
            return Value.str(s.substring(a, b));
        });
    }

    @Override
    public String name() {
        return "string";
    }

    @Override
    public Value call(String member, List<Value> args, DogContext ctx, int line, int col, String fullLine) {
        Fn fn = fns.get(member);
        if (fn == null) {
            throw DogException.at(line, col, fullLine,
                    "Unknown str function: " + member + ". Available: " + String.join(", ", fns.keySet()));
        }
        return fn.run(args, ctx, line, col, fullLine);
    }

    @Override
    public Value getConstant(String member, DogContext ctx, int line, int col, String fullLine) {
        throw DogException.at(line, col, fullLine, "str has no constants. Use functions like str.len(...)");
    }

    private static void requireCount(List<Value> args, int n, String fn, int line, int col, String fullLine) {
        if (args.size() != n) {
            throw DogException.at(line, col, fullLine, "str." + fn + "(...) expects " + n + " argument(s)");
        }
    }

    private static String requireString(Value v, int line, int col, String fullLine) {
        if (v == null || !v.isString()) {
            throw DogException.at(line, col, fullLine, "Expected a string argument");
        }
        return v.stringVal;
    }

    private static int requireInt(Value v, int line, int col, String fullLine) {
        if (v == null || !v.isNumber()) {
            throw DogException.at(line, col, fullLine, "Expected a number argument");
        }
        double d = v.toDouble();
        if (d != Math.rint(d)) {
            throw DogException.at(line, col, fullLine, "Expected an integer argument");
        }
        return (int) d;
    }
}