package adris.altoclef.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Runs {@link Scenario}s, aggregates {@link BenchmarkResult}s, exports JSON (Phase 10).
 * Works fully offline — no Minecraft client required for mock scenarios.
 */
public final class BenchmarkHarness {

    private final List<BenchmarkResult> results = new ArrayList<>();

    public BenchmarkResult run(Scenario scenario) {
        Objects.requireNonNull(scenario, "scenario");
        ScenarioContext ctx = new ScenarioContext();
        long t0 = System.nanoTime();
        BenchmarkResult raw;
        try {
            raw = scenario.run(ctx);
        } catch (RuntimeException ex) {
            long durationMs = (System.nanoTime() - t0) / 1_000_000L;
            BenchmarkResult fail = BenchmarkResult.builder(scenario.getName())
                    .success(false)
                    .durationMs(durationMs)
                    .counters(ctx.getCounters())
                    .fillMetricsFromCounters()
                    .notes("exception: " + ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .build();
            results.add(fail);
            return fail;
        }
        long durationMs = (System.nanoTime() - t0) / 1_000_000L;
        BenchmarkResult stamped = BenchmarkResult.builder(raw.getName())
                .success(raw.isSuccess())
                .durationMs(raw.getDurationMs() > 0 ? raw.getDurationMs() : durationMs)
                .deaths(raw.getDeaths() != null ? raw.getDeaths() : ctx.getCounters().getDeaths())
                .replans(raw.getReplans() != null ? raw.getReplans() : ctx.getCounters().getReplans())
                .pathFails(raw.getPathFails() != null ? raw.getPathFails() : ctx.getCounters().getPathFails())
                .notes(raw.getNotes())
                .counters(mergeCounters(raw.getCounters(), ctx.getCounters()))
                .build();
        results.add(stamped);
        return stamped;
    }

    public List<BenchmarkResult> runAll(Iterable<? extends Scenario> scenarios) {
        List<BenchmarkResult> out = new ArrayList<>();
        for (Scenario s : scenarios) {
            out.add(run(s));
        }
        return out;
    }

    public List<BenchmarkResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public void clear() {
        results.clear();
    }

    public int successCount() {
        int n = 0;
        for (BenchmarkResult r : results) {
            if (r.isSuccess()) {
                n++;
            }
        }
        return n;
    }

    public int failureCount() {
        return results.size() - successCount();
    }

    public long totalDurationMs() {
        long sum = 0;
        for (BenchmarkResult r : results) {
            sum += r.getDurationMs();
        }
        return sum;
    }

    /** Aggregate totals across all results (deaths / replans / pathFails). */
    public BenchmarkResult aggregateSummary(String name) {
        int deaths = 0;
        int replans = 0;
        int pathFails = 0;
        boolean allOk = !results.isEmpty();
        for (BenchmarkResult r : results) {
            allOk = allOk && r.isSuccess();
            if (r.getDeaths() != null) {
                deaths += r.getDeaths();
            }
            if (r.getReplans() != null) {
                replans += r.getReplans();
            }
            if (r.getPathFails() != null) {
                pathFails += r.getPathFails();
            }
        }
        BenchmarkCounters totals = new BenchmarkCounters();
        for (BenchmarkResult r : results) {
            BenchmarkCounters c = r.getCounters();
            for (var e : c.getTaskResults().entrySet()) {
                for (int i = 0; i < e.getValue(); i++) {
                    totals.recordTaskResult(e.getKey());
                }
            }
            for (var e : c.getFailureReasons().entrySet()) {
                for (int i = 0; i < e.getValue(); i++) {
                    totals.recordFailure(e.getKey());
                }
            }
            for (int i = 0; i < c.getThreatHigh(); i++) {
                totals.recordThreatHigh();
            }
            for (int i = 0; i < c.getThreatCritical(); i++) {
                totals.recordThreatCritical();
            }
            // recovery without double-counting replans via recordRecovery side-effect:
            for (var e : c.getRecoveryActions().entrySet()) {
                for (int i = 0; i < e.getValue(); i++) {
                    totals.recordRecovery(e.getKey());
                }
            }
        }
        String notes = "scenarios=" + results.size()
                + " success=" + successCount()
                + " failure=" + failureCount();
        return BenchmarkResult.builder(name != null ? name : "aggregate")
                .success(allOk)
                .durationMs(totalDurationMs())
                .deaths(deaths)
                .replans(replans)
                .pathFails(pathFails)
                .notes(notes)
                .counters(totals)
                .build();
    }

    public String toJsonSummary() {
        return BenchmarkJson.toSummaryJson(this);
    }

    public Path exportJson(Path path) throws java.io.IOException {
        return BenchmarkJson.writeSummary(path, this);
    }

    private static BenchmarkCounters mergeCounters(BenchmarkCounters a, BenchmarkCounters b) {
        // Prefer scenario-returned counters if they recorded anything; else context.
        if (a == null) {
            return b != null ? b.copy() : new BenchmarkCounters();
        }
        boolean aEmpty = a.getTaskResults().isEmpty()
                && a.getRecoveryActions().isEmpty()
                && a.getFailureReasons().isEmpty()
                && a.getDeaths() == 0 && a.getReplans() == 0 && a.getPathFails() == 0
                && a.getThreatHigh() == 0 && a.getThreatCritical() == 0;
        if (aEmpty && b != null) {
            return b.copy();
        }
        return a.copy();
    }
}
