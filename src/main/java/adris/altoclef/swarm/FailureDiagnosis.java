package adris.altoclef.swarm;

/**
 * Why an assignment did not complete, and what that means for the next attempt.
 * Physical recovery is Ostinato's and local replanning is the agent's; this only decides
 * whether the swarm should try the same agent again and whether the work restarts from scratch.
 */
public record FailureDiagnosis(Kind kind, String detail, boolean blameAgent, boolean restart) {
    public enum Kind {
        /** Agent died or dropped off. Not its fault as a worker; its inventory is gone/unreachable, so restart. */
        AGENT_LOST,
        /** Agent never answered the offer. */
        OFFER_TIMEOUT,
        /** Agent refused. */
        REJECTED,
        /** Agent stopped reporting that it holds the work. */
        ABANDONED,
        /** Took longer than the swarm allows. */
        RUN_TIMEOUT,
        /** Agent said SUCCEEDED but the evidence does not meet the objective. */
        UNVERIFIED_SUCCESS,
        /** Agent reported FAILED with a reason. */
        REPORTED
    }

    public static FailureDiagnosis agentLost(AgentLifecycle how) {
        return new FailureDiagnosis(Kind.AGENT_LOST, "agent " + how, false, true);
    }

    public static FailureDiagnosis offerTimeout() {
        return new FailureDiagnosis(Kind.OFFER_TIMEOUT, "offer not answered", true, true);
    }

    public static FailureDiagnosis rejected(String why) {
        return new FailureDiagnosis(Kind.REJECTED, "rejected: " + why, true, true);
    }

    public static FailureDiagnosis abandoned() {
        return new FailureDiagnosis(Kind.ABANDONED, "agent no longer reports the work", true, true);
    }

    public static FailureDiagnosis runTimeout() {
        return new FailureDiagnosis(Kind.RUN_TIMEOUT, "exceeded run time limit", true, true);
    }

    public static FailureDiagnosis unverified(String evidence) {
        return new FailureDiagnosis(Kind.UNVERIFIED_SUCCESS, "claimed success, evidence: " + evidence, true, true);
    }

    /** The agent's own reason code. Dying or danger says nothing about the agent's ability, anything else does. */
    public static FailureDiagnosis reported(String reason) {
        String r = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
        boolean situational = r.equals("DIED") || r.equals("DANGER") || r.equals("CANCELLED_BY_AGENT");
        return new FailureDiagnosis(Kind.REPORTED, r, !situational, true);
    }
}
