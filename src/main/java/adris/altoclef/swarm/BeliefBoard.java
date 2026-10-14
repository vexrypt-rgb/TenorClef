package adris.altoclef.swarm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared beliefs: things agents have observed, each with confidence, timestamp and provenance.
 * Not ground truth. Confidence halves every {@code halfLifeMs}; a belief that has decayed below
 * {@link #FLOOR} is treated as unknown. Agents' local state stays local; only what they choose to
 * report (op {@code obs}) lands here.
 */
public final class BeliefBoard {
    public static final double FLOOR = 0.1;

    public record Belief(String key, String value, double confidence, long timeMs, String source, int corroborations) {
        public double effective(long now, long halfLifeMs) {
            double age = Math.max(0, now - timeMs);
            return confidence * Math.pow(0.5, age / halfLifeMs);
        }

        @Override
        public String toString() {
            return key + "=" + value + " (conf " + String.format(java.util.Locale.ROOT, "%.2f", confidence)
                    + ", seen by " + source + " at t=" + timeMs + ", corroborated " + corroborations + "x)";
        }
    }

    private final Map<String, Belief> beliefs = new LinkedHashMap<>();
    private final long halfLifeMs;

    public BeliefBoard() { this(60_000); }

    public BeliefBoard(long halfLifeMs) { this.halfLifeMs = halfLifeMs; }

    /** @return true if the stored belief changed (new value, or stronger evidence for the same value). */
    public boolean observe(String key, String value, double confidence, long now, String source) {
        double conf = Math.max(0, Math.min(1, confidence));
        Belief old = beliefs.get(key);
        if (old == null) {
            beliefs.put(key, new Belief(key, value, conf, now, source, 0));
            return true;
        }
        if (old.value().equals(value)) {
            // Same claim again: corroboration from another source strengthens it, the same source only refreshes it.
            boolean other = !old.source().equals(source);
            double merged = other ? Math.min(1.0, Math.max(conf, old.effective(now, halfLifeMs)) + 0.1) : conf;
            beliefs.put(key, new Belief(key, value, merged, now, source, old.corroborations() + (other ? 1 : 0)));
            return true;
        }
        // A conflicting claim wins only if it is at least as believable as what the old one has decayed to.
        if (conf >= old.effective(now, halfLifeMs)) {
            beliefs.put(key, new Belief(key, value, conf, now, source, 0));
            return true;
        }
        return false;
    }

    /** Current belief, or null if unknown or decayed away. */
    public Belief get(String key, long now) {
        Belief b = beliefs.get(key);
        if (b == null || b.effective(now, halfLifeMs) < FLOOR) return null;
        return b;
    }

    public String explain(String key, long now) {
        Belief b = beliefs.get(key);
        if (b == null) return key + ": no belief";
        double eff = b.effective(now, halfLifeMs);
        return b + ", now " + String.format(java.util.Locale.ROOT, "%.2f", eff) + (eff < FLOOR ? " (stale, ignored)" : "");
    }

    public int size() { return beliefs.size(); }
}
