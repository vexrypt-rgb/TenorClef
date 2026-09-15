package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.tasksystem.Task;

/** Disabled. Throwing mid-run emptied the kit and locked the cursor. */
public class DropJunkTask extends Task {

    @Override
    protected void onStart() {
        T2Log.warn("E70", "DropJunk skipped (throws disabled)");
    }

    @Override
    protected Task onTick() {
        return null;
    }

    @Override
    public boolean isFinished() {
        return true;
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof DropJunkTask;
    }

    @Override
    protected String toDebugString() {
        return "drop-junk-disabled";
    }
}
