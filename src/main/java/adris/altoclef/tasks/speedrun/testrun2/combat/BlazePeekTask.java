package adris.altoclef.tasks.speedrun.testrun2.combat;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.mob.BlazeEntity;

/**
 * Short burst: hit the blaze that is already in your face, then yield.
 * Does not path to a fortress or replace CollectBlazeRodsTask.
 */
public class BlazePeekTask extends Task {

    private Task inner;
    private int ticks;
    private boolean done;

    @Override
    protected void onStart() {
        inner = FightNearbyTask.of(BlazeEntity.class);
        ticks = 0;
        done = false;
    }

    @Override
    protected Task onTick() {
        ticks++;
        if (ticks > 20 * 8) {
            done = true;
            return null;
        }
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() != null && mod.getPlayer().isOnFire() && ticks % 10 == 0) {
            // jump into water if we somehow have it — SurviveTick handles clutch
        }
        return inner;
    }

    @Override
    public boolean isFinished() {
        return done || (inner != null && inner.isFinished());
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BlazePeekTask;
    }

    @Override
    protected String toDebugString() {
        return "blaze-peek";
    }
}
