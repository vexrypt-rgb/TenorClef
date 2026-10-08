package adris.altoclef.swarm;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.speedrun.testrun2.fleet.FleetProtocol;

import java.util.Set;

/**
 * Live singleton: owns this client's leader and/or worker role and ferries swarm messages over the
 * existing fleet whisper channel ({@code ~ s1 <op> ...}). Only players in the Fleet list are trusted
 * (FleetProtocol enforces that before anything reaches here).
 */
public final class SwarmRuntime {
    private static final Set<String> TO_LEADER = Set.of("reg", "bye", "hb", "accept", "reject", "prog", "result", "obs");

    private static SwarmCoordinator leader;
    private static SwarmWorker worker;
    private static String leaderName;

    private SwarmRuntime() {}

    public static SwarmCoordinator leader() { return leader; }
    public static SwarmWorker worker() { return worker; }

    public static synchronized SwarmCoordinator lead(AltoClef mod) {
        leader = new SwarmCoordinator((agent, m) -> FleetProtocol.whisper(mod, agent, "~ " + m.encode()));
        return leader;
    }

    public static synchronized SwarmWorker join(AltoClef mod, String leaderUser, Set<Capability> caps) {
        if (worker != null) worker.leave();
        leaderName = leaderUser;
        String me = mod.getPlayer().getName().getString();
        worker = new SwarmWorker(me, caps, new AltoClefExecutor(mod),
                m -> FleetProtocol.whisper(mod, leaderUser, "~ " + m.encode()), 5_000);
        worker.register(System.currentTimeMillis());
        return worker;
    }

    private static int loggedEvents;

    /**
     * Single-client loopback for live verification: a leader and one worker in this process, wired
     * directly, with the worker running the real AltoClefExecutor. Every ledger event goes to the log.
     */
    public static synchronized SwarmCoordinator local(AltoClef mod) {
        final SwarmWorker[] w = new SwarmWorker[1];
        leader = new SwarmCoordinator((agent, m) -> {
            if (w[0] != null) w[0].onMessage(SwarmMessage.decode(m.encode()), System.currentTimeMillis());
        });
        SwarmCoordinator l = leader;
        w[0] = new SwarmWorker("local", java.util.EnumSet.allOf(Capability.class), new AltoClefExecutor(mod),
                m -> l.onMessage("local", SwarmMessage.decode(m.encode()), System.currentTimeMillis()), 2_000);
        worker = w[0];
        leaderName = "local";
        loggedEvents = 0;
        worker.register(System.currentTimeMillis());
        return leader;
    }

    public static synchronized void leave() {
        if (worker != null) worker.leave();
        worker = null;
        leaderName = null;
    }

    /** Called from FleetProtocol for {@code s1 ...} bodies. */
    public static synchronized void onWire(String from, String body) {
        SwarmMessage m = SwarmMessage.decode(body);
        if (m == null) return;
        long now = System.currentTimeMillis();
        if (TO_LEADER.contains(m.op)) {
            if (leader != null) leader.onMessage(from, m, now);
            else Debug.logMessage("SWARM " + m.op + " from " + from + " ignored: not leading");
        } else if (worker != null && from.equalsIgnoreCase(leaderName)) {
            worker.onMessage(m, now);
        } else {
            Debug.logMessage("SWARM " + m.op + " from " + from + " ignored: not my leader");
        }
    }

    public static synchronized void tick() {
        long now = System.currentTimeMillis();
        try {
            if (leader != null) leader.tick(now);
            if (worker != null) worker.tick(now);
            if (leader != null && ("local".equals(leaderName) || Boolean.getBoolean("tenorclef.swarm.log"))) {
                java.util.List<SwarmLedger.Event> all = leader.ledger().all();
                for (; loggedEvents < all.size(); loggedEvents++) Debug.logMessage("SWARMEVT " + all.get(loggedEvents));
            }
        } catch (Throwable t) {
            Debug.logWarning("SWARM tick: " + t);
        }
    }
}
