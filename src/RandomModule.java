import java.util.List;
import java.util.Random;

public final class RandomModule implements DogModule {
    private static final Random rng = new Random();

    @Override
    public String name() {
        return "rand";
    }

    @Override
    public Value call(String member, List<Value> args, DogContext ctx, int line, int col, String src) {
        switch (member) {
            case "int":
                if (args.isEmpty())
                    return Value.ofInt(rng.nextInt());
                if (args.size() == 1)
                    return Value.ofInt(rng.nextInt(args.get(0).intVal));
                if (args.size() == 2)
                    return Value.ofInt(rng.nextInt(args.get(1).intVal - args.get(0).intVal) + args.get(0).intVal);
                break;
            case "double":
                return Value.ofDouble(rng.nextDouble());
            case "bool":
                return Value.bool(rng.nextBoolean());
        }
        throw DogException.at(line, col, src, "Unknown rand function: " + member);
    }

    @Override
    public Value getConstant(String member, DogContext ctx, int line, int col, String src) {
        return Value.nil();
    }
}