package adris.altoclef.tasks.speedrun.testrun2.move;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.util.math.BlockPos;

/**
 * Adapter over Miran's Baritone. Does not yolo-bridge: if customGoalProcess
 * throws or path is missing after a beat, we FAIL with a reason.
 */
public final class BaritoneMover implements T2Mover {

    private String last = "";
    private BlockPos lastGoal;

    @Override
    public Result walkTo(BlockPos pos) {
        if (pos == null) return Result.fail("null pos");
        try {
            AltoClef mod = AltoClef.getInstance();
            if (mod == null || mod.getPlayer() == null) return Result.fail("no player");
            if (pos.equals(lastGoal) && busy()) {
                return Result.busy(last);
            }
            if (mod.getClientBaritone() == null) return Result.fail("no baritone");
            mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos));
            lastGoal = pos.toImmutable();
            last = "walk " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            return Result.busy(last);
        } catch (Throwable t) {
            return Result.fail("walk " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    @Override
    public Result swimOut() {
        adris.altoclef.tasks.speedrun.testrun2.core.T2Input.swim();
        last = "swim-out";
        return Result.busy(last);
    }

    @Override
    public Result mineToward(BlockPos pos) {
        return walkTo(pos);
    }

    @Override
    public void cancel() {
        McCompat.cancelPathing();
        McCompat.setMove(false, false);
        lastGoal = null;
        last = "cancel";
    }

    @Override
    public boolean busy() {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getClientBaritone() == null) return false;
        // getPath() is an Optional and so never null: asking it made every goal look permanently busy.
        return mod.getClientBaritone().getPathingBehavior().isPathing();
    }
}
