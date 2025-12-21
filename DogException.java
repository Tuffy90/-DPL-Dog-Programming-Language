public class DogException extends RuntimeException {
    public final int line; // 1-based
    public final int column; // 1-based
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
}