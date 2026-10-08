package adris.altoclef.swarm;

import adris.altoclef.swarm.SwarmLedger.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Focused swarm behaviour tests over an in-memory transport. No Minecraft. */
class SwarmCoordinatorTest {

    /** Scripted agent body: decides by itself what "the game" does with the objective. */
    static final class FakeExecutor implements AssignmentExecutor {
        enum Script { SUCCEED, FAIL_NO_ITEM, STALL, FALSE_SUCCESS }

        Script script = Script.SUCCEED;
        boolean alive = true;
        Objective objective;
        int polls;
        boolean cancelled;
        boolean dangerOnce;

        @Override public void start(Objective o) { objective = o; polls = 0; cancelled = false; }

        @Override public Status poll() {
            if (objective == null || cancelled) return Status.idle();
            polls++;
            if (dangerOnce) { dangerOnce = false; objective = null; return Status.failed("DANGER", Map.of("have", "0")); }
            return switch (script) {
                case STALL -> Status.running(10);
                case SUCCEED -> polls < 2 ? Status.running(50)
                        : Status.succeeded(Map.of("have", String.valueOf(objective.count)));
                case FAIL_NO_ITEM -> polls < 2 ? Status.running(30)
                        : Status.failed("MISSING_RESOURCE", Map.of("have", "0"));
                // The goal "finished" but the inventory does not have the items.
                case FALSE_SUCCESS -> Status.succeeded(Map.of("have", "1"));
            };
        }

        @Override public void cancel() { cancelled = true; }
        @Override public boolean alive() { return alive; }
    }

    static final class Node {
        final SwarmWorker worker;
        final FakeExecutor exec = new FakeExecutor();
        Node(String id, Set<Capability> caps, SwarmCoordinator[] leader, Map<String, Node> nodes, long hb) {
            worker = new SwarmWorker(id, caps, exec, m -> leader[0].onMessage(id, SwarmMessage.decode(m.encode()), clock), hb);
        }
    }

    static long clock;
    SwarmCoordinator leader;
    final SwarmCoordinator[] ref = new SwarmCoordinator[1];
    final Map<String, Node> nodes = new HashMap<>();
    final SwarmSim sim = new SwarmSim();

    /** Drives everyone in lock step; dropped agents neither send nor receive. */
    final class SwarmSim {
        final Set<String> offline = new java.util.HashSet<>();
        void step(long ms) {
            clock += ms;
            for (Node n : nodes.values()) if (!offline.contains(n.worker.id())) n.worker.tick(clock);
            leader.tick(clock);
        }
        void run(int steps, long ms) { for (int i = 0; i < steps; i++) step(ms); }
    }

    @BeforeEach
    void setUp() {
        clock = 1_000;
        SwarmCoordinator.Config cfg = new SwarmCoordinator.Config();
        cfg.heartbeatTimeoutMs = 5_000;
        cfg.offerTimeoutMs = 3_000;
        cfg.situationalRetryDelayMs = 1_000;
        leader = new SwarmCoordinator((agent, msg) -> {
            Node n = nodes.get(agent);
            if (n != null && !sim.offline.contains(agent)) n.worker.onMessage(SwarmMessage.decode(msg.encode()), clock);
        }, cfg);
        ref[0] = leader;
    }

    Node join(String id, Capability... caps) {
        Set<Capability> set = caps.length == 0 ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(java.util.List.of(caps));
        Node n = new Node(id, set, ref, nodes, 1_000);
        nodes.put(id, n);
        n.worker.register(clock);
        return n;
    }

    Assignment submit(String item, int n, int prio) {
        return leader.submit(leader.create(Objective.acquire(item, n), prio, EnumSet.noneOf(Capability.class)), clock);
    }

    @Test
    void rightAgentGetsTheTask() {
        join("miner", Capability.CAN_MINE);
        join("crafter", Capability.CAN_CRAFT);
        Assignment a = submit("iron_ore", 3, 5); // needs CAN_MINE by default
        assertEquals("miner", a.agentId());
        assertEquals(AssignmentState.ACCEPTED, a.state());
        assertNull(leader.agent("crafter").assignmentId());
        assertEquals(AgentLifecycle.BUSY, leader.agent("miner").lifecycle());
    }

    @Test
    void verifiedSuccessCompletesTheTask() {
        join("miner", Capability.CAN_MINE);
        Assignment a = submit("oak_log", 4, 5);
        sim.run(5, 500);
        assertEquals(AssignmentState.SUCCEEDED, a.state());
        assertEquals(AgentLifecycle.READY, leader.agent("miner").lifecycle());
        assertTrue(leader.ledger().count(Type.TASK_STARTED) >= 1);
        assertEquals(1, leader.ledger().count(Type.TASK_SUCCEEDED));
        assertTrue(leader.ledger().why(a.id).contains("TASK_SUCCEEDED"));
    }

    @Test
    void failureIsNotCompletion() {
        Node n = join("miner", Capability.CAN_MINE);
        n.exec.script = FakeExecutor.Script.FAIL_NO_ITEM;
        Assignment a = submit("diamond", 2, 5);
        sim.run(6, 500);
        assertNotEquals(AssignmentState.SUCCEEDED, a.state());
        assertEquals(AssignmentState.FAILED, a.state(), "only agent was blamed and excluded, nobody else can do it");
        assertEquals(0, leader.ledger().count(Type.TASK_SUCCEEDED));
        assertTrue(a.failure().contains("MISSING_RESOURCE"));
        assertEquals(1, leader.ledger().count(Type.FAILURE_DIAGNOSED));
    }

    @Test
    void claimedSuccessWithoutEvidenceIsRejected() {
        Node n = join("miner", Capability.CAN_MINE);
        n.exec.script = FakeExecutor.Script.FALSE_SUCCESS;
        Assignment a = submit("coal", 5, 5); // agent reports have=1, needs 5
        sim.run(4, 500);
        assertNotEquals(AssignmentState.SUCCEEDED, a.state());
        assertEquals(1, leader.ledger().count(Type.TASK_UNVERIFIED));
        assertEquals(0, leader.ledger().count(Type.TASK_SUCCEEDED));
    }

    @Test
    void failedWorkIsReassignedAndVerified() {
        Node a1 = join("a", Capability.CAN_MINE);
        a1.exec.script = FakeExecutor.Script.FAIL_NO_ITEM;
        Node b = join("b", Capability.CAN_MINE, Capability.CAN_CRAFT); // more surplus, so A is preferred first
        Assignment t = submit("obsidian", 2, 5);
        assertEquals("a", t.agentId());
        sim.run(8, 500);
        assertEquals(AssignmentState.SUCCEEDED, t.state());
        assertEquals("b", t.agentId() == null ? "b" : t.agentId(), "B finished it");
        assertTrue(t.excluded().contains("a"));
        assertEquals(2, t.attempts());
        assertEquals(1, leader.ledger().count(Type.TASK_REASSIGNED));
        assertEquals(1, leader.ledger().count(Type.TASK_SUCCEEDED));
    }

    @Test
    void sameAgentRetriesAfterSituationalFailureOverSynchronousTransport() {
        Node n = join("solo", Capability.CAN_MINE);
        n.exec.dangerOnce = true; // fails with DANGER once: the agent is not blamed
        Assignment t = submit("oak_log", 2, 5);
        sim.run(2, 500); // first attempt fails with DANGER
        assertEquals(AssignmentState.UNASSIGNED, t.state(), "backs off instead of retrying into the same threat");
        assertEquals(1, t.attempts());
        sim.run(10, 500);
        assertEquals(AssignmentState.SUCCEEDED, t.state());
        assertEquals(2, t.attempts());
        assertFalse(t.excluded().contains("solo"));
        assertEquals(0, leader.ledger().count(Type.TASK_UNVERIFIED));
    }

    @Test
    void deadAgentWorkGoesToAnotherAgent() {
        Node a1 = join("a", Capability.CAN_MINE);
        a1.exec.script = FakeExecutor.Script.STALL;
        join("b", Capability.CAN_MINE, Capability.CAN_CRAFT);
        Assignment t = submit("oak_log", 2, 5);
        assertEquals("a", t.agentId());
        sim.run(2, 500);
        sim.offline.add("a"); // A vanishes: no goodbye, no more heartbeats
        sim.run(14, 500);
        assertEquals(AgentLifecycle.DISCONNECTED, leader.agent("a").lifecycle());
        assertEquals(AssignmentState.SUCCEEDED, t.state());
        assertEquals(1, leader.ledger().count(Type.AGENT_DISCONNECTED));
        assertFalse(t.excluded().contains("a"), "a lost agent is not blamed for the work");
    }

    @Test
    void reportedDeathFreesReservationsAndRequeues() {
        Node a1 = join("a", Capability.CAN_MINE);
        a1.exec.script = FakeExecutor.Script.STALL;
        Assignment t = leader.submit(
                leader.create(Objective.acquire("obsidian", 1), 5, EnumSet.noneOf(Capability.class)).reserve("diamond_pickaxe_17"), clock);
        assertEquals("a", leader.reservations().holder("diamond_pickaxe_17"));
        sim.run(2, 500);
        a1.exec.alive = false;
        sim.run(3, 500);
        assertEquals(AgentLifecycle.DEAD, leader.agent("a").lifecycle());
        assertNull(leader.reservations().holder("diamond_pickaxe_17"));
        assertEquals(AssignmentState.UNASSIGNED, t.state(), "waits for a capable agent");
    }

    @Test
    void capabilityMismatchSkipsUnqualifiedAgent() {
        join("a", Capability.CAN_CRAFT);
        join("b", Capability.CAN_FIGHT);
        Assignment t = submit("blaze_rod", 1, 5); // CAN_FIGHT
        assertEquals("b", t.agentId());
        sim.run(5, 500);
        assertEquals(AssignmentState.SUCCEEDED, t.state());
    }

    @Test
    void noCapableAgentLeavesTaskPendingUntilOneJoins() {
        join("a", Capability.CAN_CRAFT);
        Assignment t = submit("diamond", 1, 5);
        assertEquals(AssignmentState.UNASSIGNED, t.state());
        join("m", Capability.CAN_MINE);
        assertEquals("m", t.agentId());
    }

    @Test
    void unansweredOfferTimesOutAndMovesOn() {
        join("a", Capability.CAN_MINE);
        join("b", Capability.CAN_MINE, Capability.CAN_CRAFT);
        sim.offline.add("a"); // never hears the offer
        Assignment t = submit("coal", 1, 5);
        assertEquals("a", t.agentId());
        sim.run(12, 500);
        assertEquals(AssignmentState.SUCCEEDED, t.state());
        assertTrue(leader.ledger().why(t.id).contains("OFFER_TIMEOUT"));
    }

    @Test
    void sharedResourceIsNotDoubleBooked() {
        join("a", Capability.CAN_MINE);
        join("b", Capability.CAN_MINE);
        leader.submit(leader.create(Objective.acquire("obsidian", 1), 9, EnumSet.noneOf(Capability.class)).reserve("pick"), clock);
        Assignment second = leader.submit(leader.create(Objective.acquire("obsidian", 1), 1, EnumSet.noneOf(Capability.class)).reserve("pick"), clock);
        assertEquals(AssignmentState.UNASSIGNED, second.state());
        assertEquals(1, leader.ledger().count(Type.RESOURCE_CONTENDED));
        sim.run(10, 500);
        assertEquals(AssignmentState.SUCCEEDED, second.state(), "runs once the pick is released");
    }

    @Test
    void priorityDecidesWhoIsServedFirst() {
        Assignment low = submit("coal", 1, 1);
        Assignment high = submit("coal", 1, 9);
        join("a", Capability.CAN_MINE);
        assertEquals("a", high.agentId());
        assertEquals(AssignmentState.UNASSIGNED, low.state());
    }

    @Test
    void staleResultFromNonOwnerIsIgnored() {
        join("a", Capability.CAN_MINE);
        join("b", Capability.CAN_MINE);
        Assignment t = submit("coal", 1, 5);
        String owner = t.agentId();
        String other = owner.equals("a") ? "b" : "a";
        leader.onMessage(other, SwarmMessage.of("result", "a", t.id, "status", "SUCCEEDED", "have", "1"), clock);
        assertNotEquals(AssignmentState.SUCCEEDED, t.state());
    }

    @Test
    void beliefsKeepProvenanceAndDecay() {
        join("a", Capability.CAN_SCOUT);
        leader.onMessage("a", SwarmMessage.of("obs", "key", "village", "value", "100,64,200", "conf", "0.8"), clock);
        BeliefBoard.Belief b = leader.beliefs().get("village", clock);
        assertNotNull(b);
        assertEquals("a", b.source());
        assertNull(leader.beliefs().get("village", clock + 10 * 60_000), "ten minutes later it is no longer believed");
        assertTrue(leader.beliefs().explain("village", clock).contains("seen by a"));
    }

    @Test
    void messageRoundTripEscapes() {
        SwarmMessage m = SwarmMessage.of("result", "reason", "a b=c%d");
        SwarmMessage back = SwarmMessage.decode(m.encode());
        assertEquals("a b=c%d", back.get("reason"));
        assertNull(SwarmMessage.decode("hello there"));
    }
}
