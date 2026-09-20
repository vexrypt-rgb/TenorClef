package adris.altoclef.tasksystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Phase 6: FailureReason → RecoveryDecision policy mapping.
 */
public class RecoveryManagerTest {

    private final RecoveryManager mgr = new RecoveryManager(3, 2);

    @Test
    void timeoutFirstIsAlternatePath() {
        RecoveryDecision d = mgr.decide(FailureReason.TIMEOUT, 0);
        Assertions.assertEquals(RecoveryAction.ALTERNATE_PATH, d.getAction());
        Assertions.assertEquals(1, d.getAttempt());
        Assertions.assertEquals(3, d.getMaxAttempts());
        Assertions.assertTrue(d.shouldContinue());
    }

    @Test
    void noPathRetriesThenAbort() {
        Assertions.assertEquals(RecoveryAction.ALTERNATE_PATH,
                mgr.decide(FailureReason.NO_PATH, 0).getAction());
        Assertions.assertEquals(RecoveryAction.RETRY,
                mgr.decide(FailureReason.NO_PATH, 1).getAction());
        Assertions.assertEquals(RecoveryAction.RETRY,
                mgr.decide(FailureReason.NO_PATH, 2).getAction());
        RecoveryDecision abort = mgr.decide(FailureReason.NO_PATH, 3);
        Assertions.assertEquals(RecoveryAction.ABORT, abort.getAction());
        Assertions.assertTrue(abort.isTerminal());
    }

    @Test
    void targetUnavailableAborts() {
        RecoveryDecision d = mgr.decide(FailureReason.TARGET_UNAVAILABLE, 0);
        Assertions.assertEquals(RecoveryAction.ABORT, d.getAction());
        Assertions.assertEquals(TaskResult.FAILURE, d.toTaskResult());
    }

    @Test
    void inventoryFullWaitsThenAborts() {
        Assertions.assertEquals(RecoveryAction.WAIT,
                mgr.decide(FailureReason.INVENTORY_FULL, 0).getAction());
        Assertions.assertEquals(RecoveryAction.WAIT,
                mgr.decide(FailureReason.INVENTORY_FULL, 1).getAction());
        Assertions.assertEquals(RecoveryAction.ABORT,
                mgr.decide(FailureReason.INVENTORY_FULL, 2).getAction());
    }

    @Test
    void dangerEscalates() {
        Assertions.assertEquals(RecoveryAction.ESCALATE,
                mgr.decide(FailureReason.DANGER, 0).getAction());
        Assertions.assertEquals(RecoveryAction.ESCALATE,
                mgr.decide(FailureReason.PLAYER_DEAD, 0).getAction());
    }

    @Test
    void applyIncrementsAndMapsResult() {
        TaskFailure seed = new TaskFailure(FailureReason.TIMEOUT, "stall", true, 0);
        RecoveryManager.Applied a = mgr.apply(seed);
        Assertions.assertEquals(RecoveryAction.ALTERNATE_PATH, a.getDecision().getAction());
        Assertions.assertEquals(TaskResult.RETRY, a.getResult());
        Assertions.assertEquals(1, a.getFailure().getRetryCount());
        Assertions.assertTrue(a.getFailure().isRecoverable());
        Assertions.assertTrue(a.getFailure().getMessage().contains("[recovery="));
    }

    @Test
    void applyExhaustedIsFailure() {
        TaskFailure seed = new TaskFailure(FailureReason.TIMEOUT, "stall", true, 3);
        RecoveryManager.Applied a = mgr.apply(seed);
        Assertions.assertEquals(RecoveryAction.ABORT, a.getDecision().getAction());
        Assertions.assertEquals(TaskResult.FAILURE, a.getResult());
        Assertions.assertFalse(a.getFailure().isRecoverable());
    }

    @Test
    void applyInventoryIsBlocked() {
        TaskFailure seed = new TaskFailure(FailureReason.INVENTORY_FULL, "full", true, 0);
        RecoveryManager.Applied a = mgr.apply(seed);
        Assertions.assertEquals(RecoveryAction.WAIT, a.getDecision().getAction());
        Assertions.assertEquals(TaskResult.BLOCKED, a.getResult());
    }

    @Test
    void decideFromTaskFailure() {
        TaskFailure f = new TaskFailure(FailureReason.NO_PATH, "x", true, 1);
        Assertions.assertEquals(RecoveryAction.RETRY, mgr.decide(f).getAction());
    }
}
