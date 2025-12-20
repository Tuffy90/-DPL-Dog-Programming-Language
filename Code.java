import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.List;

public class Code {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Dog language runner");
            System.out.println("Usage:");
            System.out.println("  java Code <file.dog>");
            System.out.println("Example:");
            System.out.println("  java Code hello.dog");
            return;
        }

        String filename = args[0];

        if (!filename.endsWith(".dog")) {
            System.out.println("Error: file must have .dog extension");
            return;
        }

        Path path = java.nio.file.Paths.get(filename);
        if (!Files.exists(path)) {
            System.out.println("Error: file not found: " + filename);
            return;
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            DogInterpreter interpreter = new DogInterpreter();
            interpreter.run(lines);
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("Runtime error: " + e.getMessage());
        }
    }
}