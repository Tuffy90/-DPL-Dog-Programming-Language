import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JsonModule implements DogModule {

    private interface Fn {
        Value run(List<Value> args, DogContext ctx, int line, int col, String fullLine);
    }

    private final Map<String, Fn> fns = new HashMap<String, Fn>();

    public JsonModule() {

        // json.minify(text)
        fns.put("minify", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "minify", line, col, fullLine);
            String s = requireString(args.get(0), line, col, fullLine);
            return Value.str(minifyJsonLike(s));
        });

        // json.pretty(text, indentSpaces)
        fns.put("pretty", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "pretty", line, col, fullLine);
            String s = requireString(args.get(0), line, col, fullLine);
            int indent = requireInt(args.get(1), line, col, fullLine);
            if (indent < 0)
                indent = 0;
            return Value.str(prettyJsonLike(s, indent));
        });

        // -------- Files --------

        // json.read(path) -> string
        fns.put("read", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "read", line, col, fullLine);
            String path = requireString(args.get(0), line, col, fullLine);
            try {
                Path p = Paths.get(path);
                if (!Files.exists(p)) {
                    throw DogException.at(line, col, fullLine, "json.read: file not found: " + path);
                }
                byte[] bytes = Files.readAllBytes(p);
                return Value.str(new String(bytes, StandardCharsets.UTF_8));
            } catch (DogException e) {
                throw e;
            } catch (IOException e) {
                throw DogException.at(line, col, fullLine, "json.read IO error: " + e.getMessage());
            } catch (RuntimeException e) {
                throw DogException.at(line, col, fullLine, "json.read error: " + e.getMessage());
            }
        });

        // json.write(path, text) -> nil
        fns.put("write", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "write", line, col, fullLine);
            String path = requireString(args.get(0), line, col, fullLine);
            String text = requireString(args.get(1), line, col, fullLine);
            try {
                Path p = Paths.get(path);
                Path parent = p.toAbsolutePath().normalize().getParent();
                if (parent != null)
                    Files.createDirectories(parent);
                Files.write(p, text.getBytes(StandardCharsets.UTF_8));
                return Value.nil();
            } catch (DogException e) {
                throw e;
            } catch (IOException e) {
                throw DogException.at(line, col, fullLine, "json.write IO error: " + e.getMessage());
            } catch (RuntimeException e) {
                throw DogException.at(line, col, fullLine, "json.write error: " + e.getMessage());
            }
        });

        // json.exists(path) -> bool
        fns.put("exists", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "exists", line, col, fullLine);
            String path = requireString(args.get(0), line, col, fullLine);
            try {
                return Value.bool(Files.exists(Paths.get(path)));
            } catch (RuntimeException e) {
                throw DogException.at(line, col, fullLine, "json.exists error: " + e.getMessage());
            }
        });

        // json.size(path) -> long
        fns.put("size", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "size", line, col, fullLine);
            String path = requireString(args.get(0), line, col, fullLine);
            try {
                Path p = Paths.get(path);
                if (!Files.exists(p))
                    return Value.ofLong(0L);
                return Value.ofLong(Files.size(p));
            } catch (IOException e) {
                throw DogException.at(line, col, fullLine, "json.size IO error: " + e.getMessage());
            } catch (RuntimeException e) {
                throw DogException.at(line, col, fullLine, "json.size error: " + e.getMessage());
            }
        });

        // json.readPretty(path, indent) -> string
        fns.put("readPretty", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 2, "readPretty", line, col, fullLine);
            String path = requireString(args.get(0), line, col, fullLine);
            int indent = requireInt(args.get(1), line, col, fullLine);
            if (indent < 0)
                indent = 0;
            try {
                Path p = Paths.get(path);
                if (!Files.exists(p)) {
                    throw DogException.at(line, col, fullLine, "json.readPretty: file not found: " + path);
                }
                String s = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                return Value.str(prettyJsonLike(s, indent));
            } catch (DogException e) {
                throw e;
            } catch (IOException e) {
                throw DogException.at(line, col, fullLine, "json.readPretty IO error: " + e.getMessage());
            } catch (RuntimeException e) {
                throw DogException.at(line, col, fullLine, "json.readPretty error: " + e.getMessage());
            }
        });

        // json.writePretty(path, text, indent) -> nil
        fns.put("writePretty", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 3, "writePretty", line, col, fullLine);
            String path = requireString(args.get(0), line, col, fullLine);
            String text = requireString(args.get(1), line, col, fullLine);
            int indent = requireInt(args.get(2), line, col, fullLine);
            if (indent < 0)
                indent = 0;
            try {
                String pretty = prettyJsonLike(text, indent);
                Path p = Paths.get(path);
                Path parent = p.toAbsolutePath().normalize().getParent();
                if (parent != null)
                    Files.createDirectories(parent);
                Files.write(p, pretty.getBytes(StandardCharsets.UTF_8));
                return Value.nil();
            } catch (DogException e) {
                throw e;
            } catch (IOException e) {
                throw DogException.at(line, col, fullLine, "json.writePretty IO error: " + e.getMessage());
            } catch (RuntimeException e) {
                throw DogException.at(line, col, fullLine, "json.writePretty error: " + e.getMessage());
            }
        });

        // -------- Build JSON text --------

        // json.escape(str) -> string
        fns.put("escape", (args, ctx, line, col, fullLine) -> {
            requireCount(args, 1, "escape", line, col, fullLine);
            String s = requireString(args.get(0), line, col, fullLine);
            return Value.str(escapeJsonString(s));
        });

        // json.arr(a,b,c,...) -> string "[...]"
        fns.put("arr", (args, ctx, line, col, fullLine) -> {
            // any count allowed
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = 0; i < args.size(); i++) {
                if (i > 0)
                    sb.append(',');
                sb.append(toJsonValue(args.get(i), line, col, fullLine));
            }
            sb.append(']');
            return Value.str(sb.toString());
        });

        // json.obj(k1,v1,k2,v2,...) -> string "{...}"
        fns.put("obj", (args, ctx, line, col, fullLine) -> {
            if (args.size() % 2 != 0) {
                throw DogException.at(line, col, fullLine,
                        "json.obj(k1,v1,k2,v2,...): expected even number of arguments");
            }
            StringBuilder sb = new StringBuilder();
            sb.append('{');

            int pairIndex = 0;
            for (int i = 0; i < args.size(); i += 2) {
                Value k = args.get(i);
                Value v = args.get(i + 1);

                if (k == null || !k.isString()) {
                    throw DogException.at(line, col, fullLine,
                            "json.obj: key #" + (pairIndex + 1) + " must be a string");
                }

                if (pairIndex > 0)
                    sb.append(',');

                sb.append('"');
                sb.append(escapeJsonString(k.stringVal));
                sb.append('"');
                sb.append(':');
                sb.append(toJsonValue(v, line, col, fullLine));

                pairIndex++;
            }

            sb.append('}');
            return Value.str(sb.toString());
        });
    }

    @Override
    public String name() {
        return "json";
    }

    @Override
    public Value call(String member, List<Value> args, DogContext ctx, int line, int col, String fullLine) {
        Fn fn = fns.get(member);
        if (fn == null) {
            throw DogException.at(line, col, fullLine,
                    "Unknown json function: " + member + ". Available: " + String.join(", ", fns.keySet()));
        }
        return fn.run(args, ctx, line, col, fullLine);
    }

    @Override
    public Value getConstant(String member, DogContext ctx, int line, int col, String fullLine) {
        throw DogException.at(line, col, fullLine, "json has no constants. Use functions like json.obj/json.arr");
    }

    // --- helpers: args ---
    private static void requireCount(List<Value> args, int n, String fn, int line, int col, String fullLine) {
        if (args.size() != n) {
            throw DogException.at(line, col, fullLine, "json." + fn + "(...) expects " + n + " argument(s)");
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
        long L = (long) d;
        if (L < Integer.MIN_VALUE || L > Integer.MAX_VALUE) {
            throw DogException.at(line, col, fullLine, "Integer out of range");
        }
        return (int) L;
    }

    // --- helpers: json formatting ---
    // Not a full JSON parser. Whitespace-safe minify/pretty for typical JSON-like
    // text.
    private static String minifyJsonLike(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean inStr = false;
        boolean esc = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (inStr) {
                out.append(c);
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }

            if (c == '"') {
                inStr = true;
                out.append(c);
                continue;
            }

            if (!Character.isWhitespace(c))
                out.append(c);
        }

        return out.toString();
    }

    private static String prettyJsonLike(String s, int indent) {
        String min = minifyJsonLike(s);
        StringBuilder out = new StringBuilder(min.length() + 64);

        boolean inStr = false;
        boolean esc = false;
        int level = 0;

        for (int i = 0; i < min.length(); i++) {
            char c = min.charAt(i);

            if (inStr) {
                out.append(c);
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }

            if (c == '"') {
                inStr = true;
                out.append(c);
                continue;
            }

            if (c == '{' || c == '[') {
                out.append(c);
                out.append('\n');
                level++;
                appendIndent(out, level, indent);
                continue;
            }

            if (c == '}' || c == ']') {
                out.append('\n');
                level = Math.max(0, level - 1);
                appendIndent(out, level, indent);
                out.append(c);
                continue;
            }

            if (c == ',') {
                out.append(c);
                out.append('\n');
                appendIndent(out, level, indent);
                continue;
            }

            if (c == ':') {
                out.append(": ");
                continue;
            }

            out.append(c);
        }

        return out.toString();
    }

    private static void appendIndent(StringBuilder sb, int level, int indent) {
        for (int i = 0; i < level * indent; i++)
            sb.append(' ');
    }

    // --- helpers: escape/build json values ---
    private static String escapeJsonString(String s) {
        if (s == null)
            return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        out.append("\\u");
                        for (int k = hex.length(); k < 4; k++)
                            out.append('0');
                        out.append(hex);
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    // Converts DPL Value to JSON literal string (no surrounding spaces).
    // Supports: string, number, bool, nil, array(Value arrayVal).
    private static String toJsonValue(Value v, int line, int col, String fullLine) {
        if (v == null || v.isNil())
            return "null";

        if (v.isBool())
            return v.boolVal ? "true" : "false";

        if (v.isString()) {
            return "\"" + escapeJsonString(v.stringVal) + "\"";
        }

        if (v.isNumber()) {
            // keep it JSON-friendly: numbers as printable
            // your Value.printable() likely returns clean number text
            String num = v.printable();
            if (num == null || num.isEmpty())
                num = "0";
            return num;
        }

        if (v.isArray()) {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = 0; i < v.arrayVal.size(); i++) {
                if (i > 0)
                    sb.append(',');
                sb.append(toJsonValue(v.arrayVal.get(i), line, col, fullLine));
            }
            sb.append(']');
            return sb.toString();
        }

        // fallback: stringify as JSON string
        return "\"" + escapeJsonString(v.printable()) + "\"";
    }
}
