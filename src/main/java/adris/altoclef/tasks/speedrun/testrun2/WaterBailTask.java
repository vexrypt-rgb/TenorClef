package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.GetOutOfWaterTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.BlockPos;

/** Swim/climb out. Uses Miran GetOutOfWaterTask, not a jump-in-place stub. */
public class WaterBailTask extends Task {

    private static final int MAX_TICKS = 20 * 8;
    private int ticks;
    private Task inner;
    private boolean done;

    @Override
    protected void onStart() {
        ticks = 0;
        done = false;
        inner = new GetOutOfWaterTask();
        Debug.logMessage("TESRUN2 water-bail -> GetOutOfWaterTask");
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        ticks++;
        if (mod.getPlayer() == null) {
            done = true;
            return null;
        }
        boolean wet = false;
        try {
            wet = mod.getPlayer().isSubmergedInWater();
        } catch (Throwable ignored) {}
        if (!wet || ticks > MAX_TICKS) {
            if (!done) T2Log.warn("E10", "water-bail end ticks=" + ticks + " wet=" + wet);
            done = true;
            return null;
        }
        if (inner == null) inner = new GetOutOfWaterTask();
        return inner;
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
