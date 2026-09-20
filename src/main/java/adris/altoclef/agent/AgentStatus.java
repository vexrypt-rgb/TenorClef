package adris.altoclef.agent;

/**
 * Lifecycle status for a structured {@link AgentRequest} (Phase 9).
 * Wire values are lowercase JSON strings.
 */
public enum AgentStatus {
    ACCEPTED("accepted"),
    RUNNING("running"),
    SUCCESS("success"),
    FAILURE("failure"),
    BLOCKED("blocked"),
    CANCELLED("cancelled");

    private final String wire;

    AgentStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILURE || this == CANCELLED;
    }

    public static AgentStatus fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toLowerCase();
        for (AgentStatus s : values()) {
            if (s.wire.equals(t) || s.name().equalsIgnoreCase(t)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return wire;
    }
}
