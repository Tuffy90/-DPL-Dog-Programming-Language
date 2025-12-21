public final class Value {
    public enum Kind {
        NUMBER, STRING
    }

    public final Kind kind;
    public final double number;
    public final String string;

    private Value(Kind kind, double number, String string) {
        this.kind = kind;
        this.number = number;
        this.string = string;
    }

    public static Value num(double n) {
        return new Value(Kind.NUMBER, n, null);
    }

    public static Value str(String s) {
        return new Value(Kind.STRING, 0, s);
    }

    public boolean isNumber() {
        return kind == Kind.NUMBER;
    }

    public boolean isString() {
        return kind == Kind.STRING;
    }

    public String printable() {
        if (isString())
            return string;
        double v = number;
        if (v == Math.rint(v))
            return String.valueOf((long) v);
        return String.valueOf(v);
    }
}