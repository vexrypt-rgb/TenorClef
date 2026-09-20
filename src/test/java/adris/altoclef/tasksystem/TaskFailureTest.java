package adris.altoclef.tasksystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TaskFailureTest {

    @Test
    void recoverableMapsToRetry() {
        TaskFailure f = new TaskFailure(FailureReason.TIMEOUT, "stall", true);
        Assertions.assertEquals(TaskResult.RETRY, f.toResult());
        Assertions.assertTrue(f.isRecoverable());
        Assertions.assertEquals(0, f.getRetryCount());
    }

    @Test
    void terminalMapsToFailure() {
        TaskFailure f = new TaskFailure(FailureReason.TARGET_UNAVAILABLE, "gone", false);
        Assertions.assertEquals(TaskResult.FAILURE, f.toResult());
    }

    @Test
    void incrementRetry() {
        TaskFailure f = new TaskFailure(FailureReason.TIMEOUT, "x", true, 2).incrementedRetry();
        Assertions.assertEquals(3, f.getRetryCount());
        Assertions.assertEquals(FailureReason.TIMEOUT, f.getReason());
    }

    @Test
    void nullReasonBecomesUnknown() {
        Assertions.assertEquals(FailureReason.UNKNOWN,
                new TaskFailure(null, null, false).getReason());
        Assertions.assertEquals("", new TaskFailure(FailureReason.NO_PATH, null, true).getMessage());
    }
}
