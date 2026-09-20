package adris.altoclef.knowledge;

/**
 * Origin of a {@link KnowledgeFact} (Phase 5).
 * <p>
 * Does not replace trackers — facts wrap/observe them with confidence metadata.
 */
public enum KnowledgeSource {
    /** Live client observation (player health, position, entity.isAlive). */
    SENSOR,
    /** BlockScanner / EntityTracker scan results. */
    SCANNER,
    /** Previously observed value retained in the fact cache. */
    MEMORY,
    /** Derived / heuristic (e.g. decayed or merged). */
    INFERRED,
    /** Explicit user / command hint. */
    USER
}
