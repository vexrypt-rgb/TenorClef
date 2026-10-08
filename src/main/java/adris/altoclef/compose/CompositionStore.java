package adris.altoclef.compose;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Compositions live as plain .java files under {@code altoclef/compositions/}, separate from production tasks. */
public final class CompositionStore {
    private final Path dir;

    public CompositionStore(Path dir) {
        this.dir = dir;
    }

    public static CompositionStore standard() {
        return new CompositionStore(Paths.get("altoclef", "compositions"));
    }

    public Path dir() {
        return dir;
    }

    public static String sanitize(String name) {
        String n = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9_]", "");
        if (n.isEmpty()) n = "Untitled";
        if (!Character.isJavaIdentifierStart(n.charAt(0))) n = "C" + n;
        return n;
    }

    public List<String> list() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir)) return names;
        try (Stream<Path> s = Files.list(dir)) {
            s.map(p -> p.getFileName().toString()).filter(f -> f.endsWith(".java"))
                    .map(f -> f.substring(0, f.length() - 5)).sorted().forEach(names::add);
        } catch (IOException ignored) {
        }
        return names;
    }

    public String load(String name) throws IOException {
        return Files.readString(dir.resolve(sanitize(name) + ".java"), StandardCharsets.UTF_8);
    }

    public void save(String name, String source) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(sanitize(name) + ".java"), source, StandardCharsets.UTF_8);
    }

    public boolean exists(String name) {
        return Files.exists(dir.resolve(sanitize(name) + ".java"));
    }

    /** Copies to a fresh "NameCopy[N]" with the class declaration renamed; returns the new name. */
    public String duplicate(String name) throws IOException {
        String src = load(name);
        String base = sanitize(name) + "Copy";
        String fresh = base;
        for (int i = 2; exists(fresh); i++) fresh = base + i;
        save(fresh, rename(src, sanitize(name), fresh));
        return fresh;
    }

    public void delete(String name) throws IOException {
        Files.deleteIfExists(dir.resolve(sanitize(name) + ".java"));
    }

    static String rename(String source, String from, String to) {
        return source.replaceAll("\\b" + java.util.regex.Pattern.quote(from) + "\\b", to);
    }

    public static String template(String name) {
        return "import adris.altoclef.AltoClef;\n"
                + "import adris.altoclef.compose.*;\n"
                + "import adris.altoclef.tasksystem.*;\n\n"
                + "public class " + name + " implements Composition {\n"
                + "    @Override\n"
                + "    public Task build(AltoClef mod) {\n"
                + "        // Compose ordinary tasks. Movement goes through the normal task path.\n"
                + "        return Kit.seq(\"" + name + "\",\n"
                + "                Kit.waitMs(1500),\n"
                + "                Kit.waitMs(1500));\n"
                + "    }\n"
                + "}\n";
    }
}
