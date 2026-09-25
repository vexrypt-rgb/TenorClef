package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.movement.TungstenMovement;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.BlockPos;

/**
 * AltoClef Task that drives Tungsten physics A* to a block.
 * Falls back to GetToBlockTask (Baritone) only on idle/hard timeout — not on brief
 * pathfinder idle while PathExecutor is still running.
 */
public class TungstenGotoTask extends Task {

    /** Idle timeout: cancel to Baritone only if elapsed and !isPathing(). */
    private static final long FALLBACK_AFTER_MS = 25000L;

    private final BlockPos target;
    private boolean started;
    private boolean failed;
    private Task baritoneFallback;
    private long startedAtMs;

    public TungstenGotoTask(BlockPos target) {
        this.target = target;
    }

    @Override
    protected void onStart() {
        started = false;
        failed = false;
        baritoneFallback = null;
        startedAtMs = 0L;
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
            startedAtMs = System.currentTimeMillis();
            if (!TungstenMovement.requestPathTo(target)) {
                failed = true;
                setDebugState("Tungsten path request failed");
            } else {
                setDebugState("Tungsten pathing to " + target.toShortString());
            }
            return null;
        }

        long elapsed = System.currentTimeMillis() - startedAtMs;

        // Hard timeout always cancels (3x idle window).
        if (elapsed > FALLBACK_AFTER_MS * 3) {
            Debug.logMessage("TungstenGoto hard-timeout after " + elapsed + "ms — Baritone fallback");
            failed = true;
            TungstenMovement.cancel();
            setDebugState("Tungsten hard-timeout — Baritone fallback");
            return null;
        }

        // Idle-timeout only: elapsed past window AND nothing is pathing (pathfinder OR executor).
        // Never cancel solely because pathfinder went idle while executor is still running —
        // isPathing() already treats a running executor as pathing.
        if (elapsed > FALLBACK_AFTER_MS && !TungstenMovement.isPathing()) {
            Debug.logMessage("TungstenGoto idle-timeout after " + elapsed + "ms (!isPathing) — Baritone fallback");
            failed = true;
            TungstenMovement.cancel();
            setDebugState("Tungsten idle-timeout — Baritone fallback");
            return null;
        }

        // Pathfinder may briefly go idle between search end and executor start; do not
        // treat that as failure. Retry only when truly idle and still within windows.
        if (!TungstenMovement.isPathing() && !isFinished() && elapsed <= FALLBACK_AFTER_MS) {
            setDebugState("Tungsten idle — retry path");
            if (!TungstenMovement.requestPathTo(target)) {
                // Keep trying until idle-timeout; do not fail immediately on one reject.
                setDebugState("Tungsten retry pending");
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
