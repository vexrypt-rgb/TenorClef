package adris.altoclef.benchmark;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.RecoveryAction;
import adris.altoclef.tasksystem.TaskResult;
import adris.altoclef.threat.ThreatLevel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Optional singleton live run recorder (post-phase-10).
 * <p>
 * When inactive ({@link #current()} is null), all static {@code note*} helpers
 * are no-ops — gameplay must not depend on an active session.
 * Hooked cheaply from RecoveryManager / ThreatMonitor / PlanExecutor / Task.
 */
public final class LiveBenchmarkSession {

    private static volatile LiveBenchmarkSession active;

    private final String name;
    private final long startNanos;
    private final BenchmarkCounters counters = new BenchmarkCounters();
    private ThreatLevel peakThreat = ThreatLevel.NONE;
    private int threatPauses;
    private int threatFails;
    private boolean running = true;
    private Boolean success;
    private String notes = "";
    private Path lastExportPath;

    private LiveBenchmarkSession(String name) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        this.startNanos = System.nanoTime();
    }

    /** Active session, or null when benchmarking is off. */
    public static LiveBenchmarkSession current() {
        return active;
    }

    public static boolean isActive() {
        LiveBenchmarkSession s = active;
        return s != null && s.running;
    }

    /**
     * Begin a named run. Stops any previous session as cancelled/failed.
     */
    public static synchronized LiveBenchmarkSession start(String name) {
        if (active != null && active.running) {
            active.stopInternal(false, "superseded by " + name);
        }
        LiveBenchmarkSession s = new LiveBenchmarkSession(name);
        active = s;
        return s;
    }

    /**
     * Stop the active session (if any), export JSON, clear singleton.
     *
     * @return the finished session, or null if none was active
     */
    public static synchronized LiveBenchmarkSession stop(boolean success, String notes) {
        LiveBenchmarkSession s = active;
        if (s == null) {
            return null;
        }
        s.stopInternal(success, notes);
        active = null;
        return s;
    }

    /** Stop without declaring success/fail (e.g. {@code @bench stop}). */
    public static LiveBenchmarkSession stop() {
        LiveBenchmarkSession s = active;
        boolean ok = s != null && s.inferSuccess();
        return stop(ok, s != null ? s.notes : "");
    }

    private void stopInternal(boolean success, String notes) {
        if (!running) {
            return;
        }
        this.running = false;
        this.success = success;
        if (notes != null && !notes.isBlank()) {
            this.notes = notes;
        }
        try {
            this.lastExportPath = exportJson();
        } catch (IOException ignored) {
            // export best-effort; status still available in-memory
        }
    }

    private boolean inferSuccess() {
        // Prefer explicit SUCCESS tallies over FAILURE when both present
        int ok = counters.getTaskResultCount(TaskResult.SUCCESS);
        int fail = counters.getTaskResultCount(TaskResult.FAILURE);
        if (ok > 0 && fail == 0) {
            return true;
        }
        if (threatFails > 0) {
            return false;
        }
        return fail == 0 && ok > 0;
    }

    public String getName() {
        return name;
    }

    public boolean isRunning() {
        return running;
    }

    public Boolean getSuccess() {
        return success;
    }

    public long getDurationMs() {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    public BenchmarkCounters getCounters() {
        return counters;
    }

    public ThreatLevel getPeakThreat() {
        return peakThreat;
    }

    public int getThreatPauses() {
        return threatPauses;
    }

    public int getThreatFails() {
        return threatFails;
    }

    public Path getLastExportPath() {
        return lastExportPath;
    }

    public String getNotes() {
        return notes;
    }

    // —— instance recorders ——

    public void recordTaskResult(TaskResult result) {
        if (!running || result == null) {
            return;
        }
        counters.recordTaskResult(result);
    }

    public void recordFailure(FailureReason reason) {
        if (!running || reason == null) {
            return;
        }
        counters.recordFailure(reason);
    }

    public void recordRecovery(RecoveryAction action) {
        if (!running || action == null) {
            return;
        }
        counters.recordRecovery(action);
    }

    public void recordReplan() {
        if (!running) {
            return;
        }
        counters.recordReplan();
    }

    public void recordThreat(ThreatLevel level) {
        if (!running || level == null) {
            return;
        }
        if (level.ordinal() > peakThreat.ordinal()) {
            peakThreat = level;
        }
        counters.observeThreatPeak(level.name());
    }

    public void recordThreatPause() {
        if (!running) {
            return;
        }
        threatPauses++;
        counters.recordThreatHigh();
        counters.recordThreatPause();
        recordThreat(ThreatLevel.HIGH);
    }

    public void recordThreatFail() {
        if (!running) {
            return;
        }
        threatFails++;
        counters.recordThreatCritical();
        counters.recordThreatFail();
        recordThreat(ThreatLevel.CRITICAL);
    }

    public BenchmarkResult toResult() {
        boolean ok = success != null ? success : inferSuccess();
        StringBuilder n = new StringBuilder();
        if (notes != null && !notes.isBlank()) {
            n.append(notes);
        }
        if (n.length() > 0) {
            n.append("; ");
        }
        n.append("peakThreat=").append(peakThreat.name())
                .append(" threatPauses=").append(threatPauses)
                .append(" threatFails=").append(threatFails)
                .append(running ? " running" : " stopped");
        return BenchmarkResult.builder(name)
                .success(ok)
                .durationMs(getDurationMs())
                .counters(counters.copy())
                .fillMetricsFromCounters()
                .notes(n.toString())
                .build();
    }

    public String toJson() {
        return BenchmarkJson.toLiveJson(this);
    }

    public Path exportJson() throws IOException {
        Path path = BenchmarkFiles.nextRunPath(name);
        return BenchmarkJson.writeLive(path, this);
    }

    public String statusLine() {
        return "bench name=" + name
                + " running=" + running
                + " durationMs=" + getDurationMs()
                + " success=" + success
                + " peakThreat=" + peakThreat
                + " pauses=" + threatPauses
                + " threatFails=" + threatFails
                + " replans=" + counters.getReplans()
                + " pathFails=" + counters.getPathFails()
                + " deaths=" + counters.getDeaths()
                + (lastExportPath != null ? " out=" + lastExportPath : "");
    }

    // —— static no-op-safe hooks (hypothesis: singleton consult is enough) ——

    public static void noteTaskResult(TaskResult result) {
        LiveBenchmarkSession s = active;
        if (s != null) {
            s.recordTaskResult(result);
        }
    }

    public static void noteFailure(FailureReason reason) {
        LiveBenchmarkSession s = active;
        if (s != null) {
            s.recordFailure(reason);
        }
    }

    public static void noteRecovery(RecoveryAction action) {
        LiveBenchmarkSession s = active;
        if (s != null) {
            s.recordRecovery(action);
        }
    }

    public static void noteReplan() {
        LiveBenchmarkSession s = active;
        if (s != null) {
            s.recordReplan();
        }
    }

    public static void noteThreat(ThreatLevel level) {
        LiveBenchmarkSession s = active;
        if (s != null) {
            s.recordThreat(level);
        }
    }

    public static void noteThreatPause() {
        LiveBenchmarkSession s = active;
        if (s != null) {
            s.recordThreatPause();
        }
    }

    public static void noteThreatFail() {
        LiveBenchmarkSession s = active;
        if (s != null) {
            s.recordThreatFail();
        }
    }

    /** Clear singleton without exporting (tests). */
    public static synchronized void clearForTests() {
        active = null;
    }
}
