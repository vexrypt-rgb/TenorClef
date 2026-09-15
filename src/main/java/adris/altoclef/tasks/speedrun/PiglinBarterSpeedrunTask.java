package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.entity.DoToClosestEntityTask;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.resources.CollectGoldIngotTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Modern speedrun-focused piglin bartering.
 *
 * Goals (in order of importance for 1.16 Any%):
 * 1. Obtain target number of Ender Pearls (default 12–16)
 * 2. Keep a buffer of gold so bartering never stalls
 * 3. Stay neutral (golden helmet)
 * 4. Prefer adult non-baby piglins
 * 5. Pick up useful secondary drops (obsidian, string, fire res, etc.)
 *
 * Notes:
 * - 1.16.1 has better pearl rates than later 1.16.x versions.
 * - We deliberately do not require bastion routing here; this task can run
 *   anywhere piglins exist (Nether Wastes / Crimson Forest preferred).
 */
public class PiglinBarterSpeedrunTask extends Task {

    private final AltoClef mod;
    private final int targetPearls;

    // How many gold ingots we try to keep in inventory while bartering
    private static final int GOLD_BUFFER = 32;

    // Minimum gold before we go mine more
    private static final int GOLD_LOW_THRESHOLD = 8;

    private Task goldTask = null;
    private final TimerGame throwCooldown = new TimerGame(0.4); // prevent spam throwing
    private final TimerGame interactCooldown = new TimerGame(0.3);

    public PiglinBarterSpeedrunTask(AltoClef mod, int targetPearls) {
        this.mod = mod;
        this.targetPearls = targetPearls;
    }

    public PiglinBarterSpeedrunTask(AltoClef mod) {
        this(mod, 14); // solid target for eyes + some pearls for travel
    }

    @Override
    protected void onStart() {
        setDebugState("Piglin bartering – targeting " + targetPearls + " ender pearls");
        // Protect the important items
        mod.getBehaviour().addProtectedItems(
                Items.ENDER_PEARL,
                Items.GOLD_INGOT,
                Items.GOLDEN_HELMET,
                Items.OBSIDIAN,
                Items.CRYING_OBSIDIAN,
                Items.STRING,
                Items.FIRE_CHARGE
        );
    }

    @Override
    protected Task onTick() {
        // Must be in the Nether
        if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
            setDebugState("Need to be in the Nether for bartering");
            return null; // parent task should handle dimension change
        }

        int pearls = StorageHelper.getItemCount(mod, Items.ENDER_PEARL);
        if (pearls >= targetPearls) {
            setDebugState("Have enough pearls (" + pearls + "/" + targetPearls + ")");
            return null;
        }

        // 1. Golden helmet for neutrality
        if (!StorageHelper.isArmorEquipped(mod, Items.GOLDEN_HELMET)) {
            if (StorageHelper.itemInventoryIncludes(mod, Items.GOLDEN_HELMET)) {
                setDebugState("Equipping golden helmet");
                return new EquipArmorTask(Items.GOLDEN_HELMET);
            }
            // Craft one if we have the gold
            if (StorageHelper.getItemCount(mod, Items.GOLD_INGOT) >= 5) {
                setDebugState("Crafting golden helmet");
                return TaskCatalogue.getItemTask(Items.GOLDEN_HELMET, 1);
            }
        }

        // 2. Ensure we have gold to barter
        int gold = StorageHelper.getItemCount(mod, Items.GOLD_INGOT);
        if (gold < GOLD_LOW_THRESHOLD) {
            setDebugState("Low on gold (" + gold + ") – collecting more");
            if (goldTask == null || goldTask.isFinished()) {
                goldTask = new CollectGoldIngotTask(GOLD_BUFFER);
            }
            return goldTask;
        }

        // 3. Find a good piglin and barter
        Optional<Entity> piglin = findBestPiglin();
        if (piglin.isEmpty()) {
            setDebugState("No suitable piglin nearby – exploring / waiting");
            // Simple wander via existing movement helpers would go here;
            // for now just keep the task alive so the parent can decide.
            return null;
        }

        Entity target = piglin.get();
        double distSq = mod.getPlayer().squaredDistanceTo(target);

        // Get close enough
        if (distSq > 4.5 * 4.5) {
            setDebugState("Approaching piglin");
            return new GetToEntityTask(target, 2.5);
        }

        // Throw gold or interact
        return performBarter(target);
    }

    private Optional<Entity> findBestPiglin() {
        // Prefer adult piglins that are not currently aggressive if possible
        Predicate<Entity> isGoodPiglin = e -> {
            if (!(e instanceof PiglinEntity piglin)) return false;
            if (piglin.isBaby()) return false;
            // Avoid ones that are already angry at us if we can
            // (simple check – more sophisticated anger tracking can be added later)
            return true;
        };

        return mod.getEntityTracker().getClosestEntity(mod.getPlayer().getPos(), isGoodPiglin, PiglinEntity.class);
    }

    private Task performBarter(Entity piglin) {
        // Prefer throwing gold on the ground near the piglin (classic speedrun method)
        // or right-clicking with gold in hand.

        if (!throwCooldown.elapsed() && !interactCooldown.elapsed()) {
            setDebugState("Waiting for barter cooldown");
            return null;
        }

        // Make sure gold is in hotbar
        if (!StorageHelper.isItemInHotbar(mod, Items.GOLD_INGOT)) {
            // Force move gold to hotbar – catalogue / slot helpers exist in Altoclef
            setDebugState("Moving gold to hotbar");
            // Fallback: just request the item again so inventory management runs
            return TaskCatalogue.getItemTask(Items.GOLD_INGOT, 1);
        }

        setDebugState("Bartering with piglin (pearls: "
                + StorageHelper.getItemCount(mod, Items.ENDER_PEARL) + "/" + targetPearls + ")");

        // Drop one gold ingot toward the piglin
        if (throwCooldown.elapsed()) {
            // Select gold and drop
            // (Actual implementation uses AltoClef's input / slot helpers)
            // For the skeleton we mark the intention clearly:
            mod.getSlotHandler().forceEquipItem(Items.GOLD_INGOT);
            // Drop the item (Q)
            // In real code this would be: mod.getInputControls().tryPress(Input.DROP);
            throwCooldown.reset();
        }

        // Also try right-click interact as backup
        if (interactCooldown.elapsed()) {
            mod.getSlotHandler().forceEquipItem(Items.GOLD_INGOT);
            // Look at piglin and interact
            // mod.getInputControls().tryPress(Input.CLICK_RIGHT);
            interactCooldown.reset();
        }

        // Continuously pick up any dropped pearls / useful items
        // (EntityTracker + PickupDroppedItemTask is already used heavily in Altoclef)

        return null; // stay in this task until pearls target is met
    }

    @Override
    protected void onStop(Task interruptTask) {
        // nothing special
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof PiglinBarterSpeedrunTask
                && ((PiglinBarterSpeedrunTask) other).targetPearls == this.targetPearls;
    }

    @Override
    protected String toDebugString() {
        return "PiglinBarterSpeedrunTask (target " + targetPearls + " pearls)";
    }

    @Override
    public boolean isFinished() {
        return StorageHelper.getItemCount(mod, Items.ENDER_PEARL) >= targetPearls;
    }
}
