package adris.altoclef.compose;

import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskFailure;

/** Tiny leaf for Compositions: waits, never ends (ms &lt; 0), or fails on its first tick. */
class TimedTask extends Task {
    private final String label;
    private final long ms;
    private final TaskFailure failure;
    private long begin;
    private boolean done;

    TimedTask(String label, long ms, TaskFailure failure) {
        this.label = label;
        this.ms = ms;
        this.failure = failure;
    }

    @Override
    protected void onStart() {
        begin = System.currentTimeMillis();
        done = false;
    }

    @Override
    protected Task onTick() {
        if (failure != null) {
            fail(failure);
            return null;
        }
        if (ms >= 0 && System.currentTimeMillis() - begin >= ms) done = true;
        setDebugState(done ? "done" : ms < 0 ? "running" : (System.currentTimeMillis() - begin) + "/" + ms + "ms");
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other == this;
    }

    @Override
    protected String toDebugString() {
        return label;
    }
}
