package adris.altoclef.control;

import baritone.api.pathing.goals.Goal;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/**
 * High-value travel controller extracted from the AltoClef god object (Phase 3).
 * <p>
 * Production implementation wraps {@link adris.altoclef.movement.MovementEngineAdapter}
 * (Phase 2). Prefer injecting / calling this over {@code AltoClef.getClientBaritone()}
 * for go-to / follow travel. Mining and builder processes stay on Baritone directly.
 */
public interface MovementController {

    boolean isEngineAvailable();

    String engineDetail();

    boolean ensureGoalAndPath(Goal goal);

    boolean ensureGoalAndPathIfInactive(Goal goal);

    boolean goToBlock(BlockPos pos);

    boolean followEntity(Entity entity, double maintainDistance);

    boolean isPathingOrActive();

    void cancel();

    String statusLine();
}
