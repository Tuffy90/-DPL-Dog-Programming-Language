import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.List;

public class Code {

    public static void main(String[] args) {
        if (args.length == 0) {
            new DogConsole().start();
            return;
        }

        // Compile only:
        //   java Code -c file.dog
        //   java Code -c file.dog outFile.dogc
        if (args[0].equals("-c") || args[0].equals("--compile") || args[0].equals("compile")) {
            if (args.length < 2) {
                printUsage();
                return;
            }
            String src = args[1];
            String out = (args.length >= 3) ? args[2] : defaultDogcName(src);
            try {
                compileToDogc(src, out);
                System.out.println("Compiled OK: " + out);
            } catch (DogException e) {
                printDogError(e);
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
            }
            return;
        }

        String filename = args[0];

        // Run source
        if (filename.endsWith(".dog")) {
            runDogFile(filename);
            return;
        }

        // Run compiled bytecode
        if (filename.endsWith(".dogc")) {
            runDogcFile(filename);
            return;
        }

        System.out.println("Error: file must have .dog or .dogc extension");
        printUsage();
    }

    static DogContext newContext() {
        ModuleRegistry reg = new ModuleRegistry();
        reg.register(new IoModule());
        reg.register(new MathModule());
        return new DogContext(reg);
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java Code                  (start console)");
        System.out.println("  java Code <file.dog>        (compile + run)");
        System.out.println("  java Code <file.dogc>       (run compiled bytecode)");
        System.out.println("  java Code -c <file.dog>     (compile to .dogc)");
        System.out.println("  java Code -c <file.dog> <out.dogc>");
    }

    private static String defaultDogcName(String src) {
        if (src == null) return "out.dogc";
        if (src.toLowerCase().endsWith(".dog")) {
            return src.substring(0, src.length() - 4) + ".dogc";
        }
        return src + ".dogc";
    }

    static void runDogFile(String filename) {
        Path path = Paths.get(filename);

        if (!Files.exists(path)) {
            System.out.println("Error: file not found: " + filename);
            return;
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

            DogContext ctx = newContext();
            DogVM vm = new DogVM();

            BytecodeCompiler compiler = new BytecodeCompiler();
            Chunk chunk = compiler.compile(lines);

            vm.execute(chunk, ctx);

        } catch (DogException e) {
            printDogError(e);
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("Runtime error: " + e.getMessage());
        }
    }

    static void compileToDogc(String srcDog, String outDogc) throws IOException {
        if (srcDog == null || !srcDog.endsWith(".dog")) {
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
        DogBytecodeIO.writeChunk(chunk, out);
    }

    static void runDogcFile(String dogcFile) {
        Path path = Paths.get(dogcFile);
        if (!Files.exists(path)) {
            System.out.println("Error: file not found: " + dogcFile);
            return;
        }

        try {
            Chunk chunk = DogBytecodeIO.readChunk(path);
            DogContext ctx = newContext();
            DogVM vm = new DogVM();
            vm.execute(chunk, ctx);
        } catch (DogException e) {
            printDogError(e);
        } catch (IOException e) {
            System.out.println("Error reading .dogc: " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("Runtime error: " + e.getMessage());
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