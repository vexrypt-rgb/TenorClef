package adris.altoclef.tasks.pvp;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.LivingEntity;

/**
 * Thin wrapper over Ostinato's {@code PvpProcess}, which owns the fighting movement (crits,
 * W-tap, strafe, jump reset, shield, axe, bow, gapples, totem). This task only starts it, keeps
 * the other chains out of the way and mirrors its stats.
 * <p>
 * The process is reached by reflection: only the 1.21.4 Ostinato jar has it, and this source is
 * shared with the 1.16.1 and 1.21.11 builds, whose jars don't (there the task ends at once).
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

    private static Object proc() {
        try {
            Object b = AltoClef.getInstance().getClientBaritone();
            return b.getClass().getMethod("getPvpProcess").invoke(b);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object call(Object p, String method, Object... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) types[i] = args[i].getClass();
            return p.getClass().getMethod(method, types).invoke(p, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Number field(Object p, String name) {
        try {
            return (Number) p.getClass().getField(name).get(p);
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    protected void onStart() {
        Object p = proc();
        if (p == null) return;
        if (mode == Mode.PLAYER) call(p, "attackPlayer", name);
        else if (mode == Mode.PLAYERS) call(p, "attackPlayers");
        else call(p, "attackHostiles");
    }

    @Override
    protected Task onTick() {
        Object p = proc();
        if (p == null) {
            setDebugState("PvP needs an Ostinato build with PvpProcess (1.21.4)");
            return null;
        }
        touch();
        if (!Boolean.TRUE.equals(call(p, "isActive"))) onStart(); // cancelled from outside (e.g. #stop): pick it back up
        attacks = field(p, "attacks").intValue();
        crits = field(p, "crits").intValue();
        sprintHits = field(p, "sprintHits").intValue();
        axeHits = field(p, "axeHits").intValue();
        blocks = field(p, "blocks").intValue();
        gapples = field(p, "gapples").intValue();
        damageTaken = field(p, "damageTaken").floatValue();
        Object d = call(p, "displayName0");
        setDebugState(String.valueOf(d));
        return null;
    }

    public LivingEntity getTarget() {
        Object p = proc();
        Object t = p == null ? null : call(p, "getTarget");
        return t instanceof LivingEntity ? (LivingEntity) t : null;
    }

    public String stats() {
        Object p = proc();
        Object s = p == null ? null : call(p, "stats");
        return s == null ? "" : s.toString();
    }

    @Override
    protected void onStop(Task interruptTask) {
        Object p = proc();
        if (p != null) call(p, "onLostControl");
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
