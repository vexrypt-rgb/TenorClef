package adris.altoclef.tasksystem;

/**
 * Structured outcome for a task tick / lifecycle (Phase 4).
 * <p>
 * Legacy tasks may never set this explicitly; {@link Task#getLastResult()}
 * shims from {@link Task#isFinished()} / stopped state.
 */
public enum TaskResult {
    /** Still executing. */
    RUNNING,
    /** Goal achieved. */
    SUCCESS,
    /** Terminal or reported failure (see {@link TaskFailure}). */
    FAILURE,
    /** Stopped by interrupt / chain cancel. */
    CANCELLED,
    /** Cannot proceed until an external condition clears. */
    BLOCKED,
    /** Soft failure; task (or parent) should retry / alternate. */
    RETRY
}
