package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.movement.TungstenMovement;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.BlockPos;

/**
 * AltoClef Task that drives Tungsten physics A* to a block.
 * If the Tungsten request fails after start, falls back to GetToBlockTask (Baritone).
 */
public class TungstenGotoTask extends Task {

    private final BlockPos target;
    private boolean started;
    private boolean failed;
    private Task baritoneFallback;

    public TungstenGotoTask(BlockPos target) {
        this.target = target;
    }

    @Override
    protected void onStart() {
        started = false;
        failed = false;
        baritoneFallback = null;
        setDebugState("Tungsten goto " + target.toShortString());
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;

        if (failed) {
            if (baritoneFallback == null) {
                baritoneFallback = new GetToBlockTask(target);
            }
            setDebugState("Tungsten failed — Baritone fallback");
            return baritoneFallback;
        }

        if (isFinished()) {
            setDebugState("Tungsten arrived " + target.toShortString());
            return null;
        }

        if (!started) {
            started = true;
            if (!TungstenMovement.requestPathTo(target)) {
                failed = true;
                setDebugState("Tungsten path request failed");
            } else {
                setDebugState("Tungsten pathing to " + target.toShortString());
            }
            return null;
        }

        if (!TungstenMovement.isPathing() && !isFinished()) {
            setDebugState("Tungsten idle — retry path");
            if (!TungstenMovement.requestPathTo(target)) {
                failed = true;
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
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return false;
        BlockPos p = mod.getPlayer().getBlockPos();
        return p.getX() == target.getX()
                && p.getZ() == target.getZ()
                && Math.abs(p.getY() - target.getY()) <= 1;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof TungstenGotoTask t) {
            return t.target.equals(target);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "TungstenGoto " + target.toShortString();
    }
}