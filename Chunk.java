import java.util.ArrayList;
import java.util.List;

public final class Chunk {
    private final List<Instruction> code = new ArrayList<Instruction>();

    public void add(Instruction ins) {
        code.add(ins);
    }

    public List<Instruction> code() {
        return code;
    }
}