package adris.altoclef.benchmark;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.RecoveryAction;
import adris.altoclef.tasksystem.RecoveryManager;
import adris.altoclef.tasksystem.TaskResult;

import java.util.Arrays;
import java.util.List;

/**
 * Offline/mock scenarios that exercise TaskResult / Recovery / Threat counters
 * without a Minecraft client (Phase 10).
 */
public final class MockScenarios {

    private MockScenarios() {}

    /** Happy-path acquire: SUCCESS with no recovery. */
    public static Scenario acquireSuccess() {
        return Scenario.of("mock_acquire_success",
                "Simulated catalogue acquire finishes SUCCESS",
                ctx -> {
                    BenchmarkCounters c = ctx.getCounters();
                    c.recordTaskResult(TaskResult.RUNNING);
                    c.recordTaskResult(TaskResult.SUCCESS);
                    return BenchmarkResult.builder("mock_acquire_success")
                            .success(true)
                            .counters(c)
                            .fillMetricsFromCounters()
                            .notes("mock SUCCESS")
                            .build();
                });
    }

    /** Path fail → RecoveryManager ALTERNATE_PATH → eventual SUCCESS. */
    public static Scenario pathFailThenRecover() {
        return Scenario.of("mock_path_fail_recover",
                "NO_PATH triggers ALTERNATE_PATH then SUCCESS",
                ctx -> {
                    BenchmarkCounters c = ctx.getCounters();
                    RecoveryManager mgr = new RecoveryManager(3, 2);
                    c.recordTaskResult(TaskResult.RUNNING);
                    c.recordFailure(FailureReason.NO_PATH);
                    RecoveryAction action = mgr.decide(FailureReason.NO_PATH, 0).getAction();
                    c.recordRecovery(action);
                    c.recordTaskResult(TaskResult.RETRY);
                    c.recordTaskResult(TaskResult.SUCCESS);
                    return BenchmarkResult.builder("mock_path_fail_recover")
                            .success(true)
                            .counters(c)
                            .fillMetricsFromCounters()
                            .notes("recovered via " + action)
                            .build();
                });
    }

    /** Threat CRITICAL → DANGER escalate → scenario fails. */
    public static Scenario threatCriticalAbort() {
        return Scenario.of("mock_threat_critical",
                "CRITICAL threat records DANGER + ESCALATE; scenario fails",
                ctx -> {
                    BenchmarkCounters c = ctx.getCounters();
                    RecoveryManager mgr = new RecoveryManager(3, 2);
                    c.recordThreatCritical();
                    c.recordFailure(FailureReason.DANGER);
                    RecoveryAction action = mgr.decide(FailureReason.DANGER, 0).getAction();
                    c.recordRecovery(action);
                    c.recordTaskResult(TaskResult.FAILURE);
                    return BenchmarkResult.builder("mock_threat_critical")
                            .success(false)
                            .counters(c)
                            .fillMetricsFromCounters()
                            .notes("aborted: " + action)
                            .build();
                });
    }

    /** Death signal recorded then ABORT. */
    public static Scenario deathAbort() {
        return Scenario.of("mock_death_abort",
                "PLAYER_DEAD → ESCALATE; deaths metric = 1",
                ctx -> {
                    BenchmarkCounters c = ctx.getCounters();
                    RecoveryManager mgr = new RecoveryManager(3, 2);
                    c.recordFailure(FailureReason.PLAYER_DEAD);
                    c.recordRecovery(mgr.decide(FailureReason.PLAYER_DEAD, 0).getAction());
                    c.recordTaskResult(TaskResult.FAILURE);
                    return BenchmarkResult.builder("mock_death_abort")
                            .success(false)
                            .counters(c)
                            .fillMetricsFromCounters()
                            .notes("player dead")
                            .build();
                });
    }

    public static List<Scenario> allDefaults() {
        return Arrays.asList(
                acquireSuccess(),
                pathFailThenRecover(),
                threatCriticalAbort(),
                deathAbort()
        );
    }
}
