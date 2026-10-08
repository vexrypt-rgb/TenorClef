package adris.altoclef.swarm;

import java.util.EnumSet;
import java.util.Set;

/** The swarm's belief about one agent. Updated only from registration, heartbeats and results. */
public final class AgentInfo {
    public final String id;
    private final Set<Capability> capabilities = EnumSet.noneOf(Capability.class);
    private AgentLifecycle lifecycle = AgentLifecycle.CONNECTED;
    private String assignmentId;
    private boolean hasPosition;
    private double x, y, z;
    private float health = 20f;
    private String risk = "";
    private long lastSeenMs;

    AgentInfo(String id, Set<Capability> caps, long now) {
        this.id = id;
        this.capabilities.addAll(caps);
        this.lastSeenMs = now;
    }

    public Set<Capability> capabilities() { return capabilities; }
    public AgentLifecycle lifecycle() { return lifecycle; }
    public String assignmentId() { return assignmentId; }
    public boolean hasPosition() { return hasPosition; }
    public float health() { return health; }
    public String risk() { return risk; }
    public long lastSeenMs() { return lastSeenMs; }

    void setLifecycle(AgentLifecycle l) { lifecycle = l; }
    void setAssignment(String id) { assignmentId = id; }
    void touch(long now) { lastSeenMs = now; }
    void setPosition(double x, double y, double z) { this.x = x; this.y = y; this.z = z; hasPosition = true; }
    void setHealth(float h) { health = h; }
    void setRisk(String r) { risk = r == null ? "" : r; }

    /** NaN when the agent has not reported a position. */
    public double distanceTo(double tx, double ty, double tz) {
        if (!hasPosition) return Double.NaN;
        double dx = x - tx, dy = y - ty, dz = z - tz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public String toString() {
        return id + "[" + lifecycle + (assignmentId != null ? " on " + assignmentId : "") + " " + capabilities + "]";
    }
}
