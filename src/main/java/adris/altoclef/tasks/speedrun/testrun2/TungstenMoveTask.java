package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Baritone travel. Class name kept for callers. */
public class TungstenMoveTask extends Task {

    private final BlockPos target;
    private final double arriveRange;
    private Task inner;

    public TungstenMoveTask(BlockPos target) {
        this(target, 2.5);
    }

    public TungstenMoveTask(BlockPos target, double arriveRange) {
        this.target = target;
        this.arriveRange = arriveRange;
    }

    @Override
    protected void onStart() {
        inner = new GetToBlockTask(target);
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        if (arrived(mod)) return null;
        if (SpeedrunOpt.AVOID_DEEP_WATER) {
            try {
                if (mod.getPlayer().isTouchingWater() || mod.getPlayer().isSubmergedInWater()) {
                    return new WaterBailTask();
                }
            } catch (Throwable ignored) {}
        }
        if (inner == null) inner = new GetToBlockTask(target);
        return inner;
    }

    private boolean arrived(AltoClef mod) {
        return mod.getPlayer().getPos().squaredDistanceTo(Vec3d.ofCenter(target))
                <= arriveRange * arriveRange;
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof TungstenMoveTask o && o.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "move " + target.toShortString();
    }

    @Override
    public boolean isFinished() {
        AltoClef mod = AltoClef.getInstance();
        return mod.getPlayer() != null && arrived(mod);
    }
}
