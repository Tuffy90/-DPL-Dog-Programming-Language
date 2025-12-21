import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DogConsole {

    private final List<String> history = new ArrayList<String>();

    private final DogContext ctx = Code.newContext();
    private final DogVM vm = new DogVM();
    private final BytecodeCompiler compiler = new BytecodeCompiler();

    private Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

    public void start() {
        System.out.println("Dog Programming Language (DPL) Console");
        System.out.println("Type :help for commands. Type Dog code directly to run it.");
        System.out.println();

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                System.out.print("dog[" + cwd.getFileName() + "]> ");
                String line = br.readLine();
                if (line == null)
                    break;

                String t = line.trim();
                if (t.isEmpty())
                    continue;

                if (t.startsWith(":")) {
                    if (handleCommand(t, br))
                        break;
                    continue;
                }

                history.add(line);
                runDogLines(singleLineList(line));

            } catch (DogException e) {
                Code.printDogError(e);
            } catch (IOException e) {
                System.out.println("IO error: " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("Runtime error: " + e.getMessage());
            }
        }

        System.out.println("\nBye!");
    }

    private List<String> singleLineList(String line) {
        ArrayList<String> one = new ArrayList<String>();
        one.add(line);
        return one;
    }

    private void runDogLines(List<String> lines) {
        Chunk chunk = compiler.compile(lines);
        vm.execute(chunk, ctx);
    }

    private boolean handleCommand(String cmdLine, BufferedReader br) throws IOException {
        if (cmdLine.equals(":help")) {
            printHelp();
            return false;
        }

        if (cmdLine.equals(":exit"))
            return true;

        if (cmdLine.equals(":clear")) {
            history.clear();
            System.out.println("History cleared.");
            return false;
        }

        if (cmdLine.equals(":pwd")) {
            System.out.println(cwd.toString());
            return false;
        }

        if (cmdLine.equals(":ls")) {
            listDir();
            return false;
        }

        if (cmdLine.startsWith(":cd ")) {
            changeDir(cmdLine.substring(4).trim());
            return false;
        }

        if (cmdLine.startsWith(":mkdir ")) {
            mkdir(cmdLine.substring(7).trim());
            return false;
        }

        if (cmdLine.startsWith(":touch ")) {
            touch(cmdLine.substring(7).trim());
            return false;
        }

        if (cmdLine.startsWith(":open ")) {
            openFile(cmdLine.substring(6).trim());
            return false;
        }

        if (cmdLine.startsWith(":edit ")) {
            editFile(cmdLine.substring(6).trim(), br);
            return false;
        }

        if (cmdLine.startsWith(":run ")) {
            String file = cmdLine.substring(5).trim();
            runDogFile(file);
            return false;
        }

        if (cmdLine.startsWith(":save ")) {
            saveHistory(cmdLine.substring(6).trim());
            return false;
        }

        if (cmdLine.equals(":vars")) {
            System.out.println(vm.globals());
            return false;
        }

        System.out.println("Unknown command. Type :help");
        return false;
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  :help              - show help");
        System.out.println("  :exit              - exit console");
        System.out.println("  :pwd               - print current directory");
        System.out.println("  :ls                - list files/folders in current directory");
        System.out.println("  :cd <folder|..>    - change directory");
        System.out.println("  :mkdir <folder>    - create directory");
        System.out.println("  :touch <file>      - create empty file if not exists");
        System.out.println("  :open <file>       - show file content");
        System.out.println("  :edit <file>       - edit file in console (end with :wq, cancel :q)");
        System.out.println("  :run <file.dog>    - run a .dog file from current directory (in current session)");
        System.out.println("  :save <file.dog>   - save your session history into a .dog file");
        System.out.println("  :vars              - show variables in current session");
        System.out.println("  :clear             - clear session history");
    }

    private Path resolveInCwd(String name) {
        return cwd.resolve(name).toAbsolutePath().normalize();
    }

    private void listDir() throws IOException {
        if (!Files.isDirectory(cwd)) {
            System.out.println("Not a directory: " + cwd);
            return;
        }

        List<Path> items = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cwd)) {
            for (Path p : stream)
                items.add(p);
        }

        items.sort(new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                boolean da = Files.isDirectory(a);
                boolean db = Files.isDirectory(b);
                if (da != db)
                    return da ? -1 : 1;
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }
        });

        if (items.isEmpty()) {
            System.out.println("(empty)");
            return;
        }

        for (Path p : items) {
            String name = p.getFileName().toString();
            if (Files.isDirectory(p))
                System.out.println("[DIR]  " + name);
            else
                System.out.println("       " + name);
        }
    }

    private void changeDir(String arg) {
        if (arg.isEmpty()) {
            System.out.println("Usage: :cd <folder|..>");
            return;
        }
        Path target = resolveInCwd(arg);
        if (!Files.exists(target)) {
            System.out.println("Folder not found: " + arg);
            return;
        }
        if (!Files.isDirectory(target)) {
            System.out.println("Not a folder: " + arg);
            return;
        }
        cwd = target;
        System.out.println("Current directory: " + cwd);
    }

    private void mkdir(String name) throws IOException {
        if (name.isEmpty()) {
            System.out.println("Usage: :mkdir <folder>");
            return;
        }
        Path dir = resolveInCwd(name);
        if (Files.exists(dir)) {
            System.out.println("Already exists: " + name);
            return;
        }
        Files.createDirectories(dir);
        System.out.println("Created directory: " + name);
    }

    private void touch(String name) throws IOException {
        if (name.isEmpty()) {
            System.out.println("Usage: :touch <file>");
            return;
        }
        Path file = resolveInCwd(name);
        if (Files.exists(file)) {
            System.out.println("Already exists: " + name);
            return;
        }
        Path parent = file.getParent();
        if (parent != null)
            Files.createDirectories(parent);
        Files.write(file, new byte[0]);
        System.out.println("Created file: " + name);
    }

    private void openFile(String name) throws IOException {
        if (name.isEmpty()) {
            System.out.println("Usage: :open <file>");
            return;
        }
        Path file = resolveInCwd(name);
        if (!Files.exists(file)) {
            System.out.println("File not found: " + name);
            return;
        }
        if (Files.isDirectory(file)) {
            System.out.println("This is a folder. Use :cd " + name);
            return;
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        System.out.println("----- " + file.getFileName() + " -----");
        if (lines.isEmpty())
            System.out.println("(empty)");
        else {
            for (int i = 0; i < lines.size(); i++) {
                System.out.printf("%4d | %s%n", (i + 1), lines.get(i));
            }
        }
        System.out.println("----- end -----");
    }

    private void editFile(String name, BufferedReader br) throws IOException {
        if (name.isEmpty()) {
            System.out.println("Usage: :edit <file>");
            return;
        }

        Path file = resolveInCwd(name);
        if (Files.exists(file) && Files.isDirectory(file)) {
            System.out.println("Cannot edit a directory: " + name);
            return;
        }

        List<String> original = new ArrayList<String>();
        if (Files.exists(file)) {
            original = Files.readAllLines(file, StandardCharsets.UTF_8);
        }

        System.out.println("Editing: " + file.getFileName());
        System.out.println("Type lines. Finish with :wq (save & quit), cancel with :q (quit without saving).");
        if (original.isEmpty())
            System.out.println("(empty)");
        else {
            for (int i = 0; i < original.size(); i++) {
                System.out.printf("%4d | %s%n", (i + 1), original.get(i));
            }
        }

        List<String> buffer = new ArrayList<String>(original);

        while (true) {
            System.out.print("edit> ");
            String line = br.readLine();
            if (line == null) {
                System.out.println("\nCanceled (EOF).");
                return;
            }

            String t = line.trim();
            if (t.equals(":q")) {
                System.out.println("Canceled. Nothing saved.");
                return;
            }
            if (t.equals(":wq")) {
                Path parent = file.getParent();
                if (parent != null)
                    Files.createDirectories(parent);
                Files.write(file, buffer, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("Saved: " + file);
                return;
            }

            buffer.add(line);
        }
    }

    private void runDogFile(String fileName) throws IOException {
        if (fileName.isEmpty()) {
            System.out.println("Usage: :run <file.dog>");
            return;
        }
        if (!fileName.endsWith(".dog")) {
            System.out.println("Error: file must end with .dog");
            return;
        }

        Path file = resolveInCwd(fileName);
        if (!Files.exists(file)) {
            System.out.println("File not found: " + fileName);
            return;
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        runDogLines(lines);
    }

    private void saveHistory(String fileName) throws IOException {
        if (fileName.isEmpty()) {
            System.out.println("Usage: :save <file.dog>");
            return;
        }
        if (!fileName.endsWith(".dog")) {
            System.out.println("Error: file must end with .dog");
            return;
        }
        Path file = resolveInCwd(fileName);
        Path parent = file.getParent();
        if (parent != null)
            Files.createDirectories(parent);

        Files.write(file, history, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Saved history: " + file);
    }
}