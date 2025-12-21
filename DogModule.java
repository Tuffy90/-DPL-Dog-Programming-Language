import java.util.List;

public interface DogModule {
    String name();

    Value call(String member, List<Value> args, DogContext ctx, int line, int col, String fullLine);

    Value getConstant(String member, DogContext ctx, int line, int col, String fullLine);
}