package adris.altoclef.tasks.speedrun.testrun2.agent;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * File transport for the agent loop (legacy inbox verbs + Phase 9 JSON).
 * <pre>
 *   &lt;gameDir&gt;/altoclef/agent/
 *     snapshot.json   — periodic / snap world snapshot
 *     inbox.txt       — legacy verb lines OR JSON request lines
 *     request.json    — optional single JSON request drop (cleared after take)
 *     response.json   — last structured AgentResponse
 *     outbox.log      — append-only log
 * </pre>
 */
public final class AgentFiles {

    private AgentFiles() {}

    public static Path dir() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.runDirectory != null) {
                return mc.runDirectory.toPath().resolve("altoclef").resolve("agent");
            }
        } catch (Throwable ignored) {}
        return Path.of("altoclef", "agent");
    }

    public static Path snapshot() { return dir().resolve("snapshot.json"); }
    public static Path inbox() { return dir().resolve("inbox.txt"); }
    public static Path outbox() { return dir().resolve("outbox.log"); }
    public static Path request() { return dir().resolve("request.json"); }
    public static Path response() { return dir().resolve("response.json"); }

    public static void ensure() {
        try {
            Files.createDirectories(dir());
            if (!Files.exists(inbox())) Files.writeString(inbox(), "", StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    public static void writeSnapshot(String json) {
        ensure();
        try {
            Files.writeString(snapshot(), json + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    /** First non-empty line, then rewrite the rest. */
    public static String takeInbox() {
        ensure();
        try {
            List<String> lines = Files.readAllLines(inbox(), StandardCharsets.UTF_8);
            String hit = null;
            StringBuilder rest = new StringBuilder();
            for (String line : lines) {
                String t = line.trim();
                if (hit == null && !t.isEmpty() && !t.startsWith("#")) {
                    hit = t;
                } else if (!t.isEmpty()) {
                    rest.append(t).append('\n');
                }
            }
            Files.writeString(inbox(), rest.toString(), StandardCharsets.UTF_8);
            return hit;
        } catch (IOException e) {
            return null;
        }
    }

    public static void pushInbox(String line) {
        ensure();
        try {
            Files.writeString(inbox(), line.trim() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    public static void log(String msg) {
        ensure();
        try {
            Files.writeString(outbox(), msg + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    /**
     * Read and clear {@code request.json} if present and non-blank.
     * Prefer this for a single structured drop from an external agent.
     */
    public static String takeRequestJson() {
        ensure();
        try {
            Path p = request();
            if (!Files.exists(p)) return null;
            String raw = Files.readString(p, StandardCharsets.UTF_8).trim();
            Files.writeString(p, "", StandardCharsets.UTF_8);
            if (raw.isEmpty() || raw.startsWith("#")) return null;
            return raw;
        } catch (IOException e) {
            return null;
        }
    }

    public static void writeRequestJson(String json) {
        ensure();
        try {
            Files.writeString(request(), json.trim() + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    public static void writeResponse(String json) {
        ensure();
        try {
            Files.writeString(response(), json + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
        log("RESPONSE " + json);
    }
}
