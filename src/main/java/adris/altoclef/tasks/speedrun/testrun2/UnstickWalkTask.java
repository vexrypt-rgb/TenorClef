package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

/**
 * Walk (no jump) for a fixed number of ticks. No Baritone.
 * isEqual by class so stick() will not restart it every frame.
 */
public class UnstickWalkTask extends Task {

    private final int duration;
    private int ticks;
    private boolean done;
    private float startYaw;

    public UnstickWalkTask() {
        this(20 * 5);
    }

    public UnstickWalkTask(int duration) {
        this.duration = Math.max(20, duration);
    }

    @Override
    protected void onStart() {
        ticks = 0;
        done = false;
        McCompat.closeScreen();
        McCompat.cancelPathing();
        startYaw = McCompat.playerYaw();
        McCompat.setYaw(startYaw + 90f);
        McCompat.setMove(true, false); // walk only — jump made craft thrash worse
    }

    @Override
    protected Task onTick() {
        ticks++;
        McCompat.closeScreen();
        McCompat.setMove(true, false);
        if (ticks >= duration) {
            done = true;
            McCompat.setMove(false, false);
        }
        return null;
    }

    @Override
    protected void onStop(Task interrupt) {
        McCompat.setMove(false, false);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof UnstickWalkTask;
    }

    @Override
    protected String toDebugString() {
        return "unstick-walk t=" + ticks + "/" + duration;
    }

    @Override
    public boolean isFinished() {
        return done;
    }
}
