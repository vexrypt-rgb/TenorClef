package adris.altoclef.agent;

import java.util.Map;

/**
 * Side-effect boundary for {@link AgentRequestHandler}.
 * Live game uses {@link AltoClefAgentRuntime}; unit tests inject fakes.
 */
public interface AgentRuntime {

    /**
     * Start acquiring {@code count} of catalogue {@code item} via GoalManager
     * (Phase 7) or TaskCatalogue.
     *
     * @return typically {@link AgentStatus#ACCEPTED} or {@link AgentStatus#RUNNING}
     */
    AgentResponse acquire(String requestId, String item, int count);

    /**
     * Same as acquire for Phase 9 — GoalManager AcquireItemGoal path.
     */
    default AgentResponse goal(String requestId, String item, int count) {
        return acquire(requestId, item, count);
    }

    /** Structured world / goal / task snapshot (partial OK). */
    Map<String, String> snapshot();

    /**
     * Cancel current user/goal task if safe.
     *
     * @return cancelled / blocked / failure
     */
    AgentResponse cancel(String requestId);

    /** Whether a user or goal task is currently active. */
    default boolean isBusy() {
        return false;
    }
}
