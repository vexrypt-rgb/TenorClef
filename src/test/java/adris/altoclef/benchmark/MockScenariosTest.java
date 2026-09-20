package adris.altoclef.benchmark;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.RecoveryAction;
import adris.altoclef.tasksystem.TaskResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Phase 10: individual mock scenario expectations.
 */
public class MockScenariosTest {

    @Test
    void acquireSuccessIsGreen() {
        BenchmarkResult r = new BenchmarkHarness().run(MockScenarios.acquireSuccess());
        Assertions.assertTrue(r.isSuccess());
        Assertions.assertEquals(1, r.getCounters().getTaskResultCount(TaskResult.SUCCESS));
        Assertions.assertEquals(Integer.valueOf(0), r.getDeaths());
    }

    @Test
    void pathFailRecoverRecordsPathAndReplan() {
        BenchmarkResult r = new BenchmarkHarness().run(MockScenarios.pathFailThenRecover());
        Assertions.assertTrue(r.isSuccess());
        Assertions.assertEquals(1, r.getCounters().getFailureCount(FailureReason.NO_PATH));
        Assertions.assertEquals(1, r.getCounters().getRecoveryCount(RecoveryAction.ALTERNATE_PATH));
        Assertions.assertEquals(Integer.valueOf(1), r.getPathFails());
        Assertions.assertEquals(Integer.valueOf(1), r.getReplans());
    }

    @Test
    void threatCriticalFails() {
        BenchmarkResult r = new BenchmarkHarness().run(MockScenarios.threatCriticalAbort());
        Assertions.assertFalse(r.isSuccess());
        Assertions.assertEquals(1, r.getCounters().getThreatCritical());
        Assertions.assertEquals(1, r.getCounters().getRecoveryCount(RecoveryAction.ESCALATE));
    }

    @Test
    void deathAbortSetsDeaths() {
        BenchmarkResult r = new BenchmarkHarness().run(MockScenarios.deathAbort());
        Assertions.assertFalse(r.isSuccess());
        Assertions.assertEquals(Integer.valueOf(1), r.getDeaths());
    }
}
