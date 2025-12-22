import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DogConsole {

    // ---- Language info ----
    private static final String DOG_NAME = "Dog Programming Language (DPL)";
    private static final String DOG_VERSION = "v0.1.0";
    private static final String DOG_AUTHOR = "Tuffy Rej";

    // ---- ANSI (colors / clear) ----
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_CLEAR = "\u001B[2J\u001B[H";

    private final List<String> history = new ArrayList<String>(); // dog code history
    private final List<String> taskHistory = new ArrayList<String>(); // console commands / actions history

    private final DogContext ctx = Code.newContext();
    private final DogVM vm = new DogVM();
    private final BytecodeCompiler compiler = new BytecodeCompiler();

    private Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

    // internal clipboard
    private Path clipboardPath = null;
    private boolean clipboardCut = false; // false=copy, true=cut(move on paste)

    public void start() {
        clearScreen();
        printLogo();
        printHeader();

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                System.out.print(prompt());
                String line = br.readLine();
                if (line == null)
                    break; // EOF

                String t = line.trim();
                if (t.isEmpty())
                    continue;

                if (t.startsWith(":")) {
                    if (handleCommand(t, br))
                        break;
                    continue;
                }

                // dog-code line
                history.add(line);
                logTask("DOG> " + line);
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

    // =======================
    // UI
    // =======================
    private String prompt() {
        String folder = (cwd.getFileName() == null) ? cwd.toString() : cwd.getFileName().toString();
        return "dog[" + folder + "]> ";
    }

    private void printLogo() {
        System.out.println(
                "==============================================================\n" +
                        "   _              __  __  _                           \n" +
                        "  |  __ \\            |  _ \\|  _ \\| |                          \n" +
                        "  | |  | | _    _| |_) | |_) | |                          \n" +
                        "  | |  | |/ _ \\ / _` |  _ <|  __/| |                          \n" +
                        "  | |__| | (_) | (_| | |_) | |   | |___                       \n" +
                        "  |_____/ \\___/ \\__, |____/|_|   |_____|                      \n" +
                        "                __/ |                                         \n" +
                        "               |___/                                          \n" +
                        "                                                              \n" +
                        "                 Dog Programming Language (DPL)                \n" +
                        "                         by Tuffy Rej                         \n" +
                        "==============================================================\n");
    }

    private void printHeader() {
        System.out.println(DOG_NAME + " " + DOG_VERSION);
        System.out.println("Type :help for commands. Type Dog code directly to run it.");
        System.out.println("Tip: :info  |  :tree  |  :ls  |  :run file.dog\n");
    }

    private void clearScreen() {
        System.out.print(ANSI_CLEAR);
        System.out.flush();
    }

    private void logTask(String text) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        taskHistory.add("[" + ts + "] " + text);
    }

    // =======================
    // Dog run
    // =======================
    private List<String> singleLineList(String line) {
        ArrayList<String> one = new ArrayList<String>();
        one.add(line);
        return one;
    }

    private void runDogLines(List<String> lines) {
        Chunk chunk = compiler.compile(lines);
        vm.execute(chunk, ctx);
    }

    // =======================
    // Commands
    // =======================
    private boolean handleCommand(String cmdLine, BufferedReader br) throws IOException {
        logTask("CMD " + cmdLine);

        List<String> args = tokenize(cmdLine);
        String cmd = args.get(0);

        if (cmd.equals(":help")) {
            printHelp();
            return false;
        }
        if (cmd.equals(":exit"))
            return true;

        if (cmd.equals(":cls") || cmd.equals(":clearconsole")) {
            clearScreen();
            printLogo();
            printHeader();
            return false;
        }

        if (cmd.equals(":clear")) {
            history.clear();
            System.out.println("Dog-code history cleared.");
            return false;
        }

        if (cmd.equals(":clearall")) {
            history.clear();
            taskHistory.clear();
            System.out.println("All history cleared (dog + tasks).");
            return false;
        }

        if (cmd.equals(":history")) {
            printTaskHistory();
            return false;
        }

        if (cmd.equals(":pwd")) {
            System.out.println(cwd.toString());
            return false;
        }

        if (cmd.equals(":refresh")) {
            if (!Files.exists(cwd)) {
                System.out.println("Warning: current directory does not exist anymore. Resetting to user.dir");
                cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            }
            System.out.println("Refreshed. Current directory: " + cwd);
            listDir();
            return false;
        }

        if (cmd.equals(":ls")) {
            listDir();
            return false;
        }

        if (cmd.equals(":tree")) {
            int depth = 4;
            if (args.size() >= 2)
                depth = parseIntSafe(args.get(1), 4);
            Path start = cwd;
            if (args.size() >= 3)
                start = resolveSmart(args.get(2));
            printTree(start, depth);
            return false;
        }

        if (cmd.equals(":info")) {
            printInfo();
            return false;
        }

        if (cmd.equals(":cd")) {
            if (args.size() < 2) {
                System.out.println("Usage: :cd <folder|..>");
                return false;
            }
            changeDir(args.get(1));
            return false;
        }

        if (cmd.equals(":mkdir")) {
            if (args.size() < 2) {
                System.out.println("Usage: :mkdir <folder>");
                return false;
            }
            mkdir(args.get(1));
            return false;
        }

        if (cmd.equals(":touch")) {
            if (args.size() < 2) {
                System.out.println("Usage: :touch <file>");
                return false;
            }
            touch(args.get(1));
            return false;
        }

        if (cmd.equals(":open")) {
            if (args.size() < 2) {
                System.out.println("Usage: :open <file>");
                return false;
            }
            openFile(args.get(1));
            return false;
        }

        if (cmd.equals(":edit")) {
            if (args.size() < 2) {
                System.out.println("Usage: :edit <file>");
                return false;
            }
            editFile(args.get(1), br);
            return false;
        }

        if (cmd.equals(":run")) {
            if (args.size() < 2) {
                System.out.println("Usage: :run <file.dog>");
                return false;
            }
            runDogFile(args.get(1));
            return false;
        }

        if (cmd.equals(":save")) {
            if (args.size() < 2) {
                System.out.println("Usage: :save <file.dog>");
                return false;
            }
            saveHistory(args.get(1));
            return false;
        }

        if (cmd.equals(":vars")) {
            System.out.println(vm.globals());
            return false;
        }

        // delete file/folder (SAFE)
        if (cmd.equals(":rm") || cmd.equals(":del")) {
            if (args.size() < 2) {
                System.out.println("Usage: :rm <file|folder>");
                return false;
            }
            deletePathSafe(resolveSmart(args.get(1)), br);
            return false;
        }

        // copy/move direct
        if (cmd.equals(":cp")) {
            if (args.size() < 3) {
                System.out.println("Usage: :cp <src> <dst>");
                return false;
            }
            copyPath(resolveSmart(args.get(1)), resolveSmart(args.get(2)));
            return false;
        }

        if (cmd.equals(":mv") || cmd.equals(":move")) {
            if (args.size() < 3) {
                System.out.println("Usage: :mv <src> <dst>");
                return false;
            }
            movePath(resolveSmart(args.get(1)), resolveSmart(args.get(2)));
            return false;
        }

        // clipboard copy/cut/paste
        if (cmd.equals(":copy")) {
            if (args.size() < 2) {
                System.out.println("Usage: :copy <src>");
                return false;
            }
            clipboardPath = resolveSmart(args.get(1));
            clipboardCut = false;
            System.out.println("Copied to clipboard: " + clipboardPath);
            return false;
        }

        if (cmd.equals(":cut")) {
            if (args.size() < 2) {
                System.out.println("Usage: :cut <src>");
                return false;
            }
            clipboardPath = resolveSmart(args.get(1));
            clipboardCut = true;
            System.out.println("Cut to clipboard: " + clipboardPath);
            return false;
        }

        if (cmd.equals(":paste")) {
            if (clipboardPath == null) {
                System.out.println("Clipboard is empty. Use :copy <src> or :cut <src> first.");
                return false;
            }
            if (args.size() < 2) {
                System.out.println("Usage: :paste <dstFolderOrPath>");
                return false;
            }

            Path dst = resolveSmart(args.get(1));
            pasteClipboard(dst);
            return false;
        }

        // colors
        if (cmd.equals(":color")) {
            if (args.size() < 2) {
                System.out.println("Usage:");
                System.out.println("  :color reset");
                System.out.println("  :color <fg> [bg]");
                System.out.println("Colors: black red green yellow blue magenta cyan white");
                return false;
            }
            if (args.get(1).equalsIgnoreCase("reset")) {
                System.out.print(ANSI_RESET);
                System.out.println("Colors reset.");
                return false;
            }
            String fg = args.get(1);
            String bg = (args.size() >= 3) ? args.get(2) : null;
            setColors(fg, bg);
            return false;
        }

        System.out.println("Unknown command. Type :help");
        return false;
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  :help                       - show help");
        System.out.println("  :exit                       - exit console");
        System.out.println("  :info                       - show language + system info");
        System.out.println("  :history                    - show executed tasks history");
        System.out.println("  :cls / :clearconsole         - clear screen and reprint logo");
        System.out.println("  :clear                      - clear dog-code history");
        System.out.println("  :clearall                   - clear ALL history (dog + tasks)");
        System.out.println();
        System.out.println("Filesystem:");
        System.out.println("  :pwd                        - print current directory");
        System.out.println("  :refresh                    - refresh current dir status + list");
        System.out.println("  :ls                         - list files/folders");
        System.out.println("  :tree [depth] [path]         - show directory tree (default depth=4)");
        System.out.println("  :cd <folder|..>             - change directory");
        System.out.println("  :mkdir <folder>             - create directory");
        System.out.println("  :touch <file>               - create empty file");
        System.out.println("  :open <file>                - show file content");
        System.out.println("  :edit <file>                - edit file (end :wq, cancel :q)");
        System.out.println("  :rm <file|folder>            - SAFE delete (asks confirmation, blocks cwd/parents)");
        System.out.println();
        System.out.println("Copy / Move:");
        System.out.println("  :cp <src> <dst>              - copy file/folder to destination");
        System.out.println("  :mv <src> <dst>              - move file/folder to destination");
        System.out.println("  :copy <src>                  - copy to internal clipboard");
        System.out.println("  :cut <src>                   - cut (move on paste) to clipboard");
        System.out.println("  :paste <dstFolderOrPath>      - paste from clipboard");
        System.out.println();
        System.out.println("Dog:");
        System.out.println("  :run <file.dog>              - run a .dog file (same session)");
        System.out.println("  :save <file.dog>             - save dog session history to file");
        System.out.println("  :vars                        - show variables in current session");
        System.out.println();
        System.out.println("Colors:");
        System.out.println("  :color reset                 - reset colors");
        System.out.println("  :color <fg> [bg]             - set text color and optional background");
        System.out.println("    colors: black red green yellow blue magenta cyan white");
    }

    private void printInfo() {
        System.out.println("--------------------------------------------------------------");
        System.out.println(DOG_NAME + " " + DOG_VERSION);
        System.out.println("Author: " + DOG_AUTHOR);
        System.out.println("--------------------------------------------------------------");
        System.out.println("Java:   " + System.getProperty("java.version"));
        System.out.println("OS:     " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("Arch:   " + System.getProperty("os.arch"));
        System.out.println("CWD:    " + cwd);
        System.out.println("--------------------------------------------------------------");
        System.out.println("Tip: :help  |  :tree 3  |  :color green black  |  :history");
    }

    private void printTaskHistory() {
        if (taskHistory.isEmpty()) {
            System.out.println("(no tasks yet)");
            return;
        }
        System.out.println("---- Task History ----");
        int start = Math.max(0, taskHistory.size() - 200); // last 200
        for (int i = start; i < taskHistory.size(); i++) {
            System.out.println(taskHistory.get(i));
        }
        System.out.println("----------------------");
    }

    // =======================
    // Tokenizer for commands (supports quotes)
    // =======================
    private List<String> tokenize(String line) {
        ArrayList<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (!inQuotes && Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }

            cur.append(c);
        }

        if (cur.length() > 0)
            out.add(cur.toString());
        if (out.isEmpty())
            out.add(":");
        return out;
    }

    private int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    // =======================
    // Path helpers
    // =======================
    private Path resolveInCwd(String name) {
        return cwd.resolve(name).toAbsolutePath().normalize();
    }

    private Path resolveSmart(String input) {
        Path p = Paths.get(input);
        if (p.isAbsolute())
            return p.normalize();
        return resolveInCwd(input);
    }

    private Path norm(Path p) {
        return p.toAbsolutePath().normalize();
    }

    private boolean isSameOrParentOfCwd(Path candidate) {
        Path c = norm(candidate);
        Path cur = norm(cwd);
        // candidate == cwd OR candidate is parent (or ancestor) of cwd
        return cur.equals(c) || cur.startsWith(c);
    }

    // =======================
    // Filesystem actions
    // =======================
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
        Path target = resolveSmart(arg);

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
        Path dir = resolveSmart(name);
        if (Files.exists(dir)) {
            System.out.println("Already exists: " + name);
            return;
        }
        Files.createDirectories(dir);
        System.out.println("Created directory: " + dir);
    }

    private void touch(String name) throws IOException {
        Path file = resolveSmart(name);
        if (Files.exists(file)) {
            System.out.println("Already exists: " + name);
            return;
        }
        Path parent = file.getParent();
        if (parent != null)
            Files.createDirectories(parent);
        Files.write(file, new byte[0]);
        System.out.println("Created file: " + file);
    }

    private void openFile(String name) throws IOException {
        Path file = resolveSmart(name);

        if (!Files.exists(file)) {
            System.out.println("File not found: " + name);
            return;
        }
        if (Files.isDirectory(file)) {
            System.out.println("This is a folder. Use :cd \"" + file + "\"");
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
        Path file = resolveSmart(name);

        if (Files.exists(file) && Files.isDirectory(file)) {
            System.out.println("Cannot edit a directory: " + name);
            return;
        }

        List<String> original = new ArrayList<String>();
        if (Files.exists(file))
            original = Files.readAllLines(file, StandardCharsets.UTF_8);

        System.out.println("Editing: " + file.getFileName());
        System.out.println("Type lines. Finish with :wq (save & quit), cancel with :q (quit without saving).");
        System.out.println("Current content:");

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
        if (!fileName.endsWith(".dog")) {
            System.out.println("Error: file must end with .dog");
            return;
        }

        Path file = resolveSmart(fileName);
        if (!Files.exists(file)) {
            System.out.println("File not found: " + fileName);
            return;
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        logTask("RUN " + file.toString());
        runDogLines(lines);
    }

    private void saveHistory(String fileName) throws IOException {
        if (!fileName.endsWith(".dog")) {
            System.out.println("Error: file must end with .dog");
            return;
        }

        Path file = resolveSmart(fileName);
        Path parent = file.getParent();
        if (parent != null)
            Files.createDirectories(parent);

        Files.write(file, history, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("Saved dog history: " + file);
    }

    // =======================
    // SAFE delete (confirmation + protection)
    // =======================
    private void deletePathSafe(Path p, BufferedReader br) throws IOException {
        Path target = norm(p);

        if (!Files.exists(target)) {
            System.out.println("Not found: " + target);
            return;
        }

        // protect root
        if (target.getParent() == null) {
            System.out.println("Refusing to delete root: " + target);
            return;
        }

        // protect cwd and its parents
        if (isSameOrParentOfCwd(target)) {
            System.out.println("Refusing to delete current directory or its parent:");
            System.out.println("  CWD:    " + norm(cwd));
            System.out.println("  Target: " + target);
            return;
        }

        // ask confirmation
        if (!confirmDelete(target, br)) {
            System.out.println("Canceled.");
            return;
        }

        deleteRecursive(target);
        System.out.println("Deleted: " + target);
    }

    private boolean confirmDelete(Path target, BufferedReader br) throws IOException {
        System.out.println("WARNING: This will permanently delete:");
        System.out.println("  " + target);
        System.out.print("Type YES to confirm: ");
        String ans = br.readLine();
        if (ans == null)
            return false;
        return ans.trim().equalsIgnoreCase("YES");
    }

    private void deleteRecursive(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // =======================
    // Copy / Move / Paste
    // =======================
    private void copyPath(Path src, Path dst) throws IOException {
        src = norm(src);
        dst = norm(dst);

        if (!Files.exists(src)) {
            System.out.println("Source not found: " + src);
            return;
        }

        if (Files.exists(dst) && Files.isDirectory(dst)) {
            dst = dst.resolve(src.getFileName());
        }

        copyRecursive(src, dst);
        System.out.println("Copied: " + src + " -> " + dst);
    }

    private void copyRecursive(Path src, Path dst) throws IOException {
        if (Files.isDirectory(src)) {
            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path targetDir = dst.resolve(src.relativize(dir));
                    Files.createDirectories(targetDir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path targetFile = dst.resolve(src.relativize(file));
                    Path parent = targetFile.getParent();
                    if (parent != null)
                        Files.createDirectories(parent);
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Path parent = dst.getParent();
            if (parent != null)
                Files.createDirectories(parent);
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private void movePath(Path src, Path dst) throws IOException {
        src = norm(src);
        dst = norm(dst);

        if (!Files.exists(src)) {
            System.out.println("Source not found: " + src);
            return;
        }

        if (Files.exists(dst) && Files.isDirectory(dst)) {
            dst = dst.resolve(src.getFileName());
        }

        Path parent = dst.getParent();
        if (parent != null)
            Files.createDirectories(parent);

        try {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            // cross-device move fallback: copy + delete
            copyRecursive(src, dst);
            deleteRecursive(src);
        }

        System.out.println("Moved: " + src + " -> " + dst);
    }

    private void pasteClipboard(Path dst) throws IOException {
        if (clipboardPath == null) {
            System.out.println("Clipboard is empty.");
            return;
        }

        Path src = norm(clipboardPath);
        dst = norm(dst);

        if (!Files.exists(src)) {
            System.out.println("Clipboard source not found anymore: " + src);
            clipboardPath = null;
            clipboardCut = false;
            return;
        }

        if (Files.exists(dst) && Files.isDirectory(dst)) {
            dst = dst.resolve(src.getFileName());
        }

        if (clipboardCut) {
            movePath(src, dst);
            clipboardPath = null;
            clipboardCut = false;
        } else {
            copyPath(src, dst);
        }
    }

    // =======================
    // Tree
    // =======================
    private void printTree(Path start, int maxDepth) throws IOException {
        start = norm(start);

        if (!Files.exists(start)) {
            System.out.println("Not found: " + start);
            return;
        }
        if (!Files.isDirectory(start)) {
            System.out.println(start.getFileName());
            return;
        }

        System.out.println(start);
        walkTree(start, start, "", 0, maxDepth);
    }

    private void walkTree(Path root, Path dir, String prefix, int depth, int maxDepth) throws IOException {
        if (depth >= maxDepth)
            return;

        List<Path> children = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream)
                children.add(p);
        }

        children.sort(new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                boolean da = Files.isDirectory(a);
                boolean db = Files.isDirectory(b);
                if (da != db)
                    return da ? -1 : 1;
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }
        });

        for (int i = 0; i < children.size(); i++) {
            Path p = children.get(i);
            boolean last = (i == children.size() - 1);

            String connector = last ? "└── " : "├── ";
            String name = p.getFileName().toString();
            if (Files.isDirectory(p))
                name += "/";

            System.out.println(prefix + connector + name);

            if (Files.isDirectory(p)) {
                String nextPrefix = prefix + (last ? "    " : "│   ");
                walkTree(root, p, nextPrefix, depth + 1, maxDepth);
            }
        }
    }

    // =======================
    // Colors
    // =======================
    private void setColors(String fg, String bg) {
        String fgCode = colorFgCode(fg);
        String bgCode = (bg == null) ? "" : colorBgCode(bg);

        if (fgCode == null || (bg != null && bgCode == null)) {
            System.out.println("Unknown color. Use: black red green yellow blue magenta cyan white");
            return;
        }

        System.out.print("\u001B[" + fgCode + (bgCode.isEmpty() ? "" : ";" + bgCode) + "m");
        System.out.println("Colors set: fg=" + fg + (bg == null ? "" : (" bg=" + bg)));
    }

    private String colorFgCode(String c) {
        c = c.toLowerCase();
        if (c.equals("black"))
            return "30";
        if (c.equals("red"))
            return "31";
        if (c.equals("green"))
            return "32";
        if (c.equals("yellow"))
            return "33";
        if (c.equals("blue"))
            return "34";
        if (c.equals("magenta"))
            return "35";
        if (c.equals("cyan"))
            return "36";
        if (c.equals("white"))
            return "37";
        return null;
    }

    private String colorBgCode(String c) {
        c = c.toLowerCase();
        if (c.equals("black"))
            return "40";
        if (c.equals("red"))
            return "41";
        if (c.equals("green"))
            return "42";
        if (c.equals("yellow"))
            return "43";
        if (c.equals("blue"))
            return "44";
        if (c.equals("magenta"))
            return "45";
        if (c.equals("cyan"))
            return "46";
        if (c.equals("white"))
            return "47";
        return null;
    }
}
