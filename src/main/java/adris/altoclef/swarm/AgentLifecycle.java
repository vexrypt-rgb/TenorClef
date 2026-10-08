package adris.altoclef.swarm;

/** Where an agent is in its life as seen by the swarm. DEAD and DISCONNECTED agents hold no work. */
public enum AgentLifecycle {
    CONNECTED, READY, BUSY, DEAD, DISCONNECTED;

    public boolean isGone() {
        return this == DEAD || this == DISCONNECTED;
    }
}
