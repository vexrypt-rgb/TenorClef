package adris.altoclef.tasksystem;

import adris.altoclef.benchmark.LiveBenchmarkSession;

import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.TimeoutWanderTask;

import java.util.function.Predicate;

public abstract class Task {

    private String oldDebugState = "";
    private String debugState = "";

    private Task sub = null;

    private boolean first = true;

    private boolean stopped = false;

    private boolean active = false;

    /** Explicit Phase 4 outcome; null means "never set — use legacy shim". */
    private TaskResult explicitResult = null;

    private TaskFailure lastFailure = null;

    /** Last recovery decision from absorb / failWithRecovery (Phase 6). */
    private RecoveryDecision lastRecovery = null;

    /** Wall-clock ms when this run started (0 = never started). Observability only. */
    private long startMillis = 0;

    public void tick(TaskChain parentChain) {
        parentChain.addTaskToChain(this);
        if (first) {
            Debug.logInternal("Task START: " + this);
            active = true;
            startMillis = System.currentTimeMillis();
            onStart();
            first = false;
            stopped = false;
            // Fresh run starts RUNNING unless subclass set something in onStart
            if (explicitResult == null) {
                explicitResult = TaskResult.RUNNING;
            }
        }
        if (stopped) return;

        Task newSub = onTick();
        // Debug state print
        if (!oldDebugState.equals(debugState)) {
            Debug.logInternal(toString());
            oldDebugState = debugState;
        }
        // We have a sub task
        if (newSub != null) {
            if (!newSub.isEqual(sub)) {
                if (canBeInterrupted(sub, newSub)) {
                    // Our sub task is new
                    if (sub != null) {
                        // Our previous sub must be interrupted.
                        sub.stop(newSub);
                    }

                    sub = newSub;
                }
            }

            // Run our child
            sub.tick(parentChain);
            absorbChildOutcome(sub);
        } else {
            // We are null
            if (sub != null && canBeInterrupted(sub, null)) {
                // Our previous sub must be interrupted.
                sub.stop();
                sub = null;
            }
        }

        // Legacy finish → SUCCESS without forcing every subclass to call succeed()
        if (isFinished()) {
            TaskResult resolved = TaskResultMapper.resolve(explicitResult, true, stopped, active);
            if (resolved == TaskResult.SUCCESS
                    && (explicitResult == null
                    || explicitResult == TaskResult.RUNNING
                    || explicitResult == TaskResult.RETRY)) {
                succeed();
            }
        }
    }

    public void reset() {
        first = true;
        active = false;
        stopped = false;
        explicitResult = null;
        lastFailure = null;
        lastRecovery = null;
    }

    public void stop() {
        stop(null);
    }

    /**
     * Stops the task. Next time it's run it will run `onStart`
     */
    public void stop(Task interruptTask) {
        if (!active) return;
        Debug.logInternal("Task STOP: " + this + ", interrupted by " + interruptTask);
        if (!first) {
            onStop(interruptTask);
        }

        if (sub != null && !sub.stopped()) {
            sub.stop(interruptTask);
        }

        first = true;
        active = false;
        stopped = true;
        if (explicitResult == null
                || explicitResult == TaskResult.RUNNING
                || explicitResult == TaskResult.RETRY) {
            explicitResult = TaskResult.CANCELLED;
        }
    }

    /**
     * Lets the task know it's execution has been "suspended"
     * <p>
     * STILL RUNS `onStop`
     * <p>
     * Doesn't stop it all-together (meaning `isActive` still returns true)
     */
    public void interrupt(Task interruptTask) {
        if (!active) return;
        if (!first) {
            onStop(interruptTask);
        }

        if (sub != null && !sub.stopped()) {
            sub.interrupt(interruptTask);
        }

        first = true;
    }

    protected void setDebugState(String state) {
        if (state == null) {
            state = "";
        }
        debugState = state;
    }

    // Virtual
    public boolean isFinished() {
        return false;
    }

    public boolean isActive() {
        return active;
    }

    public boolean stopped() {
        return stopped;
    }

    // —— Phase 4 structured outcomes (optional; unmigrated tasks keep boolean API) ——

    /**
     * Effective result for observers. Shims legacy {@link #isFinished()} / stop.
     */
    public TaskResult getLastResult() {
        return TaskResultMapper.resolve(explicitResult, isFinished(), stopped, active);
    }

    /** Explicit result only (null if never set by this task). */
    public TaskResult getExplicitResult() {
        return explicitResult;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public String getDebugState() {
        return debugState;
    }

    public TaskFailure getLastFailure() {
        return lastFailure;
    }

    /** Phase 6: last recovery decision, if any. */
    public RecoveryDecision getLastRecovery() {
        return lastRecovery;
    }

    /** Override to supply a custom policy; default is {@link RecoveryManager#DEFAULT}. */
    protected RecoveryManager getRecoveryManager() {
        return RecoveryManager.DEFAULT;
    }

    protected void setResult(TaskResult result) {
        this.explicitResult = result != null ? result : TaskResult.RUNNING;
        if (this.explicitResult == TaskResult.SUCCESS
                || this.explicitResult == TaskResult.RUNNING) {
            // SUCCESS / clear running drops stale failure
            if (this.explicitResult == TaskResult.SUCCESS) {
                this.lastFailure = null;
            }
        }
    }

    protected void succeed() {
        this.explicitResult = TaskResult.SUCCESS;
        this.lastFailure = null;
        LiveBenchmarkSession.noteTaskResult(TaskResult.SUCCESS);
    }

    /**
     * Record a structured failure. Recoverable → {@link TaskResult#RETRY};
     * otherwise {@link TaskResult#FAILURE}. Does not stop the tick loop.
     */
    protected void fail(FailureReason reason, String message, boolean recoverable) {
        fail(new TaskFailure(reason, message, recoverable));
    }

    protected void fail(TaskFailure failure) {
        if (failure == null) {
            failure = new TaskFailure(FailureReason.UNKNOWN, "", false);
        }
        this.lastFailure = failure;
        this.explicitResult = failure.toResult();
        LiveBenchmarkSession.noteFailure(failure.getReason());
        LiveBenchmarkSession.noteTaskResult(this.explicitResult);
    }

    /**
     * Record a failure after consulting {@link RecoveryManager}.
     * Increments retry count, may flip to ABORT/ESCALATE (non-recoverable FAILURE)
     * or WAIT ({@link TaskResult#BLOCKED}).
     *
     * @return the decision applied (for callers that branch on RETRY vs ABORT)
     */
    protected RecoveryDecision failWithRecovery(FailureReason reason, String message) {
        int prior = 0;
        if (lastFailure != null && lastFailure.getReason() == reason) {
            prior = lastFailure.getRetryCount();
        }
        TaskFailure seed = new TaskFailure(reason, message, true, prior);
        RecoveryManager.Applied applied = getRecoveryManager().apply(seed);
        this.lastRecovery = applied.getDecision();
        this.lastFailure = applied.getFailure();
        this.explicitResult = applied.getResult();
        return applied.getDecision();
    }

    protected void blocked(FailureReason reason, String message) {
        this.lastFailure = new TaskFailure(reason, message, true);
        this.explicitResult = TaskResult.BLOCKED;
    }

    /**
     * Pull FAILURE / RETRY / BLOCKED from a child into this task when we have
     * no stronger explicit outcome yet. Phase 6: re-consult RecoveryManager so
     * parents get enriched ABORT/ESCALATE after child retry limits.
     */
    protected void absorbChildOutcome(Task child) {
        if (child == null) return;
        TaskResult childExplicit = child.getExplicitResult();
        TaskResult childEffective = child.getLastResult();
        TaskResult childForAbsorb = childExplicit != null ? childExplicit : childEffective;
        if (!TaskResultMapper.shouldAbsorbChild(explicitResult, childForAbsorb)) {
            return;
        }
        TaskFailure childFail = child.getLastFailure();
        // Phase 6: structured failures get RecoveryManager enrichment.
        // If the child already called failWithRecovery, trust its decision;
        // otherwise apply policy once here (raw fail() emitters) — but only for failures the
        // child marked recoverable. A raw fail(..., recoverable=false) is the child saying
        // "do not retry me"; re-running policy from retry 0 turned it into ALTERNATE_PATH/RETRY
        // (TaskPropagationTest.parentAbsorbsChildFailure).
        if (childFail != null && shouldRecoverChildFailure(childFail.getReason())
                && (childFail.isRecoverable() || child.getLastRecovery() != null)) {
            RecoveryDecision d = child.getLastRecovery();
            if (d == null) {
                RecoveryManager.Applied applied = getRecoveryManager().apply(childFail);
                lastRecovery = applied.getDecision();
                lastFailure = applied.getFailure();
                explicitResult = applied.getResult();
                return;
            }
            lastRecovery = d;
            if (d.isTerminal()) {
                if (childFail.isRecoverable()) {
                    lastFailure = new TaskFailure(
                            childFail.getReason(),
                            d.enrichMessage(childFail.getMessage()),
                            false,
                            childFail.getRetryCount());
                } else {
                    lastFailure = childFail;
                }
                explicitResult = TaskResult.FAILURE;
            } else if (d.getAction() == RecoveryAction.WAIT) {
                lastFailure = childFail;
                explicitResult = TaskResult.BLOCKED;
            } else {
                lastFailure = childFail;
                explicitResult = childForAbsorb;
            }
            return;
        }
        explicitResult = childForAbsorb;
        lastFailure = TaskResultMapper.absorbFailure(lastFailure, childFail);
        if (child.getLastRecovery() != null) {
            lastRecovery = child.getLastRecovery();
        }
    }

    /**
     * Reasons Phase 6 recovers (others pass through unchanged).
     */
    protected boolean shouldRecoverChildFailure(FailureReason reason) {
        return reason == FailureReason.NO_PATH
                || reason == FailureReason.TIMEOUT
                || reason == FailureReason.TARGET_UNAVAILABLE
                || reason == FailureReason.INVENTORY_FULL;
    }

    /** Immediate child, if any (for tests / debugging). */
    public Task getSub() {
        return sub;
    }

    protected abstract void onStart();

    protected abstract Task onTick();

    // interruptTask = null if the task stopped cleanly
    protected abstract void onStop(Task interruptTask);

    protected abstract boolean isEqual(Task other);

    protected abstract String toDebugString();

    @Override
    public String toString() {
        return "<" + toDebugString() + "> " + debugState;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Task task) {
            return isEqual(task);
        }
        return false;
    }

    public boolean thisOrChildSatisfies(Predicate<Task> pred) {
        Task t = this;
        while (t != null) {
            if (pred.test(t)) return true;
            t = t.sub;
        }
        return false;
    }

    public boolean thisOrChildAreTimedOut() {
        return thisOrChildSatisfies(task -> task instanceof TimeoutWanderTask);
    }

    /**
     * Sometimes a task just can NOT be bothered to be interrupted right now.
     * For instance, if we're in mid air and MUST complete the parkour movement.
     */
    private boolean canBeInterrupted(Task subTask, Task toInterruptWith) {
        if (subTask == null) return true;
        // Our task can declare that is FORCES itself to be active NOW.
        return (subTask.thisOrChildSatisfies(task -> {
            if (task instanceof ITaskCanForce canForce) {
                return !canForce.shouldForce(toInterruptWith);
            }
            return true;
        }));
    }
}
