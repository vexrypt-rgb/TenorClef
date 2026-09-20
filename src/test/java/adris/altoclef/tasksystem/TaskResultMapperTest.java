package adris.altoclef.tasksystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Phase 4: legacy boolean ↔ TaskResult mapping and child absorb rules.
 */
public class TaskResultMapperTest {

    @Test
    void finishedShimsToSuccessWhenUnset() {
        Assertions.assertEquals(TaskResult.SUCCESS,
                TaskResultMapper.resolve(null, true, false, true));
    }

    @Test
    void finishedOverridesRunningAndRetry() {
        Assertions.assertEquals(TaskResult.SUCCESS,
                TaskResultMapper.resolve(TaskResult.RUNNING, true, false, true));
        Assertions.assertEquals(TaskResult.SUCCESS,
                TaskResultMapper.resolve(TaskResult.RETRY, true, false, true));
    }

    @Test
    void terminalFailureSurvivesFinishedFlag() {
        Assertions.assertEquals(TaskResult.FAILURE,
                TaskResultMapper.resolve(TaskResult.FAILURE, true, false, true));
    }

    @Test
    void stoppedInactiveIsCancelled() {
        Assertions.assertEquals(TaskResult.CANCELLED,
                TaskResultMapper.resolve(null, false, true, false));
    }

    @Test
    void explicitRetryWhileActive() {
        Assertions.assertEquals(TaskResult.RETRY,
                TaskResultMapper.resolve(TaskResult.RETRY, false, false, true));
    }

    @Test
    void shouldAbsorbChildFailureIntoSoftParent() {
        Assertions.assertTrue(TaskResultMapper.shouldAbsorbChild(null, TaskResult.FAILURE));
        Assertions.assertTrue(TaskResultMapper.shouldAbsorbChild(TaskResult.RUNNING, TaskResult.RETRY));
        Assertions.assertTrue(TaskResultMapper.shouldAbsorbChild(TaskResult.RETRY, TaskResult.BLOCKED));
        Assertions.assertFalse(TaskResultMapper.shouldAbsorbChild(TaskResult.FAILURE, TaskResult.RETRY));
        Assertions.assertFalse(TaskResultMapper.shouldAbsorbChild(TaskResult.RUNNING, TaskResult.SUCCESS));
    }

    @Test
    void absorbFailurePrefersChild() {
        TaskFailure parent = new TaskFailure(FailureReason.UNKNOWN, "p", true);
        TaskFailure child = new TaskFailure(FailureReason.NO_PATH, "c", false);
        Assertions.assertSame(child, TaskResultMapper.absorbFailure(parent, child));
        Assertions.assertSame(parent, TaskResultMapper.absorbFailure(parent, null));
    }
}
