package adris.altoclef.tasks.speedrun.bastion;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.container.LootContainerTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Base class for bastion-specific routes.
 *
 * Subclasses implement the optimal path for one bastion type.
 * Common behaviour (gold target, chest looting, exit conditions,
 * blacklist/stale GetToBlock abandon) lives here.
 */
public abstract class BastionRouteTask extends Task {

    protected final AltoClef mod;
    protected final BlockPos bastionOrigin; // approximate center / entry
    protected final int goldTarget;

    /** Cap retries on the same BlockPos before searching elsewhere / abandoning. */
    private static final int MAX_SAME_TARGET_FAILS = 4;
    private static final long ROUTE_HOPELESS_MS = 90_000L;

    private BlockPos lastGoTarget = null;
    private int sameTargetFails = 0;
    private boolean abandoned = false;
    private long routeStartMs = 0L;
    private int goldAtStart = 0;
    private Task activeGoTask = null;

    protected BastionRouteTask(AltoClef mod, BlockPos bastionOrigin, int goldTarget) {
        this.mod = mod;
        this.bastionOrigin = bastionOrigin;
        this.goldTarget = goldTarget;
    }

    public abstract BastionType getType();

    public boolean isAbandoned() {
        return abandoned;
    }
    /**
     * Subclasses return the next concrete action for their route.
     */
    protected abstract Task getRouteTask();

    @Override
    protected Task onTick() {
        if (abandoned) {
            setDebugState(getType() + " route – abandoned (blacklist/stale)");
            return null;
        }

        // Global exit: we already have enough gold
        if (StorageHelper.getItemCount(mod, Items.GOLD_INGOT) >= goldTarget) {
            setDebugState(getType() + " route – gold target reached");
            return null;
        }

        // Gold hopeless for 90s+ with no ingot gain → abandon bastion, let parent barter/fortress
        long now = System.currentTimeMillis();
        int goldNow = StorageHelper.getItemCount(mod, Items.GOLD_INGOT);
        if (routeStartMs > 0 && (now - routeStartMs) > ROUTE_HOPELESS_MS && goldNow <= goldAtStart) {
            abandon("gold hopeless for 90s+ (gold=" + goldNow + ")");
            return null;
        }

        // Clear / replace stale finished GetToBlock (same spirit as nether-entry gate)
        if (activeGoTask instanceof GetToBlockTask gtb && gtb.isStaleFinished()) {
            Debug.logMessage("BastionRoute[" + getType() + "]: stale GetToBlock at "
                    + (lastGoTarget != null ? lastGoTarget.toShortString() : "?") + " – clearing");
            noteTargetFail(lastGoTarget);
            activeGoTask = null;
        }

        // Loot any very close chest first (common to all routes), skip unreachable
        Optional<BlockPos> nearbyChest = findNearbyChest(12);
        if (nearbyChest.isPresent()) {
            BlockPos c = nearbyChest.get();
            if (!mod.getBlockScanner().isUnreachable(c) && !shouldSkipTarget(c)) {
                setDebugState(getType() + " – looting nearby chest");
                return trackGo(new LootContainerTask(c, java.util.Arrays.asList(importantBastionLoot())), c);
            }
        }

        Task next = getRouteTask();
        if (next instanceof GetToBlockTask || next instanceof LootContainerTask) {
            // already tracked inside goToTarget helpers when used; keep activeGoTask if same
            if (activeGoTask == null) {
                activeGoTask = next;
            }
        }
        return next;
    }

    /** Path to a block unless blacklisted / over fail cap; returns null if should abandon search. */
    protected Task goToTarget(BlockPos pos, String label) {
        if (pos == null || abandoned) return null;
        if (mod.getBlockScanner().isUnreachable(pos) || shouldSkipTarget(pos)) {
            noteTargetFail(pos);
            setDebugState(getType() + " – skip unreachable " + pos.toShortString());
            return null;
        }
        setDebugState(label);
        return trackGo(new GetToBlockTask(pos, false), pos);
    }

    private Task trackGo(Task task, BlockPos pos) {
        if (lastGoTarget != null && lastGoTarget.equals(pos)) {
            // same target continuing
        } else {
            lastGoTarget = pos;
            // new target – do not reset sameTargetFails globally; per-target tracked via noteTargetFail
        }
        activeGoTask = task;
        return task;
    }

    private boolean shouldSkipTarget(BlockPos pos) {
        return pos != null && lastGoTarget != null && lastGoTarget.equals(pos) && sameTargetFails >= MAX_SAME_TARGET_FAILS;
    }

    private void noteTargetFail(BlockPos pos) {
        if (pos == null) return;
        if (lastGoTarget != null && lastGoTarget.equals(pos)) {
            sameTargetFails++;
        } else {
            lastGoTarget = pos;
            sameTargetFails = 1;
        }
        Debug.logMessage("BastionRoute[" + getType() + "]: target fail "
                + pos.toShortString() + " (" + sameTargetFails + "/" + MAX_SAME_TARGET_FAILS + ")");
        if (sameTargetFails >= MAX_SAME_TARGET_FAILS) {
            // permanently skip this pos via scanner blacklist
            mod.getBlockScanner().requestBlockUnreachable(pos, 0);
            if (pos.equals(bastionOrigin) || sameTargetFails >= MAX_SAME_TARGET_FAILS + 2) {
                abandon("same target failed " + sameTargetFails + "x: " + pos.toShortString());
            }
        }
    }

    protected void abandon(String reason) {
        if (abandoned) return;
        abandoned = true;
        activeGoTask = null;
        Debug.logMessage("BastionRoute[" + getType() + "]: ABANDON – " + reason);
        setDebugState(getType() + " abandoned: " + reason);
    }

    protected Optional<BlockPos> findNearbyChest(double maxDist) {
        Optional<BlockPos> chest = mod.getBlockScanner().getNearestBlock(Blocks.CHEST, Blocks.BARREL);
        if (chest.isEmpty()) return Optional.empty();
        BlockPos pos = chest.get();
        if (mod.getBlockScanner().isUnreachable(pos)) return Optional.empty();
        if (mod.getPlayer().squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDist * maxDist) {
            return Optional.empty();
        }
        return Optional.of(pos);
    }

    protected net.minecraft.item.Item[] importantBastionLoot() {
        return new net.minecraft.item.Item[]{
                Items.GOLD_INGOT,
                Items.GOLD_NUGGET,
                Items.GOLDEN_SWORD,
                Items.GOLDEN_AXE,
                Items.GOLDEN_HELMET,
                Items.GOLDEN_CHESTPLATE,
                Items.GOLDEN_LEGGINGS,
                Items.GOLDEN_BOOTS,
                Items.GOLDEN_APPLE,
                Items.ENCHANTED_GOLDEN_APPLE,
                Items.IRON_INGOT,
                Items.DIAMOND,
                Items.ANCIENT_DEBRIS,
                Items.CRYING_OBSIDIAN,
                Items.OBSIDIAN,
                Items.NETHERITE_SCRAP,
                Items.ARROW,
                Items.STRING
        };
    }

    @Override
    protected void onStart() {
        abandoned = false;
        sameTargetFails = 0;
        lastGoTarget = null;
        activeGoTask = null;
        routeStartMs = System.currentTimeMillis();
        goldAtStart = StorageHelper.getItemCount(mod, Items.GOLD_INGOT);
        setDebugState("Starting " + getType() + " bastion route");
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BastionRouteTask
                && ((BastionRouteTask) other).getType() == this.getType()
                && ((BastionRouteTask) other).bastionOrigin.equals(this.bastionOrigin);
    }

    @Override
    protected String toDebugString() {
        return "BastionRoute[" + getType() + (abandoned ? "/abandoned" : "") + "]";
    }

    @Override
    public boolean isFinished() {
        return abandoned || StorageHelper.getItemCount(mod, Items.GOLD_INGOT) >= goldTarget;
    }
}
