package adris.altoclef.allblocks;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.container.StoreInContainerTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.storage.ContainerCache;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ostinato: one {@code #allblocks} run (also {@code @allblocks}). See {@link AllBlocks} for the layout.
 * <p>
 * Every tick it does the first thing that applies:
 * <ol>
 *   <li>Write or close a sign edit screen (chest labels).</li>
 *   <li>Survival kit ({@link AllBlocksKit}).</li>
 *   <li>Deposit: when 8 gathered items wait, the inventory is nearly full, or nothing is left to gather, walk home
 *   and put one of each into its chest (placing the chest and its label sign first if needed).</li>
 *   <li>Place: a stored block with a spare copy in the inventory goes into the next grid cell.</li>
 *   <li>Gather the next item (2 of a placeable block: one for the chest, one for the grid), cheapest first. A gather
 *   that times out or ends without the item is retried once after everything else, then reported as stuck.</li>
 *   <li>Blocks sweep: a stored block whose grid copy was used up is gathered once more and placed (first try
 *   right away, a retry at the end).</li>
 * </ol>
 * All gathering goes through TaskCatalogue (mining, crafting, smelting, mobs, loot); the run never sends a command.
 */
public class AllBlocksTask extends Task {

    private static final int DEPOSIT_BATCH = 4;
    private static final int MIN_FREE_SLOTS = 5;
    /** First try of an item; the retry after everything else gets {@link #RETRY_MINUTES}. */
    private static final int GATHER_MINUTES = 10;
    private static final int RETRY_MINUTES = 25;
    private static final int DEPOSIT_MINUTES = 6;
    private static final int PLACE_MINUTES = 4;
    private static final int LABEL_MINUTES = 3;
    private static final int TPM = 20 * 60;

    private final BlockPos originArg;
    private final boolean retryStuck;

    private AllBlocksProgress progress;
    private Path file;
    private List<AllBlocksPlan.Entry> plan;
    private final Map<String, AllBlocksPlan.Entry> byId = new HashMap<>();
    private BlockPos origin;
    private final AllBlocksKit kit = new AllBlocksKit();
    private long ticks;
    private boolean done;

    /** Gathered, in the inventory, not yet in a chest. */
    private final Set<String> pending = new LinkedHashSet<>();

    // Current gather.
    private AllBlocksPlan.Entry gathering;
    private Task gatherTask;
    private int gatherWant;
    private long gatherStart;
    private boolean sweep;

    // Current deposit / label / placement step.
    private Task stepTask;
    private String stepKey;
    private long stepStart;

    // Label sign waiting for its edit screen.
    private BlockPos labelPos;
    private String[] labelLines;

    // Placement.
    private String placingId;
    private int placingCell = -1;
    private int floorTry;
    private static final String[] FLOORS = {"dirt", "sand", "soul_sand", "end_stone"};

    public AllBlocksTask(BlockPos originArg, boolean retryStuck) {
        this.originArg = originArg;
        this.retryStuck = retryStuck;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onStart() {
        AltoClef mod = AltoClef.getInstance();
        mod.getBehaviour().push();
        // The bot's own gathering dug the first chest's floor out from under it (it mines wherever the closest block
        // is). Nothing breaks blocks in the storage area and grid except the step clearing a cell.
        mod.getBehaviour().avoidBlockBreaking(this::protectedArea);
        if (progress != null) {
            // Resumed after another chain (eating, mob defense) had control: keep the state.
            protectedNow.clear();
            protectPending(mod);
            return;
        }
        String world = AllBlocks.worldKey();
        file = AllBlocksProgress.file(MinecraftClient.getInstance().runDirectory.toPath(), world);
        progress = AllBlocksProgress.load(file, world);
        plan = AllBlocksPlan.build();
        byId.clear();
        for (AllBlocksPlan.Entry e : plan) byId.put(e.id, e);
        if (originArg != null) {
            progress.origin = new int[]{originArg.getX(), originArg.getY(), originArg.getZ()};
        }
        // Without a saved or given origin, siteStep() finds one before anything is built.
        origin = progress.origin == null ? null : new BlockPos(progress.origin[0], progress.origin[1], progress.origin[2]);
        if (retryStuck) {
            for (AllBlocksProgress.Failure f : progress.failures.values()) {
                f.stuck = false;
                f.attempts = 0;
            }
        }
        // Nothing stored yet: the chest assignment is still free to follow the current plan.
        if (progress.stored.isEmpty()) {
            progress.chestOf.clear();
            progress.labeled.clear();
        }
        assignChests();
        // Items gathered before a restart are still in the inventory: deposit them first.
        pending.clear();
        for (AllBlocksPlan.Entry e : plan) {
            if (e.candidate() && !progress.stored.containsKey(e.id) && count(e.item) > 0) pending.add(e.id);
        }
        protectPending(mod);
        progress.save(file);
        Debug.logMessage("ALLBLOCKS start world=" + world + " origin=" + (origin == null ? "(not chosen yet)" : origin.toShortString()) + " file=" + file
                + " generated catalogue entries=" + TaskCatalogue.generatedCount());
        Debug.logMessage("ALLBLOCKS " + statusLine());
    }

    @Override
    protected void onStop(Task interruptTask) {
        if (progress != null) progress.save(file);
        AltoClef.getInstance().getBehaviour().pop();
        // TenorClef also stops (not interrupts) the user chain while a survival chain has control; the task resumes
        // afterwards, so "stopped" is logged by AllBlocks.stop() instead.
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof AllBlocksTask;
    }

    @Override
    protected String toDebugString() {
        return "All blocks: " + (gathering != null ? "gather " + gathering.id : placingId != null ? "place " + placingId : stepKey != null ? stepKey : "idle");
    }

    // ---------------------------------------------------------------- tick

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        ticks++;
        if (handleSignScreen()) {
            if (labelChest >= 0) {
                progress.labeled.put(labelChest, true);
                labelChest = -1;
                progress.save(file);
            }
            return null;
        }
        if (labelChest >= 0) {
            if (ticks < labelDeadline) {
                setDebugState("Waiting for the sign screen of label " + labelChest);
                return null;
            }
            Debug.logMessage("ALLBLOCKS label for chest " + labelChest + " not written (no sign screen); retried next session");
            labelFailed.add(labelChest);
            labelChest = -1;
            labelPos = null;
            labelLines = null;
        }
        if (ticks % (20 * 60) == 0) progress.save(file);
        if (ticks % (20 * 60 * 10) == 1) writeReport();

        // A running step (deposit, label, placement) finishes before anything else starts.
        if (stepTask != null) {
            Task t = continueStep(mod);
            if (t != null) return t;
        }

        Task night = nightStep(mod);
        if (night != null) return night;

        if (gathering == null) {
            Task k = kit.tick(mod, ticks);
            if (k != null) {
                setDebugState("Kit: " + kit.current());
                return k;
            }
        }

        if (origin == null) return siteStep(mod);

        refreshPending(mod);
        // A gather in progress is only interrupted when the inventory is nearly full.
        if (gathering != null && freeSlots(mod) >= MIN_FREE_SLOTS) return gatherStep(mod);
        if (!pending.isEmpty() && shouldDeposit(mod)) {
            Task t = depositStep(mod);
            if (t != null) return t;
        }

        Task place = placeStep(mod);
        if (place != null) return place;

        return gatherStep(mod);
    }

    // ---------------------------------------------------------------- night

    private Task sleepTask;
    private long sleepStart;
    private long nightCooldownUntil;

    /**
     * At night (in the overworld), sleep: TenorClef's bed task places a bed, sets the spawn point there and stays in
     * it until morning. Mobs at night killed every early run. A gather in progress waits (its timer is held). After 8
     * minutes without sleeping the bot gives up for 10 minutes.
     */
    private Task nightStep(AltoClef mod) {
        long time = mod.getWorld().getTimeOfDay() % 24000;
        boolean night = time >= 12542 && time <= 23460;
        // Dusk: bank what is carried and get home, so the bed (and the night) is next to the chests. Items carried
        // into the night were lost with every death.
        boolean dusk = time >= 11200 && time < 12542;
        if (dusk && origin != null && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD && ticks >= nightCooldownUntil) {
            if (gathering != null) gatherStart++;
            kit.hold();
            if (!pending.isEmpty()) {
                Task t = depositStep(mod);
                if (t != null) {
                    setDebugState("Dusk: banking " + pending.size() + " items");
                    return t;
                }
            }
            if (mod.getPlayer().getBlockPos().getSquaredDistance(origin) > 12 * 12) {
                setDebugState("Dusk: heading home");
                return new adris.altoclef.tasks.movement.GetToBlockTask(origin.add(-2, 0, -2));
            }
        }
        if (!night || ticks < nightCooldownUntil || WorldHelper.getCurrentDimension() != Dimension.OVERWORLD
                || (sleepTask == null && !AllBlocksKit.hasBed(mod))) {
            if (sleepTask != null && !night) Debug.logMessage("ALLBLOCKS morning");
            sleepTask = null;
            return null;
        }
        if (sleepTask == null) {
            sleepTask = new adris.altoclef.tasks.misc.SleepThroughNightTask();
            sleepStart = ticks;
            Debug.logMessage("ALLBLOCKS night: going to bed" + (gathering != null ? " (holding gather of " + gathering.id + ")" : ""));
        }
        if (ticks - sleepStart > 8L * TPM && !mod.getPlayer().isSleeping()) {
            Debug.logMessage("ALLBLOCKS night: no sleep after 8 min (" + chain(sleepTask) + "); carrying on");
            nightCooldownUntil = ticks + 10L * TPM;
            sleepTask = null;
            return null;
        }
        if (gathering != null) gatherStart++;
        if (stepTask != null) stepStart++;
        kit.hold();
        setDebugState("Night: sleeping");
        return sleepTask;
    }

    // ---------------------------------------------------------------- site

    private int siteTries;
    private Task siteWalk;

    /**
     * No origin yet: take the closest dry, flat spot in the loaded area. When there is none (spawn in a lake, a
     * mountain side), walk about 96 blocks out, a different direction each time, and look again; after 8 tries the
     * player's feet are used.
     */
    private Task siteStep(AltoClef mod) {
        if (siteWalk != null && !siteWalk.isFinished() && ticks - siteWalkStart < 3L * TPM) {
            setDebugState("Looking for a dry flat site (try " + siteTries + ")");
            return siteWalk;
        }
        int placeable = 0;
        for (AllBlocksPlan.Entry e : plan) if (e.candidate() && e.placeable() && e.unsupportedReason() == null) placeable++;
        BlockPos o = AllBlocks.pickOrigin(mod, (placeable + AllBlocks.CELLS_PER_ROW - 1) / AllBlocks.CELLS_PER_ROW);
        if (o == null && siteTries >= 8) o = mod.getPlayer().getBlockPos();
        if (o != null) {
            origin = o;
            progress.origin = new int[]{o.getX(), o.getY(), o.getZ()};
            progress.save(file);
            Debug.logMessage("ALLBLOCKS origin " + o.toShortString() + " (chests from here, grid " + AllBlocks.GRID_Z + " blocks south)");
            siteWalk = null;
            return null;
        }
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        int[] d = dirs[siteTries % dirs.length];
        BlockPos feet = mod.getPlayer().getBlockPos();
        siteTries++;
        siteWalkStart = ticks;
        siteWalk = new adris.altoclef.tasks.movement.GetToXZTask(feet.getX() + d[0] * 96, feet.getZ() + d[1] * 96);
        Debug.logMessage("ALLBLOCKS walking " + (d[0] * 96) + "," + (d[1] * 96) + " to look for a site (try " + siteTries + ")");
        return siteWalk;
    }

    private long siteWalkStart;

    // ---------------------------------------------------------------- gather

    private Task gatherStep(AltoClef mod) {
        if (gathering != null) {
            int have = count(gathering.item);
            if (have >= gatherWant) {
                long took = (ticks - gatherStart) / 20;
                progress.gatherTicks += ticks - gatherStart;
                Debug.logMessage("ALLBLOCKS GOT " + gathering.id + " x" + have + " in " + took + "s");
                if (!progress.stored.containsKey(gathering.id)) pending.add(gathering.id);
                protectPending(mod);
                gathering = null;
                gatherTask = null;
                return null;
            }
            String fail = null;
            if (gatherTask.isFinished()) {
                fail = "gather task finished without the item (have " + have + "/" + gatherWant + "): " + chain(gatherTask);
            } else if (ticks - gatherStart > (long) gatherMinutes * TPM) {
                fail = "timed out after " + gatherMinutes + " min (have " + have + "/" + gatherWant + "): " + chain(gatherTask);
            }
            if (fail == null) {
                setDebugState("Gather " + gathering.id + (sweep ? " (blocks sweep)" : "") + " " + (ticks - gatherStart) / 20 + "s");
                return gatherTask;
            }
            fail(gathering.id, fail);
            String family = family(gathering.id);
            if (family != null && deferred.add(family)) {
                Debug.logMessage("ALLBLOCKS defer the other " + family + "_* items to the retry pass");
            }
            gathering = null;
            gatherTask = null;
            return null;
        }

        AllBlocksPlan.Entry next = nextToGather();
        if (next == null) {
            if (!pending.isEmpty()) {
                return depositStep(mod);
            }
            done = true;
            Debug.logMessage("ALLBLOCKS DONE " + statusLine());
            progress.save(file);
            return null;
        }
        boolean stored = progress.stored.containsKey(next.id);
        sweep = stored;
        gatherWant = stored ? 1 : (needsPlacement(next) ? 2 : 1);
        if (kitItem(next.id) || adris.altoclef.multiversion.item.ItemVer.getFoodComponent(next.item) != null) gatherWant++; // keep one for the kit
        ResourceTaskHolder holder = new ResourceTaskHolder(next, gatherWant);
        if (holder.task == null) {
            fail(next.id, next.unsupportedReason() != null ? next.unsupportedReason() : "TaskCatalogue.getItemTask returned null");
            return null;
        }
        gathering = next;
        gatherTask = holder.task;
        gatherStart = ticks;
        AllBlocksProgress.Failure prev = progress.failures.get(stored ? "sweep:" + next.id : next.id);
        gatherMinutes = prev != null && prev.attempts > 0 ? RETRY_MINUTES : GATHER_MINUTES;
        Debug.logMessage("ALLBLOCKS gather " + next.id + " x" + gatherWant + (sweep ? " (blocks sweep)" : "")
                + " via " + gatherTask.getClass().getSimpleName() + " | kit: " + AllBlocksKit.summary(mod));
        return gatherTask;
    }

    /** Needs a TaskCatalogue entry; logs the exception instead of killing the run. */
    private static final class ResourceTaskHolder {
        final Task task;

        ResourceTaskHolder(AllBlocksPlan.Entry e, int count) {
            Task t = null;
            try {
                if (TaskCatalogue.taskExists(e.item)) t = TaskCatalogue.getItemTask(e.item, count);
            } catch (RuntimeException ex) {
                Debug.logWarning("ALLBLOCKS getItemTask(" + e.id + ") threw " + ex);
            }
            task = t;
        }
    }

    /**
     * First item to gather: never-tried before deferred before once-failed, cheapest first; then the blocks sweep.
     * An item is deferred when another item of its family (same first word: acacia_, red_, ...) failed this
     * session, since they usually share the missing source.
     */
    private AllBlocksPlan.Entry nextToGather() {
        // A stored block whose grid copy got used up (dirt and cobblestone double as building filler): get it again
        // now, while its source is near, rather than in a sweep at the very end.
        for (AllBlocksPlan.Entry e : plan) {
            if (!e.candidate() || !progress.stored.containsKey(e.id) || !needsPlacement(e) || count(e.item) > 0) continue;
            AllBlocksProgress.Failure f = progress.failures.get("sweep:" + e.id);
            if (f != null && (f.stuck || f.attempts > 0)) continue;
            return e;
        }
        AllBlocksPlan.Entry retry = null, later = null;
        for (AllBlocksPlan.Entry e : plan) {
            // (A kit item held back for the kit is still gathered: the spare is what goes in the chest.)
            if (!e.candidate() || progress.stored.containsKey(e.id) || (pending.contains(e.id) && depositable(e.id))) continue;
            if (e.unsupportedReason() != null) continue;
            AllBlocksProgress.Failure f = progress.failures.get(e.id);
            if (f == null || f.attempts == 0) {
                if (!deferred.contains(family(e.id))) return e;
                if (later == null) later = e;
            } else if (!f.stuck && retry == null) {
                retry = e;
            }
        }
        if (later != null) return later;
        if (retry != null) return retry;
        for (AllBlocksPlan.Entry e : plan) {
            if (!e.candidate() || !progress.stored.containsKey(e.id) || !needsPlacement(e) || count(e.item) > 0) continue;
            AllBlocksProgress.Failure f = progress.failures.get("sweep:" + e.id);
            if (f != null && f.stuck) continue;
            return e;
        }
        return null;
    }

    private final Set<String> deferred = new LinkedHashSet<>();
    private int gatherMinutes = GATHER_MINUTES;

    private static String family(String id) {
        int i = id.indexOf('_');
        return i > 0 ? id.substring(0, i) : null;
    }

    private void fail(String id, String reason) {
        String key = progress.stored.containsKey(id) ? "sweep:" + id : id;
        AllBlocksProgress.Failure f = progress.failure(key);
        f.attempts++;
        f.last = reason;
        if (f.attempts >= 2) f.stuck = true;
        Debug.logMessage("ALLBLOCKS FAIL " + key + " attempt " + f.attempts + (f.stuck ? " (stuck)" : "") + ": " + reason);
        progress.save(file);
    }

    /** The task chain under {@code t}, deepest last, with each debug state: the failing code path. */
    static String chain(Task t) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (t != null && depth++ < 12) {
            if (sb.length() > 0) sb.append(" > ");
            sb.append(t.getClass().getSimpleName());
            String s = t.getDebugState();
            if (s != null && !s.isEmpty()) sb.append("[").append(s).append("]");
            t = t.getSub();
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- deposit

    private void assignChests() {
        int[] perChest = new int[256];
        for (int v : progress.chestOf.values()) if (v < perChest.length) perChest[v]++;
        int idx = progress.chestOf.size();
        // Only items with a gather path get a chest; one that gains a path later goes into a later chest, so the
        // labels already written stay true.
        for (AllBlocksPlan.Entry e : plan) {
            if (!e.candidate() || progress.chestOf.containsKey(e.id) || e.unsupportedReason() != null) continue;
            int k = idx / 27;
            while (k < perChest.length && perChest[k] >= 27) k++;
            progress.chestOf.put(e.id, k);
            perChest[k]++;
            idx++;
        }
    }

    private boolean shouldDeposit(AltoClef mod) {
        int ready = 0;
        for (String id : pending) if (depositable(id)) ready++;
        if (ready == 0) return false;
        if (ready >= DEPOSIT_BATCH || freeSlots(mod) < MIN_FREE_SLOTS) return true;
        // Close to home anyway: drop things off.
        return WorldHelper.getCurrentDimension() == Dimension.OVERWORLD
                && mod.getPlayer().getBlockPos().getSquaredDistance(origin) < 24 * 24;
    }

    private Task depositStep(AltoClef mod) {
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Going home to the overworld to deposit");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }
        int chest = Integer.MAX_VALUE;
        for (String id : pending) if (depositable(id)) chest = Math.min(chest, progress.chestOf.getOrDefault(id, 0));
        if (chest == Integer.MAX_VALUE) return null;
        BlockPos pos = AllBlocks.chestPos(origin, chest);
        Task home = goHomeFirst(mod, pos, "deposit");
        if (home != null) return home;
        Block at = mod.getWorld().getBlockState(pos).getBlock();
        // The walkway north of the chest is where the bot stands to open it: keep it clear and floored.
        Task stand = prepareCell(mod, pos.north(), "stand cell of chest " + chest, null);
        if (stand != null) return stand;
        if (at != Blocks.CHEST) {
            Task t = prepareCell(mod, pos, "chest " + chest, null);
            if (t != null) return t;
            if (spare(Items.CHEST) < 1) {
                return startStep("get a chest for chest " + chest, TaskCatalogue.getItemTask("chest", count(Items.CHEST) + 1), 8);
            }
            return startStep("place chest " + chest, new PlaceBlockTask(pos, Blocks.CHEST), PLACE_MINUTES);
        }
        if (!Boolean.TRUE.equals(progress.labeled.get(chest)) && !labelFailed.contains(chest) && stepTask == null) {
            Task t = labelStep(mod, chest);
            if (t != null) return t;
        }
        List<ItemTarget> targets = new ArrayList<>();
        for (String id : pending) {
            if (progress.chestOf.getOrDefault(id, -1) == chest && depositable(id)) {
                targets.add(new ItemTarget(byId.get(id).item, 1));
            }
        }
        if (targets.isEmpty()) return null;
        depositChest = chest;
        return startStep("deposit " + targets.size() + " into chest " + chest,
                new StoreInContainerTask(pos, false, targets.toArray(new ItemTarget[0])), DEPOSIT_MINUTES);
    }

    /**
     * Far from home the storage chunks are not loaded and read as air: the bot planned 7-deep floor fills from that.
     * Walk until the spot is loaded and close before looking at it.
     */
    private Task goHomeFirst(AltoClef mod, BlockPos spot, String why) {
        if (mod.getChunkTracker().isChunkLoaded(spot) && mod.getPlayer().getBlockPos().getSquaredDistance(spot) < 48 * 48) {
            return null;
        }
        setDebugState("Heading home to " + why);
        return new adris.altoclef.tasks.movement.GetToBlockTask(spot.north(2));
    }

    /**
     * Kit items (pickaxes, swords, beds) are also on the list: depositing the only one took the bot's tool or its bed
     * for the night. Those go in the chest only when a spare is carried.
     */
    private static boolean kitItem(String id) {
        return id.endsWith("_pickaxe") || id.endsWith("_sword") || id.endsWith("_bed");
    }

    private boolean depositable(String id) {
        Item item = byId.get(id).item;
        int have = count(item);
        // Food too: depositing the last of it sent the kit back out hunting.
        boolean keepOne = kitItem(id) || adris.altoclef.multiversion.item.ItemVer.getFoodComponent(item) != null;
        return have > (keepOne ? 1 : 0);
    }

    private int depositChest = -1;
    private int labelChest = -1;
    private int labelFor = -1;
    private long labelDeadline;
    private final Set<Integer> labelFailed = new LinkedHashSet<>();

    /** After a deposit step: everything the chest now holds is stored. */
    private void afterDeposit(AltoClef mod) {
        if (depositChest < 0) return;
        BlockPos pos = AllBlocks.chestPos(origin, depositChest);
        Optional<ContainerCache> cache = mod.getItemStorage().getContainerAtPosition(pos);
        for (String id : new ArrayList<>(pending)) {
            if (progress.chestOf.getOrDefault(id, -1) != depositChest) continue;
            Item item = byId.get(id).item;
            if (cache.isPresent() && cache.get().getItemCount(item) > 0) {
                progress.stored.put(id, depositChest);
                pending.remove(id);
                Debug.logMessage("ALLBLOCKS DEPOSIT " + id + " -> chest " + depositChest + " at " + pos.toShortString()
                        + " (verified in chest; stored " + progress.stored.size() + ")");
            }
        }
        depositChest = -1;
        protectPending(mod);
        progress.save(file);
    }

    // ---------------------------------------------------------------- labels

    private Task labelStep(AltoClef mod, int chest) {
        BlockPos sign = AllBlocks.signPos(origin, chest);
        Block at = mod.getWorld().getBlockState(sign).getBlock();
        if (isSign(at)) {
            // Placed earlier but the text never went out (crash between the two): the label stays blank.
            progress.labeled.put(chest, true);
            return null;
        }
        Task t = prepareCell(mod, sign, "label " + chest, null);
        if (t != null) return t;
        Item[] signs = ItemHelper.WOOD_SIGN;
        if (spare(signs) < 1) {
            return startStep("get a sign for label " + chest, TaskCatalogue.getItemTask("sign", count(signs) + 1), LABEL_MINUTES * 2);
        }
        labelPos = sign;
        labelFor = chest;
        labelLines = AllBlocks.labelLines(chest, progress.chestOf);
        List<Block> blocks = new ArrayList<>();
        for (Item s : signs) if (spare(s) > 0) blocks.add(Block.getBlockFromItem(s));
        return startStep("label chest " + chest, new PlaceBlockTask(sign, blocks.toArray(new Block[0])), LABEL_MINUTES);
    }

    /** Standing or wall sign: a label placed against the chest's side becomes a wall sign. */
    private static boolean isSign(Block b) {
        return b instanceof AbstractSignBlock;
    }

    /**
     * A sign edit screen opened (a label or a grid sign was just placed). Labels get their text; the server keeps the
     * first update it gets, so ours goes out before the screen's own (blank) one when it closes.
     */
    private boolean handleSignScreen() {
        Screen screen = adris.altoclef.multiversion.ScreenVer.current(MinecraftClient.getInstance());
        //#if MC >= 12000
        if (screen instanceof net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen) {
            if (labelPos != null && labelLines != null && MinecraftClient.getInstance().getNetworkHandler() != null) {
                //#if MC >= 260000
                //$$ net.minecraft.client.Minecraft.getInstance().getConnection().send(new net.minecraft.network.protocol.game.ServerboundSignUpdatePacket(
                //$$         labelPos, java.util.List.of(labelLines), net.minecraft.world.level.block.entity.SignTextSlot.FRONT));
                //#else
                MinecraftClient.getInstance().getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket(
                        labelPos, true, labelLines[0], labelLines[1], labelLines[2], labelLines[3]));
                //#endif
                Debug.logMessage("ALLBLOCKS LABEL " + labelPos.toShortString() + " \"" + String.join(" / ", labelLines) + "\"");
                if (labelFor >= 0) {
                    progress.labeled.put(labelFor, true);
                    progress.save(file);
                }
                labelPos = null;
                labelLines = null;
            }
            screen.close();
            return true;
        }
        //#endif
        return false;
    }

    // ---------------------------------------------------------------- placement

    private boolean needsPlacement(AllBlocksPlan.Entry e) {
        if (!e.placeable() || progress.placed.containsKey(e.id) || progress.placeSkipped.containsKey(e.id)) return false;
        Block b = e.block();
        if (b instanceof CropBlock || b instanceof StemBlock) {
            // Crops stand only on farmland, which the grid does not make (no hoeing step yet).
            progress.placeSkipped.put(e.id, "crop: needs farmland (hoed dirt), which the grid does not make");
            Debug.logMessage("ALLBLOCKS PLACE-SKIP " + e.id + ": needs farmland");
            return false;
        }
        return true;
    }

    private Task placeStep(AltoClef mod) {
        adoptPlaced(mod);
        if (placingId == null) {
            for (AllBlocksPlan.Entry e : plan) {
                if (!e.candidate() || !progress.stored.containsKey(e.id) || !needsPlacement(e)) continue;
                if (count(e.item) < 1) continue;
                placingId = e.id;
                placingCell = -1;
                floorTry = -1;
                break;
            }
            if (placingId == null) return null;
        }
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            setDebugState("Going home to the overworld to place " + placingId);
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }
        AllBlocksPlan.Entry e = byId.get(placingId);
        if (count(e.item) < 1) {
            placingId = null;
            return null;
        }
        Block block = e.block();
        if (placingCell < 0) {
            int n = progress.nextCell;
            while (progress.badCells.containsKey(n)) n++;
            placingCell = n;
        }
        BlockPos cell = AllBlocks.cellPos(origin, placingCell);
        Task home = goHomeFirst(mod, cell, "place " + e.id);
        if (home != null) return home;
        BlockState cellState = mod.getWorld().getBlockState(cell);
        if (cellState.getBlock() == block) {
            progress.placed.put(e.id, placingCell);
            progress.nextCell = Math.max(progress.nextCell, placingCell + 1);
            Debug.logMessage("ALLBLOCKS PLACED " + e.id + " in cell " + placingCell + " at " + cell.toShortString()
                    + " (placed " + progress.placed.size() + ")");
            placingId = null;
            progress.save(file);
            return null;
        }
        if (!cellState.getFluidState().isEmpty()) {
            progress.badCells.put(placingCell, "fluid in the cell");
            placingCell = -1;
            return null;
        }
        Block floorBlock = null;
        if (floorTry >= 0) {
            Item fi = McCompat.item(FLOORS[floorTry]);
            if (fi != null) floorBlock = Block.getBlockFromItem(fi);
        }
        if (floorBlock != null && spare(floorBlock.asItem()) < 1
                && mod.getWorld().getBlockState(cell.down()).getBlock() != floorBlock) {
            return startStep("get " + FLOORS[floorTry] + " floor for " + e.id,
                    TaskCatalogue.getItemTask(floorBlock.asItem(), count(floorBlock.asItem()) + 1), 10);
        }
        Task t = prepareCell(mod, cell, "cell " + placingCell, floorBlock);
        if (t != null) return t;
        if (!block.getDefaultState().canPlaceAt(mod.getWorld(), cell)) {
            // Wrong ground for this block: lay the next floor that might hold it, then look again.
            do {
                floorTry++;
            } while (floorTry < FLOORS.length && (!floorWorth(block, FLOORS[floorTry])
                    || McCompat.item(FLOORS[floorTry]) == null || !TaskCatalogue.taskExists(McCompat.item(FLOORS[floorTry]))));
            if (floorTry >= FLOORS.length) {
                skipPlacement(e, "cannot stand on a cobblestone, dirt or sand floor (canPlaceAt false):"
                        + " needs water, a wall, farmland or other support");
            }
            return null;
        }
        allowedBreak = cell;
        return startStep("place " + e.id + " in cell " + placingCell, new PlaceBlockTask(cell, block), PLACE_MINUTES);
    }

    /**
     * A block placed in the next free cell whose record was lost (the session ended between placing and noting it):
     * note it now instead of gathering it again.
     */
    private void adoptPlaced(AltoClef mod) {
        if (origin == null) return;
        int n = progress.nextCell;
        while (progress.badCells.containsKey(n)) n++;
        BlockPos cell = AllBlocks.cellPos(origin, n);
        if (!mod.getChunkTracker().isChunkLoaded(cell)) return;
        Block there = mod.getWorld().getBlockState(cell).getBlock();
        if (there == Blocks.AIR) return;
        for (AllBlocksPlan.Entry e : plan) {
            if (e.candidate() && e.block() == there && progress.stored.containsKey(e.id) && needsPlacement(e)) {
                progress.placed.put(e.id, n);
                progress.nextCell = n + 1;
                Debug.logMessage("ALLBLOCKS PLACED " + e.id + " in cell " + n + " at " + cell.toShortString() + " (found there; placed " + progress.placed.size() + ")");
                if (e.id.equals(placingId)) placingId = null;
                progress.save(file);
                return;
            }
        }
    }

    /** Only lay a special floor where it can matter (soul sand for nether wart, end stone for chorus). */
    private static boolean floorWorth(Block block, String floor) {
        String id = Registries.BLOCK.getId(block).getPath();
        if (floor.equals("soul_sand")) return id.contains("nether_wart");
        if (floor.equals("end_stone")) return id.contains("chorus");
        return true;
    }

    private void skipPlacement(AllBlocksPlan.Entry e, String reason) {
        progress.placeSkipped.put(e.id, reason);
        Debug.logMessage("ALLBLOCKS PLACE-SKIP " + e.id + ": " + reason);
        placingId = null;
        floorTry = -1;
        progress.save(file);
    }

    /**
     * Floor under {@code pos} (any solid block, or exactly {@code floor} when given) and nothing in the way at
     * {@code pos} and above it. Null when ready.
     */
    private Task prepareCell(AltoClef mod, BlockPos pos, String what, Block floor) {
        BlockPos down = pos.down();
        BlockState under = mod.getWorld().getBlockState(down);
        if (floor != null && under.getBlock() != floor) {
            if (!under.isAir() && under.getFluidState().isEmpty()) {
                allowedBreak = down;
                return startStep("clear floor of " + what, new DestroyBlockTask(down), 2);
            }
            allowedBreak = down;
            return startStep("lay " + floor.asItem() + " floor for " + what, new PlaceBlockTask(down, floor), PLACE_MINUTES);
        }
        if (floor == null && !WorldHelper.isSolidBlock(down)) {
            // Fill a dip from the bottom up: a block can only be placed against one that is already there.
            BlockPos fill = down;
            for (int i = 0; i < 6 && !WorldHelper.isSolidBlock(fill.down()); i++) fill = fill.down();
            allowedBreak = fill;
            return startStep("floor for " + what + (fill.equals(down) ? "" : " (filling " + fill.toShortString() + ")"),
                    new PlaceBlockTask(fill, new Block[0], true, true), PLACE_MINUTES);
        }
        // Two blocks of headroom above: chests are opened by standing on them.
        for (BlockPos p : new BlockPos[]{pos, pos.up(), pos.up(2)}) {
            BlockState s = mod.getWorld().getBlockState(p);
            if (!s.isAir() && s.getFluidState().isEmpty() && !isSign(s.getBlock()) && WorldHelper.canBreak(p)
                    && !(p.equals(pos) && (s.getBlock() == Blocks.CHEST || placingId != null && s.getBlock() == byId.get(placingId).block()))) {
                allowedBreak = p;
                return startStep("clear " + what, new DestroyBlockTask(p), 2);
            }
        }
        return null;
    }

    private BlockPos allowedBreak;

    /**
     * A chest, label sign or grid cell of the layout, or the floor block under one, and not the block a clear step is
     * removing. Only the structures: protecting the whole footprint walled the first chest in when the site was a
     * forest (no trunk or leaf inside it could be cut to get there).
     */
    private boolean protectedArea(BlockPos p) {
        if (origin == null || p.equals(allowedBreak)) return false;
        int dx = p.getX() - origin.getX(), dy = p.getY() - origin.getY(), dz = p.getZ() - origin.getZ();
        // The structure block itself and the column under it (holes are filled from the bottom up, and the bot's own
        // gathering mined the filler back out from under the first chest's stand cell).
        if (dy > 0 || dy < -8) return false;
        if (dx < 0 || dx % 2 != 0) return false;
        if (dz >= -1 && dz < AllBlocks.GRID_Z) {
            if (dx > 2 * (AllBlocks.CHESTS_PER_ROW - 1)) return false;
            int m = Math.floorMod(dz, 3);
            if (m == 2) return dy < 0; // stand cell north of a chest: its floor, not the cell
            return true;                // chest (3r) or its label (3r + 1)
        }
        return dz >= AllBlocks.GRID_Z && (dz - AllBlocks.GRID_Z) % 2 == 0 && dx <= 2 * (AllBlocks.CELLS_PER_ROW - 1);
    }

    // ---------------------------------------------------------------- steps

    private Task startStep(String key, Task task, int minutes) {
        if (task == null) {
            Debug.logMessage("ALLBLOCKS no task for step: " + key);
            return null;
        }
        if (stepTask == null || !key.equals(stepKey)) {
            stepTask = task;
            stepKey = key;
            stepStart = ticks;
            stepMinutes = minutes;
            Debug.logMessage("ALLBLOCKS step: " + key);
        }
        setDebugState(stepKey);
        return stepTask;
    }

    private int stepMinutes;

    private Task continueStep(AltoClef mod) {
        boolean timedOut = ticks - stepStart > (long) stepMinutes * TPM;
        // A label written as a wall sign never matches the standing sign PlaceBlockTask waits for: done once written.
        boolean labelDone = stepKey.startsWith("label chest ")
                && Boolean.TRUE.equals(progress.labeled.get(Integer.parseInt(stepKey.substring("label chest ".length()))));
        if (labelDone) {
            stepTask = null;
            stepKey = null;
            labelPos = null;
            labelLines = null;
            return null;
        }
        if (!stepTask.isFinished() && !timedOut) {
            setDebugState(stepKey);
            return stepTask;
        }
        String key = stepKey;
        if (timedOut) Debug.logMessage("ALLBLOCKS step timed out: " + key + " | " + chain(stepTask));
        stepTask = null;
        stepKey = null;
        if (key.startsWith("deposit ")) {
            afterDeposit(mod);
        } else if (key.startsWith("label chest ")) {
            int chest = Integer.parseInt(key.substring("label chest ".length()));
            if (labelPos != null && !timedOut && isSign(mod.getWorld().getBlockState(labelPos).getBlock())) {
                // The sign is down; its edit screen opens a few ticks later (server round trip). Wait for it.
                labelChest = chest;
                labelDeadline = ticks + 60;
                return null;
            }
            if (labelPos == null) {
                progress.labeled.put(chest, true);
            } else {
                labelFailed.add(chest);
                labelPos = null;
                labelLines = null;
                Debug.logMessage("ALLBLOCKS label for chest " + chest + " not written; retried next session");
            }
            progress.save(file);
        } else if (key.startsWith("place ") && timedOut && placingId != null && count(byId.get(placingId).item) > 0) {
            // (Only with the block still in hand: one lost to a death is gathered again, not held against it.)
            AllBlocksProgress.Failure f = progress.failure("place:" + placingId);
            f.attempts++;
            f.last = "PlaceBlockTask did not place it in " + PLACE_MINUTES + " min";
            if (f.attempts >= 2) {
                skipPlacement(byId.get(placingId), "placement failed twice: " + f.last);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- inventory

    private void refreshPending(AltoClef mod) {
        boolean changed = false;
        for (String id : new ArrayList<>(pending)) {
            if (count(byId.get(id).item) < 1) {
                pending.remove(id);
                changed = true;
                Debug.logMessage("ALLBLOCKS lost " + id + " before depositing it (used or dropped); gathered again later");
            }
        }
        if (changed) protectPending(mod);
    }

    private final List<Item> protectedNow = new ArrayList<>();

    private void protectPending(AltoClef mod) {
        mod.getBehaviour().removeProtectedItems(protectedNow.toArray(new Item[0]));
        protectedNow.clear();
        for (String id : pending) protectedNow.add(byId.get(id).item);
        if (placingId != null) protectedNow.add(byId.get(placingId).item);
        mod.getBehaviour().addProtectedItems(protectedNow.toArray(new Item[0]));
    }

    private static int count(Item... items) {
        return AltoClef.getInstance().getItemStorage().getItemCount(items);
    }

    /** Copies beyond the ones waiting for a chest. */
    private int spare(Item... items) {
        int c = 0;
        for (Item i : items) {
            int n = count(i);
            for (String id : pending) if (byId.get(id).item == i) n--;
            c += Math.max(0, n);
        }
        return c;
    }

    private static int freeSlots(AltoClef mod) {
        int free = 0;
        for (int i = 0; i < 36; i++) if (mod.getPlayer().getInventory().getStack(i).isEmpty()) free++;
        return free;
    }

    // ---------------------------------------------------------------- status

    /** {@code <world>-report.txt} next to the progress file: status, then every entry that will not complete and why. */
    private void writeReport() {
        try {
            List<String> lines = new ArrayList<>();
            lines.add(statusLine());
            lines.add("kit: " + AllBlocksKit.summary(AltoClef.getInstance()));
            lines.addAll(AllBlocks.reportLines(plan, progress));
            Path out = file.resolveSibling(file.getFileName().toString().replace(".json", "-report.txt"));
            java.nio.file.Files.write(out, lines, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            Debug.logWarning("ALLBLOCKS could not write the report: " + e);
        }
    }

    String statusLine() {
        return AllBlocks.summarize(plan, progress, gathering == null ? stepKey : "gather " + gathering.id);
    }

    AllBlocksProgress progress() {
        return progress;
    }

    List<AllBlocksPlan.Entry> plan() {
        return plan;
    }
}
