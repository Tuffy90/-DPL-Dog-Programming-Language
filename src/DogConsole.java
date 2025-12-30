import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class DogConsole {

    private static final String DOG_NAME = "Dog Programming Language (DPL)";
    private static final String DOG_VERSION = "v0.2.0";
    private static final String DOG_AUTHOR = "Tuffy Rej";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_CLEAR = "\u001B[2J\u001B[H";
    private static final String ANSI_RED = "\u001B[31m";
    private final List<String> history = new ArrayList<String>();
    private final List<String> taskHistory = new ArrayList<String>();
    private final DogContext ctx = Code.newContext();
    private final DogVM vm = new DogVM();
    private final BytecodeCompiler compiler = new BytecodeCompiler();
    private Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    private Path clipboardPath = null;
    private boolean clipboardCut = false;
    private final Path appRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    private final Path projectsRoot = appRoot.resolve("My_projects").toAbsolutePath().normalize();
    private final Path stateFile = projectsRoot.resolve(".dpl_state");
    private String currentProject = null;
    private final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    private boolean ansiEnabled = true;
    private boolean timingEnabled = false;
    private boolean historyDirty = false;

    public void start() {
        DogLog.init();
        initProjectsFolder();
        ansiEnabled = detectAnsiSupport();
        loadLastProject();
        clearScreen();
        printLogo();
        printHeader();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                System.out.print(prompt());
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
                historyDirty = true;
                logTask("DOG> " + line);
                long t0 = timingEnabled ? System.nanoTime() : 0L;
                runDogLines(singleLineList(line));
                if (timingEnabled)
                    printExecTime(System.nanoTime() - t0);
            } catch (DogException e) {
                printDogErrorRed(e);
                DogLog.error("DOG", e.formatForLog());
            } catch (IOException e) {
                errln("IO error: " + e.getMessage());
                DogLog.error("IO", "Console IO error: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                errln("Runtime error: " + e.getMessage());
                DogLog.error("RUNTIME", "Console runtime error: " + e.getMessage(), e);
            }
        }
        System.out.println("\nBye!");
    }

    private String prompt() {
        String folder = (cwd.getFileName() == null) ? cwd.toString() : cwd.getFileName().toString();
        String star = historyDirty ? "*" : "";
        if (currentProject != null) {
            return "dog[" + currentProject + "/" + folder + star + "]> ";
        }
        return "dog[" + folder + star + "]> ";
    }

    private void printLogo() {
        System.out.println(
                "==============================================================\n" +
                        "   ___                   _                                   \n" +
                        " /|  _ \\  _    _    |  _ \\ | |                                \n" +
                        "  | | | |/ _ \\ / _` | |_)  | |                                \n" +
                        "  | |_| | (_) | (_| |     /| |_                               \n" +
                        " \\|____/ \\___/ \\__, |_|    |_____|                             \n" +
                        "               |___/                                           \n" +
                        "                                                              \n" +
                        "                 Dog Programming Language (DPL)               \n" +
                        "                         by Tuffy Rej                         \n" +
                        "                           2025-...                           \n" +
                        "==============================================================\n");
    }

    private void printHeader() {
        System.out.println(DOG_NAME + " " + DOG_VERSION);
        System.out.println("Type :help for commands. Type Dog code directly to run it.");
        System.out.println("Tip: :info  |  :tree  |  :ls  |  :run file.dog  |  :proj list\n");
    }

    private void clearScreen() {
        if (ansiEnabled) {
            System.out.print(ANSI_CLEAR);
            System.out.flush();
            return;
        }
        if (isWindows) {
            try {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
                return;
            } catch (Exception ignored) {
            }
        }
        for (int i = 0; i < 60; i++)
            System.out.println();
    }

    private void errln(String msg) {
        if (ansiEnabled)
            System.out.println(ANSI_RED + msg + ANSI_RESET);
        else
            System.out.println("[ERROR] " + msg);
    }

    private void printDogErrorRed(DogException e) {
        errln("Dog error at line " + e.line + ", column " + e.column + ": " + e.getMessage());
        if (e.sourceLine != null) {
            System.out.println(e.sourceLine);
            int col = Math.max(1, e.column);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < col; i++)
                sb.append(' ');
            sb.append('^');
            errln(sb.toString());
        }
    }

    private void logTask(String text) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        taskHistory.add("[" + ts + "] " + text);
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

    private void printExecTime(long nanos) {
        double ms = nanos / 1_000_000.0;
        System.out.printf("⏱  %.3f ms%n", ms);
    }

    private boolean handleCommand(String cmdLine, BufferedReader br) throws IOException {
        logTask("CMD " + cmdLine);
        List<String> args = tokenize(cmdLine);
        if (args.isEmpty())
            return false;
        String cmd0 = args.get(0).toLowerCase();
        if (cmd0.equals(":h"))
            args.set(0, ":help");
        if (cmd0.equals(":q"))
            args.set(0, ":exit");
        if (cmd0.equals(":r"))
            args.set(0, ":run");
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
            historyDirty = false;
            System.out.println("Dog-code history cleared.");
            return false;
        }
        if (cmd.equals(":clearall")) {
            history.clear();
            taskHistory.clear();
            historyDirty = false;
            System.out.println("All history cleared (dog + tasks).");
            return false;
        }
        if (cmd.equals(":history")) {
            printTaskHistory();
            return false;
        }
        if (cmd.equals(":info")) {
            printInfo();
            return false;
        }
        if (cmd.equals(":time")) {
            if (args.size() < 2) {
                System.out.println("Usage: :time on | :time off");
                System.out.println("Current: " + (timingEnabled ? "on" : "off"));
                return false;
            }
            String m = args.get(1).toLowerCase();
            if (m.equals("on")) {
                timingEnabled = true;
                System.out.println("⏱ Timing: ON");
                return false;
            }
            if (m.equals("off")) {
                timingEnabled = false;
                System.out.println("⏱ Timing: OFF");
                return false;
            }
            errln("Usage: :time on | :time off");
            return false;
        }

        if (cmd.equals(":proj") || cmd.equals(":project")) {
            if (args.size() < 2) {
                System.out.println("Usage:");
                System.out.println("  :proj list");
                System.out.println("  :proj new <name>");
                System.out.println("  :proj open <name>   (alias: use)");
                System.out.println("  :proj del <name>");
                System.out.println("  :proj path");
                System.out.println("  :proj init");
                System.out.println("  :proj exit          (alias: off)");
                return false;
            }
            String sub = args.get(1).toLowerCase();
            try {
                if (sub.equals("list")) {
                    listProjects();
                    return false;
                }
                if (sub.equals("new")) {
                    if (args.size() < 3) {
                        errln("Usage: :proj new <name>");
                        return false;
                    }
                    createProject(args.get(2));
                    return false;
                }
                if (sub.equals("open") || sub.equals("use")) {
                    if (args.size() < 3) {
                        errln("Usage: :proj open <name>");
                        return false;
                    }
                    useProject(args.get(2));
                    return false;
                }
                if (sub.equals("del")) {
                    if (args.size() < 3) {
                        errln("Usage: :proj del <name>");
                        return false;
                    }
                    deleteProject(args.get(2), br);
                    return false;
                }
                if (sub.equals("path")) {
                    printProjectPath();
                    return false;
                }
                if (sub.equals("init")) {
                    initProjectSkeleton();
                    return false;
                }
                if (sub.equals("exit") || sub.equals("off")) {
                    leaveProject();
                    return false;
                }
                errln("Unknown :proj subcommand. Use: list|new|open|del|path|init|exit");
                return false;
            } catch (IllegalArgumentException ex) {
                errln(ex.getMessage());
                return false;
            }
        }

        if (cmd.equals(":logclear") || cmd.equals(":clearlog")) {
            clearLogFolder(br);
            return false;
        }
        if (cmd.equals(":pwd")) {
            System.out.println(cwd.toString());
            return false;
        }
        if (cmd.equals(":refresh")) {
            if (!Files.exists(cwd)) {
                errln("Warning: current directory does not exist anymore. Resetting to user.dir");
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
        if (cmd.equals(":cd")) {
            if (args.size() < 2) {
                errln("Usage: :cd <folder|..>");
                return false;
            }
            changeDir(args.get(1));
            return false;
        }
        if (cmd.equals(":mkdir")) {
            if (args.size() < 2) {
                errln("Usage: :mkdir <folder>");
                return false;
            }
            mkdir(args.get(1));
            return false;
        }
        if (cmd.equals(":touch")) {
            if (args.size() < 2) {
                errln("Usage: :touch <file>");
                return false;
            }
            touch(args.get(1));
            return false;
        }
        if (cmd.equals(":open")) {
            if (args.size() < 2) {
                errln("Usage: :open <file>");
                return false;
            }
            openFile(args.get(1));
            return false;
        }
        if (cmd.equals(":edit")) {
            if (args.size() < 2) {
                errln("Usage: :edit <file>");
                return false;
            }
            editFile(args.get(1), br);
            return false;
        }
        if (cmd.equals(":run")) {
            if (args.size() < 2) {
                errln("Usage: :run <file.dog | file.dogc>");
                return false;
            }
            long t0 = timingEnabled ? System.nanoTime() : 0L;
            runProgramFile(args.get(1));
            if (timingEnabled)
                printExecTime(System.nanoTime() - t0);
            return false;
        }
        if (cmd.equals(":compile") || cmd.equals(":c")) {
            if (args.size() < 2) {
                errln("Usage: :compile <file.dog> [outFile.dogc]");
                return false;
            }
            String in = args.get(1);
            String out = (args.size() >= 3) ? args.get(2) : null;
            compileDogToDogc(in, out);
            return false;
        }
        if (cmd.equals(":runc")) {
            if (args.size() < 2) {
                errln("Usage: :runc <file.dogc>");
                return false;
            }
            long t0 = timingEnabled ? System.nanoTime() : 0L;
            runDogcFile(args.get(1));
            if (timingEnabled)
                printExecTime(System.nanoTime() - t0);
            return false;
        }
        if (cmd.equals(":save")) {
            if (args.size() < 2) {
                errln("Usage: :save <file.dog>");
                return false;
            }
            saveHistory(args.get(1));
            historyDirty = false;
            return false;
        }
        if (cmd.equals(":vars")) {
            System.out.println(vm.globals());
            return false;
        }
        if (cmd.equals(":rm") || cmd.equals(":del")) {
            if (args.size() < 2) {
                errln("Usage: :rm <file|folder>");
                return false;
            }
            deletePathSafe(resolveSmart(args.get(1)), br);
            return false;
        }
        if (cmd.equals(":cp")) {
            if (args.size() < 3) {
                errln("Usage: :cp <src> <dst>");
                return false;
            }
            copyPath(resolveSmart(args.get(1)), resolveSmart(args.get(2)));
            return false;
        }
        if (cmd.equals(":mv") || cmd.equals(":move")) {
            if (args.size() < 3) {
                errln("Usage: :mv <src> <dst>");
                return false;
            }
            movePath(resolveSmart(args.get(1)), resolveSmart(args.get(2)));
            return false;
        }
        if (cmd.equals(":copy")) {
            if (args.size() < 2) {
                errln("Usage: :copy <src>");
                return false;
            }
            clipboardPath = resolveSmart(args.get(1));
            clipboardCut = false;
            System.out.println("Copied to clipboard: " + clipboardPath);
            return false;
        }
        if (cmd.equals(":cut")) {
            if (args.size() < 2) {
                errln("Usage: :cut <src>");
                return false;
            }
            clipboardPath = resolveSmart(args.get(1));
            clipboardCut = true;
            System.out.println("Cut to clipboard: " + clipboardPath);
            return false;
        }
        if (cmd.equals(":paste")) {
            if (clipboardPath == null) {
                errln("Clipboard is empty.Use :copy <src> or :cut <src> first.");
                return false;
            }
            if (args.size() < 2) {
                errln("Usage: :paste <dstFolderOrPath>");
                return false;
            }
            Path dst = resolveSmart(args.get(1));
            pasteClipboard(dst);
            return false;
        }
        if (cmd.equals(":color")) {
            if (args.size() < 2) {
                System.out.println("Usage:");
                System.out.println("  :color reset");
                System.out.println("  :color <fg> [bg]");
                System.out.println("Colors: black red green yellow blue magenta cyan white");
                return false;
            }
            if (args.get(1).equalsIgnoreCase("reset")) {
                if (ansiEnabled)
                    System.out.print(ANSI_RESET);
                else if (isWindows)
                    runCmdColor("07");
                System.out.println("Colors reset.");
                return false;
            }
            String fg = args.get(1);
            String bg = (args.size() >= 3) ? args.get(2) : null;
            setColors(fg, bg);
            return false;
        }
        errln("Unknown command. Type :help");
        return false;
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  :help / :h                 - show help");
        System.out.println("  :exit / :q                 - exit console");
        System.out.println("  :info                      - show language + system info");
        System.out.println("  :history                   - show executed tasks history");
        System.out.println("  :cls / :clearconsole       - clear screen and reprint logo");
        System.out.println("  :clear                     - clear dog-code history");
        System.out.println("  :clearall                  - clear ALL history (dog + tasks)");
        System.out.println("  :logclear / :clearlog      - delete all files in ./log (asks confirmation)");
        System.out.println("  :time on|off                - measure execution time of Dog code");
        System.out.println();
        System.out.println("Filesystem:");
        System.out.println("  :pwd                       - print current directory");
        System.out.println("  :refresh                   - refresh current dir status + list");
        System.out.println("  :ls                        - list files/folders");
        System.out.println("  :tree [depth] [path]       - show directory tree (default depth=4)");
        System.out.println("  :cd <folder|..>            - change directory");
        System.out.println("  :mkdir <folder>            - create directory");
        System.out.println("  :touch <file>              - create empty file");
        System.out.println("  :open <file>               - show file content");
        System.out.println("  :edit <file>               - edit file (end :wq, cancel :q, undo :back)");
        System.out.println("  :rm <file|folder>          - SAFE delete (asks confirmation, blocks cwd/parents)");
        System.out.println();
        System.out.println("Copy / Move:");
        System.out.println("  :cp <src> <dst>            - copy file/folder to destination");
        System.out.println("  :mv <src> <dst>            - move file/folder to destination");
        System.out.println("  :copy <src>                - copy to internal clipboard");
        System.out.println("  :cut <src>                 - cut (move on paste) to clipboard");
        System.out.println("  :paste <dstFolderOrPath>   - paste from clipboard");
        System.out.println();
        System.out.println("Dog:");
        System.out.println("  :run / :r <file.dog|file.dogc> - run .dog source or .dogc bytecode (same session)");
        System.out.println("  :compile <file.dog> [out]  - compile .dog -> .dogc (no run)");
        System.out.println("  :runc <file.dogc>          - run compiled bytecode");
        System.out.println("  :save <file.dog>           - save dog session history to file");
        System.out.println("  :vars                      - show variables in current session");
        System.out.println();
        System.out.println("Projects:");
        System.out.println("  :proj list                 - list projects (in ./My_projects)");
        System.out.println("  :proj new <name>           - create and enter project");
        System.out.println("  :proj open <name>          - enter existing project");
        System.out.println("  :proj del <name>           - delete project (asks YES)");
        System.out.println("  :proj path                 - show current project path");
        System.out.println("  :proj init                 - create main.dog + src/out/lib");
        System.out.println("  :proj exit                 - leave project (back to app root)");
        System.out.println();
        System.out.println("Colors:");
        System.out.println("  :color reset               - reset colors");
        System.out.println("  :color <fg> [bg]           - set text color and optional background");
        System.out.println("    colors: black red green yellow blue magenta cyan white");
    }

    private void printInfo() {
        System.out.println("--------------------------------------------------------------");
        System.out.println(DOG_NAME + " " + DOG_VERSION);
        System.out.println("Author: " + DOG_AUTHOR);
        System.out.println("--------------------------------------------------------------");
        System.out.println("Java:      " + System.getProperty("java.version"));
        System.out.println("OS:        " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("Arch:      " + System.getProperty("os.arch"));
        System.out.println("ANSI:      " + (ansiEnabled ? "enabled" : "disabled"));
        System.out.println("Timing:    " + (timingEnabled ? "on" : "off"));
        System.out.println("Projects:  " + projectsRoot);
        System.out.println("Project:   " + (currentProject == null ? "(none)" : currentProject));
        System.out.println("CWD:       " + cwd);
        System.out.println("--------------------------------------------------------------");
        System.out.println("Tip: :help  |  :proj list  |  :time on  |  :tree 3");
    }

    private void printTaskHistory() {
        if (taskHistory.isEmpty()) {
            System.out.println("(no tasks yet)");
            return;
        }
        System.out.println("---- Task History ----");
        int start = Math.max(0, taskHistory.size() - 200);
        for (int i = start; i < taskHistory.size(); i++) {
            System.out.println(taskHistory.get(i));
        }
        System.out.println("----------------------");
    }

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
        return cur.equals(c) || cur.startsWith(c);
    }

    private void listDir() throws IOException {
        if (!Files.isDirectory(cwd)) {
            errln("Not a directory: " + cwd);
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
            errln("Folder not found: " + arg);
            return;
        }
        if (!Files.isDirectory(target)) {
            errln("Not a folder: " + arg);
            return;
        }
        cwd = target;
        System.out.println("Current directory: " + cwd);
    }

    private void mkdir(String name) throws IOException {
        Path dir = resolveSmart(name);
        if (Files.exists(dir)) {
            errln("Already exists: " + name);
            return;
        }
        Files.createDirectories(dir);
        System.out.println("Created directory: " + dir);
    }

    private void touch(String name) throws IOException {
        Path file = resolveSmart(name);
        if (Files.exists(file)) {
            errln("Already exists: " + name);
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
            errln("File not found: " + name);
            return;
        }
        if (Files.isDirectory(file)) {
            errln("This is a folder. Use :cd \"" + file + "\"");
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
            errln("Cannot edit a directory: " + name);
            return;
        }
        List<String> original = new ArrayList<String>();
        if (Files.exists(file))
            original = Files.readAllLines(file, StandardCharsets.UTF_8);
        System.out.println("Editing: " + file.getFileName());
        System.out.println("Type lines. Finish with :wq (save & quit), cancel with :q (quit without saving).");
        System.out.println("Edit helpers:");
        System.out.println("  :back / :undo      - remove last added line (step back)");
        System.out.println("  :del N             - delete line N (1-based) from the current buffer");
        System.out.println("  :show              - show current buffer content");
        List<String> buffer = new ArrayList<String>(original);
        while (true) {
            System.out.print("edit> ");
            String line = br.readLine();
            if (line == null) {
                errln("Canceled (EOF).");
                return;
            }
            String t = line.trim();
            if (t.equals(":q")) {
                System.out.println("Canceled. Nothing saved.");
                return;
            }
            if (t.equals(":back") || t.equals(":undo")) {
                if (buffer.isEmpty())
                    errln("Nothing to undo (buffer is empty).");
                else {
                    String removed = buffer.remove(buffer.size() - 1);
                    System.out.println("Removed last line: " + removed);
                }
                continue;
            }
            if (t.equals(":show")) {
                if (buffer.isEmpty())
                    System.out.println("(buffer is empty)");
                else {
                    System.out.println("----- buffer -----");
                    for (int i = 0; i < buffer.size(); i++) {
                        System.out.printf("%4d | %s%n", (i + 1), buffer.get(i));
                    }
                    System.out.println("----- end -----");
                }
                continue;
            }
            if (t.startsWith(":del")) {
                String[] parts = t.split("\\s+");
                if (parts.length < 2) {
                    errln("Usage: :del N");
                    continue;
                }
                int n;
                try {
                    n = Integer.parseInt(parts[1]);
                } catch (Exception ex) {
                    errln("Bad line number: " + parts[1]);
                    continue;
                }
                if (n < 1 || n > buffer.size()) {
                    errln("Line out of range. Buffer size=" + buffer.size());
                    continue;
                }
                String removed = buffer.remove(n - 1);
                System.out.println("Deleted line " + n + ": " + removed);
                continue;
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

    private void runProgramFile(String fileName) throws IOException {
        if (fileName.endsWith(".dog")) {
            runDogFile(fileName);
            return;
        }
        if (fileName.endsWith(".dogc")) {
            runDogcFile(fileName);
            return;
        }
        errln("Error: file must end with .dog or .dogc");
    }

    private void runDogFile(String fileName) throws IOException {
        Path file = resolveSmart(fileName);
        if (!Files.exists(file)) {
            errln("File not found: " + fileName);
            return;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        logTask("RUN " + file.toString());
        runDogLines(lines);
    }

    private void runDogcFile(String fileName) throws IOException {
        Path file = resolveSmart(fileName);
        if (!Files.exists(file)) {
            errln("File not found: " + fileName);
            return;
        }
        logTask("RUN-C " + file.toString());
        Chunk chunk = DogBytecodeIO.readChunk(file);
        vm.execute(chunk, ctx);
    }

    private void compileDogToDogc(String srcDog, String outDogc) throws IOException {
        if (!srcDog.endsWith(".dog")) {
            errln("Error: source must end with .dog");
            return;
        }
        Path src = resolveSmart(srcDog);
        if (!Files.exists(src)) {
            errln("File not found: " + srcDog);
            return;
        }
        String outName = outDogc;
        if (outName == null || outName.trim().isEmpty()) {
            String s = src.getFileName().toString();
            outName = s.substring(0, s.length() - 4) + ".dogc";
        }
        Path out = resolveSmart(outName);
        Path parent = out.getParent();
        if (parent != null)
            Files.createDirectories(parent);
        List<String> lines = Files.readAllLines(src, StandardCharsets.UTF_8);
        Chunk chunk = compiler.compile(lines);
        DogBytecodeIO.writeChunk(chunk, out);
        System.out.println("Compiled: " + src.getFileName() + " -> " + out);
    }

    private void saveHistory(String fileName) throws IOException {
        if (!fileName.endsWith(".dog")) {
            errln("Error: file must end with .dog");
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

    private void deletePathSafe(Path p, BufferedReader br) throws IOException {
        Path target = norm(p);
        if (!Files.exists(target)) {
            errln("Not found: " + target);
            return;
        }
        if (target.getParent() == null) {
            errln("Refusing to delete root: " + target);
            return;
        }
        if (isSameOrParentOfCwd(target)) {
            errln("Refusing to delete current directory or its parent:");
            System.out.println("  CWD:    " + norm(cwd));
            System.out.println("  Target: " + target);
            return;
        }
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

    private void copyPath(Path src, Path dst) throws IOException {
        src = norm(src);
        dst = norm(dst);
        if (!Files.exists(src)) {
            errln("Source not found: " + src);
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
            errln("Source not found: " + src);
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
            copyRecursive(src, dst);
            deleteRecursive(src);
        }
        System.out.println("Moved: " + src + " -> " + dst);
    }

    private void pasteClipboard(Path dst) throws IOException {
        if (clipboardPath == null) {
            errln("Clipboard is empty.");
            return;
        }
        Path src = norm(clipboardPath);
        dst = norm(dst);
        if (!Files.exists(src)) {
            errln("Clipboard source not found anymore: " + src);
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

    private void printTree(Path start, int maxDepth) throws IOException {
        start = norm(start);
        if (!Files.exists(start)) {
            errln("Not found: " + start);
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

    private void setColors(String fg, String bg) {
        if (ansiEnabled) {
            String fgCode = colorFgCode(fg);
            String bgCode = (bg == null) ? "" : colorBgCode(bg);
            if (fgCode == null || (bg != null && bgCode == null)) {
                errln("Unknown color. Use: black red green yellow blue magenta cyan white");
                return;
            }
            System.out.print("\u001B[" + fgCode + (bgCode.isEmpty() ? "" : ";" + bgCode) + "m");
            System.out.println("Colors set: fg=" + fg + (bg == null ? "" : (" bg=" + bg)));
            return;
        }
        if (isWindows) {
            String fgHex = cmdColorHex(fg);
            String bgHex = (bg == null) ? "0" : cmdColorHex(bg);
            if (fgHex == null || bgHex == null) {
                errln("Unknown color. Use: black red green yellow blue magenta cyan white");
                return;
            }
            String code = bgHex + fgHex;
            if (runCmdColor(code)) {
                System.out.println("Colors set (CMD): fg=" + fg + (bg == null ? "" : (" bg=" + bg)));
            } else {
                System.out.println("Colors not supported here.");
            }
            return;
        }
        System.out.println("Colors not supported in this console.");
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

    private void clearLogFolder(BufferedReader br) throws IOException {
        Path logDir = Paths.get("log").toAbsolutePath().normalize();
        if (!Files.exists(logDir)) {
            System.out.println("log/ folder does not exist.");
            return;
        }
        if (!Files.isDirectory(logDir)) {
            errln("log exists but it's not a folder: " + logDir);
            return;
        }
        int count = countFiles(logDir);
        if (count == 0) {
            System.out.println("log/ is already empty.");
            return;
        }
        System.out.println("This will delete " + count + " file(s) inside:");
        System.out.println("  " + logDir);
        System.out.print("Type YES to confirm: ");
        String ans = br.readLine();
        if (ans == null || !ans.trim().equalsIgnoreCase("YES")) {
            System.out.println("Canceled.");
            return;
        }
        deleteAllFilesInside(logDir);
        System.out.println("log/ cleaned.");
        DogLog.warn("LOG", "User cleaned log folder: " + logDir);
    }

    private int countFiles(Path dir) throws IOException {
        final int[] n = new int[] { 0 };
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                n[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return n[0];
    }

    private void deleteAllFilesInside(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (!d.equals(dir)) {
                    try {
                        Files.deleteIfExists(d);
                    } catch (IOException ignored) {
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void initProjectsFolder() {
        try {
            Files.createDirectories(projectsRoot);
        } catch (IOException e) {
            errln("Cannot create projects root: " + projectsRoot + " (" + e.getMessage() + ")");
        }
    }

    private void listProjects() {
        try {
            if (!Files.isDirectory(projectsRoot)) {
                System.out.println("(no projects folder)");
                return;
            }
            List<Path> items = new ArrayList<Path>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(projectsRoot)) {
                for (Path p : ds) {
                    if (Files.isDirectory(p) && !p.getFileName().toString().startsWith("."))
                        items.add(p);
                }
            }
            items.sort(new Comparator<Path>() {
                @Override
                public int compare(Path a, Path b) {
                    return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                }
            });
            if (items.isEmpty()) {
                System.out.println("(no projects yet)");
                return;
            }
            System.out.println("Projects in: " + projectsRoot);
            for (Path p : items)
                System.out.println(" - " + p.getFileName());
        } catch (IOException e) {
            errln("Project list error: " + e.getMessage());
        }
    }

    private void createProject(String name) {
        String pr = sanitizeProjectName(name);
        Path dir = projectsRoot.resolve(pr).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            currentProject = pr;
            cwd = dir;
            saveLastProject();
            System.out.println("Project created & entered: " + pr);
            System.out.println("Path: " + dir);
        } catch (IOException e) {
            errln("Cannot create project: " + e.getMessage());
        }
    }

    private void useProject(String name) {
        String pr = sanitizeProjectName(name);
        Path dir = projectsRoot.resolve(pr).toAbsolutePath().normalize();
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            errln("Project not found: " + pr);
            return;
        }
        currentProject = pr;
        cwd = dir;
        saveLastProject();
        System.out.println("Entered project: " + pr);
        System.out.println("Path: " + dir);
    }

    private void deleteProject(String name, BufferedReader br) throws IOException {
        String pr = sanitizeProjectName(name);
        Path dir = projectsRoot.resolve(pr).toAbsolutePath().normalize();
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            errln("Project not found: " + pr);
            return;
        }
        System.out.println("WARNING: This will permanently delete project:");
        System.out.println("  " + pr);
        System.out.println("  " + dir);
        System.out.print("Type YES to confirm: ");
        String ans = br.readLine();
        if (ans == null || !ans.trim().equalsIgnoreCase("YES")) {
            System.out.println("Canceled.");
            return;
        }
        if (pr.equals(currentProject)) {
            leaveProject();
        }
        deleteRecursive(dir);
        System.out.println("Deleted project: " + pr);
    }

    private void printProjectPath() {
        if (currentProject == null) {
            System.out.println("(no active project)");
            System.out.println("Projects root: " + projectsRoot);
            return;
        }
        Path dir = projectsRoot.resolve(currentProject).toAbsolutePath().normalize();
        System.out.println("Project: " + currentProject);
        System.out.println("Path: " + dir);
    }

    private void initProjectSkeleton() {
        if (currentProject == null) {
            errln("No active project. Use :proj new <name> or :proj open <name>");
            return;
        }
        Path prDir = projectsRoot.resolve(currentProject).toAbsolutePath().normalize();
        Path src = prDir.resolve("src");
        Path out = prDir.resolve("out");
        Path lib = prDir.resolve("lib");
        Path main = prDir.resolve("main.dog");
        try {
            Files.createDirectories(src);
            Files.createDirectories(out);
            Files.createDirectories(lib);
            if (!Files.exists(main)) {
                List<String> tpl = Arrays.asList(
                        "// main.dog (DPL)",
                        "say \"Hello from project: " + currentProject + "\"",
                        "", "// tip: put sources into ./src and run with :run main.dog");
                Files.write(main, tpl, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
            System.out.println("Project initialized:");
            System.out.println(" - " + src);
            System.out.println(" - " + out);
            System.out.println(" - " + lib);
            System.out.println(" - " + main);
        } catch (IOException e) {
            errln("Project init error: " + e.getMessage());
        }
    }

    private void leaveProject() {
        currentProject = null;
        cwd = appRoot;
        saveLastProject();
        System.out.println("Left project. CWD: " + cwd);
    }

    private void loadLastProject() {
        try {
            if (!Files.exists(stateFile))
                return;
            List<String> lines = Files.readAllLines(stateFile, StandardCharsets.UTF_8);
            if (lines.isEmpty())
                return;
            String pr = lines.get(0).trim();
            if (pr.isEmpty())
                return;
            Path dir = projectsRoot.resolve(pr).toAbsolutePath().normalize();
            if (Files.isDirectory(dir)) {
                currentProject = pr;
                cwd = dir;
            }
        } catch (Exception ignored) {
        }
    }

    private void saveLastProject() {
        try {
            Files.createDirectories(projectsRoot);
            if (currentProject == null) {
                try {
                    Files.deleteIfExists(stateFile);
                } catch (IOException ignored) {
                }
                return;
            }
            Files.write(stateFile, Arrays.asList(currentProject),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private String sanitizeProjectName(String name) {
        if (name == null)
            throw new IllegalArgumentException("Project name is null");
        String s = name.trim();
        if (s.isEmpty())
            throw new IllegalArgumentException("Project name is empty");
        if (s.contains("/") || s.contains("\\") || s.contains(":") || s.contains("..")) {
            throw new IllegalArgumentException("Bad project name: " + s);
        }
        return s;
    }

    private boolean detectAnsiSupport() {
        if (!isWindows)
            return true;
        String wt = System.getenv("WT_SESSION");
        String term = System.getenv("TERM");
        if (wt != null && !wt.isEmpty())
            return true;
        if (term != null && !term.isEmpty())
            return true;
        return false;
    }

    private boolean runCmdColor(String twoHex) {
        try {
            Process p = new ProcessBuilder("cmd", "/c", "color " + twoHex).inheritIO().start();
            p.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String cmdColorHex(String c) {
        c = c.toLowerCase();
        if (c.equals("black"))
            return "0";
        if (c.equals("blue"))
            return "1";
        if (c.equals("green"))
            return "2";
        if (c.equals("cyan"))
            return "3";
        if (c.equals("red"))
            return "4";
        if (c.equals("magenta"))
            return "5";
        if (c.equals("yellow"))
            return "6";
        if (c.equals("white"))
            return "7";
        return null;
    }
}
