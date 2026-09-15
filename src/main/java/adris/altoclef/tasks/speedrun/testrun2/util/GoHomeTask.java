package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.speedrun.testrun2.T2Brain;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Sticky;
import adris.altoclef.tasksystem.Task;

public class GoHomeTask extends Task {

    private final HomeStore dest;
    private final T2Sticky sticky = new T2Sticky();
    private boolean done;

    public GoHomeTask(HomeStore dest) {
        this.dest = dest;
    }

    @Override
    protected void onStart() {
        done = false;
        sticky.clear();
        T2Brain.reset();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null || dest == null) {
            done = true;
            return null;
        }
        Task live = sticky.peek();
        Task fix = T2Brain.help(mod, "HOME", live);
        if (fix != null) return sticky.keep("brain", fix);
        if (!dest.sameDim(mod)) {
            Debug.logMessage("HOME wrong dimension (" + dest.dim + "). Walk/portal yourself.");
            done = true;
            return null;
        }
        long dx = (long) mod.getPlayer().getBlockX() - dest.x;
        long dz = (long) mod.getPlayer().getBlockZ() - dest.z;
        if (dx * dx + dz * dz <= 16) {
            done = true;
            Debug.logMessage("HOME arrived");
            return null;
        }
        return sticky.keep("walk", new GetToBlockTask(dest.pos()));
    }

    @Override
    protected void onStop(Task interrupt) {
        sticky.clear();
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GoHomeTask;
    }

    @Override
    protected String toDebugString() {
        return "gohome";
    }
}
