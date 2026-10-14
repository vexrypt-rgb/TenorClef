package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.BotBehaviour;
import adris.altoclef.tasks.movement.CustomBaritoneGoalTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalRunAway;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DragonBreathTracker {
    // Replaced whole on every update and never mutated after: the path planner reads it from its own thread.
    private volatile Set<BlockPos> breathBlocks = Collections.emptySet();

    public void updateBreath(AltoClef mod) {
        List<AreaEffectCloudEntity> clouds = mod.getEntityTracker().getTrackedEntities(AreaEffectCloudEntity.class);
        if (clouds.isEmpty()) {
            breathBlocks = Collections.emptySet();
            return;
        }
        Set<BlockPos> next = new HashSet<>();
        for (AreaEffectCloudEntity cloud : clouds) {
            for (BlockPos bad : WorldHelper.getBlocksTouchingBox(cloud.getBoundingBox())) {
                next.add(bad);
            }
        }
        breathBlocks = next;
    }

    public boolean isTouchingDragonBreath(BlockPos pos) {
        return breathBlocks.contains(pos);
    }

    public Task getRunAwayTask() {
        return new RunAwayFromDragonsBreathTask();
    }

    private class RunAwayFromDragonsBreathTask extends CustomBaritoneGoalTask {

        @Override
        protected void onStart() {
            super.onStart();
            BotBehaviour botBehaviour = AltoClef.getInstance().getBehaviour();

            botBehaviour.push();
            botBehaviour.setBlockPlacePenalty(Double.POSITIVE_INFINITY);
            // do NOT ever wander
            checker = new MovementProgressChecker((int) Float.POSITIVE_INFINITY);
        }

        @Override
        protected void onStop(Task interruptTask) {
            super.onStop(interruptTask);
            AltoClef.getInstance().getBehaviour().pop();
        }

        @Override
        protected Goal newGoal(AltoClef mod) {
            return new GoalRunAway(10, breathBlocks.toArray(BlockPos[]::new));
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof RunAwayFromDragonsBreathTask;
        }

        @Override
        protected String toDebugString() {
            return "ESCAPE Dragons Breath";
        }
    }
}
