
// File: Value.java
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Value {

    public enum Kind {
        INT, LONG, DOUBLE, BIGINT,
        STRING, BOOL, NIL,
        ARRAY,
        FUNCTION
    }

    public final Kind kind;

    public final int intVal;
    public final long longVal;
    public final double doubleVal;
    public final BigInteger bigIntVal;

    public final String stringVal;
    public final boolean boolVal;

    public final ArrayList<Value> arrayVal;

    // function payload
    public final FunctionProto funcProto;
    public final Map<String, Value> closure; // captured snapshot

    private Value(Kind kind,
            int intVal, long longVal, double doubleVal, BigInteger bigIntVal,
            String stringVal, boolean boolVal,
            ArrayList<Value> arrayVal,
            FunctionProto funcProto, Map<String, Value> closure) {

        this.kind = kind;
        this.intVal = intVal;
        this.longVal = longVal;
        this.doubleVal = doubleVal;
        this.bigIntVal = bigIntVal;

        this.stringVal = stringVal;
        this.boolVal = boolVal;

        this.arrayVal = arrayVal;

        this.funcProto = funcProto;
        this.closure = closure;
    }

    // ---- constructors ----
    public static Value ofInt(int v) {
        return new Value(Kind.INT, v, 0L, 0.0, null, null, false, null, null, null);
    }

    public static Value ofLong(long v) {
        return new Value(Kind.LONG, 0, v, 0.0, null, null, false, null, null, null);
    }

    public static Value ofDouble(double v) {
        return new Value(Kind.DOUBLE, 0, 0L, v, null, null, false, null, null, null);
    }

    public static Value ofBigInt(BigInteger v) {
        if (v == null)
            v = BigInteger.ZERO;
        return new Value(Kind.BIGINT, 0, 0L, 0.0, v, null, false, null, null, null);
    }

    public static Value str(String s) {
        return new Value(Kind.STRING, 0, 0L, 0.0, null, s == null ? "" : s, false, null, null, null);
    }

    public static Value bool(boolean b) {
        return new Value(Kind.BOOL, 0, 0L, 0.0, null, null, b, null, null, null);
    }

    public static Value nil() {
        return new Value(Kind.NIL, 0, 0L, 0.0, null, null, false, null, null, null);
    }

    public static Value array(List<Value> items) {
        ArrayList<Value> a = new ArrayList<Value>();
        if (items != null)
            a.addAll(items);
        return new Value(Kind.ARRAY, 0, 0L, 0.0, null, null, false, a, null, null);
    }

    public static Value function(FunctionProto proto, Map<String, Value> closure) {
        if (proto == null)
            throw new IllegalArgumentException("proto is null");
        Map<String, Value> cap = (closure == null) ? Collections.emptyMap() : closure;
        return new Value(Kind.FUNCTION, 0, 0L, 0.0, null, null, false, null, proto, cap);
    }

    // ---- checks ----
    public boolean isNumber() {
        return kind == Kind.INT || kind == Kind.LONG || kind == Kind.DOUBLE || kind == Kind.BIGINT;
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

    public boolean isArray() {
        return kind == Kind.ARRAY;
    }

    public boolean isFunction() {
        return kind == Kind.FUNCTION;
    }

    // ---- conversions ----
    public double toDouble() {
        switch (kind) {
            case INT:
                return (double) intVal;
            case LONG:
                return (double) longVal;
            case DOUBLE:
                return doubleVal;
            case BIGINT:
                return bigIntVal.doubleValue();
            default:
                return 0.0;
        }
    }

    public BigInteger toBigInteger() {
        switch (kind) {
            case INT:
                return BigInteger.valueOf((long) intVal);
            case LONG:
                return BigInteger.valueOf(longVal);
            case BIGINT:
                return bigIntVal;
            case DOUBLE:
                return BigInteger.valueOf((long) doubleVal);
            default:
                return BigInteger.ZERO;
        }
    }

    public static Value fromBigInteger(BigInteger bi) {
        if (bi == null)
            return Value.ofInt(0);

        if (bi.bitLength() <= 31)
            return Value.ofInt(bi.intValue());
        if (bi.bitLength() <= 63)
            return Value.ofLong(bi.longValue());
        return Value.ofBigInt(bi);
    }

    // ---- printable ----
    public String printable() {
        switch (kind) {
            case STRING:
                return stringVal;
            case BOOL:
                return boolVal ? "true" : "false";
            case NIL:
                return "nil";

            case INT:
                return String.valueOf(intVal);
            case LONG:
                return String.valueOf(longVal);
            case BIGINT:
                return bigIntVal.toString();

            case DOUBLE: {
                double v = doubleVal;
                if (v == Math.rint(v))
                    return String.valueOf((long) v);
                return String.valueOf(v);
            }

            case ARRAY: {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                for (int i = 0; i < arrayVal.size(); i++) {
                    if (i > 0)
                        sb.append(", ");
                    sb.append(arrayVal.get(i).printable());
                }
                sb.append("]");
                return sb.toString();
            }

            case FUNCTION:
                return "<fn(" + String.join(",", funcProto.params) + ")>";
        }
        return "?";
    }
}