package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.GetOutOfWaterTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.BlockPos;

/** Swim/climb out. Uses Miran GetOutOfWaterTask, not a jump-in-place stub. */
public class WaterBailTask extends Task {

    /**
     * S183 — THE BOBBING BOT: the bail ended on an INSTANTANEOUS wet test.
     *
     * <p>{@code isSubmergedInWater()} is true only while the player's EYES are under water.
     * A bot bobbing in water clears the surface for a tick at a time, so the very first bob
     * that lifted its eyes above the surface read as "dry" and ended the bail — while the bot
     * was still standing in the water. The parent resumed, the bot sank back in, S102 fired
     * again, a fresh bail was built, and it ended on the next bob. Run AD: S102 every ~6s at
     * `spd=0.000 @243,61,136` for 30+ seconds — the user's "bobbing up and down in the water".
     *
     * <p>Two fixes: (1) test ANY water contact from the world (feet and head block fluid),
     * not eye submersion; (2) require the bot to stay dry for {@link #DRY_TICKS} consecutive
     * ticks before finishing, so a single bob cannot end the bail.
     */
    private static final int MAX_TICKS = 20 * 20;
    /** Consecutive dry ticks required before the bail may finish (1s). */
    private static final int DRY_TICKS = 20;
    /** No horizontal progress for this long -> stop swimming, path to a visible shore. */
    private static final int SHORE_AFTER_TICKS = 20 * 5;

    private int ticks;
    private int dryTicks;
    private int noProgressTicks;
    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;
    private Task shoreTask;
    private boolean done;

    @Override
    protected void onStart() {
        ticks = 0;
        dryTicks = 0;
        noProgressTicks = 0;
        lastX = Double.NaN;
        lastZ = Double.NaN;
        shoreTask = null;
        done = false;
        Debug.logMessage("TESRUN2 water-bail -> GetOutOfWaterTask");
    }

    /** Any water contact at the feet or head block. World-based, so it cannot lie on a bob. */
    private boolean inWater(AltoClef mod) {
        try {
            BlockPos feet = mod.getPlayer().getBlockPos();
            return !mod.getWorld().getBlockState(feet).getFluidState().isEmpty()
                    || !mod.getWorld().getBlockState(feet.up()).getFluidState().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        ticks++;
        if (mod.getPlayer() == null || mod.getWorld() == null) {
            fail(FailureReason.PRECONDITION_FAILED, "water-bail: no player/world", false);
            done = true;
            return null;
        }

        boolean wet = inWater(mod);
        if (!wet) dryTicks++;
        else dryTicks = 0;

        if (dryTicks >= DRY_TICKS || ticks > MAX_TICKS) {
            T2Log.warn("E10", "water-bail end ticks=" + ticks + " dry=" + dryTicks
                    + " wet=" + wet + (ticks > MAX_TICKS ? " TIMEOUT" : ""));
            // Only "dry for DRY_TICKS" is a real exit; running out the clock still wet is a failure.
            if (dryTicks < DRY_TICKS) {
                fail(FailureReason.TIMEOUT, "water-bail timed out still wet after " + ticks + " ticks", false);
            }
            done = true;
            return null;
        }

        // Escalate when swimming is not converging: a 1-deep pool has no swimmable exit, and
        // GetOutOfWaterTask can circle in it forever. findShore() was already written for
        // exactly this and had no caller.
        double x = mod.getPlayer().getX();
        double z = mod.getPlayer().getZ();
        if (!Double.isNaN(lastX)) {
            double dx = x - lastX;
            double dz = z - lastZ;
            if (dx * dx + dz * dz < 0.01) noProgressTicks++;
            else noProgressTicks = 0;
        }
        lastX = x;
        lastZ = z;

        if (shoreTask == null && noProgressTicks > SHORE_AFTER_TICKS) {
            BlockPos shore = findShore(mod);
            if (shore != null) {
                shoreTask = new GetToBlockTask(shore);
                T2Log.force("S183", "water bail stalled " + (ticks / 20) + "s at "
                        + mod.getPlayer().getBlockX() + "," + mod.getPlayer().getBlockY()
                        + "," + mod.getPlayer().getBlockZ() + " — path to shore "
                        + shore.getX() + "," + shore.getY() + "," + shore.getZ());
            }
        }
        if (shoreTask != null) return shoreTask;

        return new GetOutOfWaterTask();
    }

    private BlockPos findShore(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return null;
        BlockPos from = mod.getPlayer().getBlockPos();
        for (int r = 1; r <= 16; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = -2; dy <= 3; dy++) {
                        BlockPos p = from.add(dx, dy, dz);
                        try {
                            var state = mod.getWorld().getBlockState(p);
                            var up = mod.getWorld().getBlockState(p.up());
                            if (!state.getFluidState().isEmpty()) continue;
                            if (!state.isAir() && up.isAir()) return p.up();
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
        return from.up(4);
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof WaterBailTask;
    }

    @Override
    protected String toDebugString() {
        return "water-bail";
    }
}
