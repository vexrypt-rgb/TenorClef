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
        else if (++stuckTicks >= 1800 && stuckRecoveries < 2) { // Ostinato: two recoveries before FAIL
            // Ostinato: first 90s stall: blacklist the block the chain is fighting, wander 30s, retry. Second stall FAILs.
            for (Task t = this; t != null; t = t.getSub()) if (t instanceof DestroyBlockTask d) { mod.getBlockScanner().requestBlockUnreachable(d.getPos(), 0); }
            System.out.println("MAZETEST FURNISH stuck-recover stuck=" + getDebugState() + " feet=" + feet.toShortString());
            stuckRecoveries++; stuckTicks = 0; stuckAnchor = feet;
            unstickTicks = 0; unstick = new adris.altoclef.tasks.movement.TimeoutWanderTask(16);
            return unstick;
        }
        else if (stuckTicks >= 1800) {
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
                if ((need.getKey().endsWith("planks") || need.getKey().endsWith("log")) && !plan.steps.isEmpty() && feet.getY() < plan.steps.get(0).y() - 12) {
                    setDebugState("Surfacing for " + need.getKey());
                    return new adris.altoclef.tasks.movement.GetToYTask(plan.steps.get(0).y());
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
        while (index < plan.steps.size()) {
            MazeFurnishPlan.Step step = plan.steps.get(index);
            Task sub = stepTask(mod, step);
            if (sub != null) {
                setDebugState(step.kind() + " " + step.x() + " " + step.y() + " " + step.z());
                return sub;
            }
            index++;
        }
        return null;
    }

    private static final Item[] SWORDS = {Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
    private static final Item[] IRON_ARMOR = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    private static final Item[] KEEP = {Items.BREAD, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.BAKED_POTATO, Items.APPLE, Items.CARROT, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.SHIELD}; // Ostinato
    private String lastKit = "";

    // Ostinato: the next survival-kit step, or null when the bot is armed, fed and armored.
    private Task kitTask(AltoClef mod) {
        String step; Task t;
        if (!mod.getItemStorage().hasItem(SWORDS)) { step = "stone_sword"; t = TaskCatalogue.getItemTask(Items.STONE_SWORD, 1); }
        else if (adris.altoclef.util.helpers.StorageHelper.calculateInventoryFoodScore() < 20) { step = "food"; t = new adris.altoclef.tasks.resources.CollectFoodTask(40); }
        else if (!adris.altoclef.util.helpers.StorageHelper.isArmorEquippedAll(IRON_ARMOR)) { step = "iron_armor"; t = new adris.altoclef.tasks.misc.EquipArmorTask(IRON_ARMOR); }
        else { step = "ready"; t = null; }
        if (!step.equals(lastKit)) {
            System.out.println("MAZETEST FURNISH kit " + step + " food=" + adris.altoclef.util.helpers.StorageHelper.calculateInventoryFoodScore());
            lastKit = step;
        }
        if (t != null) setDebugState("Kit " + step);
        return t;
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
                // Ostinato: run27 stalled ~90s on every floor block: the bot stood on (or fell into) the block it was replacing.
                BlockPos me = mod.getPlayer().getBlockPos();
                if (me.down().equals(pos) || me.equals(pos)) {
                    return new adris.altoclef.tasks.movement.RunAwayFromPositionTask(2, me.getY(), pos);
                }
                if (!state.isAir()) {
                    return new DestroyBlockTask(pos);
                }
                Block[] have = wanted.stream().filter(b -> mod.getItemStorage().hasItem(b.asItem())).toArray(Block[]::new);
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
                    return null;
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

    @Override
    public boolean isFinished() {
        return materials != null && index >= plan.steps.size();
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
