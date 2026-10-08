package adris.altoclef.tasks.pvp;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
//#if MC >= 12104 && MC < 260000
import baritone.process.PvpProcess;
//#endif
import net.minecraft.entity.LivingEntity;

//#if MC >= 12104 && MC < 260000
/**
 * Thin wrapper over Ostinato's {@code PvpProcess}, which owns the fighting movement (crits,
 * W-tap, strafe, jump reset, shield, axe, bow, gapples, totem). This task only starts it, keeps
 * the other chains out of the way and mirrors its stats.
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

    private static PvpProcess proc() {
        return AltoClef.getInstance().getClientBaritone().getPvpProcess();
    }

    @Override
    protected void onStart() {
        switch (mode) {
            case PLAYER -> proc().attackPlayer(name);
            case PLAYERS -> proc().attackPlayers();
            case HOSTILES -> proc().attackHostiles();
        }
    }

    @Override
    protected Task onTick() {
        touch();
        PvpProcess p = proc();
        if (!p.isActive()) onStart(); // cancelled from outside (e.g. #stop): pick it back up
        attacks = p.attacks;
        crits = p.crits;
        sprintHits = p.sprintHits;
        axeHits = p.axeHits;
        blocks = p.blocks;
        gapples = p.gapples;
        damageTaken = p.damageTaken;
        setDebugState(p.displayName0());
        return null;
    }

    public LivingEntity getTarget() {
        return (LivingEntity) (Object) proc().getTarget();
    }

    public String stats() {
        return proc().stats();
    }

    @Override
    protected void onStop(Task interruptTask) {
        proc().onLostControl();
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PvpTask p && p.mode == mode && p.name.equals(name);
    }

    @Override
    protected String toDebugString() {
        return "PvP " + name;
    }
}
//#else
//$$ /**
//$$  * PvP needs Ostinato's PvpProcess, which only ships in the 1.21.4 and 1.21.11 builds. Other versions get an
//$$  * inert stand-in with the same API so the rest of TenorClef still compiles.
//$$  */
//$$ public class PvpTask extends Task {
//$$     public int attacks, crits, sprintHits, blocks, gapples, pots, axeHits;
//$$     public float damageTaken;
//$$
//$$     public static PvpTask player(String name) { return new PvpTask(); }
//$$     public static PvpTask nearestPlayer() { return new PvpTask(); }
//$$     public static PvpTask hostiles() { return new PvpTask(); }
//$$     public static void touch() { }
//$$     public static boolean anyActive() { return false; }
//$$     public LivingEntity getTarget() { return null; }
//$$     public String stats() { return "PvP is not available on this Minecraft version"; }
//$$
//$$     @Override
//$$     protected void onStart() { }
//$$
//$$     @Override
//$$     protected Task onTick() { return null; }
//$$
//$$     @Override
//$$     protected void onStop(Task interruptTask) { }
//$$
//$$     @Override
//$$     public boolean isFinished() { return true; }
//$$
//$$     @Override
//$$     protected boolean isEqual(Task other) { return other instanceof PvpTask; }
//$$
//$$     @Override
//$$     protected String toDebugString() { return "PvP (unavailable)"; }
//$$ }
//#endif
