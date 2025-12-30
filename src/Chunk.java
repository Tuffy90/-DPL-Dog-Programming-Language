
// File: Chunk.java
import java.util.ArrayList;
import java.util.List;

public final class Chunk {
    private final ArrayList<Instruction> code = new ArrayList<Instruction>();
    private final ArrayList<FunctionProto> functions = new ArrayList<>();

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

    // ---- functions table ----
    public int addFunction(FunctionProto proto) {
        functions.add(proto);
        return functions.size() - 1;
    }

    public FunctionProto getFunction(int index) {
        return functions.get(index);
    }

    public List<FunctionProto> functions() {
        return functions;
    }
}