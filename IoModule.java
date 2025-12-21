import java.util.List;

public final class IoModule implements DogModule {

    @Override
    public String name() {
        return "io";
    }

    @Override
    public Value call(String member, List<Value> args, DogContext ctx, int line, int col, String fullLine) {
        if (member.equals("print") || member.equals("println")) {
            if (args.size() != 1) {
                throw DogException.at(line, col, fullLine,
                        "io." + member + "(...) expects exactly 1 argument");
            }
            String out = args.get(0).printable();
            if (member.equals("print"))
                System.out.print(out);
            else
                System.out.println(out);
            return Value.num(0);
        }

        throw DogException.at(line, col, fullLine,
                "Unknown io function: " + member + ". Available: print, println");
    }

    @Override
    public Value getConstant(String member, DogContext ctx, int line, int col, String fullLine) {
        throw DogException.at(line, col, fullLine,
                "Module 'io' has no constants");
    }
}