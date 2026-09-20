package adris.altoclef.control;

import adris.altoclef.movement.MovementEngineAdapter;
import baritone.api.pathing.goals.Goal;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/**
 * Thin {@link MovementController} that delegates to {@link MovementEngineAdapter}.
 * Keeps adapter static methods as the compatibility implementation while new call
 * sites prefer the injected controller.
 */
public final class AdapterMovementController implements MovementController {

    public static final AdapterMovementController INSTANCE = new AdapterMovementController();

    public AdapterMovementController() {}

    @Override
    public boolean isEngineAvailable() {
        return MovementEngineAdapter.isEngineAvailable();
    }

    @Override
    public String engineDetail() {
        return MovementEngineAdapter.detail();
    }

    @Override
    public boolean ensureGoalAndPath(Goal goal) {
        return MovementEngineAdapter.ensureGoalAndPath(goal);
    }

    @Override
    public boolean ensureGoalAndPathIfInactive(Goal goal) {
        return MovementEngineAdapter.ensureGoalAndPathIfInactive(goal);
    }

    @Override
    public boolean goToBlock(BlockPos pos) {
        return MovementEngineAdapter.goToBlock(pos);
    }

    @Override
    public boolean followEntity(Entity entity, double maintainDistance) {
        return MovementEngineAdapter.followEntity(entity, maintainDistance);
    }

    @Override
    public boolean isPathingOrActive() {
        return MovementEngineAdapter.isPathingOrActive();
    }

    @Override
    public void cancel() {
        MovementEngineAdapter.cancel();
    }

    @Override
    public String statusLine() {
        return MovementEngineAdapter.statusLine();
    }
}
