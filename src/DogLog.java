import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DogLog {

    public enum Level {
        ERROR, WARN, INFO, DEBUG
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile boolean inited = false;
    private static volatile Level minLevel = Level.ERROR;
    private static Path logDir = Paths.get("log");
    private static Path logFile = logDir.resolve("dpl.log");

    private DogLog() {
    }

    public static void init() {
        if (inited)
            return;
        inited = true;
        try {
            Files.createDirectories(logDir);
        } catch (IOException ignored) {
        }
        String lvl = System.getProperty("dpl.log.level");
        if (lvl != null) {
            try {
                minLevel = Level.valueOf(lvl.trim().toUpperCase());
            } catch (Exception ignored) {
            }
        }
    }

    public static void setMinLevel(Level level) {
        if (level == null)
            return;
        minLevel = level;
    }

    public static Level getMinLevel() {
        return minLevel;
    }

    public static void error(String tag, String msg) {
        log(Level.ERROR, tag, msg, null);
    }

    public static void error(String tag, String msg, Throwable t) {
        log(Level.ERROR, tag, msg, t);
    }

    public static void warn(String tag, String msg) {
        log(Level.WARN, tag, msg, null);
    }

    public static void info(String tag, String msg) {
        log(Level.INFO, tag, msg, null);
    }

    public static void debug(String tag, String msg) {
        log(Level.DEBUG, tag, msg, null);
    }

    private static void log(Level level, String tag, String msg, Throwable t) {
        if (!inited)
            init();
        if (!enabled(level))
            return;
        String ts = LocalDateTime.now().format(TS);
        String line = "[" + ts + "] [" + level + "]" +
                (tag == null ? "" : " [" + tag + "] ") +
                (msg == null ? "" : msg);

        StringBuilder sb = new StringBuilder();
        sb.append(line).append(System.lineSeparator());
        if (t != null) {
            sb.append(stackTrace(t)).append(System.lineSeparator());
        }
        try {
            Files.createDirectories(logDir);
            Files.write(logFile, sb.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static boolean enabled(Level level) {
        return order(level) <= order(minLevel);
    }

    private static int order(Level lvl) {
        if (lvl == Level.ERROR)
            return 0;
        if (lvl == Level.WARN)
            return 1;
        if (lvl == Level.INFO)
            return 2;
        return 3;
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}