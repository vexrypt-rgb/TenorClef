package adris.altoclef.planner;

/**
 * Lifecycle of a {@link Goal} under {@link PlanExecutor}.
 */
public enum GoalStatus {
    IDLE,
    RUNNING,
    /** Strategic work paused for HIGH threat; MobDefense/WorldSurvival still run. */
    PAUSED,
    SUCCESS,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }
}
