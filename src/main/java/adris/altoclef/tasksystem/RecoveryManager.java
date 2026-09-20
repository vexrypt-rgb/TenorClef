package adris.altoclef.tasksystem;

import adris.altoclef.benchmark.LiveBenchmarkSession;

/**
 * Maps {@link FailureReason} (+ retry count) → {@link RecoveryDecision}.
 * Pure Java — no Minecraft dependency. Not a GOAP / strategic planner.
 * <p>
 * Default limits (tunable via constructor):
 * <pre>
 * NO_PATH / TIMEOUT     → ALTERNATE_PATH then RETRY up to maxPathRetries, then ABORT
 * TARGET_UNAVAILABLE    → ABORT (ALTERNATE_TARGET only if caller already retargets)
 * INVENTORY_FULL        → WAIT up to maxInventoryWaits, then ABORT
 * DANGER / PLAYER_DEAD  → ESCALATE
 * RESOURCE_MISSING      → ESCALATE (planner later)
 * others                → ESCALATE
 * </pre>
 */
public class RecoveryManager {

    public static final RecoveryManager DEFAULT = new RecoveryManager();

    private final int maxPathRetries;
    private final int maxInventoryWaits;

    public RecoveryManager() {
        this(3, 2);
    }

    public RecoveryManager(int maxPathRetries, int maxInventoryWaits) {
        this.maxPathRetries = Math.max(1, maxPathRetries);
        this.maxInventoryWaits = Math.max(1, maxInventoryWaits);
    }

    public int getMaxPathRetries() {
        return maxPathRetries;
    }

    public int getMaxInventoryWaits() {
        return maxInventoryWaits;
    }

    public RecoveryDecision decide(TaskFailure failure) {
        if (failure == null) {
            return new RecoveryDecision(RecoveryAction.ESCALATE, 0, 0, "null failure");
        }
        return decide(failure.getReason(), failure.getRetryCount());
    }

    /**
     * @param reason     structured failure reason
     * @param retryCount attempts already spent for this reason (0 = first occurrence)
     */
    public RecoveryDecision decide(FailureReason reason, int retryCount) {
        FailureReason r = reason != null ? reason : FailureReason.UNKNOWN;
        int attempt = Math.max(0, retryCount);

        return switch (r) {
            case NO_PATH, TIMEOUT -> decidePathish(r, attempt);
            case TARGET_UNAVAILABLE ->
                    new RecoveryDecision(RecoveryAction.ABORT, attempt, 0, "target gone");
            case INVENTORY_FULL -> decideInventory(attempt);
            case DANGER, PLAYER_DEAD ->
                    new RecoveryDecision(RecoveryAction.ESCALATE, attempt, 0, "survival/threat");
            case RESOURCE_MISSING, WORLD_CHANGED, BACKEND_FAILURE, PRECONDITION_FAILED, UNKNOWN ->
                    new RecoveryDecision(RecoveryAction.ESCALATE, attempt, 0, r.name());
        };
    }

    private RecoveryDecision decidePathish(FailureReason reason, int attempt) {
        // attempt is count of prior fails; next try number = attempt + 1
        int next = attempt + 1;
        if (next <= maxPathRetries) {
            // First fail prefers ALTERNATE_PATH (wander/repath); later fails plain RETRY
            RecoveryAction action = (next == 1) ? RecoveryAction.ALTERNATE_PATH : RecoveryAction.RETRY;
            String note = reason == FailureReason.TIMEOUT ? "progress stall" : "no path";
            return new RecoveryDecision(action, next, maxPathRetries, note);
        }
        return new RecoveryDecision(RecoveryAction.ABORT, next, maxPathRetries, "path retries exhausted");
    }

    private RecoveryDecision decideInventory(int attempt) {
        int next = attempt + 1;
        if (next <= maxInventoryWaits) {
            return new RecoveryDecision(RecoveryAction.WAIT, next, maxInventoryWaits, "free a slot");
        }
        return new RecoveryDecision(RecoveryAction.ABORT, next, maxInventoryWaits, "inventory still full");
    }

    /**
     * Apply policy to an incoming failure: bump retry count, flip recoverable /
     * result according to the decision.
     */
    public Applied apply(TaskFailure incoming) {
        if (incoming == null) {
            RecoveryDecision d = decide((TaskFailure) null);
            TaskFailure f = new TaskFailure(FailureReason.UNKNOWN, d.enrichMessage(""), false, 0);
            return recordBench(new Applied(d, f, TaskResult.FAILURE));
        }
        RecoveryDecision d = decide(incoming);
        int nextCount = Math.max(incoming.getRetryCount() + 1, d.getAttempt());
        String msg = d.enrichMessage(incoming.getMessage());
        boolean recoverable = d.shouldContinue() && d.getAction() != RecoveryAction.WAIT;
        // WAIT → BLOCKED + recoverable flag true (condition may clear)
        if (d.getAction() == RecoveryAction.WAIT) {
            TaskFailure f = new TaskFailure(incoming.getReason(), msg, true, nextCount);
            return recordBench(new Applied(d, f, TaskResult.BLOCKED));
        }
        if (d.isTerminal()) {
            TaskFailure f = new TaskFailure(incoming.getReason(), msg, false, nextCount);
            return recordBench(new Applied(d, f, TaskResult.FAILURE));
        }
        TaskFailure f = new TaskFailure(incoming.getReason(), msg, true, nextCount);
        return recordBench(new Applied(d, f, d.toTaskResult()));
    }

    
    private static Applied recordBench(Applied applied) {
        if (applied != null) {
            if (applied.getDecision() != null) {
                LiveBenchmarkSession.noteRecovery(applied.getDecision().getAction());
            }
            if (applied.getFailure() != null) {
                LiveBenchmarkSession.noteFailure(applied.getFailure().getReason());
            }
            if (applied.getResult() != null) {
                LiveBenchmarkSession.noteTaskResult(applied.getResult());
            }
        }
        return applied;
    }

/** Bundle of decision + failure + result after {@link #apply}. */
    public static final class Applied {
        private final RecoveryDecision decision;
        private final TaskFailure failure;
        private final TaskResult result;

        public Applied(RecoveryDecision decision, TaskFailure failure, TaskResult result) {
            this.decision = decision;
            this.failure = failure;
            this.result = result;
        }

        public RecoveryDecision getDecision() {
            return decision;
        }

        public TaskFailure getFailure() {
            return failure;
        }

        public TaskResult getResult() {
            return result;
        }
    }
}
