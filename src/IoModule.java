import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IoModule implements DogModule {

    private interface Fn {
        Value run(List<Value> args, DogContext ctx, int line, int col, String fullLine);
    }

    private final Map<String, Fn> fns = new HashMap<String, Fn>();

    public IoModule() {
        // --- print/println ---
        fns.put("print", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "print", line, col, fullLine);
            System.out.print(args.get(0).printable());
            return Value.nil();
        });

        fns.put("println", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "println", line, col, fullLine);
            System.out.println(args.get(0).printable());
            return Value.nil();
        });

        // --- тип/длина ---
        fns.put("typeOf", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "typeOf", line, col, fullLine);
            return Value.str(typeName(args.get(0)));
        });

        fns.put("len", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "len", line, col, fullLine);
            Value v = args.get(0);
            if (v.isString())
                return Value.ofInt(v.stringVal.length());
            if (v.isArray())
                return Value.ofInt(v.arrayVal.size());
            throw DogException.at(line, col, fullLine, "io.len(x): x must be STRING or ARRAY");
        });

        // --- строки ---
        fns.put("split", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "split", line, col, fullLine);
            String text = requireString(args.get(0), line, col, fullLine);
            String sep = requireString(args.get(1), line, col, fullLine);
            String[] parts = sep.isEmpty() ? new String[] { text } : text.split(java.util.regex.Pattern.quote(sep), -1);
            ArrayList<Value> out = new ArrayList<Value>();
            for (String p : parts)
                out.add(Value.str(p));
            return Value.array(out);
        });

        fns.put("join", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "join", line, col, fullLine);
            Value arr = requireArray(args.get(0), line, col, fullLine);
            String sep = requireString(args.get(1), line, col, fullLine);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.arrayVal.size(); i++) {
                if (i > 0)
                    sb.append(sep);
                sb.append(arr.arrayVal.get(i).printable());
            }
            return Value.str(sb.toString());
        });

        // --- массивы (самое нужное) ---
        fns.put("push", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "push", line, col, fullLine);
            Value arr = requireArray(args.get(0), line, col, fullLine);
            arr.arrayVal.add(args.get(1));
            return arr; // возвращаем тот же массив
        });

        fns.put("pop", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "pop", line, col, fullLine);
            Value arr = requireArray(args.get(0), line, col, fullLine);
            if (arr.arrayVal.isEmpty())
                return Value.nil();
            return arr.arrayVal.remove(arr.arrayVal.size() - 1);
        });

        fns.put("get", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "get", line, col, fullLine);
            Value arr = requireArray(args.get(0), line, col, fullLine);
            int idx = requireIntIndex(args.get(1), line, col, fullLine);
            if (idx < 0 || idx >= arr.arrayVal.size())
                return Value.nil();
            return arr.arrayVal.get(idx);
        });

        fns.put("set", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 3, "set", line, col, fullLine);
            Value arr = requireArray(args.get(0), line, col, fullLine);
            int idx = requireIntIndex(args.get(1), line, col, fullLine);
            if (idx < 0 || idx >= arr.arrayVal.size()) {
                throw DogException.at(line, col, fullLine, "io.set(arr, idx, v): idx out of bounds");
            }
            arr.arrayVal.set(idx, args.get(2));
            return arr;
        });

        // --- файлы (минимально, но полезно) ---
        fns.put("readFile", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "readFile", line, col, fullLine);
            String path = requireString(args.get(0), line, col, fullLine);
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(path));
                return Value.str(new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw DogException.at(line, col, fullLine, "io.readFile(path) failed: " + e.getMessage());
            }
        });

        fns.put("writeFile", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "writeFile", line, col, fullLine);
            String path = requireString(args.get(0), line, col, fullLine);
            String text = requireString(args.get(1), line, col, fullLine);
            try {
                Path p = Paths.get(path);
                Path parent = p.toAbsolutePath().normalize().getParent();
                if (parent != null)
                    Files.createDirectories(parent);
                Files.write(p, text.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return Value.nil();
            } catch (Exception e) {
                throw DogException.at(line, col, fullLine, "io.writeFile(path,text) failed: " + e.getMessage());
            }
        });

        fns.put("appendFile", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "appendFile", line, col, fullLine);
            String path = requireString(args.get(0), line, col, fullLine);
            String text = requireString(args.get(1), line, col, fullLine);
            try {
                Path p = Paths.get(path);
                Path parent = p.toAbsolutePath().normalize().getParent();
                if (parent != null)
                    Files.createDirectories(parent);
                Files.write(p, text.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return Value.nil();
            } catch (Exception e) {
                throw DogException.at(line, col, fullLine, "io.appendFile(path,text) failed: " + e.getMessage());
            }
        });

        fns.put("exists", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "exists", line, col, fullLine);
            String path = requireString(args.get(0), line, col, fullLine);
            return Value.bool(Files.exists(Paths.get(path)));
        });

        fns.put("listDir", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "listDir", line, col, fullLine);
            String path = requireString(args.get(0), line, col, fullLine);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(Paths.get(path))) {
                ArrayList<Value> out = new ArrayList<Value>();
                for (Path p : ds)
                    out.add(Value.str(p.getFileName().toString()));
                return Value.array(out);
            } catch (Exception e) {
                throw DogException.at(line, col, fullLine, "io.listDir(path) failed: " + e.getMessage());
            }
        });
    }

    @Override
    public String name() {
        return "io";
    }

    @Override
    public Value call(String member, List<Value> args, DogContext ctx, int line, int col, String fullLine) {
        Fn fn = fns.get(member);
        if (fn == null) {
            throw DogException.at(line, col, fullLine,
                    "Unknown io function: " + member + ". Available: " + String.join(", ", fns.keySet()));
        }
        return fn.run(args, ctx, line, col, fullLine);
    }

    @Override
    public Value getConstant(String member, DogContext ctx, int line, int col, String fullLine) {
        throw DogException.at(line, col, fullLine, "Module 'io' has no constants");
    }

    // ---- helpers ----
    private static void requireCount(List<Value> args, int n, String fn, int line, int col, String fullLine) {
        if (args.size() != n) {
            throw DogException.at(line, col, fullLine,
                    "io." + fn + "(...) expects " + n + " argument(s)");
        }
    }

    private static String requireString(Value v, int line, int col, String fullLine) {
        if (v == null || !v.isString()) {
            throw DogException.at(line, col, fullLine, "Expected STRING argument");
        }
        return v.stringVal;
    }

    private static Value requireArray(Value v, int line, int col, String fullLine) {
        if (v == null || !v.isArray()) {
            throw DogException.at(line, col, fullLine, "Expected ARRAY argument");
        }
        return v;
    }

    private static int requireIntIndex(Value v, int line, int col, String fullLine) {
        if (v == null || !v.isNumber()) {
            throw DogException.at(line, col, fullLine, "Expected numeric index");
        }
        return (int) v.toDouble();
    }

    private static String typeName(Value v) {
        if (v == null)
            return "nil";
        switch (v.kind) {
            case INT:
                return "int";
            case LONG:
                return "long";
            case DOUBLE:
                return "double";
            case BIGINT:
                return "bigint";
            case STRING:
                return "string";
            case BOOL:
                return "bool";
            case NIL:
                return "nil";
            case ARRAY:
                return "array";
        }
        return "unknown";
    }
}