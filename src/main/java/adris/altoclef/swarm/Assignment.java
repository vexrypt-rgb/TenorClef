package adris.altoclef.swarm;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A unit of work the swarm hands to one agent. */
public final class Assignment {
    public final String id;
    public final Objective objective;
    public final int priority;
    private final Set<Capability> required = EnumSet.noneOf(Capability.class);
    private final List<String> reservations = new ArrayList<>();
    private final Set<String> excluded = new LinkedHashSet<>();
    private double[] target;
    private AssignmentState state = AssignmentState.UNASSIGNED;
    private String agentId;
    private int attempts;
    private int progress;
    private String failure = "";
    private long stateSinceMs;
    private long notBeforeMs;

    public Assignment(String id, Objective objective, int priority, Set<Capability> required) {
        this.id = id;
        this.objective = objective;
        this.priority = priority;
        this.required.addAll(required.isEmpty() ? objective.defaultCapabilities() : required);
    }

    public Set<Capability> required() { return required; }
    public List<String> reservations() { return reservations; }
    public Set<String> excluded() { return excluded; }
    public double[] target() { return target; }
    public AssignmentState state() { return state; }
    public String agentId() { return agentId; }
    public int attempts() { return attempts; }
    public int progress() { return progress; }
    public String failure() { return failure; }
    public long stateSinceMs() { return stateSinceMs; }

    /** Optional work site: nearer agents score better. */
    public Assignment target(double x, double y, double z) { this.target = new double[]{x, y, z}; return this; }
    /** A strategic resource this work needs exclusively (equipment, a station); held while the work is held. */
    public Assignment reserve(String resource) { reservations.add(resource); return this; }

    public long notBeforeMs() { return notBeforeMs; }
    void setNotBefore(long t) { notBeforeMs = t; }

    void move(AssignmentState s, long now) { state = s; stateSinceMs = now; }

    void assign(String agent, long now) {
        agentId = agent;
        attempts++;
        move(AssignmentState.ASSIGNED, now);
    }

    void release(long now) {
        agentId = null;
        move(AssignmentState.UNASSIGNED, now);
    }

    void setProgress(int p) { progress = p; }
    void setFailure(String f) { failure = f == null ? "" : f; }

    @Override
    public String toString() {
        return id + " " + objective.describe() + " " + state + (agentId != null ? " @" + agentId : "");
    }
}
