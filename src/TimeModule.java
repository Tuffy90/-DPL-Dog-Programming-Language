import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class TimeModule implements DogModule {

    @Override
    public String name() {
        return "time";
    }

    @Override
    public Value call(String member, List<Value> args, DogContext ctx, int line, int col, String fullLine) {
        switch (member) {
            case "now":
            case "nowMillis":
                requireCount(args, 0, member, line, col, fullLine);
                return Value.ofLong(System.currentTimeMillis());
            case "nowSeconds":
                requireCount(args, 0, member, line, col, fullLine);
                return Value.ofLong(Instant.now().getEpochSecond());
            case "sleep":
                requireCount(args, 1, "sleep", line, col, fullLine);
                long ms = requireLong(args.get(0), line, col, fullLine);
                if (ms < 0)
                    throw DogException.at(line, col, fullLine, "time.sleep(ms): must be >= 0");
                try {
                    Thread.sleep(ms);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Value.nil();
            case "formatNow":
                requireCount(args, 1, "formatNow", line, col, fullLine);
                String pattern = requireString(args.get(0), line, col, fullLine);
                return Value.str(LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern)));
            case "formatMillis":
                requireCount(args, 2, "formatMillis", line, col, fullLine);
                long ms2 = requireLong(args.get(0), line, col, fullLine);
                String pat = requireString(args.get(1), line, col, fullLine);
                DateTimeFormatter f2 = DateTimeFormatter.ofPattern(pat);
                LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms2), ZoneId.systemDefault());
                return Value.str(dt.format(f2));
            case "isoNow":
                requireCount(args, 0, "isoNow", line, col, fullLine);
                return Value.str(Instant.now().toString());
            case "isoMillis":
                requireCount(args, 1, "isoMillis", line, col, fullLine);
                long msIso = requireLong(args.get(0), line, col, fullLine);
                return Value.str(Instant.ofEpochMilli(msIso).toString());
            case "zone":
                requireCount(args, 0, "zone", line, col, fullLine);
                return Value.str(ZoneId.systemDefault().getId());
            case "offsetSeconds":
                requireCount(args, 0, "offsetSeconds", line, col, fullLine);
                ZonedDateTime zdt = ZonedDateTime.now(ZoneId.systemDefault());
                return Value.ofInt(zdt.getOffset().getTotalSeconds());
            case "parseMillis":
                requireCount(args, 2, "parseMillis", line, col, fullLine);
                String text = requireString(args.get(0), line, col, fullLine);
                String fmtStr = requireString(args.get(1), line, col, fullLine);
                try {
                    DateTimeFormatter fmtP = DateTimeFormatter.ofPattern(fmtStr);
                    LocalDateTime ldt = LocalDateTime.parse(text, fmtP);
                    long outMs = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    return Value.ofLong(outMs);
                } catch (IllegalArgumentException e) {
                    throw DogException.at(line, col, fullLine,
                            "time.parseMillis: bad format pattern: " + e.getMessage());
                } catch (DateTimeParseException e) {
                    throw DogException.at(line, col, fullLine,
                            "time.parseMillis: can't parse date: " + e.getParsedString());
                }
            case "year":
                requireCount(args, 0, "year", line, col, fullLine);
                return Value.ofInt(LocalDateTime.now().getYear());
            case "month":
                requireCount(args, 0, "month", line, col, fullLine);
                return Value.ofInt(LocalDateTime.now().getMonthValue());
            case "day":
                requireCount(args, 0, "day", line, col, fullLine);
                return Value.ofInt(LocalDateTime.now().getDayOfMonth());
            case "hour":
                requireCount(args, 0, "hour", line, col, fullLine);
                return Value.ofInt(LocalDateTime.now().getHour());
            case "minute":
                requireCount(args, 0, "minute", line, col, fullLine);
                return Value.ofInt(LocalDateTime.now().getMinute());
            case "second":
                requireCount(args, 0, "second", line, col, fullLine);
                return Value.ofInt(LocalDateTime.now().getSecond());
            case "weekday":
                requireCount(args, 0, "weekday", line, col, fullLine);
                return Value.ofInt(LocalDateTime.now().getDayOfWeek().getValue());
            case "dayOfYear":
                requireCount(args, 0, "dayOfYear", line, col, fullLine);
                return Value.ofInt(LocalDateTime.now().getDayOfYear());
            case "measureStart":
                requireCount(args, 0, "measureStart", line, col, fullLine);
                return Value.ofLong(System.nanoTime() / 1_000_000L);
            case "measureEnd":
                requireCount(args, 1, "measureEnd", line, col, fullLine);
                long startMs = requireLong(args.get(0), line, col, fullLine);
                long nowMs = System.nanoTime() / 1_000_000L;
                return Value.ofLong(nowMs - startMs);
            case "deadline": {
                requireCount(args, 1, "deadline", line, col, fullLine);
                long delta = requireLong(args.get(0), line, col, fullLine);
                return Value.ofLong(System.currentTimeMillis() + delta);
            }
            case "expired": {
                requireCount(args, 1, "expired", line, col, fullLine);
                long deadline = requireLong(args.get(0), line, col, fullLine);
                return Value.bool(System.currentTimeMillis() >= deadline);
            }

            case "waitUntil": {
                requireCount(args, 1, "waitUntil", line, col, fullLine);
                long deadline = requireLong(args.get(0), line, col, fullLine);
                long now = System.currentTimeMillis();
                long left = deadline - now;
                if (left > 0) {
                    try {
                        Thread.sleep(left);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return Value.nil();
            }
            case "diff": {
                requireCount(args, 2, "diff", line, col, fullLine);
                long a = requireLong(args.get(0), line, col, fullLine);
                long b = requireLong(args.get(1), line, col, fullLine);
                return Value.ofLong(b - a);
            }
            case "max": {
                requireCount(args, 2, "max", line, col, fullLine);
                long a = requireLong(args.get(0), line, col, fullLine);
                long b = requireLong(args.get(1), line, col, fullLine);
                return Value.ofLong(Math.max(a, b));
            }
            case "min": {
                requireCount(args, 2, "min", line, col, fullLine);
                long a = requireLong(args.get(0), line, col, fullLine);
                long b = requireLong(args.get(1), line, col, fullLine);
                return Value.ofLong(Math.min(a, b));
            }
            case "clamp": {
                requireCount(args, 3, "clamp", line, col, fullLine);
                long x = requireLong(args.get(0), line, col, fullLine);
                long lo = requireLong(args.get(1), line, col, fullLine);
                long hi = requireLong(args.get(2), line, col, fullLine);
                if (lo > hi) {
                    long t = lo;
                    lo = hi;
                    hi = t;
                }
                if (x < lo)
                    x = lo;
                if (x > hi)
                    x = hi;
                return Value.ofLong(x);
            }
            case "addMillis": {
                requireCount(args, 2, "addMillis", line, col, fullLine);
                long msBase = requireLong(args.get(0), line, col, fullLine);
                long delta = requireLong(args.get(1), line, col, fullLine);
                return Value.ofLong(msBase + delta);
            }
            case "addSeconds": {
                requireCount(args, 2, "addSeconds", line, col, fullLine);
                long msBase = requireLong(args.get(0), line, col, fullLine);
                long s = requireLong(args.get(1), line, col, fullLine);
                return Value.ofLong(msBase + safeMul(s, 1000L, line, col, fullLine));
            }
            case "addMinutes": {
                requireCount(args, 2, "addMinutes", line, col, fullLine);
                long msBase = requireLong(args.get(0), line, col, fullLine);
                long m = requireLong(args.get(1), line, col, fullLine);
                return Value.ofLong(msBase + safeMul(m, 60_000L, line, col, fullLine));
            }
            case "addHours": {
                requireCount(args, 2, "addHours", line, col, fullLine);
                long msBase = requireLong(args.get(0), line, col, fullLine);
                long h = requireLong(args.get(1), line, col, fullLine);
                return Value.ofLong(msBase + safeMul(h, 3_600_000L, line, col, fullLine));
            }
            case "addDays": {
                requireCount(args, 2, "addDays", line, col, fullLine);
                long msBase = requireLong(args.get(0), line, col, fullLine);
                long d = requireLong(args.get(1), line, col, fullLine);
                return Value.ofLong(msBase + safeMul(d, 86_400_000L, line, col, fullLine));
            }
            case "human": {
                requireCount(args, 1, "human", line, col, fullLine);
                long msVal = requireLong(args.get(0), line, col, fullLine);
                return Value.str(humanDuration(msVal));
            }

            case "humanDiff": {
                requireCount(args, 2, "humanDiff", line, col, fullLine);
                long a = requireLong(args.get(0), line, col, fullLine);
                long b = requireLong(args.get(1), line, col, fullLine);
                return Value.str(humanDuration(b - a));
            }
            case "unique": {
                requireCount(args, 0, "unique", line, col, fullLine);
                long t = System.currentTimeMillis();
                int r = (int) (System.nanoTime() & 0xFFFF);
                return Value.str(Long.toString(t) + "-" + Integer.toString(r));
            }
            case "seed": {
                requireCount(args, 0, "seed", line, col, fullLine);
                long s = System.currentTimeMillis() ^ (System.nanoTime() << 1);
                return Value.ofLong(s);
            }
            default:
                throw DogException.at(line, col, fullLine, "Unknown function in time module: " + member);
        }
    }

    @Override
    public Value getConstant(String member, DogContext ctx, int line, int col, String fullLine) {
        throw DogException.at(line, col, fullLine, "time module has no constants");
    }

    private static void requireCount(List<Value> args, int n, String fn, int line, int col, String fullLine) {
        if (args.size() != n) {
            throw DogException.at(line, col, fullLine, "time." + fn + "(...) expects " + n + " argument(s)");
        }
    }

    private static String requireString(Value v, int line, int col, String fullLine) {
        if (v == null || !v.isString()) {
            throw DogException.at(line, col, fullLine, "Expected a string argument");
        }
        return v.stringVal;
    }

    private static long requireLong(Value v, int line, int col, String fullLine) {
        if (v == null || !v.isNumber()) {
            throw DogException.at(line, col, fullLine, "Expected a number argument");
        }
        double d = v.toDouble();
        if (d != Math.rint(d)) {
            throw DogException.at(line, col, fullLine, "Expected an integer number");
        }
        if (d < Long.MIN_VALUE || d > Long.MAX_VALUE) {
            throw DogException.at(line, col, fullLine, "Number out of long range");
        }
        return (long) d;
    }

    private static long safeMul(long a, long b, int line, int col, String fullLine) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            throw DogException.at(line, col, fullLine, "time: overflow in multiplication");
        }
    }

    private static String humanDuration(long ms) {
        boolean neg = ms < 0;
        long x = ms;
        if (x == Long.MIN_VALUE) {
            return "-inf";
        }
        if (neg)
            x = -x;
        long days = x / 86_400_000L;
        x %= 86_400_000L;
        long hours = x / 3_600_000L;
        x %= 3_600_000L;
        long mins = x / 60_000L;
        x %= 60_000L;
        long secs = x / 1000L;
        x %= 1000L;
        long millis = x;
        StringBuilder sb = new StringBuilder();
        if (neg)
            sb.append('-');
        int parts = 0;
        if (days != 0) {
            sb.append(days).append('d');
            parts++;
        }
        if (hours != 0) {
            if (parts > 0)
                sb.append(' ');
            sb.append(hours).append('h');
            parts++;
        }
        if (mins != 0) {
            if (parts > 0)
                sb.append(' ');
            sb.append(mins).append('m');
            parts++;
        }
        if (secs != 0) {
            if (parts > 0)
                sb.append(' ');
            sb.append(secs).append('s');
            parts++;
        }
        if (parts == 0 && millis == 0)
            return (neg ? "-0ms" : "0ms");
        if (millis != 0 || parts == 0) {
            if (parts > 0)
                sb.append(' ');
            sb.append(millis).append("ms");
        }
        return sb.toString();
    }
}