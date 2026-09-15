package adris.altoclef.tasks.speedrun.testrun2.escape;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Sticky;
import adris.altoclef.tasksystem.Task;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * 1x2 shaft down, small stone room, no surface torch.
 * Not a vault — just a hole you can AFK in without a dirt pillar on the map.
 */
public class SecretBaseTask extends Task {

    private BlockPos start;
    private int depth;
    private int targetY;
    private int step;
    private boolean done;
    private final T2Sticky sticky = new T2Sticky();

    @Override
    protected void onStart() {
        AltoClef mod = AltoClef.getInstance();
        start = mod.getPlayer() != null ? mod.getPlayer().getBlockPos() : BlockPos.ORIGIN;
        int feet = start.getY();
        int minor = McCompat.gameMinor();
        // Stay in stone, above lava. 1.18+ world is deeper.
        targetY = minor >= 18 ? Math.max(-40, feet - 24) : Math.max(12, feet - 20);
        depth = 0;
        step = 0;
        done = false;
        Debug.logMessage("ESCAPE shaft to y=" + targetY + " (no surface lights)");
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null || start == null) return null;
        int y = mod.getPlayer().getBlockY();

        if (y > targetY + 1) {
            BlockPos under = mod.getPlayer().getBlockPos().down();
            try {
                var underState = mod.getWorld().getBlockState(under);
                if (underState.isOf(Blocks.LAVA) || underState.isOf(Blocks.BEDROCK)) {
                    done = true;
                    Debug.logMessage("ESCAPE shaft stop — lava/bedrock");
                    return null;
                }
                if (!underState.isAir()) {
                    return sticky.keep("shaft", new DestroyBlockTask(under));
                }
            } catch (Throwable ignored) {}
            McCompat.setMove(false, true);
            depth++;
            return null;
        }

        McCompat.setMove(false, false);
        if (step < 6) {
            BlockPos carve = mod.getPlayer().getBlockPos().add(step % 3, 0, step / 3);
            try {
                var st = mod.getWorld().getBlockState(carve);
                if (!st.isAir() && !st.isOf(Blocks.BEDROCK)) {
                    return sticky.keep("carve" + step, new DestroyBlockTask(carve));
                }
            } catch (Throwable ignored) {}
            step++;
            return null;
        }

        done = true;
        Debug.logMessage("ESCAPE room carved. Stay here. Cover the shaft by hand if you want it tighter.");
        return null;
    }

    @Override
    protected void onStop(Task interrupt) {
        McCompat.setMove(false, false);
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SecretBaseTask;
    }

    @Override
    protected String toDebugString() {
        return "secret-base y->" + targetY;
    }
}
