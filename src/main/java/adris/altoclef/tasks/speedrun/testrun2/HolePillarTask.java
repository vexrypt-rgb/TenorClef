package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

/** Pause CollectIron so it cannot mine the block we just placed. */
public class HolePillarTask extends Task {

    @Override
    protected void onStart() {}

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || HolePillar.givingUp() || !HolePillar.hasPlace(mod) || !HolePillar.boxed(mod)) {
            return null;
        }
        HolePillar.tick(mod);
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {
        HolePillar.reset();
    }

    @Override
    public boolean isFinished() {
        try {
            AltoClef mod = AltoClef.getInstance();
            return HolePillar.givingUp() || !HolePillar.boxed(mod) || !HolePillar.hasPlace(mod);
        } catch (Throwable t) {
            return true;
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof HolePillarTask;
    }

    @Override
    protected String toDebugString() {
        return "T2 pillar-out";
    }
}
