package adris.altoclef.tasksystem;

/**
 * Pure helpers for legacy boolean ↔ {@link TaskResult} and child→parent
 * failure propagation. No Minecraft dependency — offline-testable.
 */
public final class TaskResultMapper {

    private TaskResultMapper() {
    }

    /**
     * Resolve the effective result for observers.
     *
     * @param explicit   last explicitly set result, or null if never set
     * @param finished   legacy {@link Task#isFinished()}
     * @param stopped    task was stopped
     * @param active     task is/was active
     */
    public static TaskResult resolve(TaskResult explicit, boolean finished, boolean stopped, boolean active) {
        if (finished) {
            if (explicit == null
                    || explicit == TaskResult.RUNNING
                    || explicit == TaskResult.RETRY) {
                return TaskResult.SUCCESS;
            }
            // Terminal FAILURE/CANCELLED/BLOCKED win even if isFinished somehow true
            if (explicit == TaskResult.FAILURE
                    || explicit == TaskResult.CANCELLED
                    || explicit == TaskResult.BLOCKED) {
                return explicit;
            }
            return TaskResult.SUCCESS;
        }
        if (stopped && !active) {
            if (explicit == TaskResult.FAILURE || explicit == TaskResult.BLOCKED) {
                return explicit;
            }
            return TaskResult.CANCELLED;
        }
        if (explicit != null) {
            return explicit;
        }
        return TaskResult.RUNNING;
    }

    /**
     * Whether a child outcome should overwrite a parent's soft state.
     */
    public static boolean shouldAbsorbChild(TaskResult parentExplicit, TaskResult childResult) {
        if (childResult == null || childResult == TaskResult.RUNNING || childResult == TaskResult.SUCCESS) {
            return false;
        }
        if (parentExplicit == null
                || parentExplicit == TaskResult.RUNNING
                || parentExplicit == TaskResult.RETRY) {
            return childResult == TaskResult.FAILURE
                    || childResult == TaskResult.BLOCKED
                    || childResult == TaskResult.RETRY
                    || childResult == TaskResult.CANCELLED;
        }
        // Parent already terminal — keep parent
        return false;
    }

    /**
     * Pick failure to store when absorbing a child.
     */
    public static TaskFailure absorbFailure(TaskFailure parentFailure, TaskFailure childFailure) {
        if (childFailure != null) {
            return childFailure;
        }
        return parentFailure;
    }

    public static boolean isTerminal(TaskResult result) {
        return result == TaskResult.SUCCESS
                || result == TaskResult.FAILURE
                || result == TaskResult.CANCELLED;
    }
}
