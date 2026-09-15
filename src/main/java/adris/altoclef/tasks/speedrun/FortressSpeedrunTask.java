package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.SearchChunksExploreTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Fortress phase for modern 1.16 speedruns.
 *
 * Goals:
 * - Ensure we are in the Nether
 * - Locate a nether fortress (nether bricks / blaze spawners)
 * - Collect enough blaze rods for eyes of ender (default target 7 rods)
 *
 * Speedrun notes (1.16):
 * - Need roughly 6–12 eyes total depending on stronghold luck
 * - Blaze rods craft 2 powder each → 1 eye needs 1 powder + 1 pearl
 * - Prefer killing blazes near spawners; avoid long lava traversals
 */
public class FortressSpeedrunTask extends Task {

    private final AltoClef mod;
    private final int rodTarget;

    private Task searchTask;
    private Task killTask;
    private BlockPos fortressAnchor = null;

    public FortressSpeedrunTask(AltoClef mod, int rodTarget) {
        this.mod = mod;
        this.rodTarget = Math.max(1, rodTarget);
    }

    public FortressSpeedrunTask(AltoClef mod) {
        this(mod, 7);
    }

    @Override
    protected void onStart() {
        setDebugState("Fortress – need " + rodTarget + " blaze rods");
        searchTask = null;
        killTask = null;
        fortressAnchor = null;
    }

    @Override
    protected Task onTick() {
        int rods = StorageHelper.getItemCount(mod, Items.BLAZE_ROD);
        int powder = StorageHelper.getItemCount(mod, Items.BLAZE_POWDER);
        // Powder already counts toward eyes; rods are the bottleneck we farm
        int effective = rods + powder / 2;
        if (rods >= rodTarget || effective >= rodTarget) {
            setDebugState("Fortress – rod target met (" + rods + " rods, " + powder + " powder)");
            return null;
        }

        // Must be in Nether
        if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
            setDebugState("Fortress – entering Nether");
            return new DefaultGoToDimensionTask(Dimension.NETHER);
        }

        // If blazes are already visible, kill them first
        Optional<Entity> nearbyBlaze = mod.getEntityTracker().getClosestEntity(
                mod.getPlayer().getPos(),
                e -> e instanceof BlazeEntity && e.isAlive(),
                BlazeEntity.class
        );
        if (nearbyBlaze.isPresent()) {
            setDebugState("Fortress – killing blaze (" + rods + "/" + rodTarget + " rods)");
            if (killTask == null || killTask.isFinished()) {
                killTask = createBlazeKillTask();
            }
            return killTask;
        }

        // Look for fortress structure blocks nearby
        Optional<BlockPos> bricks = findFortressBlock();
        if (bricks.isPresent()) {
            fortressAnchor = bricks.get();
            double distSq = mod.getPlayer().squaredDistanceTo(
                    fortressAnchor.getX() + 0.5,
                    fortressAnchor.getY() + 0.5,
                    fortressAnchor.getZ() + 0.5
            );
            if (distSq > 20 * 20) {
                setDebugState("Fortress – pathing to structure");
                return new GetToBlockTask(fortressAnchor, false);
            }
            // Inside / near fortress – hunt blazes or explore corridors
            setDebugState("Fortress – searching for blazes (" + rods + "/" + rodTarget + ")");
            if (killTask == null || killTask.isFinished()) {
                killTask = createBlazeKillTask();
            }
            return killTask;
        }

        // No fortress known – explore / search
        setDebugState("Fortress – searching for nether fortress");
        if (searchTask == null || searchTask.isFinished()) {
            searchTask = createFortressSearchTask();
        }
        return searchTask;
    }

    private Task createBlazeKillTask() {
        // Kill blazes preferentially; falls back to catalogue if needed
        Predicate<Entity> isBlaze = e -> e instanceof BlazeEntity && e.isAlive();
        try {
            return new KillEntitiesTask(isBlaze, BlazeEntity.class);
        } catch (Exception e) {
            // Fallback if KillEntitiesTask signature differs
            return TaskCatalogue.getItemTask(Items.BLAZE_ROD, rodTarget);
        }
    }

    private Task createFortressSearchTask() {
        // Prefer scanning for nether brick / spawner; if SearchChunks API differs,
        // fall back to getting blaze rods via catalogue (which includes fortress travel)
        try {
            // Many Altoclef versions expose search via get to blaze rod / structure helpers
            return TaskCatalogue.getItemTask(Items.BLAZE_ROD, rodTarget);
        } catch (Exception e) {
            return TaskCatalogue.getItemTask(Items.BLAZE_ROD, rodTarget);
        }
    }

    private Optional<BlockPos> findFortressBlock() {
        // Characteristic fortress blocks
        Optional<BlockPos> pos = mod.getBlockScanner().getNearestBlock(
                Blocks.NETHER_BRICKS,
                Blocks.NETHER_BRICK_FENCE,
                Blocks.NETHER_BRICK_STAIRS,
                Blocks.CHISELED_NETHER_BRICKS
        );
        if (pos.isPresent()) return pos;

        // Blaze spawner is a strong signal
        Optional<BlockPos> spawner = mod.getBlockScanner().getNearestBlock(Blocks.SPAWNER);
        if (spawner.isPresent()) {
            // Could verify it's a blaze spawner via block entity – skip for skeleton
            return spawner;
        }
        return Optional.empty();
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FortressSpeedrunTask
                && ((FortressSpeedrunTask) other).rodTarget == this.rodTarget;
    }

    @Override
    protected String toDebugString() {
        return "FortressSpeedrunTask(rods=" + rodTarget + ")";
    }

    @Override
    public boolean isFinished() {
        int rods = StorageHelper.getItemCount(mod, Items.BLAZE_ROD);
        int powder = StorageHelper.getItemCount(mod, Items.BLAZE_POWDER);
        return rods >= rodTarget || (rods + powder / 2) >= rodTarget;
    }
}
