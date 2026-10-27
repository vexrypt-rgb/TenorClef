package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.tasks.CraftInInventoryTask;
import adris.altoclef.tasks.DoToClosestBlockTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.container.CraftInTableTask;
import adris.altoclef.tasks.container.SmeltInFurnaceTask;
import adris.altoclef.tasks.container.SmeltInSmokerTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.util.SmeltTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.slots.SmokerSlot;
import adris.altoclef.util.time.TimerGame;
//#if MC < 260000
import net.minecraft.block.*;
//#else
//$$ import net.minecraft.world.level.block.BeetrootBlock;
//$$ import net.minecraft.world.level.block.Block;
//$$ import net.minecraft.world.level.block.state.BlockState;
//$$ import net.minecraft.world.level.block.Blocks;
//$$ import net.minecraft.world.level.block.CarrotBlock;
//$$ import net.minecraft.world.level.block.CropBlock;
//$$ import net.minecraft.world.level.block.PotatoBlock;
//#endif
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SlimeEntity;
//#if MC < 260000
import net.minecraft.entity.passive.*;
//#else
//$$ import net.minecraft.world.entity.animal.chicken.Chicken;
//$$ import net.minecraft.world.entity.animal.fish.Cod;
//$$ import net.minecraft.world.entity.animal.cow.Cow;
//$$ import net.minecraft.world.entity.animal.cow.AbstractCow;
//$$ import net.minecraft.world.entity.animal.fish.AbstractFish;
//$$ import net.minecraft.world.entity.animal.pig.Pig;
//$$ import net.minecraft.world.entity.animal.rabbit.Rabbit;
//$$ import net.minecraft.world.entity.animal.fish.Salmon;
//$$ import net.minecraft.world.entity.animal.sheep.Sheep;
//#endif
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SmokerScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class CollectFoodTask extends Task {


    // Represents order of preferred mobs to least preferred
    public static final CookableFoodTarget[] COOKABLE_FOODS = new CookableFoodTarget[]{
            new CookableFoodTarget("beef", CowEntity.class),
            new CookableFoodTarget("porkchop", PigEntity.class),
            new CookableFoodTarget("chicken", ChickenEntity.class),
            new CookableFoodTarget("mutton", SheepEntity.class),
            new CookableFoodTarget("rabbit", RabbitEntity.class),
            // S308: fish count too (island / ocean spawns with no land animals). Scored lower below.
            new CookableFoodTargetFish("cod", CodEntity.class),
            new CookableFoodTargetFish("salmon", SalmonEntity.class)
    };

    public static final Item[] ITEMS_TO_PICK_UP = new Item[]{
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.GOLDEN_APPLE,
            Items.GOLDEN_CARROT,
            Items.BREAD,
            Items.BAKED_POTATO,
            Items.DRIED_KELP
    };

    public static final CropTarget[] CROPS = new CropTarget[]{
            new CropTarget(Items.WHEAT, Blocks.WHEAT),
            new CropTarget(Items.CARROT, Blocks.CARROTS)
    };

    private final double unitsNeeded;
    private final TimerGame checkNewOptionsTimer = new TimerGame(10);
    // S232: was a final null smoker task with the cook loop commented out, so raw meat was
    // never cooked and callers counting real food score hunted forever.
    private Task smeltTask = null;
    // S262: s260t sat on "Cooking..." 40s+ (never reached a furnace, wandered into water).
    private long smeltStartMs, cookBanUntilMs;
    // S303: s300t looped 7+ min on a cold-ocean island: the furnace needs cobble, the only stone was
    // under the sea, so every dig-down flooded -> water bail -> retry. After two failed cooks, stop
    // cooking for 10 min and count raw meat at its raw value so the bot just eats it.
    // S310: set while chasing fish so the water-stall escape (T2Solve S102) leaves the swim alone.
    public static volatile long fishingUntilMs;
    private int cookFailures, lastCookedCount = Integer.MAX_VALUE;
    private static volatile long rawOkUntilMs;
    private static boolean rawOk() { return System.currentTimeMillis() < rawOkUntilMs; }
    private Task currentResourceTask = null;

    public CollectFoodTask(double unitsNeeded) {
        this.unitsNeeded = unitsNeeded;
    }

    private static double getFoodPotential(ItemStack food) {
        if (food == null) return 0;
        int count = food.getCount();
        if (count <= 0) return 0;
        for (CookableFoodTarget cookable : COOKABLE_FOODS) {
            if (food.getItem() == cookable.getRaw()) {
                assert ItemVer.getFoodComponent(cookable.getCooked()) != null;
                Item counted = rawOk() ? cookable.getRaw() : cookable.getCooked();
                return count * ItemVer.getFoodComponent(counted).getHunger();
            }
        }

        //bread logic
        assert ItemVer.getFoodComponent( Items.BREAD) != null;

        if (food.getItem().equals(Items.HAY_BLOCK)) {
            return 3* ItemVer.getFoodComponent(Items.BREAD).getHunger()*count;
        }
        if (food.getItem().equals(Items.WHEAT)) {
            return (double) (ItemVer.getFoodComponent(Items.BREAD).getHunger() * count) /3;
        }

        // We're just an ordinary item.
        if (ItemVer.isFood(food.getItem())) {
            assert ItemVer.getFoodComponent(food.getItem()) != null;
            return count * ItemVer.getFoodComponent(food.getItem()).getHunger();
        }
        return 0;
    }

    // Gets the units of food if we were to convert all of our raw resources to food.
    @SuppressWarnings("RedundantCast")
    public static double calculateFoodPotential(AltoClef mod) {
        double potentialFood = 0;
        for (ItemStack food : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            potentialFood += getFoodPotential(food);
        }
        int potentialBread = (int) (mod.getItemStorage().getItemCount(Items.WHEAT) / 3) + mod.getItemStorage().getItemCount(Items.HAY_BLOCK) * 3;
        potentialFood += Objects.requireNonNull(ItemVer.getFoodComponent( Items.BREAD)).getHunger() * potentialBread;
        // S302: raw kelp smelts 1:1 into dried kelp (last-resort island food).
        if (!rawOk()) potentialFood += Objects.requireNonNull(ItemVer.getFoodComponent(Items.DRIED_KELP)).getHunger() * mod.getItemStorage().getItemCount(Items.KELP);
        // Check smelting
        ScreenHandler screen = mod.getPlayer().currentScreenHandler;
        if (screen instanceof SmokerScreenHandler) {
            potentialFood += getFoodPotential(StorageHelper.getItemStackInSlot(SmokerSlot.INPUT_SLOT_MATERIALS));
            potentialFood += getFoodPotential(StorageHelper.getItemStackInSlot(SmokerSlot.OUTPUT_SLOT));
        }
        return potentialFood;
    }

    @Override
    protected void onStart() {
        AltoClef mod = AltoClef.getInstance();

        mod.getBehaviour().push();
        // Protect ALL food
        mod.getBehaviour().addProtectedItems(ITEMS_TO_PICK_UP);

        // Allow us to consume food.
        /*
        for (CookableFoodTarget food : COOKABLE_FOODS)
            mod.getBehaviour().addProtectedItems(food.getRaw(), food.getCooked());
            mod.getBehaviour().addProtectedItems(crop.cropItem);
        }
         */
        mod.getBehaviour().addProtectedItems(Items.HAY_BLOCK, Items.SWEET_BERRIES, Items.KELP);
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        blackListChickenJockeys(mod);

        List<BlockPos> haysPos = mod.getBlockScanner().getKnownLocations(Blocks.HAY_BLOCK);
        for (BlockPos HaysPos : haysPos) {
            BlockPos haysUpPos = HaysPos.up();
            if (mod.getWorld().getBlockState(haysUpPos).getBlock() == Blocks.CARVED_PUMPKIN) {
                Debug.logMessage("Blacklisting pillage hay bales.");
                mod.getBlockScanner().requestBlockUnreachable(HaysPos, 0);
            }
        }
        // If we were previously smelting, keep on smelting.
        // S309: the 30s cap counted from the start, so s305t pulled one cooked item (10s each) and
        // left with the rest raw. Measure 30s without a new cooked item instead.
        if (smeltTask != null) {
            int cooked = mod.getItemStorage().getItemCount(Items.DRIED_KELP);
            for (CookableFoodTarget c : COOKABLE_FOODS) cooked += mod.getItemStorage().getItemCount(c.getCooked());
            if (cooked > lastCookedCount) smeltStartMs = System.currentTimeMillis();
            lastCookedCount = cooked;
        }
        if (smeltTask != null && smeltTask.isActive() && !smeltTask.isFinished()
                && System.currentTimeMillis() - smeltStartMs > 30_000) {
            Debug.logMessage("S262 cooking timed out after 30s - keeping raw food");
            smeltTask = null;
            cookBanUntilMs = System.currentTimeMillis() + 90_000;
            if (++cookFailures >= 2) {
                Debug.logMessage("S303 cooking failed twice - eating raw food for 10 min");
                cookBanUntilMs = rawOkUntilMs = System.currentTimeMillis() + 600_000;
                cookFailures = 0;
            }
        }
        if (smeltTask != null && smeltTask.isActive() && !smeltTask.isFinished()) {
            // TODO: If we don't have cooking materials, cancel.
            setDebugState("Cooking...");
            return smeltTask;
        }

        if (checkNewOptionsTimer.elapsed()) {
            // Try a new resource task
            checkNewOptionsTimer.reset();
            currentResourceTask = null;
        }

        // S272: the cached-task early return below skipped hayStalled() forever (s268t: 25 min on one bale).
        if (currentResourceTask != null && "Collecting Hay".equals(getDebugState()) && hayStalled(mod)) {
            currentResourceTask = null;
        }
        if (currentResourceTask != null && currentResourceTask.isActive() && !currentResourceTask.isFinished() && !currentResourceTask.thisOrChildAreTimedOut()) {
            return currentResourceTask;
        }

        // Calculate potential
        double potentialFood = calculateFoodPotential(mod);
        if (potentialFood >= unitsNeeded) {
            // Convert our raw foods
            // PLAN:
            // - If we have hay/wheat, make it into bread
            // - If we have raw foods, smelt all of them

            // Convert Hay+Wheat -> Bread.
            // A target of 99999999 never finishes: CraftInTableTask waits until that many
            // bread exist, MoveItemToSlot warns every tick, and the food score stays put.
            // Craft only the batch the inventory can already pay for.
            int wheat = mod.getItemStorage().getItemCount(Items.WHEAT);
            if (wheat >= 3) {
                setDebugState("Crafting Bread");
                int have = mod.getItemStorage().getItemCount(Items.BREAD);
                int make = wheat / 3;
                Item[] w = new Item[]{Items.WHEAT};
                Item[] o = null;
                currentResourceTask = new CraftInTableTask(new RecipeTarget(Items.BREAD, have + make, CraftingRecipe.newShapedRecipe("bread", new Item[][]{w, w, w, o, o, o, o, o, o}, 1)), false, false);
                return currentResourceTask;
            }
            int hay = mod.getItemStorage().getItemCount(Items.HAY_BLOCK);
            if (hay >= 1) {
                setDebugState("Crafting Wheat");
                int haveWheat = mod.getItemStorage().getItemCount(Items.WHEAT);
                Item[] o = null;
                currentResourceTask = new CraftInInventoryTask(new RecipeTarget(Items.WHEAT, haveWheat + hay * 9, CraftingRecipe.newShapedRecipe("wheat", new Item[][]{new Item[]{Items.HAY_BLOCK}, o, o, o}, 9)), false, false);
                return currentResourceTask;
            }
            // Convert raw foods -> cooked foods
            int kelp = mod.getItemStorage().getItemCount(Items.KELP);
            if (kelp > 0 && System.currentTimeMillis() >= cookBanUntilMs) {
                setDebugState("Smelting " + kelp + " kelp");
                smeltStartMs = System.currentTimeMillis();
                currentResourceTask = smeltTask = new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(Items.DRIED_KELP, kelp + mod.getItemStorage().getItemCount(Items.DRIED_KELP)), new ItemTarget(Items.KELP, kelp)));
                return currentResourceTask;
            }

            if (System.currentTimeMillis() >= cookBanUntilMs) for (CookableFoodTarget cookable : COOKABLE_FOODS) {
                int rawCount = mod.getItemStorage().getItemCount(cookable.getRaw());
                if (rawCount > 0) {
                    int toSmelt = rawCount + mod.getItemStorage().getItemCount(cookable.getCooked());
                    setDebugState("Cooking " + rawCount + " " + cookable.rawFood);
                    smeltStartMs = System.currentTimeMillis();
                    smeltTask = new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(cookable.getCooked(), toSmelt), new ItemTarget(cookable.getRaw(), rawCount)));
                    return smeltTask;
                }
            }
        } else {
            // Pick up food items from ground
            for (Item item : ITEMS_TO_PICK_UP) {
                Task t = this.pickupTaskOrNull(mod, item);
                if (t != null) {
                    setDebugState("Picking up Food: " + item.getTranslationKey());
                    currentResourceTask = t;
                    return currentResourceTask;
                }
            }
            // Pick up raw/cooked foods on ground
            for (CookableFoodTarget cookable : COOKABLE_FOODS) {
                Task t = this.pickupTaskOrNull(mod, cookable.getRaw(), 20);
                if (t == null) t = this.pickupTaskOrNull(mod, cookable.getCooked(), 40);
                if (t != null) {
                    setDebugState("Picking up Cookable food");
                    currentResourceTask = t;
                    return currentResourceTask;
                }
            }
            // Hay blocks
            Task hayTaskBlock = this.pickupBlockTaskOrNull(mod, Blocks.HAY_BLOCK, Items.HAY_BLOCK, 300);
            if (hayTaskBlock != null && hayStalled(mod)) hayTaskBlock = null;
            if (hayTaskBlock != null) {
                setDebugState("Collecting Hay");
                currentResourceTask = hayTaskBlock;
                return currentResourceTask;
            }
            // Crops
            for (CropTarget target : CROPS) {
                // If crops are nearby. Do not replant cause we don't care.
                Task t = pickupBlockTaskOrNull(mod, target.cropBlock, target.cropItem, (blockPos -> {
                    BlockState s = mod.getWorld().getBlockState(blockPos);
                    Block b = s.getBlock();
                    if (b instanceof CropBlock) {
                        boolean isWheat = !(b instanceof PotatoesBlock || b instanceof CarrotsBlock || b instanceof BeetrootsBlock);
                        if (isWheat) {
                            // Chunk needs to be loaded for wheat maturity to be checked.
                            if (!mod.getChunkTracker().isChunkLoaded(blockPos)) {
                                return false;
                            }
                            // Prune if we're not mature/fully grown wheat.
                            CropBlock crop = (CropBlock) b;
                            return crop.isMature(s);
                        }
                    }
                    // Unbreakable.
                    return WorldHelper.canBreak(blockPos);
                    // We're not wheat so do NOT reject.
                }), 96);
                if (t != null) {
                    setDebugState("Harvesting " + target.cropItem.getTranslationKey());
                    currentResourceTask = t;
                    return currentResourceTask;
                }
            }
            // Cooked foods
            double bestScore = 0;
            Entity bestEntity = null;
            Item bestRawFood = null;
            Predicate<Entity> notBaby = entity -> entity instanceof LivingEntity livingEntity && !livingEntity.isBaby();

            for (CookableFoodTarget cookable : COOKABLE_FOODS) {
                if (!mod.getEntityTracker().entityFound(cookable.mobToKill)) continue;
                Optional<Entity> nearest = mod.getEntityTracker().getClosestEntity(mod.getPlayer().getPos(),notBaby ,cookable.mobToKill);
                if (nearest.isEmpty()) continue; // ?? This crashed once?
                // S310: s306t chased fish down to y=11 and got stuck; only hunt fish near the surface.
                if (cookable.isFish() && nearest.get().getY() < 54) continue;
                // S355: s354o hunted cod in open ocean and a trident Drowned killed it. No fishing near drowned.
                if (cookable.isFish() && mod.getEntityTracker().getTrackedEntities(net.minecraft.entity.mob.DrownedEntity.class)
                        .stream().anyMatch(d -> d.isAlive() && d.distanceTo(nearest.get()) < 32)) continue;
                int hungerPerformance = cookable.getCookedUnits();
                double sqDistance = nearest.get().squaredDistanceTo(mod.getPlayer());
                double score = (double) 100 * hungerPerformance / (sqDistance);
                if (cookable.isFish()) {
                    // S308: swimming after fish is slow; only pick them when no land animal scores higher.
                    score *= 0.3;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestEntity = nearest.get();
                    bestRawFood = cookable.getRaw();
                }
            }
            if (bestEntity != null) {
                setDebugState("Killing " + bestEntity.getType().getTranslationKey());
                if (bestEntity instanceof net.minecraft.entity.passive.FishEntity) fishingUntilMs = System.currentTimeMillis() + 3_000;
                currentResourceTask = killTaskOrNull(bestEntity, notBaby, bestRawFood);
                return currentResourceTask;
            }

            // Sweet berries (separate from crops because they should have a lower priority than everything else cause they suck)
            Task berryPickup = pickupBlockTaskOrNull(mod, Blocks.SWEET_BERRY_BUSH, Items.SWEET_BERRIES, 96);
            if (berryPickup != null) {
                setDebugState("Getting sweet berries (no better foods are present)");
                currentResourceTask = berryPickup;
                return currentResourceTask;
            }

            // S302: last resort (e.g. stranded on an island with no animals) -- harvest kelp, then smelt it
            // into dried kelp once the potential covers what we need (handled in the branch above).
            Task kelpPickup = pickupBlockTaskOrNull(mod, Blocks.KELP_PLANT, Items.KELP, 64);
            if (kelpPickup == null) kelpPickup = pickupBlockTaskOrNull(mod, Blocks.KELP, Items.KELP, 64);
            if (kelpPickup != null) {
                setDebugState("Harvesting kelp (last-resort food)");
                currentResourceTask = kelpPickup;
                return currentResourceTask;
            }
        }

        // Look for food.
        setDebugState("Searching...");
        return new TimeoutWanderTask();
    }

    static void blackListChickenJockeys(AltoClef mod) {
        if (mod.getEntityTracker().entityFound(ChickenEntity.class)) {
            Optional<Entity> chickens = mod.getEntityTracker().getClosestEntity(ChickenEntity.class);
            if (chickens.isPresent()) {
                Iterable<Entity> entities = mod.getWorld().getEntities();
                for (Entity entity : entities) {
                    if (entity instanceof HostileEntity || entity instanceof SlimeEntity) {
                        if (chickens.get().hasPassenger(entity)) {
                            if (mod.getEntityTracker().isEntityReachable(entity)) {
                                Debug.logMessage("Blacklisting chicken jockey.");
                                mod.getEntityTracker().requestEntityUnreachable(chickens.get());
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef.getInstance().getBehaviour().pop();
    }

    @Override
    public boolean isFinished() {
        return StorageHelper.calculateInventoryFoodScore() >= unitsNeeded;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof CollectFoodTask task) {
            return task.unitsNeeded == unitsNeeded;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Collect " + unitsNeeded + " units of food.";
    }

    /**
     * Returns a task that mines a block and picks up its output.
     * Returns null if task cannot reasonably run.
     */
    // S270: s266t stood still "Collecting Hay" for ~10 min (DestroyBlockTask made no progress,
    // S200 wander reset it each time) until a zombie killed it. If the nearest bale has not
    // yielded hay in 30s, mark it unreachable so food collection moves on.
    private BlockPos hayTarget;
    private long hayTargetSinceMs;
    private int hayCountAtTarget;

    private boolean hayStalled(AltoClef mod) {
        Optional<BlockPos> near = mod.getBlockScanner().getNearestBlock(mod.getPlayer().getPos(), WorldHelper::canBreak, Blocks.HAY_BLOCK);
        if (near.isEmpty()) return false;
        int have = mod.getItemStorage().getItemCount(Items.HAY_BLOCK);
        long now = System.currentTimeMillis();
        if (!near.get().equals(hayTarget) || have > hayCountAtTarget) {
            hayTarget = near.get();
            hayTargetSinceMs = now;
            hayCountAtTarget = have;
            return false;
        }
        if (now - hayTargetSinceMs > 30_000) {
            Debug.logMessage("S270 hay bale " + hayTarget.toShortString() + " no progress 30s - marking unreachable");
            mod.getBlockScanner().requestBlockUnreachable(hayTarget, 0);
            hayTarget = null;
            return true;
        }
        return false;
    }

    private Task pickupBlockTaskOrNull(AltoClef mod, Block blockToCheck, Item itemToGrab, Predicate<BlockPos> accept, double maxRange) {
        Predicate<BlockPos> acceptPlus = (blockPos) -> {
            if (!WorldHelper.canBreak(blockPos)) return false;
            return accept.test(blockPos);
        };
        Optional<BlockPos> nearestBlock = mod.getBlockScanner().getNearestBlock(mod.getPlayer().getPos(), acceptPlus, blockToCheck);

        if (nearestBlock.isPresent() && !nearestBlock.get().isWithinDistance(mod.getPlayer().getPos(), maxRange)) {
            nearestBlock = Optional.empty();
        }

        Optional<ItemEntity> nearestDrop = Optional.empty();
        if (mod.getEntityTracker().itemDropped(itemToGrab)) {
            nearestDrop = mod.getEntityTracker().getClosestItemDrop(mod.getPlayer().getPos(), itemToGrab);
        }

        if (nearestDrop.isPresent()) {
            return pickupTaskOrNull(mod,itemToGrab);
        }
        if (nearestBlock.isPresent()) {
            return new DoToClosestBlockTask(DestroyBlockTask::new, acceptPlus, blockToCheck);
        }

        return null;
    }

    private Task pickupBlockTaskOrNull(AltoClef mod, Block blockToCheck, Item itemToGrab, double maxRange) {
        return pickupBlockTaskOrNull(mod, blockToCheck, itemToGrab, toAccept -> true, maxRange);
    }

    private Task killTaskOrNull(Entity entity, Predicate<Entity> entityPredicate, Item itemToGrab) {
        return new KillAndLootTask(entity.getClass(), entityPredicate, new ItemTarget(itemToGrab, 1));
    }

    /**
     * Returns a task that picks up a dropped item.
     * Returns null if task cannot reasonably run.
     */
    private Task pickupTaskOrNull(AltoClef mod, Item itemToGrab, double maxRange) {
        Optional<ItemEntity> nearestDrop = Optional.empty();
        if (mod.getEntityTracker().itemDropped(itemToGrab)) {
            nearestDrop = mod.getEntityTracker().getClosestItemDrop(mod.getPlayer().getPos(), itemToGrab);
        }
        if (nearestDrop.isPresent()) {
            if (nearestDrop.get().isInRange(mod.getPlayer(), maxRange)) {
                if (mod.getItemStorage().getSlotsThatCanFitInPlayerInventory(nearestDrop.get().getStack(), false).isEmpty()) {
                    Optional<Slot> slot = StorageHelper.getGarbageSlot(mod);

                    // tf am I supposed to do if its empty
                    if (slot.isPresent()) {
                        ItemStack stack = StorageHelper.getItemStackInSlot(slot.get());
                        if (ItemVer.isFood(stack.getItem())) {
                            // calculate priority, if the item laying on the ground has lower priority than the one we are gonna throw out because of it
                            // dont pick it up, otherwise we would get stuck in an infinite loop
                            int inventoryCost = ItemVer.getFoodComponent(stack.getItem()).getHunger() * stack.getCount();

                            double hunger = 0;
                            if (ItemVer.isFood(itemToGrab)) {
                                hunger = ItemVer.getFoodComponent(itemToGrab).getHunger();
                            } else if (itemToGrab.equals(Items.WHEAT)) {
                                hunger += ItemVer.getFoodComponent(Items.BREAD).getHunger()/3d;
                            } else {
                                mod.log("unknown food item: "+itemToGrab);
                            }
                            int groundCost = (int) (hunger * nearestDrop.get().getStack().getCount());

                            if (inventoryCost > groundCost) return null;
                        }
                    }
                }
                return new PickupDroppedItemTask(new ItemTarget(itemToGrab), true);
            }
        }
        return null;
    }

    private Task pickupTaskOrNull(AltoClef mod, Item itemToGrab) {
        return pickupTaskOrNull(mod, itemToGrab, Double.POSITIVE_INFINITY);
    }

    @SuppressWarnings("rawtypes")
    public static class CookableFoodTarget {
        public String rawFood;
        public String cookedFood;
        public Class mobToKill;

        public CookableFoodTarget(String rawFood, String cookedFood, Class mobToKill) {
            this.rawFood = rawFood;
            this.cookedFood = cookedFood;
            this.mobToKill = mobToKill;
        }

        public CookableFoodTarget(String rawFood, Class mobToKill) {
            this(rawFood, "cooked_" + rawFood, mobToKill);
        }

        public Item getRaw() {
            return Objects.requireNonNull(TaskCatalogue.getItemMatches(rawFood))[0];
        }

        public Item getCooked() {
            return Objects.requireNonNull(TaskCatalogue.getItemMatches(cookedFood))[0];
        }

        public int getCookedUnits() {
            assert ItemVer.getFoodComponent(getCooked()) != null;
            return ItemVer.getFoodComponent(getCooked()).getHunger();
        }

        public boolean isFish() {
            return false;
        }
    }

    @SuppressWarnings("rawtypes")
    private static class CookableFoodTargetFish extends CookableFoodTarget {

        public CookableFoodTargetFish(String rawFood, Class mobToKill) {
            super(rawFood, mobToKill);
        }

        @Override
        public boolean isFish() {
            return true;
        }
    }

    public static class CropTarget {
        public Item cropItem;
        public Block cropBlock;

        public CropTarget(Item cropItem, Block cropBlock) {
            this.cropItem = cropItem;
            this.cropBlock = cropBlock;
        }
    }
}
