package adris.altoclef.sigil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Keyring on disk, in the same JSON files sigil.py uses (circle-*.json, signet-*.json, contacts.json), so a keyring can
 * be copied between the two. Treat the directory like a password file: it holds passphrases and private keys in clear text.
 * <p>
 * Nothing here logs secrets. Methods are synchronized; the files are small and reloaded on demand.
 */
public final class SigilKeyring implements Sigil.Keys {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final Path dir;
    private final List<Sigil.Circle> circles = new ArrayList<>();
    private final List<Sigil.Signet> signets = new ArrayList<>();
    private final List<Sigil.Contact> contacts = new ArrayList<>();

    public SigilKeyring(Path dir) {
        this.dir = dir;
        reload();
    }

    public Path dir() {
        return dir;
    }

    private static String file12(String name) {
        return Sigil.slugify(name, 12);
    }

    private static String now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public synchronized void reload() {
        circles.clear();
        signets.clear();
        contacts.clear();
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> files = s.sorted().toList();
            for (Path p : files) {
                String n = p.getFileName().toString();
                try {
                    if (n.startsWith("circle-") && n.endsWith(".json")) {
                        JsonNode j = JSON.readTree(Files.readString(p, StandardCharsets.UTF_8));
                        circles.add(new Sigil.Circle(j.get("name").asText(), j.get("slug").asText(), j.get("passphrase").asText()));
                    } else if (n.startsWith("signet-") && n.endsWith(".json")) {
                        JsonNode j = JSON.readTree(Files.readString(p, StandardCharsets.UTF_8));
                        signets.add(new Sigil.Signet(j.get("name").asText(), j.get("short").asText(),
                                Sigil.b64d(j.get("pk").asText()), j.get("sk_pem").asText()));
                    }
                } catch (Exception bad) {
                    // A broken file is skipped; the user can see it is missing in the GUI.
                }
            }
            Path cp = dir.resolve("contacts.json");
            if (Files.exists(cp)) {
                JsonNode book = JSON.readTree(Files.readString(cp, StandardCharsets.UTF_8)).path("contacts");
                List<String> seen = new ArrayList<>();
                Iterator<JsonNode> it = book.elements();
                while (it.hasNext()) {
                    JsonNode e = it.next();
                    String key = e.path("alias").asText() + "\u0000" + e.path("short").asText();
                    if (seen.contains(key)) continue; // the file indexes each entry twice (alias and short-id)
                    seen.add(key);
                    contacts.add(new Sigil.Contact(e.path("alias").asText(), e.path("name_hint").asText(),
                            e.path("short").asText(), Sigil.b64d(e.path("pk").asText())));
                }
            }
        } catch (IOException e) {
            // unreadable directory: treat as empty
        }
    }

    private void writeSecret(Path p, ObjectNode node) throws IOException {
        Files.createDirectories(dir);
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        // Create owner-only before the secret goes in, where the filesystem supports it.
        try {
            Files.deleteIfExists(tmp);
            Files.createFile(tmp, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows: fall through, the file is created by the write below
        }
        Files.writeString(tmp, JSON.writeValueAsString(node), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            p.toFile().setReadable(false, false);
            p.toFile().setReadable(true, true);
            p.toFile().setWritable(false, false);
            p.toFile().setWritable(true, true);
        } catch (SecurityException ignored) {
            // best effort
        }
    }

    // ------------------------------------------------------------------------------------------
    // Circles
    // ------------------------------------------------------------------------------------------

    /** Adds or replaces a circle. Derives the key once (about 0.3s), so call off the tick thread. */
    public synchronized Sigil.Circle addCircle(String name, String passphrase, String note) throws IOException {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("circle needs a name");
        if (passphrase == null || passphrase.isEmpty()) throw new IllegalArgumentException("circle needs a passphrase");
        Sigil.Circle c = Sigil.newCircle(name.trim(), passphrase);
        ObjectNode n = JSON.createObjectNode();
        n.put("kind", "circle").put("name", c.name()).put("slug", c.slug()).put("fingerprint", c.fingerprint());
        n.put("kdf", "pbkdf2-sha256-" + Sigil.PBKDF2_ITERS).put("created", now()).put("note", note == null ? "" : note);
        n.put("passphrase", passphrase);
        writeSecret(dir.resolve("circle-" + file12(c.name()) + ".json"), n);
        circles.removeIf(o -> o.name().equals(c.name()));
        circles.add(c);
        return c;
    }

    public synchronized boolean removeCircle(String name) {
        Sigil.Circle c = circle(name);
        if (c == null) return false;
        try {
            Files.deleteIfExists(dir.resolve("circle-" + file12(c.name()) + ".json"));
        } catch (IOException e) {
            return false;
        }
        circles.remove(c);
        return true;
    }

    public synchronized Sigil.Circle circle(String nameOrSlug) {
        if (nameOrSlug == null) return null;
        String t = nameOrSlug.toLowerCase(Locale.ROOT);
        for (Sigil.Circle c : circles) if (c.name().equalsIgnoreCase(nameOrSlug) || c.slug().equals(t)) return c;
        return null;
    }

    @Override
    public synchronized List<Sigil.Circle> circles() {
        return List.copyOf(circles);
    }

    // ------------------------------------------------------------------------------------------
    // Signets
    // ------------------------------------------------------------------------------------------

    public synchronized Sigil.Signet createSignet(String name) throws IOException {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("signet needs a name");
        Sigil.Signet s = Sigil.newSignet(name.trim());
        ObjectNode n = JSON.createObjectNode();
        n.put("kind", "signet").put("name", s.name()).put("short", s.shortId()).put("pk", Sigil.b64e(s.pk()));
        n.put("sk_pem", s.skPem()).put("curve", "P-256").put("created", now());
        writeSecret(dir.resolve("signet-" + file12(s.name()) + ".json"), n);
        signets.removeIf(o -> o.name().equals(s.name()));
        signets.add(s);
        return s;
    }

    public synchronized Sigil.Signet signet(String nameOrShort) {
        if (nameOrShort == null) return null;
        for (Sigil.Signet s : signets) if (s.name().equalsIgnoreCase(nameOrShort) || s.shortId().equals(nameOrShort.toLowerCase(Locale.ROOT))) return s;
        return null;
    }

    @Override
    public synchronized List<Sigil.Signet> signets() {
        return List.copyOf(signets);
    }

    // ------------------------------------------------------------------------------------------
    // Contacts
    // ------------------------------------------------------------------------------------------

    public synchronized void addContact(Sigil.Contact c) throws IOException {
        contacts.removeIf(o -> o.alias().equalsIgnoreCase(c.alias()) || o.shortId().equals(c.shortId()));
        contacts.add(c);
        ObjectNode book = JSON.createObjectNode();
        ObjectNode map = book.putObject("contacts");
        for (Sigil.Contact k : contacts) {
            ObjectNode e = JSON.createObjectNode();
            e.put("alias", k.alias()).put("name_hint", k.nameHint()).put("pk", Sigil.b64e(k.pk()));
            e.put("short", k.shortId()).put("added", now());
            map.set(k.alias().toLowerCase(Locale.ROOT), e);
            map.set(k.shortId(), e.deepCopy());
        }
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("contacts.json"), JSON.writeValueAsString(book), StandardCharsets.UTF_8);
    }

    public synchronized Sigil.Contact contact(String aliasOrShort) {
        if (aliasOrShort == null) return null;
        String t = aliasOrShort.toLowerCase(Locale.ROOT);
        for (Sigil.Contact c : contacts) if (c.alias().equalsIgnoreCase(t) || c.shortId().equals(t) || c.nameHint().equalsIgnoreCase(t)) return c;
        return null;
    }

    @Override
    public synchronized Sigil.Contact contactByShort(String shortId) {
        for (Sigil.Contact c : contacts) if (c.shortId().equals(shortId)) return c;
        return null;
    }

    public synchronized List<Sigil.Contact> contacts() {
        return List.copyOf(contacts);
    }
}
