package adris.altoclef.tasksystem;

/**
 * What to do after a structured failure (Phase 6). Kept small — not a planner.
 */
public enum RecoveryAction {
    /** Try the same approach again (often with wander / backoff already in the task). */
    RETRY,
    /** Soft alternate: repath / wander then resume same goal. */
    ALTERNATE_PATH,
    /** Soft alternate: pick a different target if the task supports it. */
    ALTERNATE_TARGET,
    /** Pause / block until an external condition clears (e.g. free inventory). */
    WAIT,
    /** Give up this task; fail upward as non-recoverable. */
    ABORT,
    /** Fail upward with enriched context for a higher layer (Phase 7 {@code PlanExecutor}). */
    ESCALATE
}
