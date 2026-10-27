package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.storage.ContainerCache;
import adris.altoclef.trackers.storage.ContainerType;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Optional;

/**
 * Opens a STORAGE container and does whatever you want inside of it
 */
public abstract class AbstractDoToStorageContainerTask extends Task {

    private ContainerType currentContainerType = null;

    @Override
    protected void onStart() {

    }

    @Override
    protected Task onTick() {
        Optional<BlockPos> containerTarget = getContainerTarget();

        AltoClef mod = AltoClef.getInstance();
        // No container found
        if (containerTarget.isEmpty()) {
            setDebugState("Wandering");
            currentContainerType = null;
            return onSearchWander();
        }

        BlockPos targetPos = containerTarget.get();

        // We're open
        if (currentContainerType != null && ContainerType.screenHandlerMatches(currentContainerType)) {

            Optional<ContainerCache> cache = mod.getItemStorage().getContainerAtPosition(targetPos);
            if (cache.isPresent()) {
                return onContainerOpenSubtask(mod, cache.get());
            }
        }

        // Get to the container
        if (mod.getChunkTracker().isChunkLoaded(targetPos)) {
            Block type = mod.getWorld().getBlockState(targetPos).getBlock();
            currentContainerType = ContainerType.getFromBlock(type);
        }
        if (WorldHelper.isChest(targetPos) && WorldHelper.isSolidBlock(targetPos.up()) && WorldHelper.canBreak(targetPos.up())) {
            setDebugState("Clearing block above chest");
            return new DestroyBlockTask(targetPos.up());
        }
        setDebugState("Opening container: " + targetPos.toShortString());
        // Ostinato: run52. The no-direction interact stands in the block above the barrel. A ceiling
        // barrel (42,72,140) never opened, so the fill timed out. Click an open face instead.
        Direction face = openFace(mod, targetPos);
        if (face != null) {
            return new InteractWithBlockTask(adris.altoclef.util.ItemTarget.EMPTY, face, targetPos, baritone.api.utils.input.Input.CLICK_RIGHT, false, false);
        }
        return new InteractWithBlockTask(targetPos);
    }

    @Override
    protected void onStop(Task interruptTask) {

    }

    // Ostinato: run52. A side with room to stand, else the underside of a ceiling container.
    private static Direction openFace(AltoClef mod, BlockPos pos) {
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos n = pos.offset(d);
            // Ostinato: run53. Was open(n) && open(n.down()): n.down() is the floor beside a floor barrel, so no side ever
            // qualified and the no-direction fallback ran. A stand cell has feet+head open and something solid below.
            if (open(mod, n) && open(mod, n.up()) && !open(mod, n.down())) return d;
            if (open(mod, n) && open(mod, n.down()) && !open(mod, n.down(2))) return d;
        }
        if (open(mod, pos.down()) && open(mod, pos.down(2))) return Direction.DOWN;
        return null;
    }

    private static boolean open(AltoClef mod, BlockPos p) {
        BlockState s = mod.getWorld().getBlockState(p);
        return s.isAir() || s.getCollisionShape(mod.getWorld(), p).isEmpty();
    }

    protected abstract Optional<BlockPos> getContainerTarget();

    protected abstract Task onContainerOpenSubtask(AltoClef mod, ContainerCache containerCache);

    // Virtual
    // TODO: Interface this
    protected Task onSearchWander() {
        return new TimeoutWanderTask();
    }
}
