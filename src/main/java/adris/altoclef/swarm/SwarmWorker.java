package adris.altoclef.swarm;

import java.util.Map;
import java.util.Set;

/**
 * The agent's side of the swarm protocol. It accepts offers it can honour, hands them to the
 * {@link AssignmentExecutor}, and reports what the executor says actually happened.
 * It owns no game logic and never claims success on its own.
 */
public final class SwarmWorker {
    public interface Uplink {
        void send(SwarmMessage message);
    }

    private final String id;
    private final Set<Capability> caps;
    private final AssignmentExecutor executor;
    private final Uplink uplink;
    private final long heartbeatMs;

    private String current;
    private Objective currentObjective;
    private int lastProgress = -1;
    private long lastHeartbeat = Long.MIN_VALUE / 2;
    private boolean registered;

    public SwarmWorker(String id, Set<Capability> caps, AssignmentExecutor executor, Uplink uplink, long heartbeatMs) {
        this.id = id;
        this.caps = caps;
        this.executor = executor;
        this.uplink = uplink;
        this.heartbeatMs = heartbeatMs;
    }

    public String id() { return id; }
    public String currentAssignment() { return current; }

    public synchronized void register(long now) {
        uplink.send(SwarmMessage.of("reg", "caps", Capability.encode(caps)));
        registered = true;
        lastHeartbeat = Long.MIN_VALUE / 2;
    }

    public synchronized void leave() {
        if (current != null) executor.cancel();
        current = null;
        uplink.send(SwarmMessage.of("bye"));
    }

    public synchronized void onMessage(SwarmMessage m, long now) {
        switch (m.op) {
            case "offer" -> offered(m);
            case "cancel" -> {
                if (current != null && current.equals(m.get("a"))) {
                    executor.cancel();
                    current = null;
                    currentObjective = null;
                }
            }
            default -> { }
        }
    }

    private void offered(SwarmMessage m) {
        String a = m.get("a");
        if (a == null) return;
        if (current != null) {
            // A repeated offer for the work already held means the leader lost track of it (missed accept, or it
            // wrote this agent off and came back): say so again instead of letting the offer time out.
            uplink.send(current.equals(a) ? SwarmMessage.of("accept", "a", a) : SwarmMessage.of("reject", "a", a, "why", "busy"));
            return;
        }
        String item = m.get("item");
        if (item == null) {
            uplink.send(SwarmMessage.of("reject", "a", a, "why", "bad offer"));
            return;
        }
        if (!executor.alive()) {
            uplink.send(SwarmMessage.of("reject", "a", a, "why", "dead"));
            return;
        }
        current = a;
        currentObjective = Objective.acquire(item, m.getInt("n", 1));
        lastProgress = -1;
        uplink.send(SwarmMessage.of("accept", "a", a));
        executor.start(currentObjective);
    }

    public synchronized void tick(long now) {
        if (!registered) return;
        if (now - lastHeartbeat >= heartbeatMs) {
            lastHeartbeat = now;
            SwarmMessage hb = SwarmMessage.of("hb", "hp", String.valueOf(executor.health()));
            double[] p = executor.position();
            if (p != null) hb.put("x", String.valueOf(p[0])).put("y", String.valueOf(p[1])).put("z", String.valueOf(p[2]));
            if (current != null) hb.put("a", current);
            if (!executor.alive()) hb.put("alive", "0");
            String risk = executor.risk();
            if (risk != null && !risk.isEmpty()) hb.put("risk", risk);
            uplink.send(hb);
        }
        if (current == null) return;

        AssignmentExecutor.Status s = executor.poll();
        switch (s.phase()) {
            case RUNNING -> {
                if (s.progress() != lastProgress) {
                    lastProgress = s.progress();
                    uplink.send(SwarmMessage.of("prog", "a", current, "pct", String.valueOf(s.progress())));
                }
            }
            case SUCCEEDED -> finish("SUCCEEDED", s.evidence(), "");
            case FAILED -> finish("FAILED", s.evidence(), s.reason());
            case IDLE -> finish("FAILED", Map.of(), "EXECUTOR_IDLE"); // told to start, but nothing is running
        }
    }

    private void finish(String status, Map<String, String> evidence, String reason) {
        SwarmMessage r = SwarmMessage.of("result", "a", current, "status", status);
        if (!reason.isEmpty()) r.put("reason", reason);
        evidence.forEach(r::put);
        // Clear first: a synchronous transport can deliver the next offer from inside send().
        current = null;
        currentObjective = null;
        uplink.send(r);
    }

    /** Share something this agent saw. The swarm treats it as a belief, not a fact. */
    public synchronized void observe(String key, String value, double confidence) {
        uplink.send(SwarmMessage.of("obs", "key", key, "value", value, "conf", String.valueOf(confidence)));
    }
}
