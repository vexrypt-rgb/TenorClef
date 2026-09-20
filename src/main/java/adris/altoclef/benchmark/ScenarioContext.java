package adris.altoclef.benchmark;

/**
 * Per-run context handed to {@link Scenario#run}.
 */
public final class ScenarioContext {

    private final BenchmarkCounters counters = new BenchmarkCounters();
    private final long startedAtMs;

    public ScenarioContext() {
        this(System.currentTimeMillis());
    }

    public ScenarioContext(long startedAtMs) {
        this.startedAtMs = startedAtMs;
    }

    public BenchmarkCounters getCounters() {
        return counters;
    }

    public long getStartedAtMs() {
        return startedAtMs;
    }

    public long elapsedMs() {
        return Math.max(0L, System.currentTimeMillis() - startedAtMs);
    }
}
