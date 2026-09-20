package adris.altoclef.benchmark;

/**
 * Outcome of one {@link Scenario} run (Phase 10).
 */
public final class BenchmarkResult {

    private final String name;
    private final boolean success;
    private final long durationMs;
    private final Integer deaths;
    private final Integer replans;
    private final Integer pathFails;
    private final String notes;
    private final BenchmarkCounters counters;

    public BenchmarkResult(String name, boolean success, long durationMs,
                           Integer deaths, Integer replans, Integer pathFails,
                           String notes, BenchmarkCounters counters) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        this.name = name;
        this.success = success;
        this.durationMs = Math.max(0L, durationMs);
        this.deaths = deaths;
        this.replans = replans;
        this.pathFails = pathFails;
        this.notes = notes;
        this.counters = counters != null ? counters.copy() : new BenchmarkCounters();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getDurationMs() {
        return durationMs;
    }

    /** Nullable metric — null means not recorded. */
    public Integer getDeaths() {
        return deaths;
    }

    public Integer getReplans() {
        return replans;
    }

    public Integer getPathFails() {
        return pathFails;
    }

    public String getNotes() {
        return notes;
    }

    public BenchmarkCounters getCounters() {
        return counters;
    }

    public static final class Builder {
        private final String name;
        private boolean success;
        private long durationMs;
        private Integer deaths;
        private Integer replans;
        private Integer pathFails;
        private String notes;
        private BenchmarkCounters counters = new BenchmarkCounters();

        private Builder(String name) {
            this.name = name;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder deaths(Integer deaths) {
            this.deaths = deaths;
            return this;
        }

        public Builder replans(Integer replans) {
            this.replans = replans;
            return this;
        }

        public Builder pathFails(Integer pathFails) {
            this.pathFails = pathFails;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder counters(BenchmarkCounters counters) {
            this.counters = counters != null ? counters : new BenchmarkCounters();
            return this;
        }

        /** Fill deaths/replans/pathFails from counters when not set explicitly. */
        public Builder fillMetricsFromCounters() {
            if (deaths == null) {
                deaths = counters.getDeaths();
            }
            if (replans == null) {
                replans = counters.getReplans();
            }
            if (pathFails == null) {
                pathFails = counters.getPathFails();
            }
            return this;
        }

        public BenchmarkResult build() {
            return new BenchmarkResult(name, success, durationMs, deaths, replans, pathFails, notes, counters);
        }
    }
}
