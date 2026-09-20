package adris.altoclef.benchmark;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.RecoveryAction;
import adris.altoclef.tasksystem.RecoveryManager;
import adris.altoclef.tasksystem.TaskFailure;
import adris.altoclef.tasksystem.TaskResult;
import adris.altoclef.threat.ThreatLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

public class LiveBenchmarkSessionTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        LiveBenchmarkSession.clearForTests();
        BenchmarkFiles.setDirOverrideForTests(tmp);
    }

    @AfterEach
    void tearDown() {
        LiveBenchmarkSession.clearForTests();
        BenchmarkFiles.setDirOverrideForTests(null);
    }

    @Test
    void inactiveIsNoOp() {
        Assertions.assertFalse(LiveBenchmarkSession.isActive());
        LiveBenchmarkSession.noteTaskResult(TaskResult.SUCCESS);
        LiveBenchmarkSession.noteFailure(FailureReason.NO_PATH);
        Assertions.assertNull(LiveBenchmarkSession.current());
    }

    @Test
    void aggregatesAndExports() throws Exception {
        LiveBenchmarkSession s = LiveBenchmarkSession.start("fake-run");
        LiveBenchmarkSession.noteTaskResult(TaskResult.SUCCESS);
        LiveBenchmarkSession.noteFailure(FailureReason.NO_PATH);
        RecoveryManager.DEFAULT.apply(new TaskFailure(FailureReason.TIMEOUT, "stall", true, 0));
        LiveBenchmarkSession.noteReplan();
        LiveBenchmarkSession.noteThreat(ThreatLevel.MEDIUM);
        LiveBenchmarkSession.noteThreatPause();
        LiveBenchmarkSession.noteThreatFail();
        Assertions.assertEquals(ThreatLevel.CRITICAL, s.getPeakThreat());
        Assertions.assertEquals(1, s.getThreatPauses());
        Assertions.assertEquals(1, s.getThreatFails());
        LiveBenchmarkSession stopped = LiveBenchmarkSession.stop(true, "unit-test");
        Assertions.assertNotNull(stopped.getLastExportPath());
        String json = Files.readString(stopped.getLastExportPath());
        Assertions.assertTrue(json.contains("\"type\":\"live\""));
        Assertions.assertTrue(json.contains("fake-run"));
        Assertions.assertTrue(json.contains("threatPauses"));
    }

    @Test
    void recoveryHookWhenActive() {
        LiveBenchmarkSession.start("rec");
        RecoveryManager.DEFAULT.apply(new TaskFailure(FailureReason.NO_PATH, "blocked", true, 0));
        LiveBenchmarkSession s = LiveBenchmarkSession.current();
        Assertions.assertTrue(
                s.getCounters().getRecoveryCount(RecoveryAction.ALTERNATE_PATH) >= 1
                        || s.getCounters().getRecoveryCount(RecoveryAction.RETRY) >= 1);
        LiveBenchmarkSession.stop(false, "done");
    }
}
