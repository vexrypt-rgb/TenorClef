package adris.altoclef.tasks.construction.compound;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.ClearLiquidTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceObsidianBucketTask;
import adris.altoclef.tasks.movement.GetWithinRangeOfBlockTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.speedrun.beatgame.BeatMinecraftTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.tasks.speedrun.testrun2.T2Codes;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.*;

/**
 * Build a nether portal by casting each piece with water + lava.
 * <p>
 * Currently the most reliable portal building method.
 */
public class ConstructNetherPortalBucketTask extends Task {

    // Order here matters
    private static final Vec3i[] PORTAL_FRAME = new Vec3i[]{
            // Left side
            new Vec3i(0, 0, -1),
            new Vec3i(0, 1, -1),
            new Vec3i(0, 2, -1),
            // Right side
            new Vec3i(0, 0, 2),
            new Vec3i(0, 1, 2),
            new Vec3i(0, 2, 2),
            // Top
            new Vec3i(0, 3, 0),
            new Vec3i(0, 3, 1),
            // Bottom
            new Vec3i(0, -1, 0),
            new Vec3i(0, -1, 1)
    };

    private static final Vec3i[] PORTAL_INTERIOR = new Vec3i[]{
            //Inside
            new Vec3i(0, 0, 0),
            new Vec3i(0, 1, 0),
            new Vec3i(0, 2, 0),
            new Vec3i(0, 0, 1),
            new Vec3i(0, 1, 1),
            new Vec3i(0, 2, 1),
            //Outside 1
            new Vec3i(1, 0, 0),
            new Vec3i(1, 1, 0),
            new Vec3i(1, 2, 0),
            new Vec3i(1, 0, 1),
            new Vec3i(1, 1, 1),
            new Vec3i(1, 2, 1),
            //Outside 2
            new Vec3i(-1, 0, 0),
            new Vec3i(-1, 1, 0),
            new Vec3i(-1, 2, 0),
            new Vec3i(-1, 0, 1),
            new Vec3i(-1, 1, 1),
            new Vec3i(-1, 2, 1)
    };

    // The "portalable" region includes the portal (1 x 6 x 4 structure) and an outer buffer for its construction and water bullshit.
    // The "portal origin relative to region" corresponds to the portal origin with respect to the "portalable" region (see _portalOrigin).
    // This can only really be explained visually, sorry!
    private static final Vec3i PORTALABLE_REGION_SIZE = new Vec3i(4, 6, 6);
    private static final Vec3i PORTAL_ORIGIN_RELATIVE_TO_REGION = new Vec3i(1, 0, 2);
    private final TimerGame lavaSearchTimer = new TimerGame(5);
    private final MovementProgressChecker progressChecker = new MovementProgressChecker();
    private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(5);
    // Stored here to cache lava blacklist
    private final Task collectLavaTask = TaskCatalogue.getItemTask(Items.LAVA_BUCKET, 1);
    private final TimerGame refreshTimer = new TimerGame(11);
    // Stall recovery while crafting/collecting the 2nd bucket (or first if EarlyOverworld skipped)
    private final TimerGame bucketAcquireTimer = new TimerGame(75);
    private boolean bucketAcquireTiming = false;
    private final TimerGame noFluidProgressTimer = new TimerGame(90);
    private final TimerGame secondBucketIronLogTimer = new TimerGame(15);
    // S208: 2nd-bucket iron stall (fix21: stood in 1-deep water 30s+ at unreachable ore at y-12).
    private final TimerGame secondBucketIronStallTimer = new TimerGame(25);
    private int secondBucketIronLast = -1;
    private Task secondBucketRelocate;
    /** Set when Construct gives up so EarlyOverworld can tear down goToNether and re-acquire. */
    public boolean abortedForReacquire = false;
    private BlockPos portalOrigin = null;
    private Task getToLakeTask = null;
    private BlockPos currentDestroyTarget = null;

    private boolean firstSearch = false;

    @Override
    protected void onStart() {
        currentDestroyTarget = null;
        firstSearch = true;
        lavaSearchTimer.reset();
        refreshTimer.reset();
        bucketAcquireTiming = false;
        bucketAcquireTimer.reset();
        noFluidProgressTimer.reset();
        abortedForReacquire = false;

        AltoClef mod = AltoClef.getInstance();
        mod.getBehaviour().push();

        // Avoid breaking portal frame if we're obsidian.
        // Also avoid placing on the lava + water
        // Also avoid breaking the cast frame
        mod.getBehaviour().avoidBlockBreaking(block -> {
            if (portalOrigin != null) {
                // Don't break frame
                for (Vec3i framePosRelative : PORTAL_FRAME) {
                    BlockPos framePos = portalOrigin.add(framePosRelative);
                    if (block.equals(framePos)) {
                        return mod.getWorld().getBlockState(framePos).getBlock() == Blocks.OBSIDIAN;
                    }
                }
            }
            return false;
        });

        // Protect some used items
        mod.getBehaviour().addProtectedItems(Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);

        progressChecker.reset();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        if (portalOrigin != null) {
            if (mod.getWorld().getBlockState(portalOrigin.up()).getBlock() == Blocks.NETHER_PORTAL) {
                setDebugState("Done constructing nether portal.");
                mod.getBlockScanner().addBlock(Blocks.NETHER_PORTAL, portalOrigin.up());
                return null;
            }
        }
        if (mod.getClientBaritone().getPathingBehavior().isPathing()) {
            progressChecker.reset();
        }
        if (wanderTask.isActive() && !wanderTask.isFinished()) {
            setDebugState("Trying again.");
            progressChecker.reset();
            return wanderTask;
        }

        if (!progressChecker.check(mod)) {
            mod.getClientBaritone().getPathingBehavior().forceCancel();
            if (portalOrigin != null && currentDestroyTarget != null) {
                mod.getBlockScanner().requestBlockUnreachable(portalOrigin);
                mod.getBlockScanner().requestBlockUnreachable(currentDestroyTarget);
                if (mod.getBlockScanner().isUnreachable(portalOrigin) && mod.getBlockScanner().isUnreachable(currentDestroyTarget)) {
                    portalOrigin = null;
                    currentDestroyTarget = null;
                }
                return wanderTask;
            }
        }
        if (refreshTimer.elapsed()) {
            // NEVER refresh while a container/craft screen is open - double-clicking every
            // inventory slot desyncs the handler and causes "Ignoring click in mismatching container".
            if (MinecraftClient.getInstance().currentScreen == null && !mod.getControllerExtras().isBreakingBlock()) {
                Debug.logMessage("Duct tape: Refreshing inventory again just in case");
                mod.getSlotHandler().refreshInventory();
            }
            refreshTimer.reset();
        }

        //If too far, reset.
        if (portalOrigin != null && !portalOrigin.isWithinDistance(mod.getPlayer().getPos(), 2000)) {
            portalOrigin = null;
            currentDestroyTarget = null;
        }

        if (currentDestroyTarget != null) {
            if (!WorldHelper.isSolidBlock(currentDestroyTarget)) {
                currentDestroyTarget = null;
            } else {
                return new DestroyBlockTask(currentDestroyTarget);
            }
        }
        // Get flint & steel if we don't have one
        if (!mod.getItemStorage().hasItem(Items.FLINT_AND_STEEL) && !mod.getItemStorage().hasItem(Items.FIRE_CHARGE)) {
            setDebugState("Getting flint & steel");
            progressChecker.reset();
            return TaskCatalogue.getItemTask(Items.FLINT_AND_STEEL, 1);
        }
        // Bucket gate: EarlyOverworld crafts 1 empty intentionally. Do NOT stall for 2 empties
        // before water/lava ? scoop water with the first, craft the 2nd at the lava lake.
        int emptyBuckets = mod.getItemStorage().getItemCount(Items.BUCKET);
        int waterBuckets = mod.getItemStorage().getItemCount(Items.WATER_BUCKET);
        int lavaBuckets = mod.getItemStorage().getItemCount(Items.LAVA_BUCKET);
        int bucketCount = emptyBuckets + waterBuckets + lavaBuckets;
        if (bucketCount < 1) {
            setDebugState("Getting first bucket");
            progressChecker.reset();
            if (!bucketAcquireTiming) {
                bucketAcquireTiming = true;
                bucketAcquireTimer.reset();
                Debug.logMessage("Construct: acquiring first bucket (have 0)");
            } else if (bucketAcquireTimer.elapsed()) {
                Debug.logMessage("Construct: first bucket acquire stalled - close screens + wander retry");
                StorageHelper.closeScreen();
                bucketAcquireTimer.reset();
                return wanderTask;
            }
            if (mod.getEntityTracker().itemDropped(Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.BUCKET)) {
                return new PickupDroppedItemTask(new ItemTarget(new Item[]{Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.BUCKET}, 1), true);
            }
            return TaskCatalogue.getItemTask(Items.BUCKET, 1);
        }
        bucketAcquireTiming = false;

        // Complementary fluid when we already hold lava XOR water with no empty to scoop.
        if (lavaBuckets > 0 && waterBuckets == 0 && emptyBuckets == 0) {
            setDebugState("Getting water (have lava)");
            progressChecker.reset();
            return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
        }
        // Need 2nd bucket for lava scoop while holding water ? mine iron if short, else craft at lake.
        if (waterBuckets > 0 && lavaBuckets == 0 && emptyBuckets == 0 && portalOrigin != null) {
            int iron = mod.getItemStorage().getItemCount(Items.IRON_INGOT);
            if (iron < 3) {
                setDebugState("Mining iron for 2nd bucket at lava");
                if (secondBucketRelocate != null && secondBucketRelocate.isActive() && !secondBucketRelocate.isFinished()) {
                    return secondBucketRelocate;
                }
                if (iron != secondBucketIronLast) {
                    secondBucketIronLast = iron;
                    secondBucketIronStallTimer.reset();
                } else if (secondBucketIronStallTimer.elapsed()) {
                    secondBucketIronStallTimer.reset();
                    Debug.logWarning("[S208] 2nd-bucket iron stalled 25s at iron=" + iron + " - relocating");
                    secondBucketRelocate = new TimeoutWanderTask(12);
                    return secondBucketRelocate;
                }
                progressChecker.reset();
                if (secondBucketIronLogTimer.elapsed()) {
                    Debug.logMessage("Construct: need iron for 2nd bucket (iron=" + iron + " empty=" + emptyBuckets
                            + " water=" + waterBuckets + " lava=" + lavaBuckets + ")");
                    secondBucketIronLogTimer.reset();
                }
                return TaskCatalogue.getItemTask(Items.IRON_INGOT, 3);
            }
        }

        // No water/lava and no portal site yet for too long ? abort so EarlyOverworld hard-resets Construct.
        boolean hasFluid = waterBuckets > 0 || lavaBuckets > 0;
        if (!hasFluid && portalOrigin == null) {
            if (noFluidProgressTimer.elapsed()) {
                Debug.logMessage("Construct: no water/lava progress - aborting for EarlyOverworld reacquire"
                        + " (empty=" + emptyBuckets + " water=" + waterBuckets + " lava=" + lavaBuckets + ")");
                StorageHelper.closeScreen();
                abortedForReacquire = true;
                hardResetBuildState();
                noFluidProgressTimer.reset();
                return wanderTask;
            }
        } else {
            noFluidProgressTimer.reset();
        }

        boolean needsToLookForPortal = portalOrigin == null;
        if (needsToLookForPortal) {
            progressChecker.reset();
            // Get water before searching, just for convenience.
            if (!mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
                setDebugState("Getting water");
                progressChecker.reset();
                return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
            }

            boolean foundSpot = false;

            if (firstSearch || lavaSearchTimer.elapsed()) {
                firstSearch = false;
                lavaSearchTimer.reset();
                Debug.logMessage("(Searching for lava lake with portalable spot nearby...)");
                BlockPos lavaPos = findLavaLake(mod, mod.getPlayer().getBlockPos());
                if (lavaPos != null) {
                    // We have a lava lake, set our portal origin!
                    BlockPos foundPortalRegion = getPortalableRegion(mod, lavaPos, mod.getPlayer().getBlockPos(), new Vec3i(-1, 0, 0), PORTALABLE_REGION_SIZE, 48);
                    if (foundPortalRegion == null) {
                        Debug.logWarning("Failed to find portalable region nearby. Consider increasing the search timeout range");
                    } else {
                        portalOrigin = foundPortalRegion.add(PORTAL_ORIGIN_RELATIVE_TO_REGION);
                        foundSpot = true;

                        getToLakeTask = new GetWithinRangeOfBlockTask(portalOrigin,7);
                        return getToLakeTask;
                    }
                } else {
                    Debug.logMessage("(lava lake not found)");
                }
            }

            if (!foundSpot) {
                setDebugState("(timeout: Looking for lava lake)");
                return new TimeoutWanderTask();
            }
        }

        if (BeatMinecraftTask.isTaskRunning(mod,getToLakeTask)) {
            return getToLakeTask;
        }

        // We have a portal, now build it.
        for (Vec3i framePosRelative : PORTAL_FRAME) {
            BlockPos framePos = portalOrigin.add(framePosRelative);
            Block frameBlock = mod.getWorld().getBlockState(framePos).getBlock();
            if (frameBlock == Blocks.OBSIDIAN) {
                // Already satisfied, clear water above if need be.
                BlockPos waterCheck = framePos.up();
                if (mod.getWorld().getBlockState(waterCheck).getBlock() == Blocks.WATER && WorldHelper.isSourceBlock(waterCheck, true)) {
                    setDebugState("Clearing water from cast");
                    return new ClearLiquidTask(waterCheck);
                }
                continue;
            }

            // Get lava early so placing it is faster
            if (!mod.getItemStorage().hasItem(Items.LAVA_BUCKET) && frameBlock != Blocks.LAVA) {
                setDebugState("Collecting lava");
                progressChecker.reset();
                return collectLavaTask;
            }

            // We need to place obsidian here.
            if (mod.getBlockScanner().isUnreachable(framePos)) {
                Debug.logMessage("Portal frame unreachable, picking a new lava lake spot");
                portalOrigin = null;
                currentDestroyTarget = null;
                return wanderTask;
            }
            return new PlaceObsidianBucketTask(framePos);
        }

        // Now, clear the inside.
        for (Vec3i offs : PORTAL_INTERIOR) {
            BlockPos p = portalOrigin.add(offs);
            assert MinecraftClient.getInstance().world != null;
            if (!MinecraftClient.getInstance().world.getBlockState(p).isAir()) {
                setDebugState("Clearing inside of portal");
                currentDestroyTarget = p;
                return null;
                //return new DestroyBlockTask(p);
            }
        }

        setDebugState("Flinting and Steeling");
        // Flint and steel it baby
        return new InteractWithBlockTask(new ItemTarget(new Item[]{Items.FLINT_AND_STEEL, Items.FIRE_CHARGE}, 1), Direction.UP, portalOrigin.down(), true);
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef.getInstance().getBehaviour().pop();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ConstructNetherPortalBucketTask;
    }

    @Override
    protected String toDebugString() {
        return "Construct Nether Portal";
    }

    public boolean consumeAbortedForReacquire() {
        if (abortedForReacquire) {
            abortedForReacquire = false;
            return true;
        }
        return false;
    }

    /** Clear portal site + timers so EarlyOverworld retry is not an identical instant loop. */
    public void hardResetBuildState() {
        portalOrigin = null;
        currentDestroyTarget = null;
        getToLakeTask = null;
        firstSearch = true;
        bucketAcquireTiming = false;
        bucketAcquireTimer.reset();
        noFluidProgressTimer.reset();
        lavaSearchTimer.reset();
        refreshTimer.reset();
        progressChecker.reset();
        // Keep abortedForReacquire so EarlyOverworld can observe + consume it
        Debug.logMessage("Construct: hardResetBuildState (portalOrigin cleared)");
    }

    /**
     * Prefer a lava lake near the surface.
     *
     * S165: the nearest lake is often a deep one, and a deep lake means a 1x1 shaft straight
     * down through the dark with no way back up. Two runs lost their entire kit that way —
     * run Q dug 175,187 from y=71 to y=28 and was slain by a zombie at the bottom; run R was
     * shot by a skeleton at y=34. Both had already reached PORTAL.
     *
     * Deep lakes are still allowed, just last: a bare ruined portal is not always available,
     * and a bucket portal is still the most reliable build. Only the ORDER changes.
     */
    private static final int SAFE_LAKE_Y = 40;

    private BlockPos findLavaLake(AltoClef mod, BlockPos playerPos) {
        HashSet<BlockPos> alreadyExplored = new HashSet<>();
        double nearestSqDistance = Double.POSITIVE_INFINITY;
        BlockPos nearestLake = null;
        double deepestFallbackSq = Double.POSITIVE_INFINITY;
        BlockPos deepestFallback = null;
        List<BlockPos> lavas = mod.getBlockScanner().getKnownLocations(Blocks.LAVA);

        if (!lavas.isEmpty()) {
            for (BlockPos pos : lavas) {
                if (alreadyExplored.contains(pos)) continue;
                double sqDist = playerPos.getSquaredDistance(pos);
                // S165: was Math.max(...), which skipped any candidate that could not beat
                // BOTH trackers at once. With two trackers that prunes a perfectly good deep
                // fallback the moment a surface lake is found - and vice versa. Skip only
                // when it can beat neither.
                if (sqDist >= nearestSqDistance && sqDist >= deepestFallbackSq) continue;
                int depth = getNumberOfBlocksAdjacent(alreadyExplored, pos);
                if (depth == 0) continue;
                Debug.logMessage("Found with depth " + depth);
                if (depth < 12) continue;
                if (pos.getY() >= SAFE_LAKE_Y) {
                    if (sqDist < nearestSqDistance) {
                        nearestSqDistance = sqDist;
                        nearestLake = pos;
                    }
                } else if (sqDist < deepestFallbackSq) {
                    deepestFallbackSq = sqDist;
                    deepestFallback = pos;
                }
            }
        }
        if (nearestLake != null) {
            Debug.logMessage("T2 [" + T2Codes.S165_SURFACE_LAKE + "] lava lake at y="
                    + nearestLake.getY() + " (surface, safe)");
            return nearestLake;
        }
        if (deepestFallback != null) {
            Debug.logMessage("T2 [" + T2Codes.S165_SURFACE_LAKE + "] only a deep lava lake at y="
                    + deepestFallback.getY() + " - expect a long dark shaft");
        }
        return deepestFallback;
    }

    private int getNumberOfBlocksAdjacent(HashSet<BlockPos> alreadyExplored, BlockPos start) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);

        int bonus = 0;

        while (!queue.isEmpty()) {
            BlockPos origin = queue.poll();
            if (alreadyExplored.contains(origin)) continue;
            alreadyExplored.add(origin);

            // Base case: We hit a non-full lava block.
            assert MinecraftClient.getInstance().world != null;
            BlockState s = MinecraftClient.getInstance().world.getBlockState(origin);
            if (s.getBlock() != Blocks.LAVA) {
                continue;
            } else {
                // We may not be a full lava block
                if (!s.getFluidState().isStill()) continue;
                int level = s.getFluidState().getLevel();
                //Debug.logMessage("TEST LEVEL: " + level + ", " + height);
                // Only accept FULL SOURCE BLOCKS
                if (level != 8) continue;
            }

            queue.addAll(List.of(origin.north(), origin.south(), origin.east(), origin.west(), origin.up(), origin.down()));

            bonus++;
        }

        return bonus;
    }

    // Get a region that a portal can fit into
    private BlockPos getPortalableRegion(AltoClef mod, BlockPos lava, BlockPos playerPos, Vec3i sizeOffset, Vec3i sizeAllocation, int timeoutRange) {
        Vec3i[] directions = new Vec3i[]{new Vec3i(1, 0, 0), new Vec3i(-1, 0, 0), new Vec3i(0, 0, 1), new Vec3i(0, 0, -1)};

        double minDistanceToPlayer = Double.POSITIVE_INFINITY;
        BlockPos bestPos = null;

        for (Vec3i direction : directions) {

            // Inch along
            for (int offs = 1; offs < timeoutRange; ++offs) {

                Vec3i offset = new Vec3i(direction.getX() * offs, direction.getY() * offs, direction.getZ() * offs);

                boolean found = true;
                boolean solidFound = false;
                // check for collision with lava in box
                // We have an extra buffer to make sure we never break a block NEXT to lava.
                moveAlongLine:
                for (int dx = -1; dx < sizeAllocation.getX() + 1; ++dx) {
                    for (int dz = -1; dz < sizeAllocation.getZ() + 1; ++dz) {
                        for (int dy = -1; dy < sizeAllocation.getY(); ++dy) {
                            BlockPos toCheck = lava.add(offset).add(sizeOffset).add(dx,dy,dz);
                            assert MinecraftClient.getInstance().world != null;
                            BlockState state = MinecraftClient.getInstance().world.getBlockState(toCheck);
                            if (state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.BEDROCK) {
                                found = false;
                                break moveAlongLine;
                            }
                            // Also check for at least 1 solid block for us to place on...
                            if (dy <= 1 && !solidFound && WorldHelper.isSolidBlock(toCheck)) {
                                solidFound = true;
                            }
                        }
                    }
                }
                // Check for solid ground at least somewhere
                if (!solidFound) {
                    break;
                }

                if (found) {
                    BlockPos foundBoxCorner = lava.add(offset).add(sizeOffset);
                    double sqDistance = foundBoxCorner.getSquaredDistance(playerPos);
                    if (sqDistance < minDistanceToPlayer) {
                        minDistanceToPlayer = sqDistance;
                        bestPos = foundBoxCorner;
                    }
                    break;
                }
            }

        }

        return bestPos;
    }
}
