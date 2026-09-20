package adris.altoclef.planner;

/**
 * Lifecycle of a {@link Goal} under {@link PlanExecutor}.
 */
public enum GoalStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }
}
