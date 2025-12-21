import java.util.HashMap;
import java.util.Map;

public final class ModuleRegistry {
    private final Map<String, DogModule> modules = new HashMap<String, DogModule>();

    public void register(DogModule m) {
        modules.put(m.name(), m);
    }

    public boolean exists(String name) {
        return modules.containsKey(name);
    }

    public DogModule get(String name) {
        return modules.get(name);
    }
}