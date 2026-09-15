package adris.altoclef.tasks.speedrun;

import net.minecraft.util.math.Direction;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.PlaceObsidianBucketTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.container.LootContainerTask;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.EnterNetherPortalTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasks.resources.MineAndCollectTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.Arrays;

/**
 * Modern 1.16-style Early Overworld speedrun logic.
 *
 * Priority order (inspired by current RSG meta):
 *
 * 1. Basic wood + tools (axe + pick)
 * 2. Shipwreck / ocean ruins ONLY if the chest is safely reachable on land / surface
 *    (Baritone is poor underwater - we deliberately skip submerged chests)
 * 3. Buried treasure map following (if we have one) - future work
 * 4. Surface iron + lava / water for portal
 * 5. Ruined portal if available
 * 6. Craft iron pick, bucket, flint & steel
 * 7. Build / enter Nether portal as fast as possible
 *
 * Important: If the only good chests are underwater, the bot ignores them and
 * falls back to surface iron â†’ portal. This avoids getting stuck swimming.
 *
 * This task is finished once we are in the Nether.
 */
public class EarlyOverworldSpeedrunTask extends Task {

    private final AltoClef mod;

    // Sub-task cache
    private Task currentSubTask = null;

    // Simple state tracking
    private boolean hasBasicTools = false;
    private boolean hasIronPick = false;
    private boolean hasPortalIgniter = false;
    private boolean hasBucket = false;
    private boolean hasWeapon = false;
    private boolean hasSword = false;
    private boolean hasBasicArmor = false;

    // Chests already looted this run - avoid reopen loop when LootContainerTask finishes
    private final Set<BlockPos> lootedChests = new HashSet<>();
    private LootContainerTask activeLootTask = null;
    private BlockPos activeLootChest = null;

    private final TimerGame caveEscapeTimer = new TimerGame(25);
    private boolean caveEscapeActive = false;
    private boolean caveEscapeGaveUp = false;

    // Cache so ConstructNetherPortalBucketTask keeps portalOrigin across ticks
    private Task goToNetherTask = new DefaultGoToDimensionTask(Dimension.NETHER);
    private final TimerGame bucketTimer = new TimerGame(75);
    private boolean bucketTimerStarted = false;
    private final TimerGame flintTimer = new TimerGame(75);
    private boolean flintTimerStarted = false;
    private boolean flintPathLogged = false;
    private final Set<BlockPos> ruinedChestsLooted = new HashSet<>();
    private LootContainerTask ruinedLootTask = null;
    private BlockPos ruinedLootChest = null;
    private final TimerGame ironPickTimer = new TimerGame(90);
    private boolean ironPickTimerStarted = false;
    private final TimerGame weaponTimer = new TimerGame(40);
    private boolean weaponTimerStarted = false;
    private boolean weaponGaveUp = false;

    // Phase markers + stall recovery
    private String loggedPhase = "";
    private int lastIronLoggedCount = -1;
    private final TimerGame portalPhaseTimer = new TimerGame(90);
    private boolean portalPhaseTimerStarted = false;
    /** Rate-limit "found lit nether portal" to once per portal BlockPos. */
    private BlockPos lastLoggedLitPortal = null;

    private final TimerGame foodGatherTimer = new TimerGame(60);
    private boolean foodGatherActive = false;
    private boolean foodGatherGaveUp = false;

    /** When true, skip CollectFood (default for sub-10 @testrun). */
    private final boolean skipCollectFood;

    public EarlyOverworldSpeedrunTask(AltoClef mod) {
        this(mod, true);
    }

    public EarlyOverworldSpeedrunTask(AltoClef mod, boolean skipCollectFood) {
        this.mod = mod;
        this.skipCollectFood = skipCollectFood;
    }

    @Override
    protected void onStart() {
        setDebugState("Early Overworld - modern speedrun route");
        currentSubTask = null;
    }

    @Override
    protected Task onTick() {
        // Already in Nether? We're done with early overworld.
        if (WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            setDebugState("Entered Nether - early overworld complete");
            if (goToNetherTask != null) {
                goToNetherTask.stop();
                goToNetherTask = null;
            }
            currentSubTask = null;
            return null;
        }

        updateFlags();

        // ---------- Priority 0: Escape caves ----------
        // Skip once portal-ready: lava lakes are often "underground" and escape was
        // interrupting ConstructNetherPortalBucketTask every tick (fresh lava search spam).
        // Iron mining is underground - do NOT cave-escape once we have stone tools or we're portal-ready.
        boolean portalReady = hasIronPick && hasBucket && hasPortalIgniter;
        boolean allowCaveEscape = !hasBasicTools && !portalReady;
        if (allowCaveEscape) {
            if (!isUnderground()) {
                caveEscapeActive = false;
                // Only clear "gave up" when we are actually at surface height (not a sky-light flicker in a hole)
                BlockPos surfaceCheck = mod.getPlayer().getBlockPos();
                int surfaceY = WorldHelper.getGroundHeight(surfaceCheck.getX(), surfaceCheck.getZ());
                if (surfaceCheck.getY() >= surfaceY - 1) {
                    caveEscapeGaveUp = false;
                }
            } else if (!caveEscapeGaveUp) {
                if (!caveEscapeActive) {
                    caveEscapeActive = true;
                    caveEscapeTimer.reset();
                } else if (caveEscapeTimer.elapsed()) {
                    caveEscapeGaveUp = true;
                    setDebugState("Cave escape timed out - continuing without food underground");
                }
                if (!caveEscapeGaveUp) {
                    Task escape = escapeCaveTask();
                    if (escape != null) {
                        setDebugState("Escaping cave to surface (avoid mob deaths)");
                        return escape;
                    }
                }
            }
        } else {
            caveEscapeActive = false;
        }

        // ---------- Priority 1: Basic wood tools ----------
        if (!hasBasicTools) {
            setDebugState("Getting basic wood tools");
            return ensureBasicTools();
        }

        // ---------- Priority 1b: Sword for combat (axe may satisfy hasWeapon route gate; soft-timeout) ----------
        // Soft-try crafting a sword even if an axe already counts for the route gate.
        if (!hasSword && !weaponGaveUp) {
            if (!weaponTimerStarted) {
                weaponTimerStarted = true;
                weaponTimer.reset();
                Debug.logMessage("EarlyOverworld: getting a sword for combat");
            } else if (weaponTimer.elapsed()) {
                Debug.logMessage("EarlyOverworld: sword craft stalled - continuing with axe/fist fallback");
                StorageHelper.closeScreen();
                weaponGaveUp = true;
            } else {
                setDebugState("Getting a sword for mob defense");
                return ensureWeapon();
            }
        }
        // Armor equip skipped for speedrun - loot equip only if free, never block the route
        if (!hasBasicArmor) {
            Task armor = ensureBasicArmor();
            // ensureBasicArmor sets hasBasicArmor=true when nothing to equip
            if (armor != null) {
                // Don't block more than a moment - only equip if already in inventory
                setDebugState("Equipping loot armor");
                return armor;
            }
        }

        // ---------- Priority 2: Optional CollectFood (off by default for sub-10) ----------
        if (!skipCollectFood && !foodGatherGaveUp && !isUnderground()
                && adris.altoclef.util.helpers.StorageHelper.calculateInventoryFoodScore(mod) < 10) {
            if (!foodGatherActive) {
                foodGatherActive = true;
                foodGatherTimer.reset();
                Debug.logMessage("EarlyOverworld: collecting emergency food (skipFood=false)");
            } else if (foodGatherTimer.elapsed()) {
                foodGatherGaveUp = true;
                foodGatherActive = false;
                Debug.logMessage("EarlyOverworld: food gather timed out - continuing");
            } else {
                setDebugState("Collecting emergency food on surface");
                return new CollectFoodTask(15);
            }
        } else {
            foodGatherActive = false;
        }

        // ---------- Priority 3: structure chests disabled (stall magnets) ----------

        // ---------- Priority 3b: Enter lit ruined/nether portal early (chest loot deferred past iron) ----------
        Task litPortal = tryEnterLitNetherPortal();
        if (litPortal != null) {
            setDebugState("Entering lit nether portal");
            return litPortal;
        }

        // ---------- Priority 4: Iron pick ----------
        if (!hasIronPick) {
            boolean canMineIron = StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.STONE)
                    || StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.IRON);
            if (canMineIron) {
                if (!ironPickTimerStarted) {
                    ironPickTimerStarted = true;
                    ironPickTimer.reset();
                    Debug.logMessage("EarlyOverworld: starting iron pick (stone tools ready)");
                } else if (ironPickTimer.elapsed()) {
                    Debug.logMessage("EarlyOverworld: iron pick >90s - close screen + wander");
                    StorageHelper.closeScreen();
                    ironPickTimer.reset();
                    return new TimeoutWanderTask(24, true);
                }
            } else {
                ironPickTimerStarted = false;
            }
            enterPhase("IRON_PICK");
            setDebugState("Getting iron pickaxe (surface route)");
            return ensureIronPick();
        }

        // ---------- Priority 4b: Ruined portal chest loot AFTER iron pick (fire charge / gold / obsidian) ----------
        // Defer so chest loot cannot interrupt IRON_PICK once stone tools are ready.
        Task ruinedLoot = tryRuinedPortalChestLoot();
        if (ruinedLoot != null) {
            setDebugState("Ruined portal loot (post-iron)");
            return ruinedLoot;
        }

        // ---------- Priority 5: One bucket (Construct gets the 2nd at the lava lake) ----------
        // Requiring 2 buckets here forced silent extra-iron mining and stalled runs.
        if (!hasBucket) {
            portalPhaseTimerStarted = false;
            enterPhase("BUCKET");
            if (!bucketTimerStarted) {
                bucketTimerStarted = true;
                bucketTimer.reset();
                Debug.logMessage("EarlyOverworld: starting bucket craft (need 1)");
            } else if (bucketTimer.elapsed()) {
                Debug.logMessage("EarlyOverworld: bucket craft >75s - hard UI reset + wander retry");
                hardResetUi();
                bucketTimer.reset();
                return new TimeoutWanderTask(12, true);
            }
            setDebugState("Crafting bucket");
            return TaskCatalogue.getItemTask(Items.BUCKET, 1);
        }

        // ---------- Priority 6: Portal igniter ----------
        if (!hasPortalIgniter) {
            portalPhaseTimerStarted = false;
            enterPhase("FLINT");
            if (!flintTimerStarted) {
                flintTimerStarted = true;
                flintTimer.reset();
                flintPathLogged = false;
            }
            if (!flintPathLogged) {
                flintPathLogged = true;
                logFlintInventory("start");
            } else if (flintTimer.elapsed()) {
                Debug.logMessage("EarlyOverworld: flint/igniter >75s stall - hard UI reset + wander once");
                hardResetUi();
                logFlintInventory("stall");
                flintTimer.reset();
                return new TimeoutWanderTask(16, true);
            }
            // Prefer nearby ruined-portal fire charge while seeking igniter
            Task ruinedDuringFlint = tryRuinedPortalChestLoot();
            if (ruinedDuringFlint != null) {
                setDebugState("Ruined portal loot for fire charge");
                return ruinedDuringFlint;
            }
            setDebugState("Getting flint and steel / fire charge");
            return ensurePortalIgniter();
        } else {
            flintTimerStarted = false;
            flintPathLogged = false;
        }

        // ---------- Priority 7: Enter / build Nether portal ----------
        enterPhase("PORTAL_BUILD");
        if (!portalPhaseTimerStarted) {
            portalPhaseTimerStarted = true;
            portalPhaseTimer.reset();
            Debug.logMessage("EarlyOverworld: entering portal build (bucket+flint ready)");
        } else if (portalPhaseTimer.elapsed()) {
            boolean hasFluid = StorageHelper.itemInventoryIncludes(mod, Items.WATER_BUCKET)
                    || StorageHelper.itemInventoryIncludes(mod, Items.LAVA_BUCKET);
            Optional<BlockPos> litPortalPos = mod.getBlockScanner().getNearestBlock(Blocks.NETHER_PORTAL);
            boolean portalNearby = litPortalPos.isPresent() && litPortalPos.get().isWithinDistance(mod.getPlayer().getPos(), 48);
            if (!hasFluid && !portalNearby) {
                int empty = StorageHelper.getItemCount(mod, Items.BUCKET);
                int water = StorageHelper.getItemCount(mod, Items.WATER_BUCKET);
                int lava = StorageHelper.getItemCount(mod, Items.LAVA_BUCKET);
                Debug.logMessage("EarlyOverworld: portal build stalled without water/lava - abort hard-reset Construct"
                        + " (empty=" + empty + " water=" + water + " lava=" + lava + ")");
                hardResetUi();
                if (goToNetherTask instanceof DefaultGoToDimensionTask dim) {
                    dim.hardResetPortalBuild();
                    goToNetherTask.stop();
                } else if (goToNetherTask != null) {
                    goToNetherTask.stop();
                }
                // Drop cached dimension task so next enterNether() gets a fresh Construct
                goToNetherTask = null;
                portalPhaseTimerStarted = false;
                portalPhaseTimer.reset();
                return new TimeoutWanderTask(16, true);
            }
            // Progress exists - keep going but reset the watchdog
            portalPhaseTimer.reset();
        }
        // Construct self-abort (no fluid progress) ? hard reset immediately, don't wait for 90s timer
        if (goToNetherTask instanceof DefaultGoToDimensionTask dimTask && dimTask.consumeConstructAbort()) {
            int empty = StorageHelper.getItemCount(mod, Items.BUCKET);
            int water = StorageHelper.getItemCount(mod, Items.WATER_BUCKET);
            int lava = StorageHelper.getItemCount(mod, Items.LAVA_BUCKET);
            Debug.logMessage("EarlyOverworld: Construct abortedForReacquire - hard reset goToNether"
                    + " (empty=" + empty + " water=" + water + " lava=" + lava + ")");
            hardResetUi();
            goToNetherTask.stop();
            goToNetherTask = null;
            portalPhaseTimerStarted = false;
            portalPhaseTimer.reset();
            return new TimeoutWanderTask(16, true);
        }
        setDebugState("Building / entering Nether portal (safe surface route)");
        return enterNether();
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private void enterPhase(String phase) {
        if (!phase.equals(loggedPhase)) {
            loggedPhase = phase;
            Debug.logMessage("EarlyOverworld: PHASE=" + phase);
        }
    }

    /** Close craft/chest screens and drop/stash cursor so mismatching-container clicks clear. */
    private void hardResetUi() {
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        if (cursor != null && !cursor.isEmpty()) {
            var moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false);
            if (moveTo.isPresent()) {
                mod.getSlotHandler().clickSlot(moveTo.get(), 0, SlotActionType.PICKUP);
            } else {
                mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, SlotActionType.PICKUP);
            }
        }
        StorageHelper.closeScreen();
    }

    private void updateFlags() {
        // Stone+ only - wood was letting us enter iron-pick with a wooden pick (path thrash / back-and-forth)
        hasBasicTools = StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.STONE)
                || StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.IRON);

        hasIronPick = StorageHelper.itemInventoryIncludes(mod, Items.IRON_PICKAXE)
                || StorageHelper.itemInventoryIncludes(mod, Items.DIAMOND_PICKAXE)
                || StorageHelper.itemInventoryIncludes(mod, Items.NETHERITE_PICKAXE);

        hasPortalIgniter = StorageHelper.itemInventoryIncludes(mod, Items.FLINT_AND_STEEL)
                || StorageHelper.itemInventoryIncludes(mod, Items.FIRE_CHARGE);

        // Swords preferred for combat; axes count only for hasWeapon route gate.
        hasSword = StorageHelper.itemInventoryIncludes(mod, Items.WOODEN_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.STONE_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.IRON_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.DIAMOND_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.NETHERITE_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.GOLDEN_SWORD);
        hasWeapon = hasSword
                || StorageHelper.itemInventoryIncludes(mod, Items.WOODEN_AXE)
                || StorageHelper.itemInventoryIncludes(mod, Items.STONE_AXE)
                || StorageHelper.itemInventoryIncludes(mod, Items.IRON_AXE)
                || StorageHelper.itemInventoryIncludes(mod, Items.DIAMOND_AXE)
                || StorageHelper.itemInventoryIncludes(mod, Items.NETHERITE_AXE)
                || StorageHelper.itemInventoryIncludes(mod, Items.GOLDEN_AXE)
                || weaponGaveUp;

        hasBasicArmor = StorageHelper.isArmorEquipped(mod, Items.LEATHER_HELMET)
                || StorageHelper.isArmorEquipped(mod, Items.LEATHER_CHESTPLATE)
                || StorageHelper.isArmorEquipped(mod, Items.IRON_HELMET)
                || StorageHelper.isArmorEquipped(mod, Items.IRON_CHESTPLATE)
                || StorageHelper.isArmorEquipped(mod, Items.IRON_LEGGINGS)
                || StorageHelper.isArmorEquipped(mod, Items.IRON_BOOTS)
                || StorageHelper.isArmorEquipped(mod, Items.GOLDEN_HELMET)
                || StorageHelper.isArmorEquipped(mod, Items.GOLDEN_CHESTPLATE)
                || StorageHelper.isArmorEquipped(mod, Items.DIAMOND_HELMET)
                || StorageHelper.itemInventoryIncludes(mod, Items.IRON_CHESTPLATE)
                || StorageHelper.itemInventoryIncludes(mod, Items.IRON_HELMET)
                || StorageHelper.itemInventoryIncludes(mod, Items.IRON_LEGGINGS)
                || StorageHelper.itemInventoryIncludes(mod, Items.IRON_BOOTS)
                || StorageHelper.itemInventoryIncludes(mod, Items.LEATHER_CHESTPLATE);

        hasBucket = StorageHelper.itemInventoryIncludes(mod, Items.BUCKET)
                || StorageHelper.itemInventoryIncludes(mod, Items.WATER_BUCKET)
                || StorageHelper.itemInventoryIncludes(mod, Items.LAVA_BUCKET);
    }

    private Task ensureBasicTools() {
        // Wood axe + wood/stone pick is enough to start
        if (!StorageHelper.itemInventoryIncludes(mod, Items.WOODEN_AXE)
                && !StorageHelper.itemInventoryIncludes(mod, Items.STONE_AXE)
                && !StorageHelper.itemInventoryIncludes(mod, Items.IRON_AXE)) {
            return TaskCatalogue.getItemTask(Items.WOODEN_AXE, 1);
        }
        if (!StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.STONE)
                && !StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.IRON)) {
            return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
        }
        // We have enough
        return null;
    }

    private Task escapeCaveTask() {
        BlockPos feet = mod.getPlayer().getBlockPos();
        // Dig a taller shaft out - Baritone surface paths through caves thrash hard
        for (int dy = 1; dy <= 8; dy++) {
            BlockPos above = feet.up(dy);
            if (WorldHelper.isSolidBlock(above)) {
                return new DestroyBlockTask(above);
            }
        }
        // Path to a surface column that is NOT already blacklisted as unreachable
        int[][] offsets = new int[][]{{0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}, {12, 12}, {-12, 12}, {12, -12}, {-12, -12}};
        for (int[] d : offsets) {
            int x = feet.getX() + d[0];
            int z = feet.getZ() + d[1];
            int groundY = WorldHelper.getGroundHeight(x, z);
            if (groundY <= feet.getY() + 1) continue;
            BlockPos surface = new BlockPos(x, groundY + 1, z);
            if (mod.getBlockScanner().isUnreachable(surface)) {
                continue;
            }
            return new GetToBlockTask(surface, false);
        }
        // Every surface goal is blacklisted / invalid - force give-up next tick via wander
        caveEscapeGaveUp = true;
        setDebugState("Cave escape: surface goals unreachable - giving up");
        return new TimeoutWanderTask(24, true);
    }

    private boolean isUnderground() {
        if (mod.getPlayer() == null || mod.getWorld() == null) return false;
        BlockPos pos = mod.getPlayer().getBlockPos();
        int sky = mod.getWorld().getLightLevel(LightType.SKY, pos);
        // Open sky (ravine/cliff) is fine - only treat roofed areas as caves
        if (sky >= 8) {
            return false;
        }
        // Deep and dark, or any dark spot below sea level
        return pos.getY() < 55 || (sky <= 0 && pos.getY() < 62);
    }

    private Task ensureWeapon() {
        // Already have stone+ sword — good enough for combat
        if (StorageHelper.itemInventoryIncludes(mod, Items.STONE_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.IRON_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.DIAMOND_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.NETHERITE_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.GOLDEN_SWORD)) {
            return null;
        }
        // Prefer stone sword once stone (or iron) tools are available — even if wooden exists
        if (StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.STONE)
                || StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.IRON)) {
            return TaskCatalogue.getItemTask(Items.STONE_SWORD, 1);
        }
        // Pre-stone: wooden only if we do not already have one
        if (StorageHelper.itemInventoryIncludes(mod, Items.WOODEN_SWORD)) {
            return null;
        }
        return TaskCatalogue.getItemTask(Items.WOODEN_SWORD, 1);
    }

    private Task ensureBasicArmor() {
        // Equip loot only - never craft armor mid-route (iron is for pick/bucket/flint).
        Item[] equippable = new Item[]{
                Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
                Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
                Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
                Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS
        };
        for (Item piece : equippable) {
            if (StorageHelper.itemInventoryIncludes(mod, piece) && !StorageHelper.isArmorEquipped(mod, piece)) {
                return new EquipArmorTask(piece);
            }
        }
        hasBasicArmor = true;
        return null;
    }

    private Task ensureIronPick() {
        // Hard gate: never chase iron with only a wooden pick
        if (!StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.STONE)
                && !StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.IRON)) {
            Debug.logMessage("EarlyOverworld: need stone pick before iron");
            return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
        }

        int ironStuff = StorageHelper.getItemCount(mod, Items.RAW_IRON, Items.IRON_ORE, Items.IRON_INGOT);
        boolean anyIronVisible = mod.getBlockScanner().anyFound(Blocks.IRON_ORE, Blocks.IRON_ORE);
        if (ironStuff < 3) {
            if (ironStuff != lastIronLoggedCount) {
                lastIronLoggedCount = ironStuff;
                Debug.logMessage("EarlyOverworld: mining iron (" + ironStuff + "/3, oreVisible=" + anyIronVisible + ")");
            }
            setDebugState("Mining iron for pick (" + ironStuff + "/3)");
            // Use catalogue mine - same ores, includes explore behavior via ResourceTask
            return TaskCatalogue.getItemTask(Items.RAW_IRON, 3);
        }

        if (lastIronLoggedCount != -100) {
            lastIronLoggedCount = -100;
            Debug.logMessage("EarlyOverworld: crafting iron pick (ironStuff=" + ironStuff + ")");
        }
        return TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
    }

    private void logFlintInventory(String reason) {
        int flint = StorageHelper.getItemCount(mod, Items.FLINT);
        int ironIngot = StorageHelper.getItemCount(mod, Items.IRON_INGOT);
        int gravel = StorageHelper.getItemCount(mod, Items.GRAVEL);
        int fireCharge = StorageHelper.getItemCount(mod, Items.FIRE_CHARGE);
        int fas = StorageHelper.getItemCount(mod, Items.FLINT_AND_STEEL);
        String path;
        if (fas > 0 || fireCharge > 0) {
            path = "already have igniter (should skip to PORTAL_BUILD)";
        } else if (flint > 0 && ironIngot > 0) {
            path = "craft flint_and_steel";
        } else if (flint == 0) {
            path = "need flint (mine/place gravel)";
        } else {
            path = "need iron_ingot for flint_and_steel";
        }
        Debug.logMessage("EarlyOverworld: FLINT " + reason + " path=" + path
                + " flint=" + flint + " iron_ingot=" + ironIngot
                + " gravel=" + gravel + " fire_charge=" + fireCharge
                + " flint_and_steel=" + fas);
    }

    private Task ensurePortalIgniter() {
        // Prefer fire charge / existing F&S (detection safety net)
        if (StorageHelper.itemInventoryIncludes(mod, Items.FIRE_CHARGE)
                || StorageHelper.itemInventoryIncludes(mod, Items.FLINT_AND_STEEL)) {
            Debug.logMessage("EarlyOverworld: igniter already in inventory - skip craft");
            return null;
        }
        if (StorageHelper.itemInventoryIncludes(mod, Items.IRON_INGOT)
                && StorageHelper.itemInventoryIncludes(mod, Items.FLINT)) {
            return TaskCatalogue.getItemTask(Items.FLINT_AND_STEEL, 1);
        }
        // Fallback - get flint first
        if (!StorageHelper.itemInventoryIncludes(mod, Items.FLINT)) {
            return TaskCatalogue.getItemTask(Items.FLINT, 1);
        }
        return TaskCatalogue.getItemTask(Items.FLINT_AND_STEEL, 1);
    }

    /** High-priority: walk into an already-lit portal. Does not loot chests. */
    private Task tryEnterLitNetherPortal() {
        Optional<BlockPos> lit = mod.getBlockScanner().getNearestBlock(Blocks.NETHER_PORTAL);
        if (lit.isPresent() && lit.get().isWithinDistance(mod.getPlayer().getPos(), 96)) {
            if (lastLoggedLitPortal == null || !lastLoggedLitPortal.equals(lit.get())) {
                lastLoggedLitPortal = lit.get().toImmutable();
                Debug.logMessage("EarlyOverworld: found lit nether portal at " + lit.get().toShortString());
            }
            return new EnterNetherPortalTask(Dimension.NETHER);
        }
        return null;
    }

    /** Loot ruined-portal chests for fire charge / flint / gold. Call only after iron pick (or during FLINT). */
    private Task tryRuinedPortalChestLoot() {
        if (ruinedLootTask != null) {
            if (ruinedLootTask.isFinished()) {
                if (ruinedLootChest != null) {
                    ruinedChestsLooted.add(ruinedLootChest);
                    lootedChests.add(ruinedLootChest);
                    if (StorageHelper.itemInventoryIncludes(mod, Items.FIRE_CHARGE)
                            || StorageHelper.itemInventoryIncludes(mod, Items.FLINT_AND_STEEL)) {
                        Debug.logMessage("EarlyOverworld: ruined portal loot yielded portal igniter");
                    }
                }
                ruinedLootTask = null;
                ruinedLootChest = null;
            } else {
                return ruinedLootTask;
            }
        }

        Optional<BlockPos> chest = mod.getBlockScanner().getNearestBlock(
                pos -> !ruinedChestsLooted.contains(pos)
                        && !lootedChests.contains(pos)
                        && WorldHelper.isUnopenedChest(pos)
                        && isRuinedPortalChest(pos),
                Blocks.CHEST);
        if (chest.isPresent() && chest.get().isWithinDistance(mod.getPlayer().getPos(), 80)) {
            ruinedLootChest = chest.get();
            ruinedLootTask = new LootContainerTask(ruinedLootChest, Arrays.asList(ruinedPortalLootItems()));
            Debug.logMessage("EarlyOverworld: looting ruined portal chest at " + ruinedLootChest.toShortString());
            return ruinedLootTask;
        }
        return null;
    }

    private boolean isRuinedPortalChest(BlockPos blockPos) {
        if (mod.getWorld() == null) return false;
        if (mod.getWorld().getBlockState(blockPos.up()).getBlock() == Blocks.WATER || blockPos.getY() < 50) {
            return false;
        }
        for (BlockPos check : WorldHelper.scanRegion(blockPos.add(-4, -2, -4), blockPos.add(4, 2, 4))) {
            net.minecraft.block.Block b = mod.getWorld().getBlockState(check).getBlock();
            if (b == Blocks.NETHERRACK || b == Blocks.CRYING_OBSIDIAN) {
                return true;
            }
        }
        return false;
    }

    private Item[] ruinedPortalLootItems() {
        return new Item[]{
                Items.FLINT_AND_STEEL, Items.FIRE_CHARGE, Items.FLINT, Items.OBSIDIAN,
                Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.GOLD_BLOCK,
                Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
                Items.IRON_NUGGET, Items.IRON_INGOT
        };
    }

    private Task enterNether() {
        // Re-use the solid existing dimension task for now.
        // Later we can add ruined-portal preference and faster bucket portal building.
        if (goToNetherTask == null || goToNetherTask.stopped()) {
            goToNetherTask = new DefaultGoToDimensionTask(Dimension.NETHER);
        }
        return goToNetherTask;
    }

    /**
     * Find a nearby chest that is safe for Baritone (not underwater).
     *
     * Rules:
     * - Must be relatively close
     * - The chest block itself must NOT be waterlogged / submerged
     * - Prefer chests that have air or solid ground access
     *
     * If the only chests are underwater, we return empty and the bot
     * continues with the surface iron â†’ portal route instead of drowning.
     */
    private Optional<BlockPos> findNearbySafeSurfaceChest() {
        Optional<BlockPos> chest = mod.getBlockScanner().getNearestBlock(
                pos -> !lootedChests.contains(pos),
                Blocks.CHEST, Blocks.TRAPPED_CHEST);
        if (chest.isEmpty()) return Optional.empty();

        BlockPos pos = chest.get();

        // Distance limit - don't run halfway across the world
        if (mod.getPlayer().squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 70 * 70) {
            return Optional.empty();
        }

        // Reject underwater / waterlogged chests
        if (isUnderwaterOrWaterlogged(pos)) {
            setDebugState("Ignoring underwater chest at " + pos + " (Baritone is bad in water)");
            return Optional.empty();
        }

        // Accept land / surface accessible chests
        return Optional.of(pos);
    }

    /**
     * Returns true if the chest is underwater or heavily waterlogged.
     * Simple but effective check for our purposes.
     */
    private boolean isUnderwaterOrWaterlogged(BlockPos pos) {
        // Check the chest itself and a few blocks around / above it
        if (mod.getWorld().getBlockState(pos).getFluidState().isStill()
                || !mod.getWorld().getFluidState(pos).isEmpty()) {
            return true;
        }

        // Also reject if the block above is water (common for ocean ruins / shipwrecks)
        BlockPos above = pos.up();
        if (!mod.getWorld().getFluidState(above).isEmpty()) {
            return true;
        }

        // Extra safety: if the player would have to swim to reach it
        // (very rough - if chest is significantly below sea level and surrounded by water)
        if (pos.getY() < 62) {
            // Check a few neighbouring blocks for water
            for (BlockPos check : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west()}) {
                if (!mod.getWorld().getFluidState(check).isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

    private Item[] getImportantLootItems() {
        // High-value early-game items from shipwrecks / buried treasure
        return new Item[]{
                Items.IRON_INGOT,
                Items.IRON_NUGGET,
                Items.GOLD_INGOT,
                Items.GOLD_NUGGET,
                Items.DIAMOND,
                Items.EMERALD,
                Items.FILLED_MAP,          // buried treasure map
                Items.MAP,
                Items.COOKED_COD,
                Items.COOKED_SALMON,
                Items.BREAD,
                Items.APPLE,
                Items.TNT,
                Items.OBSIDIAN,
                Items.FLINT,
                Items.FEATHER,
                Items.LEATHER,
                Items.IRON_SWORD,
                Items.IRON_PICKAXE,
                Items.IRON_AXE,
                Items.IRON_SHOVEL,
                Items.IRON_HELMET,
                Items.IRON_CHESTPLATE,
                Items.IRON_LEGGINGS,
                Items.IRON_BOOTS
        };
    }

    @Override
    protected void onStop(Task interruptTask) {
        // nothing special
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EarlyOverworldSpeedrunTask;
    }

    @Override
    protected String toDebugString() {
        return "EarlyOverworldSpeedrunTask";
    }

    @Override
    public boolean isFinished() {
        return WorldHelper.getCurrentDimension() == Dimension.NETHER;
    }
}
