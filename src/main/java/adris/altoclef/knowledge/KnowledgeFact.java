package adris.altoclef.knowledge;

import java.util.Objects;
import java.util.Optional;

/**
 * Timestamped, confidence-weighted observation (Phase 5 world model slice).
 * <p>
 * Pure value type — no Minecraft dependency. Trackers remain the source of truth;
 * facts add age / confidence / source so callers can reject stale reads.
 *
 * @param <T> observed value type (may be null only when {@link #isKnown()} is false)
 */
public final class KnowledgeFact<T> {

    private static final KnowledgeFact<?> UNKNOWN =
            new KnowledgeFact<>(null, 0L, 0.0, KnowledgeSource.INFERRED, false);

    private final T value;
    private final long observedTick;
    private final double confidence;
    private final KnowledgeSource source;
    private final boolean known;

    private KnowledgeFact(T value, long observedTick, double confidence, KnowledgeSource source, boolean known) {
        this.value = value;
        this.observedTick = observedTick;
        this.confidence = clamp01(confidence);
        this.source = Objects.requireNonNull(source, "source");
        this.known = known;
    }

    @SuppressWarnings("unchecked")
    public static <T> KnowledgeFact<T> unknown() {
        return (KnowledgeFact<T>) UNKNOWN;
    }

    public static <T> KnowledgeFact<T> of(T value, long observedTick, double confidence, KnowledgeSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source");
        }
        return new KnowledgeFact<>(value, observedTick, confidence, source, true);
    }

    public boolean isKnown() {
        return known;
    }

    public T getValue() {
        return value;
    }

    public Optional<T> valueOptional() {
        return known ? Optional.ofNullable(value) : Optional.empty();
    }

    public long getObservedTick() {
        return observedTick;
    }

    /** Confidence in {@code [0, 1]} at observation time (before decay). */
    public double getConfidence() {
        return confidence;
    }

    public KnowledgeSource getSource() {
        return source;
    }

    public long ageTicks(long nowTick) {
        return Math.max(0L, nowTick - observedTick);
    }

    /** True when known and age ≤ {@code maxAgeTicks}. */
    public boolean isFreshEnough(long nowTick, long maxAgeTicks) {
        return known && ageTicks(nowTick) <= maxAgeTicks;
    }

    /**
     * Exponential half-life decay of confidence.
     * At {@code halfLifeTicks} age, confidence is halved.
     */
    public double decayedConfidence(long nowTick, long halfLifeTicks) {
        if (!known) {
            return 0.0;
        }
        if (halfLifeTicks <= 0L) {
            return confidence;
        }
        long age = ageTicks(nowTick);
        return confidence * Math.pow(0.5, (double) age / (double) halfLifeTicks);
    }

    /** Copy with a different source (e.g. SENSOR → MEMORY when served from cache). */
    public KnowledgeFact<T> withSource(KnowledgeSource newSource) {
        if (!known) {
            return unknown();
        }
        return of(value, observedTick, confidence, newSource);
    }

    /** Copy with an explicitly decayed confidence at {@code nowTick}. */
    public KnowledgeFact<T> withDecayedConfidence(long nowTick, long halfLifeTicks) {
        if (!known) {
            return unknown();
        }
        return of(value, observedTick, decayedConfidence(nowTick, halfLifeTicks), KnowledgeSource.INFERRED);
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v) || v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    @Override
    public String toString() {
        if (!known) {
            return "KnowledgeFact{unknown}";
        }
        return "KnowledgeFact{value=" + value
                + ", tick=" + observedTick
                + ", conf=" + confidence
                + ", source=" + source + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KnowledgeFact<?> that)) return false;
        return known == that.known
                && observedTick == that.observedTick
                && Double.compare(that.confidence, confidence) == 0
                && Objects.equals(value, that.value)
                && source == that.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, observedTick, confidence, source, known);
    }
}
