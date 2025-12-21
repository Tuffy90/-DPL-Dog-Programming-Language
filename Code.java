import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.List;

public class Code {

    public static void main(String[] args) {
        // Если без аргументов — запускаем консоль (REPL)
        if (args.length == 0) {
            new DogConsole().start();
            return;
        }

        // Запуск файла: java Code file.dog
        String filename = args[0];

        if (!filename.endsWith(".dog")) {
            System.out.println("Error: file must have .dog extension");
            System.out.println("Usage:");
            System.out.println("  java Code <file.dog>");
            System.out.println("  java Code            (start console)");
            return;
        }

        runFile(filename);
    }

    static void runFile(String filename) {
        Path path = Paths.get(filename);

        if (!Files.exists(path)) {
            System.out.println("Error: file not found: " + filename);
            return;
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            DogInterpreter interpreter = new DogInterpreter();
            interpreter.run(lines);
        } catch (DogInterpreter.DogException e) {
            printDogError(e);
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("Runtime error: " + e.getMessage());
        }
    }

    static void printDogError(DogInterpreter.DogException e) {
        System.out.println("Dog error at line " + e.line + ", column " + e.column + ": " + e.getMessage());
        if (e.sourceLine != null) {
            System.out.println(e.sourceLine);

            int col = Math.max(1, e.column);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < col; i++)
                sb.append(' ');
            sb.append('^');
            System.out.println(sb.toString());
        }
    }
}