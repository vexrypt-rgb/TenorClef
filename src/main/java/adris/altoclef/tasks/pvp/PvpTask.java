package adris.altoclef.tasks.pvp;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.LivingEntity;

/**
 * Thin wrapper over Ostinato's {@code PvpProcess}, which owns the fighting movement (crits,
 * W-tap, strafe, jump reset, shield, axe, bow, gapples, totem). This task only starts it, keeps
 * the other chains out of the way and mirrors its stats.
 * <p>
 * Ostinato: the process is reached through the typed {@code IBaritone.getPvpProcess()} API on every line.
 * 1.16.1 has no PvpProcess (the API returns null), so there the task only reports that.
 */
public class PvpTask extends Task {

    private enum Mode { PLAYER, PLAYERS, HOSTILES }

    private final Mode mode;
    private final String name;
    private static volatile long lastTickMs;

    public int attacks, crits, sprintHits, blocks, gapples, pots, axeHits;
    public float damageTaken;

    private PvpTask(Mode mode, String name) {
        this.mode = mode;
        this.name = name;
    }

    public static PvpTask player(String name) {
        return new PvpTask(Mode.PLAYER, name);
    }

    public static PvpTask nearestPlayer() {
        return new PvpTask(Mode.PLAYERS, "players");
    }

    public static PvpTask hostiles() {
        return new PvpTask(Mode.HOSTILES, "hostiles");
    }

    /** Marks PvP as active for this tick (the bench calls it between rounds too). */
    public static void touch() {
        lastTickMs = System.currentTimeMillis();
    }

    /** True while PvP is ticking; MobDefense/Food chains stand down so it keeps control. */
    public static boolean anyActive() {
        return System.currentTimeMillis() - lastTickMs < 500;
    }

    // Ostinato: typed driver API (IPvpProcess), no reflection.
    private static baritone.api.process.IPvpProcess typed() {
        AltoClef mod = AltoClef.getInstance();
        return mod == null || mod.getClientBaritone() == null ? null : mod.getClientBaritone().getPvpProcess();
    }

    @Override
    protected void onStart() {
        baritone.api.process.IPvpProcess p = typed();
        if (p == null) return;
        if (mode == Mode.PLAYER) p.attackPlayer(name);
        else if (mode == Mode.PLAYERS) p.attackPlayers();
        else p.attackHostiles();
    }

    @Override
    protected Task onTick() {
        baritone.api.process.IPvpProcess p = typed();
        if (p == null) {
            setDebugState("PvP needs an Ostinato build with PvpProcess");
            return null;
        }
        touch();
        if (!p.isActive()) onStart(); // cancelled from outside (e.g. #stop): pick it back up
        baritone.api.process.PvpStats st = p.getStats();
        attacks = st.attacks;
        crits = st.crits;
        sprintHits = st.sprintHits;
        axeHits = st.axeHits;
        blocks = st.blocks;
        gapples = st.gapples;
        damageTaken = st.damageTaken;
        setDebugState(p.displayName0());
        return null;
    }

    public LivingEntity getTarget() {
        baritone.api.process.IPvpProcess p = typed();
        return p == null ? null : p.getTarget();
    }

    public String stats() {
        baritone.api.process.IPvpProcess p = typed();
        return p == null ? "" : p.stats();
    }

    @Override
    protected void onStop(Task interruptTask) {
        baritone.api.process.IPvpProcess p = typed();
        if (p != null) p.onLostControl();
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PvpTask && ((PvpTask) other).mode == mode && ((PvpTask) other).name.equals(name);
    }

    @Override
    protected String toDebugString() {
        return "PvP " + name;
    }
}
