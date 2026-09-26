package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskResult;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.TungstenHelper;
import net.minecraft.item.Items;

/**
 * After a death the bot respawns empty and decide() jumps to BOOTSTRAP.
 * Before punching a new tree, grab the old iron/eyes if the grave is close.
 */
public class DeathRecycleTask extends Task {

    private static final int MAX_TICKS = 20 * 8;
    private PickupDroppedItemTask pickup;
    private ItemTarget[] targets;
    private int ticks;
    private boolean done;

    @Override
    protected void onStart() {
        ticks = 0;
        done = false;
        pickup = null;
        Debug.logMessage("TESRUN2 death-recycle: looking for grave drops");
        try { TungstenHelper.stop(); } catch (Throwable ignored) {}
        targets = graveTargets();
        pickup = new PickupDroppedItemTask(targets, false);
    }

    static boolean graveVisible() {
        return AltoClef.getInstance().getEntityTracker().itemDropped(graveTargets());
    }

    private static ItemTarget[] graveTargets() {
        return new ItemTarget[]{
                new ItemTarget(Items.IRON_PICKAXE, 1),
                new ItemTarget(Items.IRON_INGOT, 16),
                new ItemTarget(Items.ENDER_EYE, 12),
                new ItemTarget(Items.ENDER_PEARL, 12),
                new ItemTarget(Items.BLAZE_ROD, 8),
                new ItemTarget(Items.FLINT_AND_STEEL, 1),
                new ItemTarget(Items.WATER_BUCKET, 1),
                new ItemTarget(Items.BUCKET, 1),
                new ItemTarget(Items.IRON_SWORD, 1)
        };
    }

    @Override
    protected Task onTick() {
        ticks++;
        if (ticks >= MAX_TICKS) {
            done = true;
            fail(FailureReason.TIMEOUT, "grave pickup timed out", false);
            return null;
        }
        // S254: with no grave drops in view, PickupDroppedItemTask wanders; s253t walked a
        // fresh respawn through night skeletons for 25s this way, four deaths in a row.
        if (!AltoClef.getInstance().getEntityTracker().itemDropped(targets)) {
            done = true;
            // Drops seen at start and gone later = picked up (or despawned: not verifiable here).
            if (ticks <= 1) fail(FailureReason.TARGET_UNAVAILABLE, "no grave drops in view", false);
            else succeed();
            return null;
        }
        return pickup;
    }

    @Override
    public boolean isFinished() {
        return done || (pickup != null && pickup.isFinished());
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof DeathRecycleTask;
    }

    @Override
    protected String toDebugString() {
        return "death-recycle";
    }
}
