package adris.altoclef.swarm;

import java.util.Map;

/**
 * The worker's only link to "how the game does it". The live implementation hands the objective to
 * TenorClef's goal system; tests use a scripted one. It must report what actually happened:
 * a finished goal that did not produce the item is FAILED, never SUCCEEDED.
 */
public interface AssignmentExecutor {
    enum Phase { IDLE, RUNNING, SUCCEEDED, FAILED }

    record Status(Phase phase, int progress, Map<String, String> evidence, String reason) {
        public static Status idle() { return new Status(Phase.IDLE, 0, Map.of(), ""); }
        public static Status running(int progress) { return new Status(Phase.RUNNING, progress, Map.of(), ""); }
        public static Status succeeded(Map<String, String> evidence) { return new Status(Phase.SUCCEEDED, 100, evidence, ""); }
        public static Status failed(String reason, Map<String, String> evidence) { return new Status(Phase.FAILED, 0, evidence, reason); }
    }

    void start(Objective objective);

    Status poll();

    void cancel();

    /** Latest local facts for heartbeats; may return null for any field it does not know. */
    default double[] position() { return null; }

    default float health() { return 20f; }

    default boolean alive() { return true; }

    default String risk() { return ""; }
}
