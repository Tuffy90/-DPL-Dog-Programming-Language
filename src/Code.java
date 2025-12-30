import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.List;

public class Code {

    public static void main(String[] args) {
        DogLog.init();
        try {
            if (args == null || args.length == 0) {
                DogLog.info("MAIN", "Starting console (no args)");
                new DogConsole().start();
                return;
            }
            DogLog.info("MAIN", "Args: " + joinArgs(args));
            String a0 = args[0];
            if (isCompileFlag(a0)) {
                if (args.length < 2) {
                    printUsage();
                    return;
                }
                String src = args[1];
                String out = (args.length >= 3) ? args[2] : defaultDogcName(src);
                try {
                    compileToDogc(src, out);
                    System.out.println("Compiled OK: " + out);
                    DogLog.info("COMPILE", "Compiled: " + src + " -> " + out);
                } catch (DogException e) {
                    printDogError(e);
                    DogLog.error("DOG", e.formatForLog());
                    System.exit(2);
                } catch (IOException e) {
                    System.out.println("❌ IO error: " + e.getMessage());
                    DogLog.error("IO", "Compile IO error: " + e.getMessage(), e);
                    System.exit(3);
                }
                return;
            }

            String filename = a0;

            if (filename.toLowerCase().endsWith(".dog")) {
                runDogFile(filename);
                return;
            }
            if (filename.toLowerCase().endsWith(".dogc")) {
                runDogcFile(filename);
                return;
            }
            System.out.println("❌ Error: file must have .dog or .dogc extension");
            printUsage();
            System.exit(1);
        } catch (Throwable t) {
            DogLog.error("FATAL", "Crash in main()", t);
            throw t;
        }
    }

    private static String joinArgs(String[] args) {
        if (args == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0)
                sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static boolean isCompileFlag(String s) {
        if (s == null)
            return false;
        return s.equals("-c") || s.equals("--compile") || s.equals("compile");
    }

    static DogContext newContext() {
        ModuleRegistry reg = new ModuleRegistry();
        reg.register(new IoModule());
        reg.register(new MathModule());
        reg.register(new TimeModule());
        reg.register(new StringModule());
        reg.register(new RandomModule());
        reg.register(new SystemModule());
        reg.register(new JsonModule());
        return new DogContext(reg);
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar dpl.jar                  (start console)");
        System.out.println("  java -jar dpl.jar <file.dog>       (compile + run)");
        System.out.println("  java -jar dpl.jar <file.dogc>      (run compiled bytecode)");
        System.out.println("  java -jar dpl.jar -c <file.dog>    (compile to .dogc)");
        System.out.println("  java -jar dpl.jar -c <file.dog> <out.dogc>");
    }

    private static String defaultDogcName(String src) {
        if (src == null)
            return "out.dogc";
        String lower = src.toLowerCase();
        if (lower.endsWith(".dog")) {
            return src.substring(0, src.length() - 4) + ".dogc";
        }
        return src + ".dogc";
    }

    static void runDogFile(String filename) {
        Path path = Paths.get(filename);
        if (!Files.exists(path)) {
            System.out.println("❌ Error: file not found: " + filename);
            DogLog.error("IO", "File not found: " + filename);
            System.exit(4);
            return;
        }
        try {
            DogLog.info("RUN", "Running .dog: " + path.toAbsolutePath().normalize());
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            DogContext ctx = newContext();
            DogVM vm = new DogVM();
            BytecodeCompiler compiler = new BytecodeCompiler();
            Chunk chunk = compiler.compile(lines);
            vm.execute(chunk, ctx);
        } catch (DogException e) {
            printDogError(e);
            DogLog.error("DOG", e.formatForLog());
            System.exit(2);
        } catch (IOException e) {
            System.out.println("❌ Error reading file: " + e.getMessage());
            DogLog.error("IO", "Read .dog error: " + e.getMessage(), e);
            System.exit(3);
        } catch (RuntimeException e) {
            System.out.println("❌ Runtime error: " + e.getMessage());
            DogLog.error("RUNTIME", "Runtime error while running .dog: " + e.getMessage(), e);
            System.exit(5);
        }
    }

    static void compileToDogc(String srcDog, String outDogc) throws IOException {
        if (srcDog == null || !srcDog.toLowerCase().endsWith(".dog")) {
            throw new IOException("Source must be a .dog file");
        }
        Path src = Paths.get(srcDog);
        if (!Files.exists(src)) {
            throw new IOException("Source file not found: " + srcDog);
        }
        List<String> lines = Files.readAllLines(src, StandardCharsets.UTF_8);
        BytecodeCompiler compiler = new BytecodeCompiler();
        Chunk chunk = compiler.compile(lines);
        Path out = Paths.get(outDogc);
        DogBytecodeIO.writeToFile(chunk, out);
    }

    static void runDogcFile(String dogcFile) {
        Path path = Paths.get(dogcFile);
        if (!Files.exists(path)) {
            System.out.println("❌ Error: file not found: " + dogcFile);
            DogLog.error("IO", "File not found: " + dogcFile);
            System.exit(4);
            return;
        }

        try {
            DogLog.info("RUN", "Running .dogc: " + path.toAbsolutePath().normalize());
            Chunk chunk = DogBytecodeIO.readFromFile(path);
            DogContext ctx = newContext();
            DogVM vm = new DogVM();
            vm.execute(chunk, ctx);
        } catch (DogException e) {
            printDogError(e);
            DogLog.error("DOG", e.formatForLog());
            System.exit(2);
        } catch (IOException e) {
            System.out.println("❌ Error reading .dogc: " + e.getMessage());
            DogLog.error("IO", "Read .dogc error: " + e.getMessage(), e);
            System.exit(3);
        } catch (RuntimeException e) {
            System.out.println("❌ Runtime error: " + e.getMessage());
            DogLog.error("RUNTIME", "Runtime error while running .dogc: " + e.getMessage(), e);
            System.exit(5);
        }
    }

    static void printDogError(DogException e) {
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