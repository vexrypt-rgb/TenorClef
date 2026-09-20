package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * Standing on the crafting table makes Baritone jump-click forever.
 * Close the screen so the 8 ingots return to the backpack, sneak-walk
 * one block, then finish. Parent must not reopen craft until isFinished.
 */
public class StepOffTableTask extends Task {

    private int ticks;
    private boolean done;
    private float startYaw;

    @Override
    protected void onStart() {
        ticks = 0;
        done = false;
        startYaw = McCompat.playerYaw();
        McCompat.closeScreen();
        try { McCompat.cancelPathing(); } catch (Throwable ignored) {}
        adris.altoclef.tasks.speedrun.testrun2.core.T2Input.releaseAll();
        adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
    }

    @Override
    protected Task onTick() {
        ticks++;
        McCompat.closeScreen();
        adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
        // Finish as soon as we are off the table — do not keep walking/jumping.
        if (!onTable()) {
            done = true;
            McCompat.setMove(false, false);
            return null;
        }
        McCompat.setMove(true, false);
        if (ticks == 10) McCompat.setYaw(startYaw + 90f);
        if (ticks == 20) McCompat.setYaw(startYaw + 180f);
        if (ticks == 40) McCompat.setYaw(startYaw + 270f);
        // Longer timeout if still standing on the table (was 40 → endless reopen thrash).
        if (ticks >= 20 * 5) done = true;
        return null;
    }

    @Override
    protected void onStop(Task interrupt) {
        McCompat.setMove(false, false);
        McCompat.closeScreen();
        adris.altoclef.tasks.speedrun.testrun2.core.T2Input.releaseAll();
    }

    private boolean onTable() {
        try {
            AltoClef mod = AltoClef.getInstance();
            if (mod == null || mod.getPlayer() == null || mod.getWorld() == null) return false;
            BlockPos feet = mod.getPlayer().getBlockPos();
            return mod.getWorld().getBlockState(feet).isOf(Blocks.CRAFTING_TABLE)
                    || mod.getWorld().getBlockState(feet.down()).isOf(Blocks.CRAFTING_TABLE);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof StepOffTableTask;
    }

    @Override
    protected String toDebugString() {
        return "step-off-table t=" + ticks;
    }

    @Override
    public boolean isFinished() {
        return done;
    }
}
