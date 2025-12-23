import java.util.ArrayList;
import java.util.List;

public final class Chunk {
    private final ArrayList<Instruction> code = new ArrayList<Instruction>();

    public int add(Instruction ins) {
        code.add(ins);
        return code.size() - 1;
    }

    public void set(int index, Instruction ins) {
        code.set(index, ins);
    }

    public int size() {
        return code.size();
    }

    public List<Instruction> code() {
        return code;
    }
}