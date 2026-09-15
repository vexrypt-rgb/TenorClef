package adris.altoclef.tasks.movement;

import adris.altoclef.movement.TungstenMovement;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.Entity;

/**
 * AltoClef Task wrapping Tungsten FollowEntityTask (chase / manhunt).
 * Falls back to GetToEntityTask if Tungsten follow cannot start.
 */
public class TungstenFollowTask extends Task {

    private final Entity entity;
    private final double maintainDistance;
    private boolean started;
    private boolean failed;
    private Task baritoneFallback;

    public TungstenFollowTask(Entity entity, double maintainDistance) {
        this.entity = entity;
        this.maintainDistance = maintainDistance;
    }

    @Override
    protected void onStart() {
        started = false;
        failed = false;
        baritoneFallback = null;
        setDebugState("Tungsten follow " + nameOf(entity));
    }

    @Override
    protected Task onTick() {
        if (entity == null || entity.removed || !entity.isAlive()) {
            setDebugState("Tungsten follow target gone");
            return null;
        }
        if (failed) {
            if (baritoneFallback == null) {
                baritoneFallback = new GetToEntityTask(entity, maintainDistance);
            }
            setDebugState("Tungsten follow failed — Baritone fallback");
            return baritoneFallback;
        }
        if (!started) {
            started = true;
            if (!TungstenMovement.requestFollow(entity, maintainDistance)) {
                failed = true;
                setDebugState("Tungsten follow request failed");
            } else {
                setDebugState("Tungsten following " + nameOf(entity));
            }
        }
        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {
        TungstenMovement.cancel();
    }

    @Override
    public boolean isFinished() {
        return entity == null || entity.removed || !entity.isAlive();
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof TungstenFollowTask t) {
            return t.entity == entity && Math.abs(t.maintainDistance - maintainDistance) < 1e-6;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "TungstenFollow " + nameOf(entity) + " d=" + maintainDistance;
    }

    private static String nameOf(Entity e) {
        if (e == null) return "null";
        try {
            return e.getName().getString();
        } catch (Throwable t) {
            return e.toString();
        }
    }
}