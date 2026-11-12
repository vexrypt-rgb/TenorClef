package adris.altoclef.swarm;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;

import java.util.Set;

/**
 * Live singleton: owns this client's leader and/or worker role. Messages ride Ostinato's swarm link
 * ({@link SwarmLink}); who leads and who may talk is decided by the Ostinato roster, and every message
 * is already sealed, signed and replay-checked before it reaches {@link #onWire}.
 */
public final class SwarmRuntime {
    private static final Set<String> TO_LEADER = Set.of("reg", "bye", "hb", "accept", "reject", "prog", "result", "obs");

    private static SwarmLink link = SwarmLink.create();
    private static SwarmCoordinator leader;
    private static SwarmWorker worker;
    private static String leaderName;
    /** The leader is the single-client loopback of {@link #local}, not one that leads over the link. */
    private static boolean loopback;

    private SwarmRuntime() {}

    /** Replace the link (tests). Roles held over the old link end with it. */
    public static synchronized void useLink(SwarmLink l) {
        link = l;
        leader = null;
        worker = null;
        leaderName = null;
        loopback = false;
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
        // Leading again must not start over: the helpers are registered with the coordinator that is running.
        if (leader != null && !loopback) return leader;
        loopback = false;
        // The link is one rate-limited chat line stream (0.5 lines/s, signed lines are bigger), so give replies room.
        SwarmCoordinator.Config cfg = new SwarmCoordinator.Config();
        cfg.heartbeatTimeoutMs = 40_000;
        cfg.offerTimeoutMs = 30_000;
        leader = new SwarmCoordinator((agent, m) -> link.send(agent, m), cfg);
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
        worker = new SwarmWorker(link.self(), caps, new AltoClefExecutor(mod), m -> link.send(lead, m), 12_000);
        worker.register(System.currentTimeMillis());
        return worker;
    }

    /** Sequence number of the last ledger event echoed to chat; the ledger is capped, so an index would stall. */
    private static long loggedSeq;

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
        loopback = true;
        loggedSeq = 0;
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

    private static boolean protocolHookAttempted;

    /**
     * Publish safety latches and the {@code @} hook. Reflection only: a stock Baritone jar has none of
     * these methods, and this class must still compile against that jar. No effect while the protocol is off.
     */
    private static void noteProtocolSafety() {
        try {
            IBaritone primary = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (primary == null || primary.getSwarm() == null) {
                return;
            }
            Object swarm = primary.getSwarm();
            if (!protocolHookAttempted) {
                protocolHookAttempted = true;
                try {
                    Class<?> hookType = Class.forName("baritone.api.swarm.protocol.ExternalCommandHook");
                    Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                            hookType.getClassLoader(),
                            new Class<?>[]{hookType},
                            new SwarmProtocolHook());
                    swarm.getClass().getMethod("setExternalCommandHook", hookType).invoke(swarm, proxy);
                } catch (Throwable ignoredHook) {
                    // This Baritone build has no Phase 1 hook.
                }
            }
            AltoClef mod = AltoClef.getInstance();
            if (mod == null) {
                return;
            }
            invokeBool(swarm, "noteOwnerEmergency", mod.isPaused());
            // Ostinato: FoodChain.isActive() and MobDefenseChain.isActive() always return
            // true ("always checking"). Using them here latched localSurvival on every
            // TenorClef client and refused every swarm command as outranked-by-local-survival.
            boolean eating = mod.getFoodChain() != null
                    && (mod.getFoodChain().isTryingToEat() || mod.getFoodChain().needsToEat());
            adris.altoclef.chains.MobDefenseChain defense = mod.getMobDefenseChain();
            boolean defending = defense != null && (defense.isShielding() || defense.isDoingAcrobatics()
                    || defense.isPuttingOutFire() || defense.getPriority() > 0);
            invokeBool(swarm, "noteLocalSurvival", eating || defending);
            boolean danger = false;
            if (mod.getPlayer() != null) {
                danger = mod.getPlayer().isOnFire()
                        || mod.getPlayer().isInLava()
                        || mod.getPlayer().getAir() < 40
                        || mod.getPlayer().getHealth() <= 6.0f;
            }
            invokeBool(swarm, "noteImmediateSafety", danger);
        } catch (Throwable ignored) {
            // Baritone is not ready, or this build has no protocol methods. The link still ticks.
        }
    }

    private static void invokeBool(Object swarm, String name, boolean value) throws Exception {
        swarm.getClass().getMethod(name, boolean.class).invoke(swarm, Boolean.valueOf(value));
    }

    public static synchronized void tick() {
        long now = System.currentTimeMillis();
        try {
            noteProtocolSafety();
            if (leader != null) leader.tick(now);
            if (worker != null) worker.tick(now);
            if (leader != null && ("local".equals(leaderName) || Boolean.getBoolean("tenorclef.swarm.log"))) {
                java.util.List<SwarmLedger.Event> all = leader.ledger().all();
                for (SwarmLedger.Event e : all) {
                    if (e.seq() <= loggedSeq) continue;
                    loggedSeq = e.seq();
                    Debug.logMessage("SWARMEVT " + e);
                }
            }
        } catch (Throwable t) {
            Debug.logWarning("SWARM tick: " + t);
        }
    }
}
