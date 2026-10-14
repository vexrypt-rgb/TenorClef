package adris.altoclef.swarm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Append-only record of why the swarm did what it did. Every state change in the coordinator goes
 * through here, so "why does the swarm believe this?" is answered by reading it back.
 */
public final class SwarmLedger {
    public enum Type {
        AGENT_REGISTERED, AGENT_READY, AGENT_DISCONNECTED, AGENT_DIED,
        TASK_SUBMITTED, TASK_ASSIGNED, TASK_ACCEPTED, TASK_REJECTED, TASK_STARTED, TASK_PROGRESS,
        TASK_SUCCEEDED, TASK_FAILED, TASK_CANCELLED, TASK_UNVERIFIED, TASK_REASSIGNED, FAILURE_DIAGNOSED,
        RESOURCE_RESERVED, RESOURCE_RELEASED, RESOURCE_CONTENDED,
        WORLD_BELIEF_UPDATED
    }

    public record Event(long seq, long timeMs, Type type, String agent, String assignment, String detail) {
        @Override
        public String toString() {
            return "#" + seq + " t=" + timeMs + " " + type
                    + (agent != null ? " agent=" + agent : "")
                    + (assignment != null ? " a=" + assignment : "")
                    + (detail == null || detail.isEmpty() ? "" : " - " + detail);
        }
    }

    private static final int CAP = 1000;
    private final Deque<Event> events = new ArrayDeque<>();
    private long seq;

    public synchronized Event add(long now, Type type, String agent, String assignment, String detail) {
        Event e = new Event(++seq, now, type, agent, assignment, detail);
        events.addLast(e);
        while (events.size() > CAP) events.removeFirst();
        return e;
    }

    public synchronized List<Event> all() {
        return new ArrayList<>(events);
    }

    public synchronized List<Event> forAssignment(String assignmentId) {
        List<Event> out = new ArrayList<>();
        for (Event e : events) if (assignmentId.equals(e.assignment())) out.add(e);
        return out;
    }

    public synchronized int count(Type type) {
        int n = 0;
        for (Event e : events) if (e.type() == type) n++;
        return n;
    }

    /** Human-readable history of one assignment, oldest first. */
    public String why(String assignmentId) {
        StringBuilder b = new StringBuilder();
        for (Event e : forAssignment(assignmentId)) b.append(e).append('\n');
        return b.toString();
    }
}
