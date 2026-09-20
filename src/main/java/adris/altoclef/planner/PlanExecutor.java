package adris.altoclef.planner;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.RecoveryAction;
import adris.altoclef.tasksystem.RecoveryDecision;
import adris.altoclef.tasksystem.RecoveryManager;
import adris.altoclef.tasksystem.TaskFailure;
import adris.altoclef.threat.ThreatAssessment;
import adris.altoclef.threat.ThreatLevel;

/**
 * Runs a {@link Plan} step-by-step above the task layer (Phase 7).
 * <p>
 * On step {@link TaskFailure}, consults {@link RecoveryManager}. Soft actions
 * (RETRY / ALTERNATE_* / WAIT) leave the current step running. On ABORT (or
 * ESCALATE), marks the step failed and — once — naively rebuilds the plan via
 * the {@link Planner}. A second terminal failure fails the goal.
 * <p>
 * Pure Java: does not construct Minecraft tasks; callers / {@code PlanRunnerTask}
 * map {@link PlanStep#getCatalogueKey()} → TaskCatalogue.
 */
public class PlanExecutor {

    private final Planner planner;
    private final RecoveryManager recoveryManager;
    private InventoryView inventory;

    private Goal goal;
    private Plan plan;
    private GoalStatus status = GoalStatus.IDLE;
    private boolean hasReplanned;
    private RecoveryDecision lastDecision;
    private String lastNote = "";

    public PlanExecutor() {
        this(SimplePlanner.INSTANCE, RecoveryManager.DEFAULT);
    }

    public PlanExecutor(Planner planner, RecoveryManager recoveryManager) {
        this.planner = planner != null ? planner : SimplePlanner.INSTANCE;
        this.recoveryManager = recoveryManager != null ? recoveryManager : RecoveryManager.DEFAULT;
    }

    public void setInventory(InventoryView inventory) {
        this.inventory = inventory;
    }

    public InventoryView getInventory() {
        return inventory;
    }

    public Goal getGoal() {
        return goal;
    }

    public Plan getPlan() {
        return plan;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public boolean hasReplanned() {
        return hasReplanned;
    }

    public RecoveryDecision getLastDecision() {
        return lastDecision;
    }

    public String getLastNote() {
        return lastNote;
    }

    public Planner getPlanner() {
        return planner;
    }

    public RecoveryManager getRecoveryManager() {
        return recoveryManager;
    }

    /**
     * Start pursuing {@code goal}. Builds an initial plan from current inventory.
     */
    public void start(Goal goal, InventoryView inventory) {
        this.goal = goal;
        this.inventory = inventory;
        this.hasReplanned = false;
        this.lastDecision = null;
        this.lastNote = "";
        if (goal == null) {
            this.plan = Plan.empty();
            this.status = GoalStatus.FAILED;
            this.lastNote = "null goal";
            return;
        }
        if (goal.isSatisfied(inventory)) {
            this.plan = Plan.empty();
            this.status = GoalStatus.SUCCESS;
            this.lastNote = "already satisfied";
            return;
        }
        this.plan = planner.plan(goal, inventory);
        if (plan.isEmpty()) {
            this.status = GoalStatus.FAILED;
            this.lastNote = "empty plan for unsatisfied goal";
            return;
        }
        plan.beginCurrent();
        this.status = GoalStatus.RUNNING;
        this.lastNote = "started";
    }

    /** Convenience overload using previously set inventory. */
    public void start(Goal goal) {
        start(goal, this.inventory);
    }

    public void cancel() {
        if (!status.isTerminal()) {
            status = GoalStatus.CANCELLED;
            lastNote = "cancelled";
        }
    }

    /** Current plan step, or null. */
    public PlanStep currentStep() {
        return plan != null ? plan.currentStep() : null;
    }

    /**
     * Current step succeeded (underlying Task finished SUCCESS / isFinished).
     * Advances or completes the goal (re-checks inventory satisfaction).
     */
    public void onStepSuccess() {
        if (status != GoalStatus.RUNNING || plan == null) {
            return;
        }
        boolean more = plan.completeCurrent();
        if (goal != null && goal.isSatisfied(inventory)) {
            status = GoalStatus.SUCCESS;
            plan.setStatus(PlanStatus.SUCCESS);
            lastNote = "goal satisfied after step";
            return;
        }
        if (!more) {
            // Plan exhausted — check goal once more
            if (goal != null && goal.isSatisfied(inventory)) {
                status = GoalStatus.SUCCESS;
                lastNote = "plan done, goal satisfied";
            } else {
                status = GoalStatus.FAILED;
                plan.setStatus(PlanStatus.FAILED);
                lastNote = "plan exhausted, goal unmet";
            }
            return;
        }
        plan.beginCurrent();
        lastNote = "advanced to step " + plan.getCurrentIndex();
    }

    /**
     * Current step reported a structured failure. Consults RecoveryManager.
     *
     * @return the decision applied (for status / logging)
     */
    public RecoveryDecision onStepFailure(TaskFailure failure) {
        if (status != GoalStatus.RUNNING) {
            lastDecision = new RecoveryDecision(RecoveryAction.ABORT, 0, 0, "executor not running");
            return lastDecision;
        }
        RecoveryManager.Applied applied = recoveryManager.apply(failure);
        lastDecision = applied.getDecision();

        if (lastDecision.shouldContinue()) {
            // Soft recovery: keep current step RUNNING; caller retries same Task
            lastNote = "soft recovery " + lastDecision;
            return lastDecision;
        }

        // Terminal for this step
        if (plan != null) {
            plan.failCurrent();
        }

        if (lastDecision.getAction() == RecoveryAction.ABORT && !hasReplanned) {
            return replanOnce("abort → naive replan");
        }

        status = GoalStatus.FAILED;
        lastNote = "terminal " + lastDecision.getAction()
                + (hasReplanned ? " after replan" : "");
        return lastDecision;
    }

    /**
     * Naive replan: rebuild plan from current inventory. Allowed once per goal run.
     */
    public RecoveryDecision replanOnce(String reason) {
        hasReplanned = true;
        lastNote = reason != null ? reason : "replan";
        if (goal == null) {
            status = GoalStatus.FAILED;
            return lastDecision;
        }
        if (goal.isSatisfied(inventory)) {
            plan = Plan.empty();
            status = GoalStatus.SUCCESS;
            lastNote = "replan: already satisfied";
            return lastDecision;
        }
        plan = planner.plan(goal, inventory);
        if (plan.isEmpty()) {
            status = GoalStatus.FAILED;
            lastNote = "replan: empty plan";
            return lastDecision;
        }
        plan.beginCurrent();
        status = GoalStatus.RUNNING;
        lastNote = "replanned (" + plan.size() + " steps)";
        return lastDecision;
    }

    /** Force SUCCESS (e.g. inventory already holds the target). */
    public void markGoalSuccess(String note) {
        status = GoalStatus.SUCCESS;
        if (plan != null) {
            plan.setStatus(PlanStatus.SUCCESS);
        }
        lastNote = note != null ? note : "success";
    }


    /**
     * Phase 8: react to a threat assessment.
     * <ul>
     *   <li>CRITICAL → fail current goal via {@link FailureReason#DANGER}
     *       (RecoveryManager ESCALATEs; no naive replan)</li>
     *   <li>HIGH → pause ({@link GoalStatus#PAUSED})</li>
     *   <li>below HIGH → resume if paused</li>
     * </ul>
     *
     * @return true if status changed
     */
    public boolean applyThreat(ThreatAssessment assessment) {
        if (assessment == null || status.isTerminal()) {
            return false;
        }
        ThreatLevel level = assessment.getLevel();
        if (level == ThreatLevel.CRITICAL) {
            return failForDanger(assessment.getReason());
        }
        if (level == ThreatLevel.HIGH) {
            if (status == GoalStatus.RUNNING) {
                status = GoalStatus.PAUSED;
                lastNote = "paused for threat: " + assessment.getReason();
                return true;
            }
            return false;
        }
        // Clear enough — resume
        if (status == GoalStatus.PAUSED) {
            status = GoalStatus.RUNNING;
            lastNote = "resumed (threat " + level + ")";
            return true;
        }
        return false;
    }

    /**
     * Fail the running/paused goal as DANGER. Uses RecoveryManager (ESCALATE);
     * skips naive replan so survival/combat chains can take over.
     */
    public boolean failForDanger(String reason) {
        if (status.isTerminal()) {
            return false;
        }
        String msg = reason != null && !reason.isEmpty() ? reason : "danger";
        TaskFailure failure = new TaskFailure(FailureReason.DANGER, msg, false, 0);
        RecoveryManager.Applied applied = recoveryManager.apply(failure);
        lastDecision = applied.getDecision();
        if (plan != null && plan.currentStep() != null) {
            plan.failCurrent();
        }
        status = GoalStatus.FAILED;
        lastNote = "DANGER: " + msg + " [" + lastDecision.getAction() + "]";
        return true;
    }

    /** Debug / status line. */
    public String summarize() {
        String g = goal != null ? goal.getId() : "-";
        PlanStep step = currentStep();
        return "goal=" + g
                + " status=" + status
                + " replan=" + hasReplanned
                + " step=" + (step != null ? step.getLabel() : "-")
                + (lastNote.isEmpty() ? "" : " note=" + lastNote);
    }
}
