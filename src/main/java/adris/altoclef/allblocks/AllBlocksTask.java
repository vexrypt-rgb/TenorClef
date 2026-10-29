package adris.altoclef.allblocks;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.InteractWithBlockTask;
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
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.MushroomPlantBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Direction;

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
        if (!guarded) {
            // The bot's own gathering dug the first chest's floor out from under it (it mines wherever the closest
            // block is). Nothing breaks blocks in the storage area and grid except the step clearing a cell.
            mod.getBehaviour().push();
            mod.getBehaviour().avoidBlockBreaking(this::protectedArea);
            guarded = true;
        }
        if (progress != null) {
            // Resumed after another chain (eating, mob defense) had control: keep the state.
            protectedNow.clear();
            protectPending(mod);
            return;
        }
        String world = AllBlocks.worldKey();
        file = AllBlocksProgress.file(MinecraftClient.getInstance().runDirectory.toPath(), world);
        progress = AllBlocksProgress.load(file, world);
        // Crops used to be skipped for want of farmland; the place step tills it now.
        progress.placeSkipped.values().removeIf(r -> r.startsWith("crop:") || r.equals("needs farmland")
                || r.contains("farmland or other support")); // older, vaguer reason: tried again for the exact one
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
        // TenorClef also stops (not interrupts) the user chain while a survival chain has control; the task resumes
        // afterwards, so "stopped" is logged by AllBlocks.stop() instead. The layout stays protected through those
        // pauses: mob defense and eating ran unguarded and broke a placed log out of the grid.
        if (AllBlocks.stopping || isFinished()) {
            AltoClef.getInstance().getBehaviour().pop();
            guarded = false;
        }
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

        inBunker = false;
        Task night = nightStep(mod);
        // In the closed pit there is nothing to do but wait: falling through sent the kit out for a bed at night and
        // a zombie killed the bot.
        if (night != null || inBunker) return night;

        Task recover = recoverStep(mod);
        if (recover != null) return recover;

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

        Task trim = trimStep(mod);
        if (trim != null) return trim;

        Task place = placeStep(mod);
        if (place != null) return place;

        return gatherStep(mod);
    }

    private final Set<Integer> trimmed = new LinkedHashSet<>();

    /**
     * One of each item, not a stack: earlier deposits on 26.3 stored whole stacks. Near home, each storage chest is
     * opened once per session (or again when its contents show a duplicate) and the extra copies are taken back out.
     */
    private Task trimStep(AltoClef mod) {
        if (stepTask != null && stepKey != null && stepKey.startsWith("trim chest ")) return stepTask;
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD
                || mod.getPlayer().getBlockPos().getSquaredDistance(origin) > 24 * 24 || freeSlots(mod) < MIN_FREE_SLOTS) {
            return null;
        }
        for (int chest : new java.util.TreeSet<>(progress.stored.values())) {
            BlockPos pos = AllBlocks.chestPos(origin, chest);
            if (mod.getWorld().getBlockState(pos).getBlock() != Blocks.CHEST) continue;
            Optional<ContainerCache> cache = mod.getItemStorage().getContainerAtPosition(pos);
            boolean dup = cache.isPresent() && cache.get().getItemCounts().values().stream().anyMatch(n -> n > 1);
            if (trimmed.contains(chest) && !dup) continue;
            if (trimmed.contains(chest) && trimTries.merge(chest, 1, Integer::sum) > 3) continue;
            trimmed.add(chest);
            return startStep("trim chest " + chest, new AllBlocksTrimTask(pos), 3);
        }
        return null;
    }

    private final Map<Integer, Integer> trimTries = new HashMap<>();

    // ---------------------------------------------------------------- death

    private String seenDeath;
    private BlockPos recoverPos;
    private BlockPos lastRecover;
    private boolean sawDrops;
    private long recoverUntil;
    private static final int RECOVER_MINUTES = 8;

    /**
     * After a death, walk back and pick up what was dropped: rebuilding pick, sword, armor and food from nothing took
     * about 45 minutes every time. Drops last 5 minutes in loaded chunks (the clock stops while the chunk is unloaded),
     * so they are often still there. The last handled death is saved, so a death just before a restart is still chased;
     * deaths from before the first run are not.
     */
    private Task recoverStep(AltoClef mod) {
        Optional<GlobalPos> death = mod.getPlayer().getLastDeathPos();
        String key = death.map(Object::toString).orElse("");
        if (seenDeath == null) seenDeath = progress.seenDeath != null ? progress.seenDeath : key;
        if (!key.equals(seenDeath)) {
            seenDeath = key;
            progress.seenDeath = key;
            progress.save(file);
            BlockPos at = death.get().pos();
            // Not underground or underwater, and not where the last trip back for drops just ended in another death: walking back
            // unarmed into the cave that killed it got the bot killed again by the same zombies.
            boolean underground = at.getY() < 62; // below sea level: a cave or the sea floor (drowned)
            boolean deathTrap = lastRecover != null && lastRecover.getSquaredDistance(at) < 32 * 32;
            if (underground || deathTrap) {
                Debug.logMessage("ALLBLOCKS died at " + at.toShortString() + "; not going back for the drops ("
                        + (underground ? "below sea level" : "died there again") + ")");
            } else if (WorldHelper.getCurrentDimension() == Dimension.OVERWORLD
                    && death.get().dimension() == World.OVERWORLD
                    && at.getSquaredDistance(mod.getPlayer().getBlockPos()) < 400 * 400) {
                recoverPos = at;
                lastRecover = at;
                recoverUntil = ticks + (long) RECOVER_MINUTES * TPM;
                Debug.logMessage("ALLBLOCKS died at " + at.toShortString() + "; going back for the drops");
            }
        }
        if (progress.seenDeath == null) {
            progress.seenDeath = seenDeath;
            progress.save(file);
        }
        if (recoverPos == null) return null;
        if (ticks > recoverUntil) {
            Debug.logMessage("ALLBLOCKS gave up on the drops at " + recoverPos.toShortString());
            recoverPos = null;
            return null;
        }
        List<ItemEntity> near = new ArrayList<>();
        for (ItemEntity drop : mod.getEntityTracker().getDroppedItems()) {
            if (drop.getBlockPos().getSquaredDistance(recoverPos) < 12 * 12) near.add(drop);
        }
        if (mod.getPlayer().getBlockPos().getSquaredDistance(recoverPos) > 8 * 8 && (near.isEmpty() || !mod.getChunkTracker().isChunkLoaded(recoverPos))) {
            setDebugState("Back to the death spot for the drops");
            return new adris.altoclef.tasks.movement.GetToBlockTask(recoverPos);
        }
        if (near.isEmpty() || freeSlots(mod) == 0) {
            Debug.logMessage("ALLBLOCKS " + (sawDrops ? "picked up the drops" : "no drops left (despawned or burned)")
                    + " at " + recoverPos.toShortString());
            recoverPos = null;
            sawDrops = false;
            return null;
        }
        sawDrops = true;
        List<ItemTarget> targets = new ArrayList<>();
        for (ItemEntity drop : near) {
            Item item = drop.getStack().getItem();
            targets.add(new ItemTarget(item, count(item) + drop.getStack().getCount()));
        }
        setDebugState("Picking up the drops (" + near.size() + " left)");
        return new adris.altoclef.tasks.movement.PickupDroppedItemTask(targets.toArray(new ItemTarget[0]), true);
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
        // Without a bed, or swimming, the bot cannot sleep at nightfall: start digging in at dusk instead, so it is
        // underground before the monsters come out (swimming ashore and digging after dark got it killed).
        if (!night && time >= 11800 && time < 12542 && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD
                && (!mod.getItemStorage().hasItem(ItemHelper.BED) && !nearBed(mod) || mod.getPlayer().isTouchingWater())) {
            night = true;
        }
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
            double d2 = mod.getPlayer().getBlockPos().getSquaredDistance(origin);
            // Home only when it is close: walking far at dusk ends in the dark. The carried bed works anywhere.
            if (d2 > 12 * 12 && d2 < 64 * 64) {
                setDebugState("Dusk: heading home");
                return new adris.altoclef.tasks.movement.GetToBlockTask(origin.add(-2, 0, -2));
            }
        }
        if (!night || ticks < nightCooldownUntil || WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            if (sleepTask != null && !night) {
                Debug.logMessage("ALLBLOCKS morning");
                if (bedPos != null) bedPickupUntil = ticks + 60L * 20;
            }
            sleepTask = null;
            bunkered = false;
            return bedPickupStep(mod);
        }
        // Only a bed carried or one close by: the bed task walked 110 blocks in the dark to an old bed and died.
        boolean bedHere = mod.getItemStorage().hasItem(ItemHelper.BED) || nearBed(mod);
        if (sleepTask == null && !bunkered) {
            sleepTask = new adris.altoclef.tasks.misc.SleepThroughNightTask();
            sleepStart = mod.getWorld().getTime();
            Debug.logMessage("ALLBLOCKS night: going to bed" + (gathering != null ? " (holding gather of " + gathering.id + ")" : ""));
        }
        // World time, not this task's ticks: while mob defense has control this task does not tick at all.
        if (!mod.getPlayer().isSleeping() && (bunkered || !bedHere || mod.getWorld().getTime() - sleepStart > SLEEP_TRY_SECONDS * 20L)) {
            // No sleep (monsters nearby, no bed, standing in water): a capped pit two deep keeps mobs off.
            if (!bunkered) {
                // Dig in where the bot stands: walking home in the dark is what got it killed.
                bunkerTop = mod.getPlayer().getBlockPos();
                Debug.logMessage("ALLBLOCKS night: " + (bedHere ? "no sleep after " + SLEEP_TRY_SECONDS + "s (" + chain(sleepTask) + ")" : "no bed carried or nearby")
                        + "; bunkering at " + bunkerTop.toShortString());
            }
            bunkered = true;
            if (gathering != null) gatherStart++;
            kit.hold();
            return bunkerStep(mod);
        }
        if (gathering != null) gatherStart++;
        if (stepTask != null) stepStart++;
        kit.hold();
        if (mod.getPlayer().isSleeping()) mod.getPlayer().getSleepingPosition().ifPresent(p -> bedPos = p);
        setDebugState("Night: sleeping");
        return sleepTask;
    }

    private BlockPos bedPos;
    private long bedPickupUntil;

    /** In the morning, take the bed along: one left behind every night cost three wool a day. */
    private Task bedPickupStep(AltoClef mod) {
        if (bedPos == null) return null;
        if (ticks > bedPickupUntil) {
            bedPos = null;
            return null;
        }
        if (mod.getWorld().getBlockState(bedPos).getBlock() instanceof BedBlock) {
            setDebugState("Morning: picking up the bed");
            allowedBreak = bedPos;
            return new DestroyBlockTask(bedPos);
        }
        if (!mod.getItemStorage().hasItem(ItemHelper.BED)) {
            setDebugState("Morning: collecting the bed");
            return new adris.altoclef.tasks.movement.PickupDroppedItemTask(new ItemTarget(ItemHelper.BED, 1), true);
        }
        Debug.logMessage("ALLBLOCKS morning: took the bed along");
        bedPos = null;
        return null;
    }

    private boolean bunkered;
    private BlockPos bunkerTop;
    /**
     * Time to get into bed before digging in. Sleep is possible from 12542; monsters come out around 13200, so after
     * that the bot should already be underground (a 60 s try left it digging among skeletons).
     */
    private static final int SLEEP_TRY_SECONDS = 30;

    /** Standing room at {@code top} over two solid, fluid-free blocks to dig into. */
    private static boolean dryGround(AltoClef mod, BlockPos top) {
        for (BlockPos p : new BlockPos[]{top, top.down(), top.down(2), top.down(3)}) {
            if (!mod.getWorld().getBlockState(p).getFluidState().isEmpty()) return false;
        }
        return WorldHelper.isSolidBlock(top.down(3));
    }

    static boolean nearBed(AltoClef mod) {
        Optional<BlockPos> bed = mod.getBlockScanner().getNearestBlock(ItemHelper.itemsToBlocks(ItemHelper.BED));
        return bed.isPresent() && bed.get().getSquaredDistance(mod.getPlayer().getBlockPos()) < 24 * 24;
    }

    /** A pit two deep under where the bot stood when it gave up on sleeping, closed over its head. */
    private Task bunkerStep(AltoClef mod) {
        // A pit in water is no shelter: get onto solid, dry ground first and dig in there.
        if (bunkerTop == null || !dryGround(mod, bunkerTop)) {
            BlockPos here = mod.getPlayer().getBlockPos();
            if (mod.getPlayer().isTouchingWater() || !mod.getPlayer().isOnGround() || !dryGround(mod, here)) {
                setDebugState("Night: out of the water to dig in");
                bunkerTop = null;
                return new adris.altoclef.tasks.movement.GetOutOfWaterTask();
            }
            bunkerTop = here;
            Debug.logMessage("ALLBLOCKS night: bunkering at " + bunkerTop.toShortString());
        }
        BlockPos top = bunkerTop, head = top.down(), feet = top.down(2);
        if (!mod.getWorld().getBlockState(head).isAir() && !mod.getPlayer().getBlockPos().equals(feet)) {
            setDebugState("Night: digging the bunker");
            return new DestroyBlockTask(head);
        }
        if (!mod.getWorld().getBlockState(feet).isAir() && !mod.getPlayer().getBlockPos().equals(feet)) {
            setDebugState("Night: digging the bunker");
            return new DestroyBlockTask(feet);
        }
        if (!mod.getPlayer().getBlockPos().equals(feet)) {
            setDebugState("Night: getting into the bunker");
            return new adris.altoclef.tasks.movement.GetToBlockTask(feet);
        }
        if (!WorldHelper.isSolidBlock(top)) {
            setDebugState("Night: closing the bunker");
            return new PlaceBlockTask(top, new Block[0], true, true);
        }
        setDebugState("Night: in the bunker");
        inBunker = true;
        return null;
    }

    private boolean inBunker;

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
        gatherMinutes = prev != null && prev.attempts > 0 ? RETRY_MINUTES : stored && remainingNew() ? EARLY_SWEEP_MINUTES : GATHER_MINUTES;
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
        // A short try only (see EARLY_SWEEP_MINUTES): a rare one (potatoes) sent the bot wandering 10 minutes before any
        // new item was tried. A failed early sweep waits for the sweep at the end.
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

    /** Time for re-gathering a used-up grid block before new items (dirt and cobblestone take seconds). */
    private static final int EARLY_SWEEP_MINUTES = 3;
    private final Set<String> deferred = new LinkedHashSet<>();
    private int gatherMinutes = GATHER_MINUTES;

    /** Any item never stored and not yet failed for good: an early sweep is still ahead of them. */
    private boolean remainingNew() {
        for (AllBlocksPlan.Entry e : plan) {
            if (!e.candidate() || progress.stored.containsKey(e.id) || e.unsupportedReason() != null) continue;
            AllBlocksProgress.Failure f = progress.failures.get(e.id);
            if (f == null || !f.stuck) return true;
        }
        return false;
    }

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
        // Already in the chest (a deposit that was not recorded, e.g. cut short by a crash): record it, do not add another.
        Optional<ContainerCache> known = mod.getItemStorage().getContainerAtPosition(pos);
        if (known.isPresent()) {
            boolean changed = false;
            for (String id : new ArrayList<>(pending)) {
                if (progress.chestOf.getOrDefault(id, -1) == chest && known.get().getItemCount(byId.get(id).item) > 0) {
                    progress.stored.put(id, chest);
                    pending.remove(id);
                    changed = true;
                    Debug.logMessage("ALLBLOCKS DEPOSIT " + id + " -> chest " + chest + " at " + pos.toShortString()
                            + " (already in chest; stored " + progress.stored.size() + ")");
                }
            }
            if (changed) {
                protectPending(mod);
                progress.save(file);
            }
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
        return id.endsWith("_pickaxe") || id.endsWith("_sword") || id.endsWith("_bed") || id.endsWith("_hoe");
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
        return true;
    }

    /** Crops (seeds, potatoes, stems, torchflower, pitcher pod) stand only on farmland: the place step hoes the floor. */
    static boolean needsFarmland(Block b) {
        return b instanceof CropBlock || b instanceof StemBlock || b == Blocks.PITCHER_CROP;
    }

    private static final Item[] HOES = {Items.NETHERITE_HOE, Items.DIAMOND_HOE, Items.IRON_HOE, Items.GOLDEN_HOE,
            Items.STONE_HOE, Items.WOODEN_HOE};

    /** Dirt floor tilled to farmland for a crop. Null when the farmland is there. */
    private Task farmlandStep(AltoClef mod, BlockPos cell, int cellIndex) {
        BlockPos floor = cell.down();
        if (mod.getWorld().getBlockState(floor).getBlock() == Blocks.FARMLAND) return null;
        if (mod.getWorld().getBlockState(floor).getBlock() != Blocks.DIRT && spare(Items.DIRT) < 1) {
            return startStep("get dirt floor for cell " + cellIndex, TaskCatalogue.getItemTask(Items.DIRT, count(Items.DIRT) + 1), 10);
        }
        Task t = prepareCell(mod, cell, "cell " + cellIndex, Blocks.DIRT);
        if (t != null) return t;
        if (!mod.getItemStorage().hasItem(HOES)) {
            return startStep("get a hoe to till cell " + cellIndex, TaskCatalogue.getItemTask("wooden_hoe", 1), 10);
        }
        return startStep("till the floor of cell " + cellIndex,
                new InteractWithBlockTask(new ItemTarget(HOES, 1), Direction.UP, floor, false), 3);
    }

    /** Cells whose block went missing after it was placed: the block goes back into the same cell. */
    private final Map<String, Integer> repairs = new HashMap<>();
    private long lastPlacedCheck = -1200;

    /** Every minute near home: a placed cell that is empty again (broken by a fight or a path) is placed again. */
    private void checkPlaced(AltoClef mod) {
        if (ticks - lastPlacedCheck < 1200 || origin == null) return;
        lastPlacedCheck = ticks;
        for (Map.Entry<String, Integer> en : new ArrayList<>(progress.placed.entrySet())) {
            BlockPos cell = AllBlocks.cellPos(origin, en.getValue());
            if (!mod.getChunkTracker().isChunkLoaded(cell) || cell.getSquaredDistance(mod.getPlayer().getBlockPos()) > 48 * 48) continue;
            AllBlocksPlan.Entry e = byId.get(en.getKey());
            if (e == null || mod.getWorld().getBlockState(cell).getBlock() == e.block()) continue;
            if (!mod.getWorld().getBlockState(cell).isAir()) continue;
            progress.placed.remove(en.getKey());
            repairs.put(en.getKey(), en.getValue());
            Debug.logMessage("ALLBLOCKS cell " + en.getValue() + " lost its " + en.getKey() + "; placing it again there");
            progress.save(file);
        }
    }

    private Task placeStep(AltoClef mod) {
        checkPlaced(mod);
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
            // A hole left by a block that went missing (its record was dropped) is filled before the grid grows.
            java.util.Set<Integer> used = new java.util.HashSet<>(progress.placed.values());
            for (int h = 0; h < progress.nextCell; h++) {
                if (!used.contains(h) && !progress.badCells.containsKey(h)) {
                    n = h;
                    break;
                }
            }
            placingCell = repairs.getOrDefault(placingId, n);
        }
        BlockPos cell = AllBlocks.cellPos(origin, placingCell);
        Task home = goHomeFirst(mod, cell, "place " + e.id);
        if (home != null) return home;
        BlockState cellState = mod.getWorld().getBlockState(cell);
        if (cellState.getBlock() == block) {
            progress.placed.put(e.id, placingCell);
            repairs.remove(e.id);
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
        if (needsFarmland(block)) {
            Task till = farmlandStep(mod, cell, placingCell);
            if (till != null) return till;
            if (!block.getDefaultState().canPlaceAt(mod.getWorld(), cell)) {
                skipPlacement(e, "crop: canPlaceAt false on tilled farmland (too dark?)");
                return null;
            }
            allowedBreak = cell;
            // Planted the way a player does, with the seed on the farmland's top face: the builder behind
            // PlaceBlockTask would not place against farmland (not a full block) and wandered off.
            return startStep("plant " + e.id + " in cell " + placingCell,
                    new InteractWithBlockTask(new ItemTarget(e.item, 1), Direction.UP, cell.down(), false), PLACE_MINUTES);
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
                if (block instanceof MushroomPlantBlock) {
                    skipPlacement(e, "mushroom: canPlaceAt needs light below 13 or a podzol/mycelium floor (both silk"
                            + " touch); the grid is out in daylight");
                } else {
                    skipPlacement(e, "cannot stand on a cobblestone, dirt, sand, soul sand or end stone floor (canPlaceAt"
                            + " false): needs water, a wall or other support");
                }
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
                // Normally the place step's own block (checked in the world); otherwise a block already standing there.
                String how = e.id.equals(placingId) ? "placed by the place step" : "found there";
                Debug.logMessage("ALLBLOCKS PLACED " + e.id + " in cell " + n + " at " + cell.toShortString() + " (" + how + "; placed " + progress.placed.size() + ")");
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
        // Room to stand next to the cell: a tree growing into the grid left nowhere to place the floor from. The 8
        // blocks around a cell are gaps of the layout, so only leaves and logs there are cut.
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos p = pos.add(dx, dy, dz);
                    if (AllBlocks.isTree(mod.getWorld().getBlockState(p)) && WorldHelper.canBreak(p)) {
                        allowedBreak = p;
                        return startStep("clear tree around " + what, new DestroyBlockTask(p), 2);
                    }
                }
            }
        }
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
    private boolean guarded;
    private final Map<Integer, Integer> cellTimeouts = new HashMap<>();

    /**
     * A chest, label sign or grid cell of the layout, or the floor block under one, and not the block a clear step is
     * removing. Only the structures: protecting the whole footprint walled the first chest in when the site was a
     * forest (no trunk or leaf inside it could be cut to get there).
     */
    private boolean protectedArea(BlockPos p) {
        return origin != null && !p.equals(allowedBreak) && AllBlocks.inLayout(origin, p);
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
        // Interact steps never finish by themselves: a till step is done once the floor is farmland. (It went on
        // clicking for its whole 3 minutes, and the dry farmland turned back to dirt before the crop went in.)
        if (stepKey.startsWith("till the floor of cell ") && placingCell >= 0
                && mod.getWorld().getBlockState(AllBlocks.cellPos(origin, placingCell).down()).getBlock() == Blocks.FARMLAND) {
            Debug.logMessage("ALLBLOCKS tilled the floor of cell " + placingCell);
            stepTask = null;
            stepKey = null;
            return null;
        }
        if (stepKey.startsWith("plant ") && placingId != null && placingCell >= 0
                && mod.getWorld().getBlockState(AllBlocks.cellPos(origin, placingCell)).getBlock() == byId.get(placingId).block()) {
            stepTask = null;
            stepKey = null;
            return null;
        }
        if (!stepTask.isFinished() && !timedOut) {
            setDebugState(stepKey);
            return stepTask;
        }
        String key = stepKey;
        if (timedOut) {
            Debug.logMessage("ALLBLOCKS step timed out: " + key + " | " + chain(stepTask));
            // A grid cell that cannot be readied twice over is given up; the block goes to the next cell.
            if (placingCell >= 0 && (key.endsWith("cell " + placingCell) || key.contains("cell " + placingCell + " ")) && cellTimeouts.merge(placingCell, 1, Integer::sum) >= 2) {
                progress.badCells.put(placingCell, "could not be readied: " + key);
                Debug.logMessage("ALLBLOCKS cell " + placingCell + " given up (" + key + "); using the next cell");
                placingCell = -1;
                progress.save(file);
            }
        }
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
