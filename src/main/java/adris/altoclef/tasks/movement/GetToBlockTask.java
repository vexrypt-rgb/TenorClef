package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.ITaskRequiresGrounded;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import adris.altoclef.util.time.TimerGame;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

public class GetToBlockTask extends CustomBaritoneGoalTask implements ITaskRequiresGrounded {

    private final BlockPos _position;
    private final boolean _preferStairs;
    private final Dimension _dimension;
    private int finishedTicks = 0;
    private final TimerGame wanderTimer = new TimerGame(2);
    private boolean portalPatientInit = false;

    public GetToBlockTask(BlockPos position, boolean preferStairs) {
        this(position, preferStairs, null);
    }

    public GetToBlockTask(BlockPos position, Dimension dimension) {
        this(position, false, dimension);
    }

    public GetToBlockTask(BlockPos position, boolean preferStairs, Dimension dimension) {
        _dimension = dimension;
        _position = position;
        _preferStairs = preferStairs;
    }

    public GetToBlockTask(BlockPos position) {
        this(position, false);
    }

    /** Phase 2: route travel through Ostinato MovementEngine when present. */
    @Override
    protected boolean useMovementEngine() {
        return true;
    }

    /** True once we have been isFinished for >10s while still being ticked (parent stuck). */
    private boolean staleFinished = false;
    private boolean staleFinishedWarned = false;

    /** Parents should clear/replace this subtask when true. */
    public boolean isStaleFinished() {
        return staleFinished || (isFinished() && finishedTicks > 10 * 20);
    }

    @Override
    protected Task onTick() {
        AltoClef modEarly = AltoClef.getInstance();
        ClientWorld world = modEarly.getWorldKnowledge().getWorld();
        // Post-death reportal often needs 100+ block walks; default 6s progress checker
        // fails during long Baritone calcs and abandons a live portal via wander.
        if (!portalPatientInit && world != null
                && world.getBlockState(_position).getBlock() == Blocks.NETHER_PORTAL) {
            portalPatientInit = true;
            checker = new MovementProgressChecker(40, 0.05, 2.0, 0.001, 5);
        }
        if (portalPatientInit) {
            if (modEarly.getClientBaritone().getPathingBehavior().isPathing()
                    || modEarly.getClientBaritone().getPathingBehavior().ticksRemainingInSegment().isPresent()) {
                checker.reset();
            }
        }
        if (_dimension != null && WorldHelper.getCurrentDimension() != _dimension) {
            staleFinished = false;
            finishedTicks = 0;
            return new DefaultGoToDimensionTask(_dimension);
        }

        if (isFinished()) {
            finishedTicks++;
        } else {
            finishedTicks = 0;
            staleFinished = false;
            staleFinishedWarned = false;
        }
        // Parent keeps calling a finished GetToBlock (e.g. portal entry after dimension change).
        // Do NOT wander forever - idle so parent can observe isFinished/isStaleFinished and replace.
        if (finishedTicks > 10 * 20) {
            staleFinished = true;
            if (!staleFinishedWarned) {
                staleFinishedWarned = true;
                Debug.logWarning("GetToBlock was finished for 10 seconds yet is still being called (stale) at "
                        + _position.toShortString() + " - idling; parent must clear/replace");
            }
            // Phase 4: parent stuck on finished travel — TIMEOUT (not recoverable by this task)
            fail(FailureReason.TIMEOUT, "Stale finished GetToBlock still ticked at " + _position.toShortString(), false);
            return null;
        }
        if (!wanderTimer.elapsed()) {
            return new TimeoutWanderTask();
        }

        return super.onTick();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (_preferStairs) {
            AltoClef.getInstance().getBehaviour().push();
            AltoClef.getInstance().getBehaviour().setPreferredStairs(true);
        }
    }


    @Override
    protected void onStop(Task interruptTask) {
        super.onStop(interruptTask);
        if (_preferStairs) {
            AltoClef.getInstance().getBehaviour().pop();
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof GetToBlockTask task) {
            return task._position.equals(_position) && task._preferStairs == _preferStairs && task._dimension == _dimension;
        }
        return false;
    }

    @Override
    public boolean isFinished() {
        return super.isFinished() && (_dimension == null || _dimension == WorldHelper.getCurrentDimension());
    }

    @Override
    protected String toDebugString() {
        return "Getting to block " + _position + (_dimension != null ? " in dimension " + _dimension : "");
    }


    @Override
    protected Goal newGoal(AltoClef mod) {
        return new GoalBlock(_position);
    }


    @Override
    protected boolean shouldWanderOnFail(AltoClef mod) {
        // Never wander-abandon a live nether portal goal (post-death reportal).
        ClientWorld world = mod.getWorldKnowledge().getWorld();
        return world == null
                || world.getBlockState(_position).getBlock() != Blocks.NETHER_PORTAL;
    }

    @Override
    protected void onWander(AltoClef mod) {
        super.onWander(mod);
        // Never blacklist nether portal blocks — EnterNetherPortalTask goals them, and
        // blacklisting causes post-death GetToBlock thrash (Try 1..4) that abandons a live portal.
        ClientWorld world = mod.getWorldKnowledge().getWorld();
        if (world != null && world.getBlockState(_position).getBlock() == Blocks.NETHER_PORTAL) {
            return;
        }
        mod.getWorldKnowledge().getBlockScanner().requestBlockUnreachable(_position);
    }
}
