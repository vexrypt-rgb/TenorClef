package adris.altoclef.maze;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.container.StoreInContainerTask;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Furnishes a carved Ostinato maze room: gathers {@link MazeFurnishPlan#materials}, then lights (breaking the stone the
 * dig pass left), containers, loot fills, and last the shell and entry-shaft seals, bottom-up as Ostinato ordered them.
 * Every step checks the world, so a resumed or repeated run skips what is already done.
 */
public class MazeFurnishTask extends Task {

    private final MazeFurnishPlan plan;
    private Map<String, Integer> materials;
    private int index;
    private boolean warnedPot;
    private Task fill;
    // Ostinato: watchdog. A gather that leaves the bot standing still for 2 minutes logs its task chain and wanders off.
    private BlockPos lastFeet;
    private int stillTicks;
    private Task unstick;
    private int unstickTicks;   // Ostinato: wander timeout
    private int watchdogFires;  // Ostinato: repeat-fire count
    // Ostinato: hard stuck abort. No inventory change and under 8 blocks of travel for 90s fails the run and exits.
    private int lastInvSig = -1;
    private BlockPos stuckAnchor;
    private int stuckTicks;
    private boolean prevAutoTool;
    private boolean failed;
    private int stuckRecoveries; // Ostinato: one wander+blacklist recovery before FAIL
    private boolean gathered;    // Ostinato: gather-once latch
    private String gatherKey;    // Ostinato: loot-only gather budget
    private int gatherTicks;
    private final java.util.Set<String> skipped = new java.util.HashSet<>();
    private java.util.List<MazeFurnishPlan.Step> work; // Ostinato: lights, container, fill, then seals
    private boolean loggedFill; // Ostinato
    private int foodTicks;      // Ostinato: kit food budget
    private int foodCooldown;   // Ostinato: ticks after skip-food before a starve retry
    private boolean foodGaveUp; // Ostinato
    private String surfacedFor; // Ostinato: wood gather already reached open air

    // Ostinato: wood-derived items whose recipe chain bottoms out in logs.
    private static boolean isWoody(String id) {
        return id.endsWith("planks") || id.endsWith("log") || id.endsWith("wood") || id.endsWith("_slab") && !id.contains("stone") || id.equals("barrel") || id.equals("chest") || id.equals("bowl") || id.equals("stick") || id.equals("crafting_table") || id.equals("bookshelf");
    }

    // Ostinato: true when a LIGHT step places this id (cladding).
    private boolean isCladding(String id) {
        for (MazeFurnishPlan.Step s : plan.steps) if (s.kind() == MazeFurnishPlan.Kind.LIGHT && s.blockIds().contains(id)) return true;
        return false;
    }

    // Ostinato: true when the id only appears as FILL contents (never placed as a block).
    private boolean lootOnly(String id) {
        boolean inFill = false;
        for (MazeFurnishPlan.Step s : plan.steps) {
            if (s.kind() == MazeFurnishPlan.Kind.FILL) { for (MazeFurnishPlan.Item it : s.contents()) if (it.id().equals(id)) inFill = true; }
            else if (s.blockIds() != null && s.blockIds().contains(id)) return false;
        }
        return inFill;
    }

    public MazeFurnishTask(MazeFurnishPlan plan) {
        this.plan = plan;
    }

    static boolean obtainable(String id) {
        Item item = McCompat.item(id);
        return item != null && (TaskCatalogue.taskExists(item) || AltoClef.getInstance().getItemStorage().hasItem(item));
    }

    @Override
    protected void onStart() {
        materials = plan.materials(MazeFurnishTask::obtainable);
        work = plan.placeOrder(); // Ostinato
        index = 0;
        Debug.logMessage("Maze furnish " + plan.summary + ", gathering " + materials);
        // Ostinato: cobblestone/cobbled_deepslate/andesite sit on the throwaway list, so a full inventory dumped the very
        // cladding being gathered and the mine loop stalled. Protect every planned material for the life of the task.
        AltoClef.getInstance().getBehaviour().push();
        AltoClef.getInstance().getBehaviour().addProtectedItems(SWORDS); // Ostinato: kit is never garbage
        AltoClef.getInstance().getBehaviour().addProtectedItems(IRON_ARMOR);
        AltoClef.getInstance().getBehaviour().addProtectedItems(KEEP); // Ostinato: food and tools are never garbage
        // Ostinato: run16 tug-of-war. Baritone autoTool and TenorClef's equip kept swapping tools on the same block, so it
        // never broke. TenorClef's PlayerInteractionFixChain equips for the block actually being broken; let it own tools.
        prevAutoTool = AltoClef.getInstance().getClientBaritoneSettings().autoTool.value;
        // Ostinato: run18/19 cherry-log stalls with autoTool off (held seeds/dirt, Baritone never reached logs); keep Baritone autoTool on.
        for (String id : materials.keySet()) { Item it = McCompat.item(id); if (it != null) AltoClef.getInstance().getBehaviour().addProtectedItems(it); }
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        BlockPos feet = mod.getPlayer().getBlockPos();
        if (failed) return null;
        int sig = 0;
        for (int i = 0; i < mod.getPlayer().getInventory().size(); i++) { net.minecraft.item.ItemStack st = mod.getPlayer().getInventory().getStack(i); sig = sig * 31 + st.getCount() * 7 + net.minecraft.registry.Registries.ITEM.getRawId(st.getItem()); }
        if (sig != lastInvSig || stuckAnchor == null || stuckAnchor.getSquaredDistance(feet) > 64) { if (sig != lastInvSig) stuckRecoveries = 0; lastInvSig = sig; stuckAnchor = feet; stuckTicks = 0; }
        else if (++stuckTicks >= 600 && stuckRecoveries < surfaceStuckLimit()) { // Ostinato: 90s -> 30s (user: faster) // Ostinato: two recoveries before FAIL; surfacing gets more (run37)
            // Ostinato: first 90s stall: blacklist the block the chain is fighting, wander 30s, retry. Second stall FAILs.
            for (Task t = this; t != null; t = t.getSub()) if (t instanceof DestroyBlockTask d) { mod.getBlockScanner().requestBlockUnreachable(d.getPos(), 0); }
            System.out.println("MAZETEST FURNISH stuck-recover stuck=" + getDebugState() + " feet=" + feet.toShortString());
            stuckRecoveries++; stuckTicks = 0; stuckAnchor = feet;
            unstickTicks = 0; unstick = new adris.altoclef.tasks.movement.TimeoutWanderTask(16);
            return unstick;
        }
        else if (stuckTicks >= 1200) {
            StringBuilder chain = new StringBuilder();
            for (Task t = this; t != null; t = t.getSub()) chain.append(" > ").append(t).append(" [").append(t.getDebugState()).append(']');
            System.out.println("MAZETEST FURNISH FAIL stuck=" + getDebugState() + " for=90s feet=" + feet.toShortString() + " held=" + mod.getPlayer().getMainHandStack().getItem().getTranslationKey() + " emptySlot=" + mod.getPlayer().getInventory().getEmptySlot() + " chain" + chain);
            failed = true;
            net.minecraft.client.MinecraftClient.getInstance().scheduleStop();
            return null;
        }
        if (feet.equals(lastFeet)) stillTicks++; else { stillTicks = 0; lastFeet = feet; }
        // Ostinato: run13 watchdog fired once, then the wander never reported finished and the bot idled forever.
        // Cap each wander at 30s and let stillness keep counting so the watchdog can fire again.
        if (unstick != null && !unstick.isFinished() && ++unstickTicks < 600) return unstick;
        unstick = null;
        if (stillTicks >= 2400) {
            StringBuilder chain = new StringBuilder();
            for (Task t = this; t != null; t = t.getSub()) chain.append(" > ").append(t).append(" [").append(t.getDebugState()).append(']');
            Debug.logMessage("Maze furnish watchdog: still at " + feet.toShortString() + " for " + stillTicks + " ticks; chain" + chain + "; wandering to unstick");
            System.out.println("MAZETEST FURNISH watchdog fire=" + watchdogFires + " held=" + mod.getPlayer().getMainHandStack().getItem().getTranslationKey() + " feet=" + feet.toShortString() + " emptySlot=" + mod.getPlayer().getInventory().getEmptySlot() + " chain" + chain);
            stillTicks = 0;
            unstickTicks = 0;
            watchdogFires++;
            unstick = new adris.altoclef.tasks.movement.TimeoutWanderTask(16);
            return unstick;
        }
        // Ostinato: survival kit before cladding. Runs 13-21 furnished naked with no food or sword: spider hunts with
        // seeds in hand, starvation risk on long mines. Order: sword -> food buffer -> full iron armor (equipped).
        // Ostinato: run39 drowned at t=22.7k gathering barrel underground, then spent ~24 min rebuilding the kit.
        // Air below 2/3 under water: surfacing outranks every kit/gather step (WorldSurvivalChain waits for 1/2).
        if (mod.getPlayer().isSubmergedInWater() && mod.getPlayer().getAir() < mod.getPlayer().getMaxAir() * 2 / 3) {
            setDebugState("Out of water (air " + mod.getPlayer().getAir() + ")");
            stuckTicks = 0;
            return new adris.altoclef.tasks.movement.GetOutOfWaterTask();
        }
        Task kit = kitTask(mod);
        if (kit != null) return kit;        // Gather first, so the bot is not walking out of the sealed room for one more glowstone.
        // Ostinato: gather once; run23 re-gathered cherry_planks x26 every few placed blocks. Regather only when a step runs dry.
        // Ostinato: run24 crafted bowls/barrel from the gathered cherry cladding planks, forcing a 10-min regather. Gather
        // non-cladding items (loot, containers) first so their crafts eat planks before the cladding count is filled.
        java.util.List<Map.Entry<String, Integer>> order = new java.util.ArrayList<>(materials.entrySet());
        order.sort(java.util.Comparator.comparingInt(e -> isCladding(e.getKey()) ? 1 : 0));
        if (!gathered) for (Map.Entry<String, Integer> need : order) {
            if (skipped.contains(need.getKey())) continue; // Ostinato
            Item item = McCompat.item(need.getKey());
            if (item != null && mod.getItemStorage().getItemCount(item) < need.getValue() && TaskCatalogue.taskExists(item)) {
                // Ostinato: loot-only items (string, bowls) are optional; a fill stores whatever is held. Cap each at 3 min
                // so a spider hunt or cobweb search never blocks the cladding (run22: string x6 ran 5+ min with 0 webs).
                if (lootOnly(need.getKey())) {
                    if (!need.getKey().equals(gatherKey)) { gatherKey = need.getKey(); gatherTicks = 0; }
                    if (++gatherTicks > 3600) {
                        System.out.println("MAZETEST FURNISH skip-loot " + need.getKey() + " have=" + mod.getItemStorage().getItemCount(item) + "/" + need.getValue());
                        skipped.add(need.getKey());
                        continue;
                    }
                }
                // Ostinato: run26 FAILed at y=43 pathing to a cherry log at y=103 after the string search left it deep in a cave.
                // Wood grows on the surface: climb back to room level before hunting logs.
                // Ostinato: run29 spent 14min on "Gathering barrel" cave-pathing at room level (the room is underground): any wood-derived
                // item needs logs, so compare against the real surface (heightmap), not the room Y.
                // Ostinato: run35 yo-yo. MOTION_BLOCKING_NO_LEAVES is the tree canopy, so every
                // chop looked underground and GetToYTask cancelled the gather. Climb only under
                // a real ceiling; once open air is reached, stay there unless we fall back into a cave.
                if (isWoody(need.getKey()) && needsSurface(mod, feet, need.getKey())) {
                    return surfaceClimb(mod, feet, need.getKey());
                }
                setDebugState("Gathering " + need.getKey() + " x" + need.getValue());
                return TaskCatalogue.getItemTask(item, need.getValue());
            }
        }
        if (!gathered) System.out.println("MAZETEST FURNISH gathered skipped=" + skipped);
        gathered = true;
        if (fill != null) {
            if (!fill.isFinished()) {
                return fill;
            }
            fill = null;
            index++;
        }
        // Ostinato: run32 looped 4min on one ceiling block (builder "Failed to place" from the same spot) toward a FAIL.
        // A LIGHT step stuck 60s is deferred to a second pass (new approach angle); stuck again there, it is skipped.
        for (int pass = 0; pass < 2; pass++) {
            while (work != null && index < work.size()) {
                MazeFurnishPlan.Step step = work.get(index);
                if (!secondPass && deferred.contains(index)) { index++; continue; }
                Task sub = stepTask(mod, step);
                if (sub != null) {
                    if (index != stepIdx) { stepIdx = index; stepTicks = 0; }
                    // Ostinato: only place attempts count toward the 15s defer; a mid-cladding regather used to defer every light.
                    boolean placing = sub instanceof PlaceBlockTask || sub instanceof MazeCeilingPlaceTask;
                    if (step.kind() == MazeFurnishPlan.Kind.LIGHT && placing && ++stepTicks > 300) {
                        System.out.println("MAZETEST FURNISH " + (secondPass ? "skip-light " : "defer-light ") + step.x() + " " + step.y() + " " + step.z());
                        if (!secondPass) deferred.add(index);
                        stuckTicks = 0; stuckRecoveries = 0; stepTicks = 0; index++;
                        continue;
                    }
                    setDebugState(step.kind() + " " + step.x() + " " + step.y() + " " + step.z());
                    return sub;
                }
                index++;
            }
            if (secondPass || deferred.isEmpty()) break;
            secondPass = true; index = 0; stepIdx = -1;
            System.out.println("MAZETEST FURNISH retry-deferred " + deferred.size());
        }
        return null;
    }

    private static final Item[] SWORDS = {Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
    private static final Item[] PICKS = {Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE};
    private static final Item[] IRON_ARMOR = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    private static final Item[] KEEP = {Items.BREAD, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.BAKED_POTATO, Items.APPLE, Items.CARROT, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.SHIELD}; // Ostinato
    private String lastKit = ""; private int armorTicks; private boolean armorGaveUp, died; // Ostinato
    private final java.util.Set<Integer> deferred = new java.util.HashSet<>(); // Ostinato
    private boolean secondPass; private int stepIdx = -1, stepTicks; // Ostinato

    // Ostinato: the next survival-kit step, or null when the bot is armed, fed and armored.
    // Run35: score dropped to 17 mid-lighting and CollectFoodTask(40) never returned
    // (infinite bread craft + a 45min hunt), so lights froze at 72 and container/fill/seal never started.
    private Task kitTask(AltoClef mod) {
        if (foodCooldown > 0) foodCooldown--;
        if (lastKit.equals("ready") && !mod.getItemStorage().hasItem(SWORDS)) died = true; // Ostinato: lost the kit = died; skip armor rebuild
        int food = adris.altoclef.util.helpers.StorageHelper.calculateInventoryFoodScore();
        // Place phase already has a buffer. Topping up from 17 to 40 is what ate the furnish timeout.
        int gate = gathered ? 8 : 20;
        if (food >= gate) { foodTicks = 0; foodGaveUp = false; foodCooldown = 0; }
        boolean starveRetry = foodGaveUp && food < 6 && foodCooldown == 0;
        if (starveRetry) foodGaveUp = false;
        String step; Task t;
        // Run39: the harness pick is enough to mine. Forcing a stone sword first sent MineAndCollect
        // at a log 39 blocks above the room (-102,112,55 from y=73) and the 90s abort closed the client.
        boolean armed = mod.getItemStorage().hasItem(SWORDS) || mod.getItemStorage().hasItem(PICKS);
        if (!armed) {
            BlockPos feetNow = mod.getPlayer().getBlockPos();
            if (needsSurface(mod, feetNow, "stick")) return surfaceClimb(mod, feetNow, "stick");
            step = "stone_sword"; t = TaskCatalogue.getItemTask(Items.STONE_SWORD, 1);
        }
        // Run36: stone pick broke mid-ceiling. MazeCeilingPlaceTask then sat on "No pickaxe" for 60s per cell (31 defers, lights stuck at 79).
        else if (!mod.getItemStorage().hasItem(PICKS)) { step = "stone_pickaxe"; t = TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1); }
        else if (food < gate && !foodGaveUp) {
            if (++foodTicks > 1800) {
                System.out.println("MAZETEST FURNISH skip-food food=" + food);
                foodGaveUp = true; foodTicks = 0; foodCooldown = 1200; // Ostinato: 5 min -> 1 min
                step = "ready"; t = null;
            } else {
                // Match the gate. CollectFoodTask(40) keeps hunting after the kit would already be satisfied.
                step = "food"; t = new adris.altoclef.tasks.resources.CollectFoodTask(gate);
            }
        }
        // Ostinato: run39 iron armor blocked lights ~11 min (40 unreachable iron_ore tries) and was rebuilt after a drown.
        // Armor is optional: only before the place phase, only on the first life, 3 min budget.
        else if (!armorGaveUp && !gathered && !died && !adris.altoclef.util.helpers.StorageHelper.isArmorEquippedAll(IRON_ARMOR)) {
            if (++armorTicks > 3600) { System.out.println("MAZETEST FURNISH skip-armor"); armorGaveUp = true; step = "ready"; t = null; }
            else { step = "iron_armor"; t = new adris.altoclef.tasks.misc.EquipArmorTask(IRON_ARMOR); }
        }
        else { step = "ready"; t = null; }
        if (!step.equals(lastKit)) {
            System.out.println("MAZETEST FURNISH kit " + step + " food=" + food);
            lastKit = step;
        }
        if (t != null) setDebugState("Kit " + step);
        return t;
    }

    // Ostinato: true when a wood gather is still under a cave ceiling. A tree canopy is not a ceiling.
    private boolean needsSurface(AltoClef mod, BlockPos feet, String key) {
        int surfaceY = mod.getWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
        if (surfaceY <= 0) return false;
        int solid = 0;
        for (int dy = 2; dy <= 6; dy++) {
            BlockPos p = feet.up(dy);
            BlockState st = mod.getWorld().getBlockState(p);
            if (!st.isAir() && !st.getCollisionShape(mod.getWorld(), p).isEmpty()) solid++;
        }
        boolean buried = solid >= 3 && feet.getY() < surfaceY - 6;
        if (!buried) surfacedFor = key;
        else if (key.equals(surfacedFor) && feet.getY() < surfaceY - 24) surfacedFor = null;
        return buried && !key.equals(surfacedFor);
    }

    // Run37 died at y=28: GetToYTask(surfaceY) sat in a sealed pocket until the 90s abort.
    // Mine the closest ceiling, then step up eight. A full-height goal paths sideways and stalls.
    private Task surfaceClimb(AltoClef mod, BlockPos feet, String key) {
        setDebugState("Surfacing for " + key);
        for (int dy = 2; dy <= 5; dy++) {
            BlockPos p = feet.up(dy);
            BlockState st = mod.getWorld().getBlockState(p);
            if (!st.isAir() && !st.getCollisionShape(mod.getWorld(), p).isEmpty()) {
                return new DestroyBlockTask(p);
            }
        }
        int surfaceY = mod.getWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
        int goal = Math.min(Math.max(surfaceY, feet.getY() + 1), feet.getY() + 8);
        return new adris.altoclef.tasks.movement.GetToYTask(goal);
    }

    private int surfaceStuckLimit() {
        String state = getDebugState();
        return state != null && state.startsWith("Surfacing") ? 6 : 2;
    }
    /** The sub task still needed for this step, or null when the world already matches it. */
    private Task stepTask(AltoClef mod, MazeFurnishPlan.Step step) {
        BlockPos pos = new BlockPos(step.x(), step.y(), step.z());
        BlockState state = mod.getWorld().getBlockState(pos);
        switch (step.kind()) {
            case LIGHT:
            case CONTAINER: {
                List<Block> wanted = blocks(step.blockIds());
                if (wanted.isEmpty() || wanted.contains(state.getBlock())) {
                    return null;
                }
                // Ostinato: breaking a wall/ceiling cell next to lava pours it onto the bot standing under it. Leave it.
                if (step.kind() == MazeFurnishPlan.Kind.LIGHT && !state.isAir() && fluidNext(mod, pos)) {
                    if (skippedFluid.add(pos)) System.out.println("MAZETEST FURNISH skip-fluid " + pos.toShortString());
                    return null;
                }
                // Ostinato: run27/28 stalled 90s+ per floor block: break, fall in the hole, path back, place. Baritone's builder
                // replaces a wrong block in one pass (breaks it, then places), so only destroy when nothing placeable is held.
                Block[] have = wanted.stream().filter(b -> mod.getItemStorage().hasItem(b.asItem())).toArray(Block[]::new);
                // Ostinato: interior ceiling. The cell under it is open, so the builder's side-face search wanders
                // after it breaks the stone. Click the underside of the block above from the floor.
                if (have.length > 0 && openBelow(mod, pos) && !thinLight(have)) return new MazeCeilingPlaceTask(pos, have);
                if (have.length > 0) return new PlaceBlockTask(pos, have);
                if (have.length == 0) { // Ostinato: ran dry mid-cladding; regather the planned count instead of skipping the block
                    for (Block b : wanted) {
                        String id = net.minecraft.registry.Registries.BLOCK.getId(b).getPath();
                        if (materials.containsKey(id) && TaskCatalogue.taskExists(b.asItem())) {
                            if (gathered) System.out.println("MAZETEST FURNISH regather " + id + " x" + materials.get(id));
                            gathered = false;
                            return TaskCatalogue.getItemTask(b.asItem(), materials.get(id));
                        }
                    }
                    return null;
                }
                return new PlaceBlockTask(pos, have);
            }
            case SEAL: {
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    return null;
                }
                List<Block> wanted = blocks(step.blockIds());
                Block[] have = wanted.stream().filter(b -> mod.getItemStorage().hasItem(b.asItem())).toArray(Block[]::new);
                // Falls back to throwaways (dirt, cobble) so the shaft is never left open.
                return new PlaceBlockTask(pos, have.length == 0 ? wanted.toArray(new Block[0]) : have, true, false);
            }
            case FILL: {
                if (state.isAir()) {
                    return null; // container never got placed; nothing to fill
                }
                if (McCompat.block("decorated_pot") == state.getBlock()) {
                    if (!warnedPot) {
                        Debug.logMessage("Maze furnish: decorated pot fills are not supported yet, leaving pots empty");
                        warnedPot = true;
                    }
                    return null;
                }
                List<ItemTarget> targets = new ArrayList<>();
                for (MazeFurnishPlan.Item it : step.contents()) {
                    Item item = McCompat.item(it.id());
                    if (item != null && mod.getItemStorage().getItemCount(item) > 0) {
                        targets.add(new ItemTarget(item, Math.min(it.count(), mod.getItemStorage().getItemCount(item))));
                    }
                }
                if (targets.isEmpty()) {
                    // Ostinato: paper (sugar cane) is often skipped. Store a small stack of something already
                    // gathered that later steps do not still need, so the barrel is not empty. If nothing is spare,
                    // mine cobble before this step is allowed to count as done.
                    ItemTarget fallback = fallbackLoot(mod);
                    if (fallback == null) {
                        int keep = stillNeeded(mod, Items.COBBLESTONE);
                        setDebugState("Spare cobble for barrel");
                        return TaskCatalogue.getItemTask(Items.COBBLESTONE, Math.max(keep + 8, 8));
                    }
                    if (!loggedFill) {
                        System.out.println("MAZETEST FURNISH fill-fallback " + net.minecraft.registry.Registries.ITEM.getId(fallback.getMatches()[0]).getPath() + " x" + fallback.getTargetCount());
                        loggedFill = true;
                    }
                    targets.add(fallback);
                }
                // one store per container; the step advances when it finishes, and Ostinato's ledger stops refills
                fill = new StoreInContainerTask(pos, false, targets.toArray(new ItemTarget[0]));
                return fill;
            }
            default:
                return null;
        }
    }

    private static List<Block> blocks(List<String> ids) {
        List<Block> out = new ArrayList<>();
        for (Iterator<String> it = ids.iterator(); it.hasNext(); ) {
            Block b = McCompat.block(it.next());
            if (b != null) {
                out.add(b);
            }
        }
        return out;
    }

    // Ostinato: torches and lanterns cannot be clicked onto a ceiling underside. Full blocks can.
    private static boolean thinLight(Block[] have) {
        for (Block b : have) {
            String p = net.minecraft.registry.Registries.BLOCK.getId(b).getPath();
            if (p.contains("torch") || p.contains("lantern") || p.contains("candle") || p.equals("end_rod")) return true;
        }
        return false;
    }

    private final java.util.Set<BlockPos> skippedFluid = new java.util.HashSet<>(); // Ostinato

    // Ostinato: true when lava touches this cell from above or a side.
    private static boolean fluidNext(AltoClef mod, BlockPos pos) {
        for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.values()) {
            if (d == net.minecraft.util.math.Direction.DOWN) continue;
            net.minecraft.fluid.Fluid fluid = mod.getWorld().getBlockState(pos.offset(d)).getFluidState().getFluid();
            if (fluid == net.minecraft.fluid.Fluids.LAVA || fluid == net.minecraft.fluid.Fluids.FLOWING_LAVA) return true;
        }
        return false;
    }

    // Ostinato: true when the cell under the place target is open room air (interior ceiling).
    private static boolean openBelow(AltoClef mod, BlockPos pos) {
        BlockPos below = pos.down();
        BlockState s = mod.getWorld().getBlockState(below);
        return s.isAir() || s.getCollisionShape(mod.getWorld(), below).isEmpty();
    }

    // Ostinato: blocks of this item that are still the wrong block in the world.
    private int stillNeeded(AltoClef mod, Item item) {
        String id = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
        if (work == null || !materials.containsKey(id)) return 0;
        int need = 0;
        for (MazeFurnishPlan.Step s : work) {
            if (s.kind() == MazeFurnishPlan.Kind.FILL) continue;
            if (!id.equals(MazeFurnishPlan.pick(s.blockIds(), MazeFurnishTask::obtainable))) continue;
            BlockState st = mod.getWorld().getBlockState(new BlockPos(s.x(), s.y(), s.z()));
            if (!blocks(s.blockIds()).contains(st.getBlock())) need++;
        }
        return need;
    }

    // Ostinato: one natural stack for the barrel when the rolled loot was never gathered.
    private ItemTarget fallbackLoot(AltoClef mod) {
        Item[] junk = {Items.STICK, Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS,
                Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING,
                Items.ROTTEN_FLESH, Items.BONE, Items.STRING, Items.FEATHER, Items.ARROW, Items.WHEAT, Items.APPLE,
                Items.COBBLESTONE, Items.DIRT, Items.OAK_PLANKS, Items.POLISHED_DIORITE};
        for (Item it : junk) {
            int have = mod.getItemStorage().getItemCount(it);
            int keep = stillNeeded(mod, it);
            if (have > keep) return new ItemTarget(it, Math.min(8, have - keep));
        }
        return null;
    }

    @Override
    public boolean isFinished() {
        return materials != null && work != null && index >= work.size();
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef.getInstance().getClientBaritoneSettings().autoTool.value = prevAutoTool; // Ostinato
        AltoClef.getInstance().getBehaviour().pop();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof MazeFurnishTask t && t.plan == plan;
    }

    @Override
    protected String toDebugString() {
        return "Furnishing maze room " + plan.summary;
    }
}
