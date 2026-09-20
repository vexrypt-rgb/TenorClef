package adris.altoclef.control;

import baritone.api.pathing.goals.Goal;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/**
 * Recording fake for {@link MovementController} (Phase 3 unit tests / examples).
 * Compiles with the Minecraft/Baritone classpath (e.g. {@code :1.21.1:testClasses}).
 */
public final class FakeMovementController implements MovementController {

    public int cancelCount;
    public int ensureGoalCount;
    public int followCount;
    public boolean pathing;
    public boolean engineAvailable;
    public Goal lastGoal;
    public Entity lastFollowEntity;
    public double lastFollowDistance;

    @Override
    public boolean isEngineAvailable() {
        return engineAvailable;
    }

    @Override
    public String engineDetail() {
        return engineAvailable ? "fake-engine" : "fake-unavailable";
    }

    @Override
    public boolean ensureGoalAndPath(Goal goal) {
        ensureGoalCount++;
        lastGoal = goal;
        pathing = goal != null;
        return goal != null;
    }

    @Override
    public boolean ensureGoalAndPathIfInactive(Goal goal) {
        if (pathing) {
            return true;
        }
        return ensureGoalAndPath(goal);
    }

    @Override
    public boolean goToBlock(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        pathing = true;
        ensureGoalCount++;
        return true;
    }

    @Override
    public boolean followEntity(Entity entity, double maintainDistance) {
        followCount++;
        lastFollowEntity = entity;
        lastFollowDistance = maintainDistance;
        pathing = entity != null;
        return entity != null;
    }

    @Override
    public boolean isPathingOrActive() {
        return pathing;
    }

    @Override
    public void cancel() {
        cancelCount++;
        pathing = false;
    }

    @Override
    public String statusLine() {
        return pathing ? "FAKE:PATHING" : "FAKE:IDLE";
    }
}
