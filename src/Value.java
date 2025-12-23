public final class Value {
    public enum Kind {
        NUMBER, STRING, BOOL, NIL
    }

    public final Kind kind;
    public final double number;
    public final String string;
    public final boolean bool;

    private Value(Kind kind, double number, String string, boolean bool) {
        this.kind = kind;
        this.number = number;
        this.string = string;
        this.bool = bool;
    }

    public static Value num(double n) {
        return new Value(Kind.NUMBER, n, null, false);
    }

    public static Value str(String s) {
        return new Value(Kind.STRING, 0, s, false);
    }

    public static Value bool(boolean b) {
        return new Value(Kind.BOOL, 0, null, b);
    }

    public static Value nil() {
        return new Value(Kind.NIL, 0, null, false);
    }

    public boolean isNumber() {
        return kind == Kind.NUMBER;
    }

    public boolean isString() {
        return kind == Kind.STRING;
    }

    public boolean isBool() {
        return kind == Kind.BOOL;
    }

    public boolean isNil() {
        return kind == Kind.NIL;
    }

    public String printable() {
        if (isString())
            return string;
        if (isBool())
            return bool ? "true" : "false";
        if (isNil())
            return "nil";

        double v = number;
        if (v == Math.rint(v))
            return String.valueOf((long) v);
        return String.valueOf(v);
    }
}