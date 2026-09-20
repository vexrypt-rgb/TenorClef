package adris.altoclef.benchmark;

/**
 * Named offline/mock (or future in-game) scenario for the Phase 10 harness.
 * Implementations must not require a live Minecraft client when written for offline use.
 */
public interface Scenario {

    String getName();

    default String getDescription() {
        return "";
    }

    /**
     * Execute against the shared harness context (counters, clock).
     * Return a completed {@link BenchmarkResult} (harness may still stamp duration).
     */
    BenchmarkResult run(ScenarioContext ctx);

    /** Factory for a simple named scenario. */
    static Scenario of(String name, String description, ScenarioBody body) {
        return new Scenario() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return description != null ? description : "";
            }

            @Override
            public BenchmarkResult run(ScenarioContext ctx) {
                return body.run(ctx);
            }
        };
    }

    @FunctionalInterface
    interface ScenarioBody {
        BenchmarkResult run(ScenarioContext ctx);
    }
}
