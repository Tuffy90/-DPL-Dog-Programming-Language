import java.util.HashSet;
import java.util.Set;

public final class DogContext {
    private final ModuleRegistry registry;
    private final Set<String> imported = new HashSet<String>();

    public DogContext(ModuleRegistry registry) {
        this.registry = registry;
    }

    public ModuleRegistry registry() {
        return registry;
    }

    public void importModule(String name, int line, int col, String fullLine) {
        if (!registry.exists(name)) {
            throw DogException.at(line, col, fullLine,
                    "Unknown module '" + name + "'. Available: io, math");
        }
        imported.add(name);
    }

    public void requireImported(String name, int line, int col, String fullLine) {
        if (!imported.contains(name)) {
            throw DogException.at(line, col, fullLine,
                    "Module '" + name + "' is not imported. Add: import " + name);
        }
    }
}