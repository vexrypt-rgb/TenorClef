package adris.altoclef.util;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Opt-in ({@code -Dtenorclef.waterTrace=true}) bounded trace of the mining / water-escape boundary in
 * {@code DestroyBlockTask}. Records one line per wet (or just-dried) mining tick plus lifecycle events into a ring
 * buffer and prints the buffer once a single target has issued {@link #DUMP_AFTER_ESCAPES} water escapes. Off by
 * default: every entry point returns after one boolean check. Client thread only.
 */
public final class WaterEscapeTrace {

    public static final boolean ENABLED = Boolean.getBoolean("tenorclef.waterTrace");
    static final int CAPACITY = 240;
    static final int DUMP_AFTER_ESCAPES = 10;
    private static final int MAX_DUMPS = 6;
    /** Keep recording this many ticks after the last wet tick, to see what the bot does once it reads dry. */
    private static final int TAIL_TICKS = 40;

    private static final String[] ring = new String[CAPACITY];
    private static int head;
    private static int size;
    private static int dumps;
    private static long lastWetTick = Long.MIN_VALUE;
    /** Per-trial counters, read and cleared by {@link #summaryAndReset()}. */
    private static int escapesTotal, wetTicks, heldTicks, bounds;

    private WaterEscapeTrace() {
    }

    /** One mining tick of {@code DestroyBlockTask}: the branch it took and the state that chose it. */
    public static void tick(BlockPos target, String branch, boolean wet, boolean grounded, boolean reach) {
        if (!ENABLED) return;
        AltoClef mod = AltoClef.getInstance();
        PlayerEntity p = mod.getPlayer();
        if (p == null || mod.getWorld() == null) return;
        long t = mod.getWorld().getTime();
        if (wet) wetTicks++;
        if ("ESCAPE_HELD".equals(branch)) heldTicks++;
        if (wet) lastWetTick = t;
        else if (t - lastWetTick > TAIL_TICKS) return;
        Vec3d v = p.getVelocity();
        BlockPos feet = p.getBlockPos();
        boolean feetW = isWater(feet), bodyW = isWater(feet.up()), belowW = isWater(feet.down());
        boolean pathing = false, goalActive = false;
        String goal = "-";
        try {
            pathing = mod.getClientBaritone().getPathingBehavior().isPathing();
            goalActive = mod.getClientBaritone().getCustomGoalProcess().isActive();
            Object g = mod.getClientBaritone().getCustomGoalProcess().getGoal();
            if (g != null) goal = g.getClass().getSimpleName();
        } catch (Throwable ignored) {}
        record(String.format("t=%d %s tgt=%d,%d,%d pos=%.2f,%.2f,%.2f vy=%.3f ground=%b wet=%b sub=%b reach=%b feetW=%b bodyW=%b belowW=%b path=%b goal=%s/%b",
                t, branch, target.getX(), target.getY(), target.getZ(), p.getX(), p.getY(), p.getZ(), v.y, grounded, wet,
                p.isSubmergedInWater(), reach, feetW, bodyW, belowW, pathing, goal, goalActive));
    }

    /** A lifecycle event (escape issued, navigation cancelled, goal set, escape task start/stop). */
    public static void event(String what) {
        if (!ENABLED) return;
        AltoClef mod = AltoClef.getInstance();
        long t = mod.getWorld() == null ? -1 : mod.getWorld().getTime();
        record("t=" + t + " EVENT " + what);
    }

    /** Called with the escape count for the current target; prints the buffer once per target at the threshold. */
    public static void escapes(BlockPos target, int count) {
        if (!ENABLED) return;
        escapesTotal++;
        if (count == DUMP_AFTER_ESCAPES) dump(target, "escapes=" + count);
    }

    /** The re-entry bound reported {@code target} unreachable; prints the buffer that led there. */
    public static void reentryBound(BlockPos target) {
        if (!ENABLED) return;
        bounds++;
        event("reentry bound -> requestBlockUnreachable " + target.toShortString());
        dump(target, "reentryBound");
    }

    /** {@code escapes= wetTicks= heldTicks= bounds=} since the last call (one benchmark trial). */
    public static String summaryAndReset() {
        String r = "escapes=" + escapesTotal + " wetTicks=" + wetTicks + " heldTicks=" + heldTicks + " bounds=" + bounds;
        escapesTotal = wetTicks = heldTicks = bounds = 0;
        return r;
    }

    private static void dump(BlockPos target, String why) {
        if (dumps >= MAX_DUMPS) return;
        dumps++;
        Debug.logHarness("WATERTRACE DUMP #" + dumps + " target=" + target.toShortString() + " " + why + " lines=" + size);
        for (int i = 0; i < size; i++) {
            Debug.logHarness("WATERTRACE " + ring[(head - size + i + CAPACITY) % CAPACITY]);
        }
        Debug.logHarness("WATERTRACE END #" + dumps);
        size = 0;
    }

    private static void record(String line) {
        ring[head] = line;
        head = (head + 1) % CAPACITY;
        if (size < CAPACITY) size++;
    }

    private static boolean isWater(BlockPos pos) {
        try {
            return AltoClef.getInstance().getWorld().getFluidState(pos).getFluid().matchesType(Fluids.WATER);
        } catch (Throwable t) {
            return false;
        }
    }
}
