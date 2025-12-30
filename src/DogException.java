public class DogException extends RuntimeException {
    public final int line;
    public final int column;
    public final String sourceLine;

    public DogException(int line, int column, String sourceLine, String message) {
        super(message);
        this.line = line;
        this.column = Math.max(1, column);
        this.sourceLine = sourceLine;
    }

    public static DogException at(int line, int column, String sourceLine, String message) {
        return new DogException(line, column, sourceLine, message);
    }

    public String formatForLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("DogException at line ").append(line)
                .append(", col ").append(column)
                .append(": ").append(getMessage());
        if (sourceLine != null) {
            sb.append(System.lineSeparator()).append("Source: ").append(sourceLine);
        }
        return sb.toString();
    }
}