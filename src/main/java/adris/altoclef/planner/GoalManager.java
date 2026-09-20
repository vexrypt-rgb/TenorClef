package adris.altoclef.planner;

import adris.altoclef.tasksystem.RecoveryManager;

/**
 * Thin façade naming for the Phase 7 planning layer.
 * Delegates to {@link PlanExecutor} (kept as the state machine).
 */
public class GoalManager extends PlanExecutor {

    public GoalManager() {
        super();
    }

    public GoalManager(Planner planner, RecoveryManager recoveryManager) {
        super(planner, recoveryManager);
    }
}
