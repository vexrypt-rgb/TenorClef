package adris.altoclef.swarm;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;

import java.util.Set;

/**
 * Live singleton: owns this client's leader and/or worker role. Messages ride Ostinato's swarm link
 * ({@link SwarmLink}); who leads and who may talk is decided by the Ostinato roster, and every message
 * is already sealed, signed and replay-checked before it reaches {@link #onWire}.
 */
public final class SwarmRuntime {
    private static final Set<String> TO_LEADER = Set.of("reg", "bye", "hb", "accept", "reject", "prog", "result", "obs");

    private static SwarmLink link = new SwarmLink.Ostinato();
    private static SwarmCoordinator leader;
    private static SwarmWorker worker;
    private static String leaderName;

    private SwarmRuntime() {}

    /** Replace the link (tests). */
    public static synchronized void useLink(SwarmLink l) {
        link = l;
    }

    public static SwarmLink link() { return link; }
    public static SwarmCoordinator leader() { return leader; }
    public static SwarmWorker worker() { return worker; }

    /** @return why we cannot swarm right now, or null if the link is up */
    public static String problem() { return link.problem(); }

    public static synchronized SwarmCoordinator lead(AltoClef mod) {
        String why = link.problem();
        if (why != null) throw new IllegalStateException(why);
        if (!link.leads()) throw new IllegalStateException("you are not the lead of any roster group (lead= in swarm.txt)");
        link.onReceive(SwarmRuntime::onWire);
        leader = new SwarmCoordinator((agent, m) -> link.send(agent, m));
        return leader;
    }

    public static synchronized SwarmWorker join(AltoClef mod, Set<Capability> caps) {
        String why = link.problem();
        if (why != null) throw new IllegalStateException(why);
        String lead = link.leader();
        if (lead == null) throw new IllegalStateException("your roster group has no lead=");
        link.onReceive(SwarmRuntime::onWire);
        if (worker != null) worker.leave();
        leaderName = lead;
        worker = new SwarmWorker(link.self(), caps, new AltoClefExecutor(mod), m -> link.send(lead, m), 5_000);
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

    /**
     * Inbound objective message from the Ostinato link. Worker-bound ops are accepted only from the lead
     * of the group they arrived on.
     */
    public static synchronized void onWire(String from, String group, String body) {
        SwarmMessage m = SwarmMessage.decode(body);
        if (m == null) return;
        long now = System.currentTimeMillis();
        if (Boolean.getBoolean("tenorclef.swarm.log")) {
            Debug.logMessage("SWARMRX " + m.op + " from " + from + " " + body);
        }
        if (TO_LEADER.contains(m.op)) {
            if (leader != null) leader.onMessage(from, m, now);
            else Debug.logMessage("SWARM " + m.op + " from " + from + " ignored: not leading");
        } else if (worker != null && from.equals(link.leadOf(group)) && from.equals(leaderName)) {
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
