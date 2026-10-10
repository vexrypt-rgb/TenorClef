package adris.altoclef.swarm;

import adris.altoclef.swarm.SwarmLedger.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The swarm's decision layer. It never touches the game: it observes agents through messages,
 * decides WHAT each should do, checks the outcome against evidence, and reassigns on failure.
 * HOW a task is carried out (pathing, combat, recovery) stays with the agent and Ostinato.
 *
 * Time is passed in, so behaviour is deterministic and testable. All entry points are synchronized
 * because messages arrive off the game thread.
 */
public final class SwarmCoordinator {
    public interface Outbox {
        void send(String agentId, SwarmMessage message);
    }

    public static final class Config {
        public long heartbeatTimeoutMs = 15_000;
        public long offerTimeoutMs = 10_000;
        public long runTimeoutMs = 10 * 60_000;
        /** After accepting, the agent must keep claiming the work in heartbeats. */
        public long abandonGraceMs = 5_000;
        public int maxAttempts = 3;
        /** Wait before retrying after a situational failure (danger, death): retrying into the same threat just burns attempts. */
        public long situationalRetryDelayMs = 10_000;
    }

    private final Outbox outbox;
    private final Config cfg;
    private final Map<String, AgentInfo> agents = new LinkedHashMap<>();
    private final Map<String, Assignment> assignments = new LinkedHashMap<>();
    private final ReservationTable reservations = new ReservationTable();
    private final BeliefBoard beliefs = new BeliefBoard();
    private final SwarmLedger ledger = new SwarmLedger();
    private int nextId = 1;

    public SwarmCoordinator(Outbox outbox) { this(outbox, new Config()); }

    public SwarmCoordinator(Outbox outbox, Config cfg) {
        this.outbox = outbox;
        this.cfg = cfg;
    }

    // ---- views ----

    public synchronized AgentInfo agent(String id) { return agents.get(id); }
    public synchronized Assignment assignment(String id) { return assignments.get(id); }
    public synchronized List<AgentInfo> agents() { return new ArrayList<>(agents.values()); }
    public synchronized List<Assignment> assignments() { return new ArrayList<>(assignments.values()); }
    public ReservationTable reservations() { return reservations; }
    public BeliefBoard beliefs() { return beliefs; }
    public SwarmLedger ledger() { return ledger; }

    // ---- work in ----

    public synchronized Assignment create(Objective objective, int priority, Set<Capability> required) {
        return new Assignment("a" + nextId++, objective, priority, required);
    }

    public synchronized Assignment submit(Assignment a, long now) {
        assignments.put(a.id, a);
        a.move(AssignmentState.UNASSIGNED, now);
        ledger.add(now, Type.TASK_SUBMITTED, null, a.id, a.objective.describe() + " needs " + a.required() + " prio " + a.priority);
        schedule(now);
        return a;
    }

    public synchronized boolean cancel(String id, long now) {
        Assignment a = assignments.get(id);
        if (a == null || a.state().isTerminal()) return false;
        String agent = a.agentId();
        if (agent != null) {
            outbox.send(agent, SwarmMessage.of("cancel", "a", a.id));
            freeAgent(agent, a);
        }
        a.release(now);
        a.move(AssignmentState.CANCELLED, now);
        ledger.add(now, Type.TASK_CANCELLED, agent, a.id, "cancelled by leader");
        return true;
    }

    // ---- messages in ----

    public synchronized void onMessage(String from, SwarmMessage m, long now) {
        switch (m.op) {
            case "reg" -> register(from, m, now);
            case "bye" -> loseAgent(from, AgentLifecycle.DISCONNECTED, "said goodbye", now);
            case "hb" -> heartbeat(from, m, now);
            case "accept" -> accepted(from, m, now);
            case "reject" -> rejected(from, m, now);
            case "prog" -> progress(from, m, now);
            case "result" -> result(from, m, now);
            case "obs" -> observed(from, m, now);
            default -> { /* unknown ops from newer agents are ignored */ }
        }
        schedule(now);
    }

    private void register(String id, SwarmMessage m, long now) {
        Set<Capability> caps = Capability.decode(m.get("caps", ""));
        AgentInfo old = agents.get(id);
        if (old != null && old.assignmentId() != null) {
            // A fresh registration means the agent restarted; whatever it was doing is gone.
            failHeld(old, FailureDiagnosis.agentLost(AgentLifecycle.DISCONNECTED), now);
        }
        AgentInfo a = new AgentInfo(id, caps, now);
        agents.put(id, a);
        ledger.add(now, Type.AGENT_REGISTERED, id, null, "caps " + caps);
        a.setLifecycle(AgentLifecycle.READY);
        ledger.add(now, Type.AGENT_READY, id, null, "");
    }

    private void heartbeat(String id, SwarmMessage m, long now) {
        AgentInfo a = agents.get(id);
        if (a == null) return; // must register first
        a.touch(now);
        if (m.get("x") != null) a.setPosition(m.getDouble("x", 0), m.getDouble("y", 0), m.getDouble("z", 0));
        a.setHealth((float) m.getDouble("hp", a.health()));
        a.setRisk(m.get("risk", ""));
        boolean alive = !"0".equals(m.get("alive", "1"));
        if (!alive) {
            loseAgent(id, AgentLifecycle.DEAD, "reported dead", now);
            return;
        }
        if (a.lifecycle().isGone()) {
            a.setLifecycle(AgentLifecycle.READY);
            ledger.add(now, Type.AGENT_READY, id, null, "back after " + "silence");
        }
        String claimed = m.get("a");
        Assignment held = a.assignmentId() == null ? null : assignments.get(a.assignmentId());
        if (held != null && (held.state() == AssignmentState.ACCEPTED || held.state() == AssignmentState.RUNNING)
                && !held.id.equals(claimed) && now - held.stateSinceMs() > cfg.abandonGraceMs) {
            failHeld(a, FailureDiagnosis.abandoned(), now);
        }
    }

    private void accepted(String id, SwarmMessage m, long now) {
        Assignment a = ownedBy(id, m.get("a"), now, "accept");
        if (a == null || a.state() != AssignmentState.ASSIGNED) return;
        a.move(AssignmentState.ACCEPTED, now);
        ledger.add(now, Type.TASK_ACCEPTED, id, a.id, "");
    }

    private void rejected(String id, SwarmMessage m, long now) {
        Assignment a = ownedBy(id, m.get("a"), now, "reject");
        if (a == null) return;
        ledger.add(now, Type.TASK_REJECTED, id, a.id, m.get("why", ""));
        fail(a, FailureDiagnosis.rejected(m.get("why", "no reason")), now);
    }

    private void progress(String id, SwarmMessage m, long now) {
        Assignment a = ownedBy(id, m.get("a"), now, "prog");
        if (a == null) return;
        if (a.state() == AssignmentState.ASSIGNED) {
            // Progress implies acceptance; the accept may simply have been lost.
            a.move(AssignmentState.ACCEPTED, now);
            ledger.add(now, Type.TASK_ACCEPTED, id, a.id, "implied by progress");
        }
        if (a.state() == AssignmentState.ACCEPTED) {
            a.move(AssignmentState.RUNNING, now);
            ledger.add(now, Type.TASK_STARTED, id, a.id, "");
        }
        int p = m.getInt("pct", a.progress());
        if (p != a.progress()) {
            a.setProgress(p);
            ledger.add(now, Type.TASK_PROGRESS, id, a.id, p + "%");
        }
    }

    private void result(String id, SwarmMessage m, long now) {
        Assignment a = ownedBy(id, m.get("a"), now, "result");
        if (a == null) return;
        String status = m.get("status", "FAILED");
        if (status.equals("SUCCEEDED")) {
            if (a.objective.verify(m.fields())) {
                freeAgent(id, a);
                a.setProgress(100);
                a.move(AssignmentState.SUCCEEDED, now);
                ledger.add(now, Type.TASK_SUCCEEDED, id, a.id, "verified, have=" + m.get("have"));
                reservations.releaseAll(id).forEach(r -> ledger.add(now, Type.RESOURCE_RELEASED, id, a.id, r));
            } else {
                ledger.add(now, Type.TASK_UNVERIFIED, id, a.id, "have=" + m.get("have") + " need=" + a.objective.count);
                fail(a, FailureDiagnosis.unverified("have=" + m.get("have")), now);
            }
        } else if (status.equals("CANCELLED")) {
            fail(a, FailureDiagnosis.reported("CANCELLED_BY_AGENT"), now);
        } else {
            fail(a, FailureDiagnosis.reported(m.get("reason")), now);
        }
    }

    private void observed(String from, SwarmMessage m, long now) {
        String key = m.get("key");
        String value = m.get("value");
        if (key == null || value == null) return;
        if (beliefs.observe(key, value, m.getDouble("conf", 0.7), now, from)) {
            ledger.add(now, Type.WORLD_BELIEF_UPDATED, from, null, key + "=" + value);
        }
    }

    /** The message must come from the agent that currently holds the assignment; stale ones are dropped. */
    private Assignment ownedBy(String agent, String assignmentId, long now, String op) {
        Assignment a = assignmentId == null ? null : assignments.get(assignmentId);
        if (a == null || a.state().isTerminal() || !agent.equals(a.agentId())) return null;
        agents.computeIfPresent(agent, (k, v) -> { v.touch(now); return v; });
        return a;
    }

    // ---- time ----

    public synchronized void tick(long now) {
        for (AgentInfo a : new ArrayList<>(agents.values())) {
            if (!a.lifecycle().isGone() && now - a.lastSeenMs() > cfg.heartbeatTimeoutMs) {
                loseAgent(a.id, AgentLifecycle.DISCONNECTED, "heartbeat silent " + (now - a.lastSeenMs()) + "ms", now);
            }
        }
        for (Assignment a : new ArrayList<>(assignments.values())) {
            long held = now - a.stateSinceMs();
            if (a.state() == AssignmentState.ASSIGNED && held > cfg.offerTimeoutMs) {
                // The agent may have taken the offer and only the accept was lost; do not leave it working unowned.
                if (a.agentId() != null) outbox.send(a.agentId(), SwarmMessage.of("cancel", "a", a.id));
                fail(a, FailureDiagnosis.offerTimeout(), now);
            } else if ((a.state() == AssignmentState.ACCEPTED || a.state() == AssignmentState.RUNNING)
                    && now - startOfHold(a) > cfg.runTimeoutMs) {
                if (a.agentId() != null) outbox.send(a.agentId(), SwarmMessage.of("cancel", "a", a.id));
                fail(a, FailureDiagnosis.runTimeout(), now);
            }
        }
        schedule(now);
    }

    private final Map<String, Long> heldSince = new LinkedHashMap<>();

    private long startOfHold(Assignment a) { return heldSince.getOrDefault(a.id, a.stateSinceMs()); }

    // ---- failure and recovery ----

    private void loseAgent(String id, AgentLifecycle how, String why, long now) {
        AgentInfo a = agents.get(id);
        if (a == null || a.lifecycle().isGone()) return;
        a.setLifecycle(how);
        ledger.add(now, how == AgentLifecycle.DEAD ? Type.AGENT_DIED : Type.AGENT_DISCONNECTED, id, a.assignmentId(), why);
        if (a.assignmentId() != null) failHeld(a, FailureDiagnosis.agentLost(how), now);
        for (String r : reservations.releaseAll(id)) ledger.add(now, Type.RESOURCE_RELEASED, id, null, r + " (agent gone)");
    }

    private void failHeld(AgentInfo a, FailureDiagnosis d, long now) {
        Assignment as = assignments.get(a.assignmentId());
        if (as != null) fail(as, d, now);
    }

    /** Records the diagnosis, frees the agent and resources, and either requeues or ends the assignment. */
    private void fail(Assignment a, FailureDiagnosis d, long now) {
        String agent = a.agentId();
        if (agent != null) freeAgent(agent, a);
        ledger.add(now, Type.TASK_FAILED, agent, a.id, d.kind() + ": " + d.detail());
        ledger.add(now, Type.FAILURE_DIAGNOSED, agent, a.id,
                (d.blameAgent() ? "agent excluded from this work" : "agent not blamed")
                        + (d.restart() ? ", restart from scratch" : ", resume"));
        a.setFailure(d.kind() + ": " + d.detail());
        if (d.blameAgent() && agent != null) a.excluded().add(agent);
        a.release(now);
        if (d.kind() == FailureDiagnosis.Kind.REPORTED && !d.blameAgent()) a.setNotBefore(now + cfg.situationalRetryDelayMs);

        if (a.attempts() >= cfg.maxAttempts) {
            a.move(AssignmentState.FAILED, now);
            ledger.add(now, Type.TASK_FAILED, null, a.id, "gave up after " + a.attempts() + " attempts");
            return;
        }
        boolean anyone = false;
        for (AgentInfo x : agents.values()) if (Scheduler.capable(x, a)) { anyone = true; break; }
        if (!anyone && d.blameAgent()) {
            a.move(AssignmentState.FAILED, now);
            ledger.add(now, Type.TASK_FAILED, null, a.id, "no remaining capable agent");
            return;
        }
        ledger.add(now, Type.TASK_REASSIGNED, agent, a.id, "requeued, " + (anyone ? "candidates remain" : "waiting for an agent"));
    }

    private void freeAgent(String agentId, Assignment a) {
        AgentInfo ag = agents.get(agentId);
        if (ag != null) {
            ag.setAssignment(null);
            if (!ag.lifecycle().isGone()) ag.setLifecycle(AgentLifecycle.READY);
        }
        for (String r : a.reservations()) reservations.release(r, agentId);
    }

    // ---- assignment ----

    private void schedule(long now) {
        List<Assignment> pending = new ArrayList<>();
        for (Assignment a : assignments.values()) if (a.state() == AssignmentState.UNASSIGNED) pending.add(a);
        pending.sort(Comparator.comparingInt((Assignment a) -> -a.priority));
        for (Assignment a : pending) {
            if (now < a.notBeforeMs()) continue;
            Scheduler.Choice c = Scheduler.choose(agents.values(), a);
            if (c == null) continue;
            AgentInfo ag = c.agent();
            if (!reserveFor(a, ag, now)) continue;
            ag.setAssignment(a.id);
            ag.setLifecycle(AgentLifecycle.BUSY);
            a.assign(ag.id, now);
            heldSince.put(a.id, now);
            ledger.add(now, Type.TASK_ASSIGNED, ag.id, a.id, c.why());
            outbox.send(ag.id, SwarmMessage.of("offer", "a", a.id, "item", a.objective.item, "n", String.valueOf(a.objective.count)));
        }
    }

    private boolean reserveFor(Assignment a, AgentInfo ag, long now) {
        List<String> taken = new ArrayList<>();
        for (String r : a.reservations()) {
            if (!reservations.reserve(r, ag.id, a.objective.describe())) {
                ledger.add(now, Type.RESOURCE_CONTENDED, ag.id, a.id, r + " held by " + reservations.holder(r));
                for (String t : taken) reservations.release(t, ag.id);
                return false;
            }
            taken.add(r);
            ledger.add(now, Type.RESOURCE_RESERVED, ag.id, a.id, r);
        }
        return true;
    }

    /** One line per agent and assignment, for @swarm status. */
    public synchronized List<String> status() {
        List<String> out = new ArrayList<>();
        for (AgentInfo a : agents.values()) out.add("agent " + a);
        for (Assignment a : assignments.values()) {
            out.add("task " + a + (a.failure().isEmpty() ? "" : " last failure: " + a.failure())
                    + " attempts=" + a.attempts() + " progress=" + a.progress() + "%");
        }
        for (ReservationTable.Claim c : reservations.all()) out.add("reserved " + c.resource() + " by " + c.owner() + " for " + c.purpose());
        return out;
    }
}
