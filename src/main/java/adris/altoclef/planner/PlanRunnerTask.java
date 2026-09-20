package adris.altoclef.planner;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.RecoveryDecision;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskFailure;
import adris.altoclef.tasksystem.TaskResult;

/**
 * Executes a {@link PlanExecutor} by constructing catalogue Tasks for each
 * {@link PlanStep}. Does not replace {@code @get} / UserTaskChain — only used
 * when a goal is started via {@code @goal}.
 */
public class PlanRunnerTask extends Task {

    private final PlanExecutor executor;
    private Task stepTask;
    private String stepKey;
    private int stepCount;
    /** True once we have handed a terminal step failure to the executor. */
    private boolean failureHandled;

    public PlanRunnerTask(PlanExecutor executor) {
        this.executor = executor;
    }

    public PlanExecutor getExecutor() {
        return executor;
    }

    @Override
    protected void onStart() {
        failureHandled = false;
        stepTask = null;
        stepKey = null;
        setDebugState("plan start");
    }

    @Override
    protected void onStop(Task interruptTask) {
        if (executor != null && !executor.getStatus().isTerminal()) {
            executor.cancel();
        }
        stepTask = null;
    }

    @Override
    public boolean isFinished() {
        return executor != null && executor.getStatus() == GoalStatus.SUCCESS;
    }

    @Override
    protected Task onTick() {
        if (executor == null) {
            fail(FailureReason.PRECONDITION_FAILED, "no executor", false);
            return null;
        }

        AltoClef mod = AltoClef.getInstance();
        if (mod != null) {
            executor.setInventory(new CatalogueInventoryView(mod));
        }

        // Early exit if goal already met (inventory filled)
        Goal goal = executor.getGoal();
        if (goal != null && goal.isSatisfied(executor.getInventory())) {
            executor.markGoalSuccess("inventory satisfied");
            succeed();
            setDebugState("goal satisfied");
            return null;
        }

        GoalStatus st = executor.getStatus();
        if (st == GoalStatus.SUCCESS) {
            succeed();
            return null;
        }
        if (st == GoalStatus.FAILED || st == GoalStatus.CANCELLED) {
            if (getExplicitResult() != TaskResult.FAILURE) {
                fail(FailureReason.RESOURCE_MISSING,
                        "goal " + st + ": " + executor.getLastNote(), false);
            }
            setDebugState("goal " + st);
            return null;
        }
        if (st != GoalStatus.RUNNING) {
            fail(FailureReason.PRECONDITION_FAILED, "executor idle", false);
            return null;
        }

        // Child finished successfully → advance plan
        Task sub = getSub();
        if (sub != null && sub.isFinished() && !failureHandled) {
            executor.onStepSuccess();
            clearStep();
            setResult(TaskResult.RUNNING);
            if (executor.getStatus() == GoalStatus.SUCCESS) {
                succeed();
                return null;
            }
            if (executor.getStatus() == GoalStatus.FAILED) {
                fail(FailureReason.RESOURCE_MISSING, executor.getLastNote(), false);
                return null;
            }
            return ensureStepTask();
        }

        // Child / absorb reported terminal failure → PlanExecutor + RecoveryManager
        TaskResult absorbed = getExplicitResult();
        if (absorbed == TaskResult.FAILURE && !failureHandled) {
            failureHandled = true;
            TaskFailure failure = getLastFailure();
            if (failure == null) {
                failure = new TaskFailure(FailureReason.UNKNOWN, "step failed", false);
            }
            RecoveryDecision d = executor.onStepFailure(failure);
            clearStep();
            if (executor.getStatus() == GoalStatus.RUNNING) {
                // Soft recovery already applied by child, or naive replan succeeded
                failureHandled = false;
                setResult(TaskResult.RUNNING);
                setDebugState("recovery/replan: " + d);
                return ensureStepTask();
            }
            setDebugState("goal failed: " + d);
            return null;
        }

        // Soft RETRY / BLOCKED — keep same catalogue step
        if (absorbed == TaskResult.RETRY || absorbed == TaskResult.BLOCKED) {
            setDebugState("soft recovery, same step");
            return ensureStepTask();
        }

        return ensureStepTask();
    }

    private void clearStep() {
        stepTask = null;
        stepKey = null;
        stepCount = 0;
    }

    private Task ensureStepTask() {
        PlanStep step = executor.currentStep();
        if (step == null) {
            Goal g = executor.getGoal();
            if (g != null && g.isSatisfied(executor.getInventory())) {
                executor.markGoalSuccess("no step, satisfied");
                succeed();
                return null;
            }
            fail(FailureReason.RESOURCE_MISSING,
                    "no plan step: " + executor.getLastNote(), false);
            return null;
        }

        String key = step.getCatalogueKey();
        int count = step.getCount();
        if (stepTask != null && key.equals(stepKey) && count == stepCount) {
            setDebugState(step.getLabel());
            return stepTask;
        }

        if (!TaskCatalogue.taskExists(key)) {
            TaskFailure f = new TaskFailure(
                    FailureReason.PRECONDITION_FAILED, "catalogue missing: " + key, false);
            fail(f);
            executor.onStepFailure(f);
            return null;
        }

        Task built = TaskCatalogue.getItemTask(key, count);
        if (built == null) {
            fail(FailureReason.PRECONDITION_FAILED, "null task for: " + key, false);
            return null;
        }
        stepTask = built;
        stepKey = key;
        stepCount = count;
        setDebugState(step.getLabel());
        Debug.logMessage("Plan step → catalogue: " + key + " x" + count);
        return stepTask;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof PlanRunnerTask o) {
            Goal a = executor != null ? executor.getGoal() : null;
            Goal b = o.executor != null ? o.executor.getGoal() : null;
            if (a == null || b == null) {
                return a == b;
            }
            return a.getId().equals(b.getId());
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        if (executor == null) {
            return "PlanRunner(null)";
        }
        return "PlanRunner(" + executor.summarize() + ")";
    }
}
