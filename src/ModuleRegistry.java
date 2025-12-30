import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    public String available() {
        List<String> names = new ArrayList<String>(modules.keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", names);
    }
}