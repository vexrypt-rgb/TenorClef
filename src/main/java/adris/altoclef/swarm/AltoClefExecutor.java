package adris.altoclef.swarm;

import adris.altoclef.AltoClef;
import adris.altoclef.agent.AgentResponse;
import adris.altoclef.agent.AgentStatus;
import adris.altoclef.agent.AltoClefAgentRuntime;
import adris.altoclef.commands.GoalCommand;
import adris.altoclef.planner.CatalogueInventoryView;
import adris.altoclef.planner.GoalManager;
import adris.altoclef.planner.GoalStatus;

import java.util.Locale;
import java.util.Map;

/**
 * Runs swarm objectives through TenorClef's existing goal system (AltoClefAgentRuntime -> GoalManager
 * -> PlanRunnerTask). Success is never taken from the goal status alone: the inventory must hold the
 * items, and that count is sent as evidence for the swarm to verify again.
 */
public final class AltoClefExecutor implements AssignmentExecutor {
    private final AltoClef mod;
    private final CatalogueInventoryView inventory;
    private Objective objective;
    private GoalManager manager;
    private String startFailure;
    private int startDelay;
    private static final long STALL_MS = 60_000;
    private long lastMove, lastLog;
    private double lx, ly, lz;
    private int lastHave;

    public AltoClefExecutor(AltoClef mod) {
        this.mod = mod;
        this.inventory = new CatalogueInventoryView(mod);
    }

    @Override
    public void start(Objective o) {
        objective = o;
        manager = null;
        startFailure = null;
        // Not started here: an offer can arrive while a command is executing, and that command
        // finishing would cancel the task just started. First poll after a few ticks begins it.
        startDelay = 3;
        lastMove = System.currentTimeMillis();
        lastHave = inventory.getCount(o.item);
        lx = ly = lz = Double.NaN;
    }

    private void begin() {
        Objective o = objective;
        AgentResponse r = new AltoClefAgentRuntime(mod).acquire("swarm-" + System.nanoTime(), o.item, o.count);
        if (r.getStatus() == AgentStatus.FAILURE || r.getStatus() == AgentStatus.BLOCKED) {
            startFailure = "START_REFUSED: " + r.getError();
            return;
        }
        manager = GoalCommand.getActiveManager();
    }

    @Override
    public Status poll() {
        if (objective == null) return Status.idle();
        if (startDelay > 0) {
            if (--startDelay > 0) return Status.running(0);
            begin();
        }
        int have = inventory.getCount(objective.item);
        Map<String, String> evidence = Map.of("have", Integer.toString(have), "item", objective.item);
        if (startFailure != null) return Status.failed(startFailure, evidence);
        if (manager == null) return Status.failed("NO_GOAL_MANAGER", evidence);
        if (GoalCommand.getActiveManager() != manager) return Status.failed("SUPERSEDED", evidence);

        GoalStatus st = manager.getStatus();
        if (!mod.inGame()) return Status.failed("LEFT_GAME", evidence);
        return switch (st) {
            case SUCCESS -> have >= objective.count
                    ? Status.succeeded(evidence)
                    : Status.failed("GOAL_FINISHED_WITHOUT_ITEMS", evidence);
            case FAILED -> Status.failed(classify(manager.getLastNote()), evidence);
            case CANCELLED -> Status.failed("CANCELLED_BY_AGENT", evidence);
            default -> have >= objective.count
                    ? Status.succeeded(evidence)
                    : watch(st, have, evidence);
        };
    }

    /** RUNNING/PAUSED goals must still show life: movement or inventory change, else it is reported as stalled. */
    private Status watch(GoalStatus st, int have, Map<String, String> evidence) {
        long now = System.currentTimeMillis();
        double[] p = position();
        if (p != null && (Double.isNaN(lx) || Math.abs(p[0] - lx) + Math.abs(p[1] - ly) + Math.abs(p[2] - lz) > 1.5) || have != lastHave) {
            if (p != null) { lx = p[0]; ly = p[1]; lz = p[2]; }
            lastHave = have;
            lastMove = now;
        }
        if (now - lastLog > 10_000) {
            lastLog = now;
            adris.altoclef.Debug.logMessage("SWARM exec goal=" + st + " note=" + manager.getLastNote() + " idleFor=" + (now - lastMove) / 1000 + "s");
        }
        if (now - lastMove > STALL_MS) {
            // The leader hands this work to someone else; a goal left running here would compete with them.
            String reason = "STALLED:" + st;
            cancel();
            return Status.failed(reason, evidence);
        }
        return Status.running(Math.min(99, have * 100 / objective.count));
    }

    private static String classify(String note) {
        String n = note == null ? "" : note.toLowerCase(Locale.ROOT);
        if (n.contains("danger") || n.contains("threat")) return "DANGER";
        if (n.contains("died") || n.contains("death")) return "DIED";
        return "GOAL_FAILED";
    }

    @Override
    public void cancel() {
        if (manager != null && !manager.getStatus().isTerminal()) manager.cancel();
        try {
            mod.cancelUserTask();
        } catch (Throwable ignored) {
            // nothing running
        }
        objective = null;
    }

    @Override
    public double[] position() {
        return mod.getPlayer() == null ? null : new double[]{mod.getPlayer().getX(), mod.getPlayer().getY(), mod.getPlayer().getZ()};
    }

    @Override
    public float health() {
        return mod.getPlayer() == null ? 0f : mod.getPlayer().getHealth();
    }

    @Override
    public boolean alive() {
        if (mod.getPlayer() == null) return false;
        if (mod.getPlayer().getHealth() > 0) return true;
        // A dead worker must come back or the leader never gets it again. DeathMenuChain only acts while a
        // DeathScreen is up; this covers a client that is dead without one. Throttled, game thread only.
        long now = System.currentTimeMillis();
        if (now - lastRespawnTry > 3_000) {
            lastRespawnTry = now;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.requestRespawn();
                mc.setScreen(null);
            }
        }
        return false;
    }

    private long lastRespawnTry;
}
