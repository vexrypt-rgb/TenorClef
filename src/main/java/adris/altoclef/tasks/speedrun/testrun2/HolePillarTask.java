package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.Task;

/** Pause CollectIron so it cannot mine the block we just placed. */
public class HolePillarTask extends Task {

    private String finishWhy = "-";

    @Override
    protected void onStart() {}

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null) {
            fail(FailureReason.PRECONDITION_FAILED, "pillar-out: mod null", false);
            return null;
        }
        // isFinished() also returns true here; without fail() Task.tick would record the
        // give-up as SUCCESS.
        if (HolePillar.givingUp()) {
            fail(FailureReason.TIMEOUT, "pillar-out gave up, cool=" + HolePillar.failCoolLeft(), false);
            return null;
        }
        // Keep ticking while holding even if boxed flickers for a hop.
        if (!HolePillar.hasPlace(mod) && !HolePillar.holding()) {
            return null;
        }
        HolePillar.tick(mod);
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef mod = AltoClef.getInstance();
        String who = interruptTask == null ? "-" : interruptTask.getClass().getSimpleName();
        if (mod != null) {
            T2Log.force("S136", "onStop interrupt=" + who
                    + " finishWhy=" + finishWhy
                    + " lastEnd=" + HolePillar.lastEndReason()
                    + " " + HolePillar.snap(mod));
        }
        // reset() clears holding/keys but keeps failCool (post-fail / post-success).
        HolePillar.reset();
    }

    @Override
    public boolean isFinished() {
        try {
            AltoClef mod = AltoClef.getInstance();
            if (mod == null) {
                finishWhy = "mod-null";
                return true;
            }
            if (HolePillar.givingUp()) {
                finishWhy = "givingUp cool=" + HolePillar.failCoolLeft();
                return true;
            }
            if (HolePillar.risenEnough(mod)) {
                finishWhy = "risenEnough y=" + mod.getPlayer().getBlockY()
                        + " startY=" + HolePillar.startY();
                return true;
            }
            if (!HolePillar.hasPlace(mod) && !HolePillar.holding()) {
                finishWhy = "!hasPlace && !holding";
                return true;
            }
            // Actively escaping - do not hand back to CollectIron on a 1-tick !boxed hop.
            if (HolePillar.holding()) {
                finishWhy = "holding";
                return false;
            }
            boolean box = HolePillar.boxed(mod);
            finishWhy = box ? "idle-boxed" : "!boxed && !holding";
            return !box;
        } catch (Throwable t) {
            finishWhy = "throw:" + t.getClass().getSimpleName();
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