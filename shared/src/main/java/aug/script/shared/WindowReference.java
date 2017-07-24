package aug.script.shared;

public class WindowReference {
    private final String name;

    protected WindowReference() {
        this.name = "";
    }

    public WindowReference(String name) {
        if (name == null || name.equals("")) throw new RuntimeException("name cannot be null");
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
