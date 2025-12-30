import java.util.List;

public final class SystemModule implements DogModule {
    @Override
    public String name() {
        return "sys";
    }

    @Override
    public Value call(String member, List<Value> args, DogContext ctx, int line, int col, String src) {
        switch (member) {
            case "os":
                return Value.str(System.getProperty("os.name"));
            case "java":
                return Value.str(System.getProperty("java.version"));
            case "user":
                return Value.str(System.getProperty("user.name"));
            case "cwd":
                return Value.str(System.getProperty("user.dir"));
        }
        throw DogException.at(line, col, src, "Unknown sys function: " + member);
    }

    @Override
    public Value getConstant(String member, DogContext ctx, int line, int col, String src) {
        return Value.nil();
    }
}