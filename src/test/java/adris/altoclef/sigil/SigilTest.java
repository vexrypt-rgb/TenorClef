package adris.altoclef.sigil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Interop with sigil.py 0.3.0: tokens in vectors.json were sealed by the Python reference. */
class SigilTest {

    static JsonNode root;
    static Sigil.Circle circle;
    static Sigil.Signet alice;
    static Sigil.Signet bob;

    static Sigil.Signet signet(JsonNode n) {
        return new Sigil.Signet(n.get("name").asText(), n.get("short").asText(), Sigil.b64d(n.get("pk").asText()), n.get("sk_pem").asText());
    }

    /** Bob's keyring: his own signet, Alice as a contact, the circle. */
    static final Sigil.Keys BOB_KEYS = new Sigil.Keys() {
        public List<Sigil.Circle> circles() { return List.of(circle); }
        public List<Sigil.Signet> signets() { return List.of(bob); }
        public Sigil.Contact contactByShort(String s) {
            return s.equals(alice.shortId()) ? Sigil.newContact("alice", "Alice", alice.pk()) : null;
        }
    };

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = SigilTest.class.getResourceAsStream("/sigil/vectors.json")) {
            root = new ObjectMapper().readTree(in);
        }
        JsonNode c = root.get("circle");
        circle = Sigil.newCircle(c.get("name").asText(), c.get("passphrase").asText());
        alice = signet(root.get("alice"));
        bob = signet(root.get("bob"));
    }

    static String openAll(List<String> lines, Sigil.Keys keys) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(Sigil.open(l, keys).plaintext());
        return sb.toString();
    }

    static String squash(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    @Test
    void identitiesMatchPython() {
        assertEquals(root.get("circle").get("slug").asText(), circle.slug());
        assertEquals(root.get("circle").get("fp").asText(), circle.fingerprint());
        assertEquals(root.get("alice").get("short").asText(), Sigil.shortId(Sigil.compressPoint(alice.pk())));
        assertEquals(root.get("announce_circle").asText(), Sigil.announceCircle(circle));
        assertEquals(root.get("announce_alice").asText(), Sigil.announceSignet(alice));
        assertEquals(root.get("speak").asText(), Sigil.speakFingerprint(circle.fingerprint()));
        // The Python PKCS#8 PEM must load.
        assertNotNull(Sigil.privateKey(alice.skPem()));
    }

    @Test
    void javaOpensEveryPythonToken() {
        int n = 0;
        for (JsonNode v : root.get("vectors")) {
            List<String> lines = new ArrayList<>();
            v.get("lines").forEach(l -> lines.add(l.asText()));
            String got = openAll(lines, BOB_KEYS);
            String want = v.get("plaintext").asText();
            String label = v.get("kind").asText() + " " + lines.get(0);
            // The codebook normalises whitespace, so compare without it for compact vectors.
            if (v.get("compact").asBoolean()) assertEquals(squash(want), squash(got), label);
            else assertEquals(want, got, label);
            n++;
        }
        assertTrue(n >= 30);
    }

    @Test
    void javaRoundTripsAllModes() {
        String msg = "Meet at the fortress, 3 blazes at x=-345 y=64 z=1024. ☃ 😀 ".repeat(8).trim();
        Sigil.Contact toBob = Sigil.newContact("bob", "Bob", bob.pk());
        for (boolean compact : new boolean[]{false, true}) {
            List<String> c = Sigil.sealCircle(circle, msg, 256, compact);
            for (String l : c) assertTrue(l.length() <= 256, l.length() + " " + l);
            assertEquals(squash(msg), squash(openAll(c, BOB_KEYS)));
            for (boolean eph : new boolean[]{false, true}) {
                List<String> k = Sigil.sealToSignet(alice, toBob, msg, eph, 256, compact);
                for (String l : k) assertTrue(l.length() <= 256, l.length() + " " + l);
                assertEquals(squash(msg), squash(openAll(k, BOB_KEYS)));
            }
        }
    }

    @Test
    void tamperedAndForeignTokensAreRejected() {
        String line = Sigil.sealCircle(circle, "hello", 256, false).get(0);
        String bad = line.substring(0, line.length() - 3) + (line.endsWith("AAA") ? "BBB" : "AAA");
        assertThrows(IllegalArgumentException.class, () -> Sigil.open(bad, BOB_KEYS));
        Sigil.Circle other = Sigil.newCircle("Night.Watch", "wrong passphrase");
        String foreign = Sigil.sealCircle(other, "hello", 256, false).get(0);
        assertThrows(IllegalArgumentException.class, () -> Sigil.open(foreign, BOB_KEYS));
        assertThrows(IllegalArgumentException.class, () -> Sigil.open("just a normal whisper", BOB_KEYS));
    }

    @Test
    void announcementParse() {
        Sigil.Contact c = Sigil.parseSignetAnnouncement("hey look " + Sigil.announceSignet(alice) + " ok");
        assertNotNull(c);
        assertEquals(alice.shortId(), c.shortId());
        assertNull(Sigil.parseSignetAnnouncement("S1+PK.Eve." + alice.shortId() + "." + Sigil.b64e(bob.pk())));
    }

    @Test
    void codebookRoundTrips() {
        String[] samples = {"hello world", "go to 100 64 -200 now", "x=-345, y=64!", "a\nb", "ünï ☃ 😀", "12345678901234567890"};
        for (String s : samples) assertEquals(squash(s), squash(SigilCodebook.expand(SigilCodebook.compress(s))), s);
    }

    /** Writes Java-sealed tokens so tools can check that sigil.py opens them (build/sigil-java-vectors.json). */
    @Test
    void writeJavaVectorsForPython() throws Exception {
        ObjectMapper m = new ObjectMapper();
        ObjectNode out = m.createObjectNode();
        ArrayNode arr = out.putArray("vectors");
        String msg = "follow me to 100 64 -200 ☃ and bring 12 blocks! " + "alpha beta gamma delta ".repeat(10);
        Sigil.Contact toBob = Sigil.newContact("bob", "Bob", bob.pk());
        for (boolean compact : new boolean[]{false, true}) {
            add(arr, "C", compact, msg, Sigil.sealCircle(circle, msg, 256, compact));
            add(arr, "K", compact, msg, Sigil.sealToSignet(alice, toBob, msg, false, 256, compact));
            add(arr, "E", compact, msg, Sigil.sealToSignet(alice, toBob, msg, true, 256, compact));
        }
        Path p = Path.of("build", "sigil-java-vectors.json");
        Files.createDirectories(p.getParent());
        Files.writeString(p, m.writerWithDefaultPrettyPrinter().writeValueAsString(out));
    }

    static void add(ArrayNode arr, String kind, boolean compact, String msg, List<String> lines) {
        ObjectNode o = arr.addObject();
        o.put("kind", kind).put("compact", compact).put("plaintext", msg);
        ArrayNode l = o.putArray("lines");
        lines.forEach(l::add);
    }
}
