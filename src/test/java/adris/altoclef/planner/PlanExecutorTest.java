package adris.altoclef.planner;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.RecoveryAction;
import adris.altoclef.tasksystem.RecoveryDecision;
import adris.altoclef.tasksystem.RecoveryManager;
import adris.altoclef.tasksystem.TaskFailure;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 7: PlanExecutor advances steps, consults RecoveryManager, replans once.
 */
public class PlanExecutorTest {

    private static final class FakeInv implements InventoryView {
        final Map<String, Integer> counts = new HashMap<>();

        @Override
        public int getCount(String catalogueKey) {
            return counts.getOrDefault(catalogueKey, 0);
        }

        void set(String key, int n) {
            counts.put(key, n);
        }
    }

    @Test
    void startBuildsPlanAndRuns() {
        FakeInv inv = new FakeInv();
        PlanExecutor ex = new PlanExecutor();
        ex.start(new AcquireItemGoal("cobblestone", 16), inv);
        Assertions.assertEquals(GoalStatus.RUNNING, ex.getStatus());
        Assertions.assertNotNull(ex.currentStep());
        Assertions.assertEquals("cobblestone", ex.currentStep().getCatalogueKey());
        Assertions.assertEquals(PlanStepStatus.RUNNING, ex.currentStep().getStatus());
    }

    @Test
    void alreadySatisfiedSucceedsImmediately() {
        FakeInv inv = new FakeInv();
        inv.set("dirt", 10);
        PlanExecutor ex = new PlanExecutor();
        ex.start(new AcquireItemGoal("dirt", 5), inv);
        Assertions.assertEquals(GoalStatus.SUCCESS, ex.getStatus());
        Assertions.assertTrue(ex.getPlan().isEmpty());
    }

    @Test
    void stepSuccessCompletesGoalWhenInventoryMet() {
        FakeInv inv = new FakeInv();
        PlanExecutor ex = new PlanExecutor();
        ex.start(new AcquireItemGoal("cobblestone", 8), inv);
        // Simulate collection completing
        inv.set("cobblestone", 8);
        ex.onStepSuccess();
        Assertions.assertEquals(GoalStatus.SUCCESS, ex.getStatus());
    }

    @Test
    void softRecoveryKeepsRunning() {
        FakeInv inv = new FakeInv();
        PlanExecutor ex = new PlanExecutor(SimplePlanner.INSTANCE, new RecoveryManager(3, 2));
        ex.start(new AcquireItemGoal("log", 4), inv);
        RecoveryDecision d = ex.onStepFailure(
                new TaskFailure(FailureReason.TIMEOUT, "stall", true, 0));
        Assertions.assertEquals(RecoveryAction.ALTERNATE_PATH, d.getAction());
        Assertions.assertEquals(GoalStatus.RUNNING, ex.getStatus());
        Assertions.assertEquals(PlanStepStatus.RUNNING, ex.currentStep().getStatus());
        Assertions.assertFalse(ex.hasReplanned());
    }

    @Test
    void abortReplansOnceThenFails() {
        FakeInv inv = new FakeInv();
        PlanExecutor ex = new PlanExecutor(SimplePlanner.INSTANCE, new RecoveryManager(3, 2));
        ex.start(new AcquireItemGoal("iron_ingot", 1), inv);

        // Exhaust path retries → ABORT → naive replan
        TaskFailure exhausted = new TaskFailure(FailureReason.TIMEOUT, "stall", true, 3);
        RecoveryDecision d1 = ex.onStepFailure(exhausted);
        Assertions.assertEquals(RecoveryAction.ABORT, d1.getAction());
        Assertions.assertTrue(ex.hasReplanned());
        Assertions.assertEquals(GoalStatus.RUNNING, ex.getStatus());
        Assertions.assertNotNull(ex.currentStep());
        Assertions.assertEquals(PlanStepStatus.RUNNING, ex.currentStep().getStatus());

        // Second terminal ABORT → fail goal
        RecoveryDecision d2 = ex.onStepFailure(
                new TaskFailure(FailureReason.TIMEOUT, "stall again", true, 3));
        Assertions.assertEquals(RecoveryAction.ABORT, d2.getAction());
        Assertions.assertEquals(GoalStatus.FAILED, ex.getStatus());
    }

    @Test
    void abortReplanWhenSatisfiedSucceeds() {
        FakeInv inv = new FakeInv();
        PlanExecutor ex = new PlanExecutor();
        ex.start(new AcquireItemGoal("stick", 2), inv);
        inv.set("stick", 2); // filled during failed attempt
        RecoveryDecision d = ex.onStepFailure(
                new TaskFailure(FailureReason.TARGET_UNAVAILABLE, "gone", false, 0));
        Assertions.assertEquals(RecoveryAction.ABORT, d.getAction());
        Assertions.assertTrue(ex.hasReplanned());
        Assertions.assertEquals(GoalStatus.SUCCESS, ex.getStatus());
    }

    @Test
    void cancelSetsCancelled() {
        FakeInv inv = new FakeInv();
        PlanExecutor ex = new PlanExecutor();
        ex.start(new AcquireItemGoal("dirt", 1), inv);
        ex.cancel();
        Assertions.assertEquals(GoalStatus.CANCELLED, ex.getStatus());
    }

    @Test
    void goalManagerIsPlanExecutor() {
        GoalManager gm = new GoalManager();
        Assertions.assertTrue(gm instanceof PlanExecutor);
        FakeInv inv = new FakeInv();
        gm.start(new AcquireItemGoal("coal", 3), inv);
        Assertions.assertEquals(GoalStatus.RUNNING, gm.getStatus());
        Assertions.assertTrue(gm.summarize().contains("acquire:coal"));
    }
}
