package adris.altoclef.sigil;

import adris.altoclef.Debug;
import adris.altoclef.butler.ButlerConfig;
import adris.altoclef.tasks.speedrun.testrun2.util.GameFiles;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Game-facing side of SIGIL: owns the keyring, reassembles fragments and keeps every slow step (PBKDF2, ECDH) off the
 * game thread. Callbacks are delivered back on the game thread.
 * <p>
 * Logging rule: passphrases and plaintext never go to the log. Only mode, label and lengths do.
 */
public final class SigilService {

    /** Whisper command room: "/w " + a 16 char name + " " leaves this many characters of the 256 for the token. */
    public static final int WHISPER_LINE = 256 - 22;
    private static final long FRAGMENT_TTL_MS = 120_000;

    /** A fully reassembled sealed message and how to answer it. */
    public record Sealed(String mode, String label, String from, String fromShort, String plaintext) {}

    private static final SigilService INSTANCE = new SigilService();

    public static SigilService get() {
        return INSTANCE;
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sigil-worker");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, TreeMap<Integer, Sigil.Opened>> pending = new HashMap<>();
    private final Map<String, Long> pendingSince = new HashMap<>();
    private volatile SigilKeyring keyring;

    private SigilService() {}

    public SigilKeyring keyring() {
        SigilKeyring k = keyring;
        if (k == null) {
            synchronized (this) {
                if (keyring == null) keyring = new SigilKeyring(GameFiles.dir().resolve("sigil"));
                k = keyring;
            }
        }
        return k;
    }

    public static boolean looksSealed(String text) {
        return text != null && Sigil.findToken(text) != null;
    }

    /** Runs {@code task} on the worker, then delivers its result on the game thread. Failures arrive as a message. */
    public <T> void async(java.util.concurrent.Callable<T> task, Consumer<T> ok, Consumer<String> fail) {
        worker.execute(() -> {
            try {
                T result = task.call();
                onGame(() -> ok.accept(result));
            } catch (Throwable t) {
                String why = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                onGame(() -> fail.accept(why));
            }
        });
    }

    private static void onGame(Runnable r) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.execute(r);
        else r.run();
    }

    /**
     * Opens a sealed line. {@code ok} runs on the game thread once the whole message is available; for a fragment
     * that is not the last one it is not called, and nothing is reported.
     */
    public void open(String sender, String text, Consumer<Sealed> ok, Consumer<String> fail) {
        worker.execute(() -> {
            try {
                Sealed done = openBlocking(sender, text);
                if (done != null) onGame(() -> ok.accept(done));
            } catch (Throwable t) {
                String why = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                onGame(() -> fail.accept(why));
            }
        });
    }

    /** Worker-thread open. Returns null while fragments are still missing. */
    Sealed openBlocking(String sender, String text) {
        Sigil.Opened o = Sigil.open(text, keyring());
        Debug.logInternal("SIGIL opened " + o.mode() + " " + o.label() + " part " + o.part() + "/" + o.total() + " from " + sender);
        if (o.total() <= 1) return new Sealed(o.mode(), o.label(), o.from(), o.fromShort(), o.plaintext());
        String key = sender + "|" + o.mode() + "|" + o.label() + "|" + o.fromShort() + "|" + o.total();
        long now = System.currentTimeMillis();
        synchronized (pending) {
            pendingSince.entrySet().removeIf(e -> {
                if (now - e.getValue() > FRAGMENT_TTL_MS) {
                    pending.remove(e.getKey());
                    return true;
                }
                return false;
            });
            TreeMap<Integer, Sigil.Opened> parts = pending.computeIfAbsent(key, k -> new TreeMap<>());
            pendingSince.putIfAbsent(key, now);
            parts.put(o.part(), o);
            if (parts.size() < o.total()) return null;
            pending.remove(key);
            pendingSince.remove(key);
            return new Sealed(o.mode(), o.label(), o.from(), o.fromShort(), Sigil.joinParts(new ArrayList<>(parts.values())));
        }
    }

    /**
     * Seals {@code plaintext} for the same peer that sent {@code via}. Returns null when no sealed route exists
     * (an ephemeral sender with no contact or default circle), in which case nothing should be sent.
     */
    List<String> sealReplyBlocking(Sealed via, String toPlayer, String plaintext) {
        SigilKeyring kr = keyring();
        if (via != null && via.mode().equals("circle")) {
            Sigil.Circle c = kr.circle(via.label());
            if (c != null) return Sigil.sealCircle(c, plaintext, WHISPER_LINE, true);
        }
        if (via != null && via.mode().equals("signet")) {
            Sigil.Signet local = kr.signet(via.label());
            Sigil.Contact to = kr.contactByShort(via.fromShort());
            if (local != null && to != null) return Sigil.sealToSignet(local, to, plaintext, false, WHISPER_LINE, true);
        }
        return sealToPlayerBlocking(toPlayer, plaintext);
    }

    /** Directed to a contact whose alias is {@code player} if there is one, else the default circle. Null if neither. */
    List<String> sealToPlayerBlocking(String player, String plaintext) {
        SigilKeyring kr = keyring();
        ButlerConfig cfg = ButlerConfig.getInstance();
        Sigil.Contact to = kr.contact(player);
        Sigil.Signet local = kr.signet(cfg.sigilSignet);
        if (local == null && !kr.signets().isEmpty()) local = kr.signets().get(0);
        if (to != null && local != null) return Sigil.sealToSignet(local, to, plaintext, false, WHISPER_LINE, true);
        Sigil.Circle c = kr.circle(cfg.sigilCircle);
        if (c != null) return Sigil.sealCircle(c, plaintext, WHISPER_LINE, true);
        return null;
    }

    /** Seal for a circle name or contact, line size for either whispers or public chat. */
    List<String> sealToBlocking(String target, String plaintext, int maxLine) {
        SigilKeyring kr = keyring();
        Sigil.Circle c = kr.circle(target);
        if (c != null) return Sigil.sealCircle(c, plaintext, maxLine, true);
        Sigil.Contact to = kr.contact(target);
        if (to == null) throw new IllegalArgumentException("no circle or contact called '" + target + "'");
        Sigil.Signet local = kr.signet(ButlerConfig.getInstance().sigilSignet);
        if (local == null && !kr.signets().isEmpty()) local = kr.signets().get(0);
        if (local == null) throw new IllegalArgumentException("make a signet first (SIGIL tab)");
        return Sigil.sealToSignet(local, to, plaintext, false, maxLine, true);
    }

    public void seal(String target, String plaintext, int maxLine, Consumer<List<String>> ok, Consumer<String> fail) {
        async(() -> sealToBlocking(target, plaintext, maxLine), ok, fail);
    }

    /** Seals a reply for a peer and hands each line to {@code send} on the game thread, in order. */
    public void sealReply(Sealed via, String toPlayer, String plaintext, Consumer<String> send) {
        async(() -> sealReplyBlocking(via, toPlayer, plaintext), lines -> {
            if (lines == null) {
                Debug.logInternal("SIGIL: no sealed route back to " + toPlayer + ", reply dropped");
                return;
            }
            lines.forEach(send);
        }, why -> Debug.logInternal("SIGIL: could not seal reply to " + toPlayer + ": " + why));
    }

    /** Warms the PBKDF2 cache for every circle so the first message is not slow. */
    public void warmUp() {
        worker.execute(() -> {
            for (Sigil.Circle c : keyring().circles()) Sigil.circleKey(c.name(), c.passphrase());
        });
    }
}
