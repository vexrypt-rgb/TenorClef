package adris.altoclef.knowledge;

/**
 * Pure helpers for age / decay / merge-replace rules (Phase 5).
 * <p>
 * Keep rules simple: prefer fresher observations; break ties by confidence;
 * SENSOR/SCANNER beat MEMORY/INFERRED when ages are within a small window.
 */
public final class KnowledgeFacts {

    /** Ages within this many ticks are treated as "same freshness" for tie-break. */
    public static final long FRESHNESS_TIE_WINDOW_TICKS = 5L;

    private KnowledgeFacts() {
    }

    public static boolean isFreshEnough(KnowledgeFact<?> fact, long nowTick, long maxAgeTicks) {
        return fact != null && fact.isFreshEnough(nowTick, maxAgeTicks);
    }

    public static double decayedConfidence(KnowledgeFact<?> fact, long nowTick, long halfLifeTicks) {
        if (fact == null) {
            return 0.0;
        }
        return fact.decayedConfidence(nowTick, halfLifeTicks);
    }

    /**
     * Merge two facts for the same signal.
     * <ul>
     *   <li>Unknown yields to known</li>
     *   <li>Clearly fresher observation wins</li>
     *   <li>Within {@link #FRESHNESS_TIE_WINDOW_TICKS}: higher confidence wins;
     *       then prefer SENSOR/SCANNER/USER over MEMORY/INFERRED</li>
     * </ul>
     */
    public static <T> KnowledgeFact<T> merge(KnowledgeFact<T> a, KnowledgeFact<T> b) {
        if (a == null || !a.isKnown()) {
            return b != null ? b : KnowledgeFact.unknown();
        }
        if (b == null || !b.isKnown()) {
            return a;
        }
        long ageDiff = a.getObservedTick() - b.getObservedTick();
        if (ageDiff > FRESHNESS_TIE_WINDOW_TICKS) {
            return a;
        }
        if (ageDiff < -FRESHNESS_TIE_WINDOW_TICKS) {
            return b;
        }
        // Similar age: confidence, then source rank
        int confCmp = Double.compare(a.getConfidence(), b.getConfidence());
        if (confCmp > 0) {
            return a;
        }
        if (confCmp < 0) {
            return b;
        }
        return sourceRank(a.getSource()) >= sourceRank(b.getSource()) ? a : b;
    }

    /** Replace cached fact when incoming is known; otherwise keep previous. */
    public static <T> KnowledgeFact<T> replaceIfKnown(KnowledgeFact<T> previous, KnowledgeFact<T> incoming) {
        if (incoming != null && incoming.isKnown()) {
            return incoming;
        }
        return previous != null ? previous : KnowledgeFact.unknown();
    }

    /**
     * True when a fact is usable for decisions: known, fresh enough, and
     * decayed confidence ≥ {@code minConfidence}.
     */
    public static boolean isReliable(KnowledgeFact<?> fact, long nowTick, long maxAgeTicks,
                                     long halfLifeTicks, double minConfidence) {
        if (fact == null || !fact.isKnown()) {
            return false;
        }
        if (!fact.isFreshEnough(nowTick, maxAgeTicks)) {
            return false;
        }
        return fact.decayedConfidence(nowTick, halfLifeTicks) >= minConfidence;
    }

    static int sourceRank(KnowledgeSource source) {
        if (source == null) {
            return 0;
        }
        return switch (source) {
            case SENSOR -> 5;
            case SCANNER -> 4;
            case USER -> 3;
            case MEMORY -> 2;
            case INFERRED -> 1;
        };
    }
}
