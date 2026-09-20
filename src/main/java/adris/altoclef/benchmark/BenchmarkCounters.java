package adris.altoclef.benchmark;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.RecoveryAction;
import adris.altoclef.tasksystem.TaskResult;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Mutable tallies for TaskResult / Recovery / Threat / path outcomes
 * recorded during a scenario run (Phase 10). No Minecraft dependency.
 */
public final class BenchmarkCounters {

    private final EnumMap<TaskResult, Integer> taskResults = new EnumMap<>(TaskResult.class);
    private final EnumMap<RecoveryAction, Integer> recoveryActions = new EnumMap<>(RecoveryAction.class);
    private final EnumMap<FailureReason, Integer> failureReasons = new EnumMap<>(FailureReason.class);
    private int deaths;
    private int replans;
    private int pathFails;
    private int threatHigh;
    private int threatCritical;
    private int threatPauses;
    private int threatFails;
    private String threatPeak = "NONE";

    public void recordTaskResult(TaskResult result) {
        if (result == null) {
            return;
        }
        taskResults.merge(result, 1, Integer::sum);
    }

    public void recordRecovery(RecoveryAction action) {
        if (action == null) {
            return;
        }
        recoveryActions.merge(action, 1, Integer::sum);
        if (action == RecoveryAction.ALTERNATE_PATH || action == RecoveryAction.RETRY) {
            // naive replan / recovery attempt signal
            replans++;
        }
    }

    public void recordFailure(FailureReason reason) {
        if (reason == null) {
            return;
        }
        failureReasons.merge(reason, 1, Integer::sum);
        if (reason == FailureReason.NO_PATH || reason == FailureReason.BACKEND_FAILURE) {
            pathFails++;
        }
        if (reason == FailureReason.PLAYER_DEAD) {
            deaths++;
        }
    }

    public void recordDeath() {
        deaths++;
    }

    public void recordReplan() {
        replans++;
    }

    public void recordPathFail() {
        pathFails++;
    }

    public void recordThreatHigh() {
        threatHigh++;
    }

    public void recordThreatCritical() {
        threatCritical++;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getReplans() {
        return replans;
    }

    public int getPathFails() {
        return pathFails;
    }

    public int getThreatHigh() {
        return threatHigh;
    }

    public int getThreatCritical() {
        return threatCritical;
    }

    public int getTaskResultCount(TaskResult result) {
        return taskResults.getOrDefault(result, 0);
    }

    public int getRecoveryCount(RecoveryAction action) {
        return recoveryActions.getOrDefault(action, 0);
    }

    public int getFailureCount(FailureReason reason) {
        return failureReasons.getOrDefault(reason, 0);
    }

    public Map<TaskResult, Integer> getTaskResults() {
        return Collections.unmodifiableMap(new EnumMap<>(taskResults));
    }

    public Map<RecoveryAction, Integer> getRecoveryActions() {
        return Collections.unmodifiableMap(new EnumMap<>(recoveryActions));
    }

    public Map<FailureReason, Integer> getFailureReasons() {
        return Collections.unmodifiableMap(new EnumMap<>(failureReasons));
    }


    public void recordThreatPause() {
        threatPauses++;
    }

    public void recordThreatFail() {
        threatFails++;
    }

    /** Update peak threat name if {@code levelName} is higher ordinal-wise than current. */
    public void observeThreatPeak(String levelName) {
        if (levelName == null || levelName.isBlank()) {
            return;
        }
        int neu = threatOrdinal(levelName);
        int cur = threatOrdinal(threatPeak);
        if (neu >= cur) {
            threatPeak = levelName;
        }
    }

    public int getThreatPauses() {
        return threatPauses;
    }

    public int getThreatFails() {
        return threatFails;
    }

    public String getThreatPeak() {
        return threatPeak;
    }

    private static int threatOrdinal(String name) {
        // Mirror ThreatLevel order without a hard package cycle in tests.
        return switch (name) {
            case "NONE" -> 0;
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CRITICAL" -> 4;
            default -> -1;
        };
    }

    /** Snapshot into a new counters object (shallow copy of tallies). */
    public BenchmarkCounters copy() {
        BenchmarkCounters c = new BenchmarkCounters();
        c.taskResults.putAll(taskResults);
        c.recoveryActions.putAll(recoveryActions);
        c.failureReasons.putAll(failureReasons);
        c.deaths = deaths;
        c.replans = replans;
        c.pathFails = pathFails;
        c.threatHigh = threatHigh;
        c.threatCritical = threatCritical;
        c.threatPauses = threatPauses;
        c.threatFails = threatFails;
        c.threatPeak = threatPeak;
        return c;
    }
}
