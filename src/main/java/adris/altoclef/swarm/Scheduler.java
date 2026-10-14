package adris.altoclef.swarm;

import java.util.Collection;
import java.util.Locale;

/**
 * Deterministic agent choice. An agent is eligible when it is READY, idle, has every required
 * capability and has not already failed this assignment. Among eligible agents the score prefers:
 * fewer surplus capabilities (keep flexible agents free), a nearer position, no reported risk, more health.
 * Ties break on agent id so identical inputs always give the same answer.
 */
public final class Scheduler {
    private Scheduler() {}

    public record Choice(AgentInfo agent, double score, String why) {}

    public static boolean eligible(AgentInfo a, Assignment as) {
        return a.lifecycle() == AgentLifecycle.READY
                && a.assignmentId() == null
                && a.capabilities().containsAll(as.required())
                && !as.excluded().contains(a.id);
    }

    /** Could this agent ever do the work (ignores momentary busy-ness)? Used to detect "nobody left". */
    public static boolean capable(AgentInfo a, Assignment as) {
        return !a.lifecycle().isGone()
                && a.capabilities().containsAll(as.required())
                && !as.excluded().contains(a.id);
    }

    public static double score(AgentInfo a, Assignment as) {
        double s = 100;
        s -= 5 * (a.capabilities().size() - as.required().size());
        double[] t = as.target();
        if (t != null) {
            double d = a.distanceTo(t[0], t[1], t[2]);
            if (!Double.isNaN(d)) s -= Math.min(50, d / 10.0);
        }
        if (!a.risk().isEmpty()) s -= 20;
        s += a.health() / 20.0 * 5;
        return s;
    }

    public static Choice choose(Collection<AgentInfo> agents, Assignment as) {
        AgentInfo best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (AgentInfo a : agents) {
            if (!eligible(a, as)) continue;
            double s = score(a, as);
            if (s > bestScore || (s == bestScore && best != null && a.id.compareTo(best.id) < 0)) {
                best = a;
                bestScore = s;
            }
        }
        if (best == null) return null;
        return new Choice(best, bestScore, String.format(Locale.ROOT, "score=%.1f caps=%s", bestScore, best.capabilities()));
    }
}
