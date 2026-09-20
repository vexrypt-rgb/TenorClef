package adris.altoclef.benchmark;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.RecoveryAction;
import adris.altoclef.tasksystem.TaskResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Phase 10: result aggregation + JSON export.
 */
public class BenchmarkAggregationTest {

    @Test
    void mockSuiteAggregatesCounts() {
        BenchmarkHarness harness = new BenchmarkHarness();
        List<BenchmarkResult> ran = harness.runAll(MockScenarios.allDefaults());
        Assertions.assertEquals(4, ran.size());
        Assertions.assertEquals(2, harness.successCount());
        Assertions.assertEquals(2, harness.failureCount());

        BenchmarkResult agg = harness.aggregateSummary("suite");
        Assertions.assertEquals("suite", agg.getName());
        Assertions.assertFalse(agg.isSuccess()); // mixed
        Assertions.assertEquals(Integer.valueOf(1), agg.getDeaths());
        Assertions.assertTrue(agg.getPathFails() >= 1);
        Assertions.assertTrue(agg.getReplans() >= 1);
        Assertions.assertTrue(agg.getNotes().contains("scenarios=4"));
    }

    @Test
    void countersRecordTaskRecoveryThreat() {
        BenchmarkCounters c = new BenchmarkCounters();
        c.recordTaskResult(TaskResult.SUCCESS);
        c.recordFailure(FailureReason.NO_PATH);
        c.recordRecovery(RecoveryAction.ALTERNATE_PATH);
        c.recordThreatHigh();
        Assertions.assertEquals(1, c.getTaskResultCount(TaskResult.SUCCESS));
        Assertions.assertEquals(1, c.getPathFails());
        Assertions.assertEquals(1, c.getReplans());
        Assertions.assertEquals(1, c.getThreatHigh());
        Assertions.assertEquals(1, c.getFailureCount(FailureReason.NO_PATH));
    }

    @Test
    void jsonSummaryContainsScenarioNames() {
        BenchmarkHarness harness = new BenchmarkHarness();
        harness.runAll(MockScenarios.allDefaults());
        String json = harness.toJsonSummary();
        Assertions.assertTrue(json.contains("\"scenarioCount\":4"));
        Assertions.assertTrue(json.contains("mock_acquire_success"));
        Assertions.assertTrue(json.contains("mock_threat_critical"));
        Assertions.assertTrue(json.contains("\"aggregate\""));
        Assertions.assertTrue(json.contains("\"taskResults\""));
    }

    @Test
    void exportJsonWritesFile(@TempDir Path dir) throws Exception {
        BenchmarkHarness harness = new BenchmarkHarness();
        harness.run(MockScenarios.acquireSuccess());
        Path out = dir.resolve("bench-summary.json");
        harness.exportJson(out);
        Assertions.assertTrue(Files.isRegularFile(out));
        String body = Files.readString(out);
        Assertions.assertTrue(body.contains("mock_acquire_success"));
        Assertions.assertTrue(body.contains("\"success\":true"));
    }

    @Test
    void exceptionInScenarioIsFailureResult() {
        BenchmarkHarness harness = new BenchmarkHarness();
        Scenario boom = Scenario.of("boom", "throws", ctx -> {
            throw new IllegalStateException("kaboom");
        });
        BenchmarkResult r = harness.run(boom);
        Assertions.assertFalse(r.isSuccess());
        Assertions.assertTrue(r.getNotes().contains("kaboom"));
        Assertions.assertEquals(1, harness.failureCount());
    }
}
