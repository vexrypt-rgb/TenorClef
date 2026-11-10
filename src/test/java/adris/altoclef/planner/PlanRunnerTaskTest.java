package adris.altoclef.planner;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link PlanRunnerTask#isFinished()} is the only signal {@code SingleTaskChain} uses to stop a user task, so it must
 * be true for every terminal goal status — not just SUCCESS.
 * <p>
 * Regression: it answered {@code status == SUCCESS}, so a goal that FAILED kept the user chain ticking a dead task
 * forever: {@code fail()} records FAILURE without ending the task (see {@code Task#fail}), nothing else sets
 * {@code stopped()}, and {@code UserTaskChain.onTaskFinish} — which runs the {@code runUserTask} callback that
 * {@code @goal} and the agent runtime wait on — is only reached through {@code isFinished()}.
 */
public class PlanRunnerTaskTest {

    private static final class FakeInv implements InventoryView {
        final Map<String, Integer> counts = new HashMap<>();

        @Override
        public int getCount(String catalogueKey) {
            return counts.getOrDefault(catalogueKey, 0);
        }
    }

    private static PlanExecutor started(GoalStatus wanted) {
        PlanExecutor ex = new PlanExecutor();
        ex.start(new AcquireItemGoal("cobblestone", 8), new FakeInv());
        Assertions.assertEquals(GoalStatus.RUNNING, ex.getStatus(), "precondition: goal is running");
        switch (wanted) {
            case SUCCESS -> ex.markGoalSuccess("test");
            case FAILED -> {
                // Terminal ABORT path: retries exhausted, then a second terminal failure.
                ex.onStepFailure(new adris.altoclef.tasksystem.TaskFailure(
                        adris.altoclef.tasksystem.FailureReason.TIMEOUT, "a", true, 3));
                ex.onStepFailure(new adris.altoclef.tasksystem.TaskFailure(
                        adris.altoclef.tasksystem.FailureReason.TIMEOUT, "b", true, 3));
            }
            case CANCELLED -> ex.cancel();
            default -> throw new IllegalArgumentException(wanted.toString());
        }
        Assertions.assertEquals(wanted, ex.getStatus(), "precondition: goal reached " + wanted);
        return ex;
    }

    @Test
    void failedGoalFinishesTheTask() {
        PlanRunnerTask task = new PlanRunnerTask(started(GoalStatus.FAILED));
        Assertions.assertTrue(task.isFinished(), "a FAILED goal must report finished or the user chain never ends");
    }

    @Test
    void cancelledGoalFinishesTheTask() {
        PlanRunnerTask task = new PlanRunnerTask(started(GoalStatus.CANCELLED));
        Assertions.assertTrue(task.isFinished());
    }

    @Test
    void successfulGoalFinishesTheTask() {
        PlanRunnerTask task = new PlanRunnerTask(started(GoalStatus.SUCCESS));
        Assertions.assertTrue(task.isFinished());
    }

    @Test
    void runningGoalDoesNotFinish() {
        PlanExecutor ex = new PlanExecutor();
        ex.start(new AcquireItemGoal("cobblestone", 8), new FakeInv());
        Assertions.assertFalse(new PlanRunnerTask(ex).isFinished());
    }

    @Test
    void neverStartedGoalDoesNotFinish() {
        Assertions.assertFalse(new PlanRunnerTask(new PlanExecutor()).isFinished());
    }

    @Test
    void nullExecutorDoesNotFinish() {
        Assertions.assertFalse(new PlanRunnerTask(null).isFinished());
    }
}
