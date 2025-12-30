import java.util.ArrayList;
import java.util.List;

public final class FunctionProto {
    public final ArrayList<String> params;
    public final Chunk body;

    public FunctionProto(List<String> params, Chunk body) {
        this.params = new ArrayList<>();
        if (params != null)
            this.params.addAll(params);
        this.body = (body == null) ? new Chunk() : body;
    }
}