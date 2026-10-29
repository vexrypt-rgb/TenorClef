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
    private int escalations; // Ostinato: run86 stall escalations this room (skip + walk home) before FAIL
    private int stuckRecoveries; // Ostinato: one wander+blacklist recovery before FAIL
    private boolean gathered;    // Ostinato: gather-once latch
    private boolean treesReplanted; private adris.altoclef.tasks.resources.ReplantTreesTask replantTask; // Ostinato: wood farm, once per room
    private final java.util.Set<String> lootDone = new java.util.HashSet<>(); private String lootKey; private int lootTicks; private boolean lootGathering; // Ostinato: loot at FILL
    private String gatherKey;    // Ostinato: loot-only gather budget
    private int gatherTicks;
    private final java.util.Set<String> skipped = new java.util.HashSet<>();
    private java.util.List<MazeFurnishPlan.Step> work; // Ostinato: lights, container, fill, then seals
    private boolean loggedFill; // Ostinato
    private int foodTicks;      // Ostinato: kit food budget
    private int foodCooldown;   // Ostinato: ticks after skip-food before a starve retry
    private boolean foodGaveUp; // Ostinato
    private String surfacedFor; // Ostinato: wood gather already reached open air
    private String woodFor;     // Ostinato: run94 last pooled wood trip owner (log once)
    // Ostinato: run94. Items whose wood was in hand once. The craft then spends it (table placed, planks -> slabs), so a
    // live "has wood" check would read short mid-craft and restart the wood trip; the craft budget bounds them instead.
    private final java.util.Set<String> woodReady = new java.util.HashSet<>();
    private final Map<String, Integer> woodShort = new java.util.HashMap<>(); // Ostinato: run96 ticks a latch has read short

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
        this.plan = MazeFurnishPlan.stoneIfAboveDeepslate(plan);
        // Ostinato: every cell the plan will place into. The surface climb must never mine these, or it eats the
        // cladding it just put in (run42: ceiling cells at y=88 mined away while "Surfacing for barrel").
        for (MazeFurnishPlan.Step s : plan.steps) plannedCells.add(new BlockPos(s.x(), s.y(), s.z()));
        for (MazeFurnishPlan.Step s : plan.steps) if (s.kind() == MazeFurnishPlan.Kind.SEAL) sealCells.add(new BlockPos(s.x(), s.y(), s.z()));
        int[] air = this.plan.airCells; // Ostinato: run100
        for (int i = 0; i + 2 < air.length; i += 3) airCells.add(new BlockPos(air[i], air[i + 1], air[i + 2]));
    }

    static boolean obtainable(String id) {
        Item item = McCompat.item(id);
        if (item == null) return false;
        if (AltoClef.getInstance().getItemStorage().hasItem(item)) return true;
        // Ostinato: run51. Diamond and emerald are real ores, but the nearest vein is not beside the room.
        // A cave hunt for one emerald hits the same 90s stuck abort that closed the deepslate run. Fill uses fallback loot.
        if (id.equals("diamond") || id.equals("emerald")) return false;
        // Ostinato: run48. taskExists said yes to rotten_flesh on peaceful (zombie-only drop), so the fill gather hunted
        // a mob that never spawns. Ask whether the item can be produced in THIS world; fallbackLoot fills the barrel instead.
        return TaskCatalogue.obtainableNow(item);
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
        // Ostinato: run50 seal yo-yo. The stone pick broke mid-lighting; the stone_pickaxe kit then mined cobble out of the
        // open seal shaft at -22,88..92,137 (pillar up = place into seal cells, mine them back), seal 0/5 <-> 5/5 for 9 min.
        // A planned cell that holds a solid block is never broken by any subtask (kit, gather, pathing). No step destroys
        // its own cells, so this only stops self-cannibalising; seals stay sealed once placed.
        // Ostinato: run52. Protect a planned cell only once it already is the block the plan wants.
        // Protecting every solid cell also protected the stone and dirt the ceiling pass has to mine
        // (8 cells sat on "Mining ceiling" until skip, light 125/133). A finished seal stays protected.
        AltoClef.getInstance().getBehaviour().avoidBlockBreaking(p -> plannedCells.contains(p) && isSolidCell(p) && cellMatchesPlan(p) && !(repairOpen && sealCells.contains(p)));
        MazeRooms.begin(airCells, plannedCells); // Ostinato: run101, earlier rooms are off limits to gathers and paths
        AltoClef.getInstance().getBehaviour().avoidBlockBreaking(MazeRooms::isProtected);
        AltoClef.getInstance().getBehaviour().addProtectedItems(IRON_ARMOR);
        AltoClef.getInstance().getBehaviour().addProtectedItems(KIT_ARMOR);
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
        traceWoodSpend(mod);
        int sig = 0;
        for (int i = 0; i < mod.getPlayer().getInventory().size(); i++) { net.minecraft.item.ItemStack st = mod.getPlayer().getInventory().getStack(i); sig = sig * 31 + st.getCount() * 7 + net.minecraft.registry.Registries.ITEM.getRawId(st.getItem()); }
        // Ostinato: run99. The copper kit stood at its furnace on "Waiting..." while 11 raw copper smelted: the inventory does
        // not change until the bot takes the output, so this fired stuck-recover at 30s and wandered it away from the furnace.
        // A burning furnace the bot has open is progress; an unfuelled one is not, and still trips the watchdog.
        boolean smelting = mod.getPlayer().currentScreenHandler instanceof net.minecraft.screen.AbstractFurnaceScreenHandler fh
                && (adris.altoclef.util.helpers.StorageHelper.getFurnaceFuel(fh) > 0 || fh.getCookProgress() > 0);
        if (smelting || sig != lastInvSig || stuckAnchor == null || stuckAnchor.getSquaredDistance(feet) > 64) { if (sig != lastInvSig) stuckRecoveries = 0; lastInvSig = sig; stuckAnchor = feet; stuckTicks = 0; }
        else if (++stuckTicks >= 600 && stuckRecoveries < surfaceStuckLimit()) { // Ostinato: 90s -> 30s (user: faster) // Ostinato: two recoveries before FAIL; surfacing gets more (run37)
            // Ostinato: first 90s stall: blacklist the block the chain is fighting, wander 30s, retry. Second stall FAILs.
            for (Task t = this; t != null; t = t.getSub()) if (t instanceof DestroyBlockTask d) { mod.getBlockScanner().requestBlockUnreachable(d.getPos(), 0); }
            // Ostinato: run40 audit. A stuck CONTAINER/FILL/SEAL cell burned both recoveries then FAILed the run. Blacklist the
            // cell itself: defer it to the second pass (skip it there) instead of wandering back to the same face.
            // Ostinato: run91. A FILL step hunting loot (paper -> sugar cane) is a gather, not a stuck place: it was deferred
            // 30s into the hunt. It takes the gather recovery below; the 3-min loot budget still bounds it.
            if (inStep() && !lootGathering) { deferStep("stuck"); stuckRecoveries = 0; stuckTicks = 0; stuckAnchor = feet; return null; }
            System.out.println("MAZETEST FURNISH stuck-recover stuck=" + getDebugState() + " feet=" + feet.toShortString());
            stuckRecoveries++; stuckTicks = 0; stuckAnchor = feet;
            unstickTicks = 0; unstick = new adris.altoclef.tasks.movement.TimeoutWanderTask(16);
            return unstick;
        }
        else if (stuckTicks >= 1200 && escalations < 4 && home() != null) {
            // Ostinato: run86 (forever expand) FAILed and closed the client on one stuck "Gathering stick x6" 73 blocks out.
            // A stall now escalates first: skip that optional gather, walk back to the room, retry from there. FAIL only
            // after 4 escalations in this room.
            escalations++;
            String ds = getDebugState() == null ? "" : getDebugState();
            String key = ds.startsWith("Gathering ") && ds.contains(" x") ? ds.substring(10, ds.indexOf(" x")).trim() : null;
            if (key != null && materials.containsKey(key) && !isCladding(key)) skipped.add(key); else key = null;
            System.out.println("MAZETEST FURNISH escalate " + escalations + "/4 stuck=" + ds + " skip=" + key + " dist=" + homeDist(feet) + " feet=" + feet.toShortString());
            stuckRecoveries = 0; stuckTicks = 0; stuckAnchor = feet;
            BlockPos h = home();
            unstickTicks = 0; unstick = new adris.altoclef.tasks.movement.GetToBlockTask(h);
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
        if (feet.equals(lastFeet) && !smelting) stillTicks++; else { stillTicks = 0; lastFeet = feet; } // Ostinato: run99 smelt waits are not stillness
        // Ostinato: run13 watchdog fired once, then the wander never reported finished and the bot idled forever.
        // Cap each wander at 30s and let stillness keep counting so the watchdog can fire again.
        // Ostinato: an escalation walk home (GetToXZ) gets 2 min, a wander 30 s.
        if (unstick != null && !unstick.isFinished() && ++unstickTicks < (unstick instanceof adris.altoclef.tasks.movement.GetToBlockTask ? 2400 : 600)) return unstick;
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
        // Ostinato: run40 audit. The escape let go once the head cleared and the gather pathed straight back under. Latch it
        // until feet are dry and grounded. Hold jump after 5s still submerged (flooded enclosed pocket, no path out). 60s cap.
        if (!escapingWater && mod.getPlayer().isSubmergedInWater() && mod.getPlayer().getAir() < mod.getPlayer().getMaxAir() * 2 / 3) {
            escapingWater = true; escapeTicks = 0; escapeTask = null;
            System.out.println("MAZETEST FURNISH water-escape air=" + mod.getPlayer().getAir() + " feet=" + feet.toShortString());
        }
        if (escapingWater) {
            boolean dry = !mod.getPlayer().isTouchingWater() && mod.getPlayer().isOnGround();
            if (dry || ++escapeTicks > 1200) {
                System.out.println("MAZETEST FURNISH water-escape " + (dry ? "done" : "timeout") + " ticks=" + escapeTicks);
                escapingWater = false; escapeTask = null;
                if (jumpHeld) { mod.getInputControls().release(baritone.api.utils.input.Input.JUMP); jumpHeld = false; }
            } else {
                setDebugState("Out of water (air " + mod.getPlayer().getAir() + ")");
                stuckTicks = 0;
                if (mod.getPlayer().isSubmergedInWater() && escapeTicks > 100) { mod.getInputControls().hold(baritone.api.utils.input.Input.JUMP); jumpHeld = true; }
                else if (jumpHeld) { mod.getInputControls().release(baritone.api.utils.input.Input.JUMP); jumpHeld = false; }
                if (escapeTask == null) escapeTask = new adris.altoclef.tasks.movement.GetOutOfWaterTask();
                // Head is clear but feet are still in water: swim to shore instead of idling at the surface.
                else if (escapeTask.isFinished()) escapeTask = new adris.altoclef.tasks.movement.TimeoutWanderTask(8);
                return escapeTask;
            }
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
            // Ostinato: run43 yo-yo. A seal/light regather sets gathered=false, which re-ran this whole loop; the barrel
            // was already placed (inventory 0), so the bot went back for another barrel and mined up its own seal shaft.
            // An item that reached its count once is done here; the step regather path handles real shortfalls.
            if (satisfied.contains(need.getKey())) continue;
            // Ostinato: run86/88. Loot (paper, seeds, string...) was gathered before the first light: a paper hunt sent the
            // bot 70 blocks out at 0/133 lights. Loot is now gathered at the FILL step (after lights + container), where it
            // is actually used; the sugar cane farm also gets the whole build to regrow.
            if (lootOnly(need.getKey())) continue;
            // Ostinato: run47. Wood cladding steps accept every nearby wood (MazeFurnishPlanner.wood appends them all), but the
            // gather pinned each step to one plank id: walls asked for oak_planks x79 with ~1 oak tree around (179 cherry
            // logs), so the bot wandered 25 min chopping nothing. Pool all wood cladding into one any-planks count.
            if (isWoodCladding(need.getKey())) {
                int pooled = plankCladdingMissing(mod);
                if (heldPlanks(mod) >= pooled) {
                    // Ostinato: wood is a farm. Replant where the trees were just cut, once per room (1 min cap), so later
                    // rooms of a forever expand still have wood near them instead of hunting farther every room.
                    if (!treesReplanted) {
                        if (replantTask == null) replantTask = new adris.altoclef.tasks.resources.ReplantTreesTask(3, 1200);
                        if (!replantTask.isFinished()) { setDebugState("Replanting trees"); return replantTask; }
                        System.out.println("MAZETEST FURNISH replant-trees planted=" + replantTask.planted() + " feet=" + feet.toShortString());
                        treesReplanted = true;
                    }
                    satisfied.add(need.getKey()); continue;
                }
                return pooledWoodGather(mod, feet, need.getKey(), Math.max(pooled, pooledWoodNeed(mod))); // Ostinato: run94
            }
            Item item = McCompat.item(need.getKey());
            if (item != null && mod.getItemStorage().getItemCount(item) >= need.getValue()) { satisfied.add(need.getKey()); continue; }
            // Ostinato: run42 self-cannibalised. `materials` is the PLAN total, so a mid-build run-dry asked for the
            // full count again ("Gathering polished_andesite x53") and the nearest source of that block was the room's
            // own cladding: light went 97 -> 89 in two minutes. Ask only for cells that are still wrong, and hide the
            // placed cells from the scanner before mining.
            if (item != null && !lootOnly(need.getKey()) && stillMissing(mod, need.getKey()) <= 0) continue;
            // Ostinato: run60. Barrel FILL contents listed string, so the loot gather ran CollectStringTask and the bot left
            // the room on a mineshaft cobweb expedition ("Gathering string x8"). The profile removed the string hunt from
            // furnish: string is fill-only loot, so the fill stores what is already held and fallback junk covers the rest.
            if (item != null && lootOnly(need.getKey()) && NO_HUNT_LOOT.contains(need.getKey())) {
                System.out.println("MAZETEST FURNISH skip-loot " + need.getKey() + " why=no-hunt have=" + mod.getItemStorage().getItemCount(item) + "/" + need.getValue());
                skipped.add(need.getKey());
                continue;
            }
            if (item != null && mod.getItemStorage().getItemCount(item) < need.getValue() && TaskCatalogue.obtainableNow(item)) {
                // Ostinato: loot-only items (string, bowls) are optional; a fill stores whatever is held. Cap each at 3 min
                // so a spider hunt or cobweb search never blocks the cladding (run22: string x6 ran 5+ min with 0 webs).
                // Ostinato: run41 yo-yoed 14+ min between "Gathering barrel" and "Surfacing for barrel" in a cobweb mineshaft
                // (y27-40, light 0/133). Non-cladding woody items (barrel/chest/bowl) get a 6 min budget, surfacing included;
                // past it the item is skipped so lights/seal start and the CONTAINER step defers/skips instead.
                boolean woodyExtra = isWoody(need.getKey()) && !isCladding(need.getKey());
                // Ostinato: run94. Without the wood for this craft, do the shared leashed planks trip first; it is the
                // cladding's wood trip too, so it does not spend this item's 6-min craft budget or its leash skip.
                int missingCount = need.getValue() - mod.getItemStorage().getItemCount(item);
                if (woodyExtra && !lootOnly(need.getKey())) latchWood(mod, need.getKey(), missingCount);
                if (woodyExtra && !lootOnly(need.getKey()) && !woodReady.contains(need.getKey())) {
                    int pooled = Math.max(planksFor(mod, need.getKey(), missingCount), pooledWoodNeed(mod));
                    if (!need.getKey().equals(woodFor)) { woodFor = need.getKey(); System.out.println("MAZETEST FURNISH wood-pool for=" + woodFor + " planks=" + pooled + " " + woodInv(mod)); }
                    return pooledWoodGather(mod, feet, need.getKey(), pooled);
                }
                if (lootOnly(need.getKey()) || woodyExtra) {
                    if (!need.getKey().equals(gatherKey)) { gatherKey = need.getKey(); gatherTicks = 0; }
                    // Ostinato: run52. book/paper hunts dragged the bot ~350 blocks from the room into a flooded cave before the
                    // cladding gather even started. Optional loot is only worth it near home; past the leash it is skipped.
                    // Ostinato: run58. string/bowl were skipped at dist=65 (one block past the leash) and the barrel had
                    // nothing to hold. Walk back to the room once and gather there; skip only if still far after that.
                    if (lootOnly(need.getKey()) && homeDist(feet) > HOME_LEASH) {
                        Task home = returnHome(mod, feet, need.getKey());
                        if (home != null) return home;
                    }
                    if (lootOnly(need.getKey()) && homeDist(feet) > HOME_LEASH) {
                        System.out.println("MAZETEST FURNISH skip-loot " + need.getKey() + " why=far dist=" + homeDist(feet));
                        skipped.add(need.getKey());
                        continue;
                    }
                    if (++gatherTicks > (woodyExtra && !lootOnly(need.getKey()) ? 7200 : 3600)) {
                        System.out.println("MAZETEST FURNISH skip-loot " + need.getKey() + " have=" + mod.getItemStorage().getItemCount(item) + "/" + need.getValue());
                        skipped.add(need.getKey());
                        woodReady.remove(need.getKey()); // Ostinato: run94 the step regather re-checks the wood
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
                // Ostinato: run43. Craft in place when held planks/logs already cover the item; never surface for it.
                // Ostinato: run55. stick x13 asked hasWoodFor(13) = 104 planks, so the held cherry planks still read "no wood" and the
                // bot yo-yoed "Surfacing for stick" y57-79 for 9+ min. Wood need is now per recipe (sticks: 2 planks per 4), and a
                // surface climb that cannot get out (6000 ticks) falls back to the normal gather/craft from where the bot stands.
                // Ostinato: run96. A woody extra's wood comes from the pooled trip (or its latched craft), never this climb.
                if (isWoody(need.getKey()) && !(woodyExtra && !lootOnly(need.getKey())) && !hasWoodFor(mod, need.getKey(), need.getValue()) && needsSurface(mod, feet, need.getKey())) {
                    Task climb = surfaceOrGiveUp(mod, feet, need.getKey(), need.getValue()); // Ostinato: run56 shared, persistent budget
                    if (climb != null) return climb;
                }
                if (!lootOnly(need.getKey())) { Task home = returnHome(mod, feet, need.getKey()); if (home != null) return home; } // Ostinato: run52
                // Ostinato: run65/66. After the one-shot return the gather was unbounded (book/barrel/planks drifted 65-100+ blocks).
                // Ostinato: run94. A woody extra reaching here holds its wood and crafts in place; distance does not matter.
                if (homeDist(feet) > leashFor(need.getKey()) && !(woodyExtra && woodReady.contains(need.getKey()))) {
                    if (!isCladding(need.getKey())) { System.out.println("MAZETEST FURNISH skip-gather " + need.getKey() + " why=leash dist=" + homeDist(feet)); skipped.add(need.getKey()); continue; }
                    Task leash = gatherLeash(mod, feet, need.getKey(), false); if (leash != null) return leash;
                }
                // Ostinato: run66. Baritone paused on "Missing materials for 1x crafting_table" while gathering barrel; get the table first.
                if (isWoody(need.getKey()) && needsTable(need.getKey()) && !tableAvailable(mod) && hasWoodFor(mod, "crafting_table", 1)) {
                    setDebugState("Crafting table for " + need.getKey());
                    return TaskCatalogue.getItemTask(Items.CRAFTING_TABLE, 1);
                }
                setDebugState("Gathering " + need.getKey() + " x" + need.getValue());
                hidePlacedCladding(mod, need.getKey()); // Ostinato: never mine the room we are building
                return TaskCatalogue.getItemTask(item, need.getValue());
            }
        }
        if (!gathered) System.out.println("MAZETEST FURNISH gathered skipped=" + skipped);
        gathered = true;
        if (fill != null && fill.isFinished()) { fill = null; index++; } // Ostinato: run58
        if (fill != null) {
            // Ostinato: run40 audit. A FILL that cannot reach/open its barrel gets the same defer-then-skip as a light.
            // Ostinato: run58. A starved fill (barrel open, nothing to move) goes back through stepTask, which picks
            // fallback loot or mines spare cobble, instead of idling at the barrel until skip-fill.
            if (fill instanceof MazeBarrelFillTask bf && bf.starved() && !bf.isFinished()) {
                System.out.println("MAZETEST FURNISH fill-regather " + work.get(index).x() + " " + work.get(index).y() + " " + work.get(index).z());
                fill = null; loggedFill = false;
            } else if (!fill.isFinished() && ++stepTicks <= 600) {
                return fill;
            }
            if (!fill.isFinished()) deferStep("timeout");
            else { fill = null; index++; }
        }
        // Ostinato: run32 looped 4min on one ceiling block (builder "Failed to place" from the same spot) toward a FAIL.
        // A LIGHT step stuck 60s is deferred to a second pass (new approach angle); stuck again there, it is skipped.
        for (int pass = 0; pass < 2; pass++) {
            while (work != null && index < work.size()) {
                MazeFurnishPlan.Step step = work.get(index);
                if (!secondPass && deferred.contains(index)) { index++; continue; }
                // Ostinato: run90. Deferred lights were retried after the seals, so two ceiling holes could only be reached
                // from inside a sealed room (bot on the roof, 3 repair rounds, 131/133). Retry them while it is still open.
                // Ostinato: the interior repairs of this round are done; the opened seal cells are protected again (once
                // re-placed) and the SEAL steps below close the room the same way the first seal pass did.
                if (repairOpen && step.kind() == MazeFurnishPlan.Kind.SEAL) { repairOpen = false; System.out.println("MAZETEST FURNISH repair-reseal"); }
                if (!secondPass && step.kind() == MazeFurnishPlan.Kind.SEAL && !deferred.isEmpty()) {
                    System.out.println("MAZETEST FURNISH retry-before-seal " + deferred.size());
                    break;
                }
                Task sub = stepTask(mod, step);
                if (sub != null) {
                    if (index != stepIdx) { stepIdx = index; stepTicks = 0; }
                    // Ostinato: only place attempts count toward the 15s defer; a mid-cladding regather used to defer every light.
                    // Ostinato: run40 audit. CONTAINER/FILL/SEAL get the same defer-then-skip as LIGHT (30s each) instead of
                    // burning stuck-recover into a FAIL on one cell. A deferred SEAL retries with throwaway blocks first.
                    boolean placing = sub instanceof PlaceBlockTask || sub instanceof MazeCeilingPlaceTask || sub instanceof StoreInContainerTask || sub instanceof MazeBarrelFillTask;
                    // Ostinato: run58. A deferred light retries with the direct stand-and-click task, which walks to a new
                    // stand cell first; 15s was used up by the walk, so the second pass gets 45s.
                    // Ostinato: run96. Walls go direct on the first pass too, so the direct task gets 45s on either pass.
                    int budget = step.kind() == MazeFurnishPlan.Kind.LIGHT ? (secondPass || sub instanceof MazeCeilingPlaceTask ? 900 : 300) : 600;
                    if (placing && ++stepTicks > budget) {
                        deferStep("timeout");
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
        // Ostinato: run62 FAIL one floor plank short. 105,79,-22 was skipped (why=stuck, bot stood over the hole) and
        // later used as a bad stand; nothing revisited it, so verify saw air (light 132/133). Before handing back,
        // re-check every planned LIGHT/CONTAINER/SEAL cell and re-place any that are air or wrong (skipped, dug through,
        // or broken while placing seals/lights) with the direct stand-and-click task. Up to 3 sweeps.
        if (work != null && index >= work.size() && repairRounds >= 3) repairDone = true;
        if (work != null && index >= work.size() && repairRounds < 3) {
            java.util.List<Integer> wrong = new java.util.ArrayList<>();
            StringBuilder cells = new StringBuilder();
            for (int i = 0; i < work.size(); i++) {
                if (cellWrong(mod, work.get(i))) {
                    wrong.add(i);
                    MazeFurnishPlan.Step s = work.get(i);
                    if (cells.length() < 400) cells.append(' ').append(s.kind()).append('@').append(s.x()).append(',').append(s.y()).append(',').append(s.z());
                }
            }
            if (!wrong.isEmpty()) {
                repairRounds++;
                // Ostinato: run93 room 1. Four lights were wrong after the seal; the bot was outside a room enclosed by
                // protected cells (walls, finished seal), every walk-in searched 1M+ nodes, and rounds 2-3 skipped all four.
                // With only interior cells wrong and the seal finished, this round may break the seal cells to get back in.
                boolean sealDone = true, interiorWrong = false;
                for (int i : wrong) { if (work.get(i).kind() == MazeFurnishPlan.Kind.SEAL) sealDone = false; else interiorWrong = true; }
                if (sealDone && interiorWrong && !sealCells.isEmpty()) { repairOpen = true; System.out.println("MAZETEST FURNISH repair-open-seal cells=" + sealCells.size()); }
                System.out.println("MAZETEST FURNISH repair-sweep round=" + repairRounds + " cells=" + wrong.size() + cells);
                deferred.clear(); deferred.addAll(wrong); secondPass = true; index = wrong.get(0); stepIdx = -1; stepTicks = 0;
                return null;
            }
            if (repairRounds > 0 && !repairCleanLogged) { repairCleanLogged = true; System.out.println("MAZETEST FURNISH repair-sweep clean rounds=" + repairRounds); }
            repairDone = true;
        }
        return null;
    }

    private MazeCeilingPlaceTask ceilTask; private Block[] ceilHave; // Ostinato: run91
    private int repairRounds; private boolean repairCleanLogged; // Ostinato: run62
    // Ostinato: run92 room 3 FAIL 3 cells short after one repair round. A skip on the stuck path moved index past the end
    // outside the step loop, so isFinished() was true before the next tick could run the sweep: rounds 2-3 never ran.
    private boolean repairDone;
    private final java.util.Map<String, Integer> stepRegather = new java.util.HashMap<>(); // Ostinato: run94
    private boolean repairOpen; // Ostinato: run93, seal cells may be broken to reach interior repairs
    private final java.util.Set<BlockPos> sealCells = new java.util.HashSet<>();

    // Ostinato: run62. Pure check (no side effects) that a planned cell is missing or holds the wrong block.
    private boolean cellWrong(AltoClef mod, MazeFurnishPlan.Step step) {
        BlockPos pos = new BlockPos(step.x(), step.y(), step.z());
        BlockState state = mod.getWorld().getBlockState(pos);
        switch (step.kind()) {
            case LIGHT:
            case CONTAINER: {
                if (skippedFluid.contains(pos)) return false;
                List<Block> wanted = blocks(step.blockIds());
                if (step.kind() == MazeFurnishPlan.Kind.LIGHT && woodCell(step)) wanted = withAllPlanks(wanted);
                return !wanted.isEmpty() && !wanted.contains(state.getBlock());
            }
            case SEAL:
                return state.isAir() || !state.getFluidState().isEmpty();
            default:
                return false;
        }
    }

    private static final Item[] SWORDS = {Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
    private static final Item[] PICKS = {Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE};
    private static final Item[] IRON_ARMOR = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    // Ostinato: run97. Copper first, like the tunnel kit (copper -> iron): room 1's iron kit spent its whole 3-min budget
    // mining iron_ore and equipped nothing (skip-armor). Copper ore is far more common. Iron where copper does not exist.
    private static final Item[] KIT_ARMOR = McCompat.item("copper_helmet") == null ? IRON_ARMOR : new Item[]{McCompat.item("copper_helmet"),
            McCompat.item("copper_chestplate"), McCompat.item("copper_leggings"), McCompat.item("copper_boots")};
    // Ostinato: run99. The first kit piece (helmet, chestplate, leggings, boots) whose slot holds neither it nor iron.
    private static Item nextKitPiece() {
        for (int i = 0; i < KIT_ARMOR.length; i++)
            if (!adris.altoclef.util.helpers.StorageHelper.isArmorEquipped(KIT_ARMOR[i], IRON_ARMOR[i])) return KIT_ARMOR[i];
        return null;
    }
    private static final Item[] KEEP = {Items.BREAD, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.BAKED_POTATO, Items.APPLE, Items.CARROT, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.SHIELD}; // Ostinato
    // Ostinato: run60. Fill-only loot that needs a long hunt (cobwebs/spiders). Used from inventory only, never gathered.
    // Ostinato: run100. Fill loot whose only source is ore far below the room is not hunted either: room 1's pipes barrel
    // wanted redstone x6, the bot mined down to y~11 (the leash only counts depth past 16), stuck in a pocket at y=37 on the
    // way back, skipped the fill and all four seals and the room failed verify. The fill stores what is held; fallback junk
    // covers the rest. Diamond/emerald were already kept out of the material list; this keeps them out of the fill hunt.
    private static final java.util.Set<String> NO_HUNT_LOOT = java.util.Set.of("string",
            "redstone", "redstone_torch", "repeater", "comparator", "lapis_lazuli", "diamond", "emerald");
    private String lastKit = ""; private int armorTicks; private boolean armorGaveUp, died; // Ostinato
    private final java.util.Set<Integer> deferred = new java.util.HashSet<>(); // Ostinato
    private boolean secondPass; private int stepIdx = -1, stepTicks; // Ostinato
    private boolean escapingWater, jumpHeld; private int escapeTicks; private Task escapeTask; // Ostinato: water latch
    private final java.util.Set<BlockPos> sealFallback = new java.util.HashSet<>(); // Ostinato: deferred seals use throwaways

    // Ostinato: true while the current tick is working a plan step (not kit, gather or water).
    private boolean inStep() {
        String st = getDebugState();
        if (!gathered || work == null || index >= work.size() || st == null) return false;
        for (MazeFurnishPlan.Kind k : MazeFurnishPlan.Kind.values()) if (st.startsWith(k + " ")) return true;
        return false;
    }

    // Ostinato: defer the current step to the second pass, or skip it when already there. Logs defer-/skip-<kind>.
    private void deferStep(String why) {
        MazeFurnishPlan.Step step = work.get(index);
        String kind = step.kind().name().toLowerCase();
        System.out.println("MAZETEST FURNISH " + (secondPass ? "skip-" : "defer-") + kind + " " + step.x() + " " + step.y() + " " + step.z() + " why=" + why
                + (secondPass && step.kind() == MazeFurnishPlan.Kind.SEAL ? " SEAL-LEFT-OPEN" : ""));
        if (!secondPass) deferred.add(index);
        if (step.kind() == MazeFurnishPlan.Kind.SEAL) sealFallback.add(new BlockPos(step.x(), step.y(), step.z()));
        fill = null; stuckTicks = 0; stuckRecoveries = 0; stepTicks = 0; index++;
    }

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
            // Ostinato: run53. The pick broke during the roof pass with 100+ cherry planks held; this climbed out above the
            // room roof "Surfacing for stick" and pathed ~300 blocks. Sticks come from held planks; surface only without wood.
            // Ostinato: run56. This kit path surfaced with no budget at all (the run55 6000-tick give-up only lived in the gather
            // loop), so an unarmed bot yo-yoed "Surfacing for stick" y85-93 from t=100 to t=12000+ with no surface-giveup line.
            // A held stick already covers the sword/pick; otherwise the same persistent give-up applies.
            if (mod.getItemStorage().getItemCount(Items.STICK) < 1 && !hasWoodFor(mod, "stick", 1) && needsSurface(mod, feetNow, "stick")) {
                Task climb = surfaceOrGiveUp(mod, feetNow, "stick", 1);
                if (climb != null) return climb;
            }
            step = "stone_sword"; t = TaskCatalogue.getItemTask(Items.STONE_SWORD, 1);
        }
        // Run36: stone pick broke mid-ceiling. MazeCeilingPlaceTask then sat on "No pickaxe" for 60s per cell (31 defers, lights stuck at 79).
        else if (!mod.getItemStorage().hasItem(PICKS)) { step = "stone_pickaxe"; hideAllPlaced(mod); t = TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1); }
        // Ostinato: run50. Replace a worn pick while it can still mine stone, instead of losing it mid-shaft and rebuilding
        // the kit from a wooden pick inside the seal column. One spare when the only pick is under 12 uses.
        else if (pickWorn(mod)) { step = "spare_pickaxe"; hideAllPlaced(mod); t = TaskCatalogue.getItemTask(Items.STONE_PICKAXE, mod.getItemStorage().getItemCount(Items.STONE_PICKAXE) + 1); }
        // Ostinato: run57 kit-food yo-yo. Food score hit 0 mid-place and the food hunt ran 13 min while the hunger bar was not
        // urgent, starving fill/seals (fill-stand restarted every 20s cooldown window). Only hunt when hunger is actually low
        // (bar < 14, not peaceful); otherwise defer and let fill/seals/lights run. Re-arms as soon as the bar drops.
        else if (food < gate && !foodGaveUp && !hungerUrgent(mod)) {
            if (!foodDeferred) { System.out.println("MAZETEST FURNISH defer-food food=" + food + " hunger=" + mod.getPlayer().getHungerManager().getFoodLevel()); foodDeferred = true; }
            foodTicks = 0; step = "ready"; t = null;
        }
        else if (food < gate && !foodGaveUp) {
            if (foodDeferred) { System.out.println("MAZETEST FURNISH rearm-food food=" + food + " hunger=" + mod.getPlayer().getHungerManager().getFoodLevel()); foodDeferred = false; }
            // Ostinato: run40 audit. Under 6 the bot starves; give the hunt 4 min and retry after 20s instead of 1.5/1 min.
            if (++foodTicks > (food < 6 ? 4800 : 1800)) {
                System.out.println("MAZETEST FURNISH skip-food food=" + food);
                foodGaveUp = true; foodTicks = 0; foodCooldown = food < 6 ? 400 : 1200; // Ostinato: 5 min -> 1 min
                step = "ready"; t = null;
            } else {
                // Match the gate. CollectFoodTask(40) keeps hunting after the kit would already be satisfied.
                step = "food"; t = new adris.altoclef.tasks.resources.CollectFoodTask(gate);
            }
        }
        // Ostinato: run39 iron armor blocked lights ~11 min (40 unreachable iron_ore tries) and was rebuilt after a drown.
        // Armor is optional: only before the place phase, only on the first life, 3 min budget.
        // Ostinato: run40 audit. Never mine iron for armor on an empty stomach (food < 6); food comes first.
        // Ostinato: run99. The whole copper set was one EquipArmorTask: 24 ingots planned up front, nothing worn until all
        // four pieces existed, and each room's 3-min budget ran out mid-smelt (skip-armor in runs 98 and 99). One piece at a
        // time now, helmet first; a finished piece is worn at once and the next room's budget starts on the next piece.
        else if (food >= 6 && !armorGaveUp && !gathered && !died && nextKitPiece() != null) {
            Item piece = nextKitPiece();
            if (++armorTicks > 3600) { System.out.println("MAZETEST FURNISH skip-armor next=" + net.minecraft.registry.Registries.ITEM.getId(piece).getPath()); armorGaveUp = true; step = "ready"; t = null; }
            else { step = net.minecraft.registry.Registries.ITEM.getId(piece).getPath(); t = new adris.altoclef.tasks.misc.EquipArmorTask(piece); }
        }
        else { step = "ready"; t = null; }
        if (!step.equals(lastKit)) {
            // Ostinato: run98. Provisioned wood (38 planks-equivalent) was down to planks=6 logs=0 when the copper kit ended,
            // and nothing logged where it went. Print the wood held at every kit step change.
            System.out.println("MAZETEST FURNISH kit " + step + " food=" + food + " " + woodInv(mod));
            lastKit = step;
        }
        if (t != null) setDebugState("Kit " + step);
        // Ostinato: run53. Mid-place kit gathers (stone/spare pick) are nearest-to-player; never run them from hundreds of
        // blocks out while lights/fill are unfinished. Walk back inside the leash first.
        // Ostinato: run57. Food sources sat ~65 blocks out: the gather crossed the 64 leash, return-home walked back one block,
        // the gather resumed and crossed again (13 min "Kit food" <-> "Returning to room for kit food"). Food gets a wider
        // leash, and a started return runs until well inside it (hysteresis) instead of flipping at the boundary.
        int kitLeash = "food".equals(step) ? FOOD_LEASH : HOME_LEASH;
        int kd = homeDist(mod.getPlayer().getBlockPos());
        if (kitHome != null && (kitHome.isFinished() || kd <= kitLeash - 24 || t == null)) kitHome = null;
        if (t != null && gathered && (kd > kitLeash || kitHome != null)) {
            BlockPos h = home();
            if (kitHome == null) {
                kitHome = new adris.altoclef.tasks.movement.GetToBlockTask(h);
                System.out.println("MAZETEST FURNISH return-home for=kit-" + step + " dist=" + homeDist(mod.getPlayer().getBlockPos()));
            }
            setDebugState("Returning to room for kit " + step);
            return kitHome;
        }
        return t;
    }

    // Ostinato: true when a wood gather is still under a cave ceiling. A tree canopy is not a ceiling.
    private boolean needsSurface(AltoClef mod, BlockPos feet, String key) {
        int surfaceY = mod.getWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
        if (surfaceY <= 0) return false;
        // Ostinato: run98. 3 planks short for the barrel at y=87, "Surfacing for barrel" yo-yoed with the gather for 2.5 min
        // until surface-giveup; the in-place gather then had its log within ~5s. A reachable log the scanner already
        // knows near the bot is mined from here (CollectPlanksTask mines logs only); the climb is for when none is known.
        if (mod.getBlockScanner().getNearestBlock(net.minecraft.util.math.Vec3d.ofCenter(feet), adris.altoclef.util.helpers.WorldHelper::canReach,
                adris.altoclef.util.helpers.ItemHelper.itemsToBlocks(adris.altoclef.util.helpers.ItemHelper.LOG))
                .map(p -> p.getSquaredDistance(feet) <= 32 * 32).orElse(false)) return false;
        int solid = 0;
        for (int dy = 2; dy <= 6; dy++) {
            BlockPos p = feet.up(dy);
            BlockState st = mod.getWorld().getBlockState(p);
            if (!st.isAir() && !st.getCollisionShape(mod.getWorld(), p).isEmpty()) solid++;
        }
        // Run41: mining one ceiling block used to clear the buried latch at y=33, so barrel gather and
        // surface swapped every few seconds. Keep climbing until the sky is open.
        boolean sky = mod.getWorld().isSkyVisible(feet.up());
        boolean openAbove = solid == 0;
        boolean near = sky || (openAbove && feet.getY() >= surfaceY - 20);
        if (near) { surfacedFor = key; return false; }
        if (key.equals(surfacedFor) && openAbove) return false;
        return feet.getY() < surfaceY - 6;
    }

    // Run37 died at y=28: GetToYTask(surfaceY) sat in a sealed pocket until the 90s abort.
    // Mine the closest ceiling, then step up eight. A full-height goal paths sideways and stalls.
    private Task surfaceClimb(AltoClef mod, BlockPos feet, String key) {
        setDebugState("Surfacing for " + key);
        for (int dy = 2; dy <= 5; dy++) {
            BlockPos p = feet.up(dy);
            BlockState st = mod.getWorld().getBlockState(p);
            // Ostinato: never mine a planned cell to get out; the entry shaft is the way up.
            if (!st.isAir() && !st.getCollisionShape(mod.getWorld(), p).isEmpty() && !plannedCells.contains(p)) {
                return new DestroyBlockTask(p);
            }
        }
        int surfaceY = mod.getWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
        int goal = Math.min(Math.max(surfaceY, feet.getY() + 1), feet.getY() + 8);
        return new adris.altoclef.tasks.movement.GetToYTask(goal);
    }

    // Ostinato: run43. Planks (logs count 4) enough for a woody craft: 8 per item covers barrel (6 planks + slab), chest, bowls.
    private final java.util.Set<String> satisfied = new java.util.HashSet<>();
    // Ostinato: run52. Gathers ran from wherever the last loot hunt ended (feet ~349 blocks out, y58 in water), so
    // "Gathering any planks x105" and then polished_diorite pathed 277+ blocks and burned stuck-recovers far from the
    // room. Nearest-source tasks (CollectPlanksTask over every log type, MineAndCollect) are nearest to the PLAYER, so
    // walk back near the room first: the woods/stone around the room win, and no wood type is excluded.
    // Once per item, so an item whose only source is far still gets gathered there.
    private static final int HOME_LEASH = 64;
    private static final int FOOD_LEASH = 128; // Ostinato: run57 food sources are sparse; don't yo-yo at 64
    private boolean foodDeferred; // Ostinato: run57
    // Ostinato: run66. Hard gather leash with hysteresis for every furnish gather (not once per item like returnHome).
    private static final int GATHER_LEASH = 96;
    private int homeTicks, leashTicks, leashTrips;
    private Task leashHome;
    // Ostinato: run88. Oak within 96 of the room was chopped out: 5 trips walked home to 56 and straight back out to the same
    // trees at 97 (14 min flat, light 0/133). The leash now widens by 32 per trip for that item, so the next trip reaches it.
    private final java.util.Map<String, Integer> keyTrips = new java.util.HashMap<>();
    private int leashFor(String key) { return GATHER_LEASH + 32 * keyTrips.getOrDefault(key, 0); }
    private Task gatherLeash(AltoClef mod, BlockPos feet, String key, boolean check) {
        int d = homeDist(feet); BlockPos h = home();
        if (h == null || leashTrips > 6) return null;
        if (leashHome != null && (leashHome.isFinished() || d <= leashFor(key) - 40 || ++leashTicks > 3600)) { leashHome = null; return null; }
        if (leashHome == null) {
            if (check && d <= leashFor(key)) return null;
            leashHome = new adris.altoclef.tasks.movement.GetToBlockTask(h); leashTicks = 0; leashTrips++; keyTrips.merge(key, 1, Integer::sum);
            System.out.println("MAZETEST FURNISH gather-leash for=" + key + " dist=" + d + " trip=" + leashTrips + " nextLeash=" + leashFor(key));
        }
        setDebugState("Leash back to room for " + key + " dist=" + d);
        return leashHome;
    }
    // Ostinato: run57. Hunger is urgent when the bar is under 14 (sprint stops at 6, regen at 18) and not on peaceful.
    private static boolean hungerUrgent(AltoClef mod) {
        if (mod.getWorld().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return false;
        return mod.getPlayer().getHungerManager().getFoodLevel() < 14;
    }
    private final java.util.Set<String> returnedHome = new java.util.HashSet<>();
    private Task homeTask, kitHome; // Ostinato: run53 kitHome
    private BlockPos home() {
        if (plannedCells.isEmpty()) return null;
        // Ostinato: run87. home had y=0 and every leash was horizontal: an andesite gather 50 blocks under the room read as
        // dist=4, so the walk home finished at once and the bot stayed in the cave. home is now on top of the roof: right at the
        // room but outside it (a sealed room cannot be pathed into without breaking protected cells).
        long x = 0, z = 0; int maxY = Integer.MIN_VALUE; for (BlockPos c : plannedCells) { x += c.getX(); z += c.getZ(); maxY = Math.max(maxY, c.getY()); }
        return new BlockPos((int) (x / plannedCells.size()), maxY + 1, (int) (z / plannedCells.size()));
    }
    private int homeDist(BlockPos feet) {
        BlockPos h = home(); if (h == null) return 0;
        // Ostinato: run87. Depth counts past 16 blocks above/below the room (a cave under the room is not "home").
        return (int) Math.sqrt(Math.pow(feet.getX() - h.getX(), 2) + Math.pow(feet.getZ() - h.getZ(), 2)) + Math.max(0, Math.abs(feet.getY() - h.getY()) - 16);
    }
    private Task returnHome(AltoClef mod, BlockPos feet, String key) {
        BlockPos h = home();
        if (h == null || returnedHome.contains(key)) return null;
        int d = homeDist(feet);
        if (homeTask == null && d <= HOME_LEASH) return null;
        if (homeTask == null) { homeTask = new adris.altoclef.tasks.movement.GetToBlockTask(h); homeTicks = 0; System.out.println("MAZETEST FURNISH return-home for=" + key + " dist=" + d); }
        // Ostinato: run65. "Returning to room for book dist=65" never finished (wandered 65-81). Cap the walk at 2 min.
        if (++homeTicks > 2400) { System.out.println("MAZETEST FURNISH return-home giveup for=" + key + " dist=" + d); returnedHome.add(key); homeTask = null; return null; }
        if (d > 24 && !homeTask.isFinished()) { setDebugState("Returning to room for " + key + " dist=" + d); return homeTask; }
        returnedHome.add(key); homeTask = null;
        return null;
    }

    // Ostinato: run56. The surface budget is keyed by room + item and kept for the whole client run (static), so neither a
    // stuck-recover wander, a stall reset, nor the bridge re-creating this task can restart it. Ticks only count while a
    // climb is actually being returned; once spent, that item is gathered/crafted from where the bot stands.
    private static final java.util.Map<String, Integer> SURFACE_TICKS = new java.util.HashMap<>();
    private static final java.util.Set<String> SURFACE_GAVE_UP = new java.util.HashSet<>();
    private static final int SURFACE_BUDGET = 6000;
    private String surfaceKey(String key) { return plan.summary + "|" + key; }
    private int surfaceBestY = Integer.MIN_VALUE, surfaceBestT; private String surfaceBestKey; // Ostinato: run92
    private Task surfaceOrGiveUp(AltoClef mod, BlockPos feet, String key, int count) {
        String k = surfaceKey(key);
        if (SURFACE_GAVE_UP.contains(k)) return null;
        int t = SURFACE_TICKS.merge(k, 1, Integer::sum);
        if (t == 1 || t % 1200 == 0) System.out.println("MAZETEST FURNISH surface-for " + key + " x" + count + " ticks=" + t + "/" + SURFACE_BUDGET + " " + woodInv(mod) + " feet=" + feet.toShortString());
        // Ostinato: run92. "Surfacing for barrel" sat at one spot (y82) for the whole 5-min budget. No height gained in 60s
        // means the climb cannot work from here: give up now and gather in place. Logs what is overhead.
        if (!k.equals(surfaceBestKey)) { surfaceBestKey = k; surfaceBestY = Integer.MIN_VALUE; }
        if (surfaceBestY == Integer.MIN_VALUE || feet.getY() > surfaceBestY) { surfaceBestY = feet.getY(); surfaceBestT = t; }
        if (t - surfaceBestT > 1200) {
            StringBuilder above = new StringBuilder();
            for (int dy = 2; dy <= 5; dy++) { BlockPos p = feet.up(dy); above.append(net.minecraft.registry.Registries.BLOCK.getId(mod.getWorld().getBlockState(p).getBlock()).getPath()).append(plannedCells.contains(p) ? "(planned)" : "").append(' '); }
            System.out.println("MAZETEST FURNISH surface-stalled " + key + " y=" + feet.getY() + " above=" + above + "sky=" + mod.getWorld().isSkyVisible(feet.up()));
            t = SURFACE_BUDGET + 1;
        }
        if (t > SURFACE_BUDGET) {
            System.out.println("MAZETEST FURNISH surface-giveup " + key + " gather-in-place ticks=" + t + " " + woodInv(mod) + " feet=" + feet.toShortString());
            SURFACE_GAVE_UP.add(k);
            return null;
        }
        return surfaceClimb(mod, feet, key);
    }
    private static String woodInv(AltoClef mod) {
        int planks = 0, logs = 0;
        for (Item i : adris.altoclef.util.helpers.ItemHelper.PLANKS) planks += mod.getItemStorage().getItemCount(i);
        for (Item i : adris.altoclef.util.helpers.ItemHelper.LOG) logs += mod.getItemStorage().getItemCount(i);
        return "inv planks=" + planks + " logs=" + logs + " slabs=" + mod.getItemStorage().getItemCount(adris.altoclef.util.helpers.ItemHelper.WOOD_SLAB)
                + " sticks=" + mod.getItemStorage().getItemCount(Items.STICK) + " table=" + tableAvailable(mod);
    }
    // Ostinato: run55. Planks a woody item needs: sticks are 4 per 2 planks; other crafts keep the 8-per-item budget.
    // Ostinato: run94. Per recipe now: the flat 8 left a barrel (6 planks + 2 slabs = 9) with no crafting table 5 planks
    // short, so its own gather hunted logs/slabs far from the room. A 3x3 craft also pays 4 for the table when none is held.
    private static int recipePlanks(String key, int count) {
        int n = Math.max(1, count);
        switch (key) {
            case "stick": return 2 * ((n + 3) / 4);
            case "crafting_table": return 4 * n;
            case "barrel": return 6 * n + 3 * ((2 * n + 5) / 6);
            case "chest": return 8 * n;
            case "bowl": return 3 * ((n + 3) / 4);
            default:
                if (key.endsWith("_planks")) return n;
                if (key.endsWith("_slab")) return 3 * ((n + 5) / 6);
                return 8 * n;
        }
    }
    private static boolean needsTable(String key) {
        return key.equals("barrel") || key.equals("chest") || key.equals("bowl") || key.endsWith("_slab");
    }
    private static int planksFor(AltoClef mod, String key, int count) {
        return recipePlanks(key, count) + (needsTable(key) && !tableAvailable(mod) ? 4 : 0);
    }
    private static boolean hasWoodFor(AltoClef mod, String key, int count) {
        return heldWood(mod) >= planksFor(mod, key, count);
    }
    // Ostinato: run98. 38 planks-equivalent fell to 6 during the copper kit and no line said what spent it. Before the
    // place phase, every drop in held wood names the running task chain once.
    private int lastWood = -1;
    private void traceWoodSpend(AltoClef mod) {
        int w = heldWood(mod);
        if (lastWood >= 0 && w < lastWood && !gathered) {
            StringBuilder chain = new StringBuilder();
            Task c = this.getSub();
            for (int d = 0; c != null && d < 6; d++, c = c.getSub()) { String n = c.toString(); chain.append(n, 0, Math.min(70, n.length())).append(" / "); }
            System.out.println("MAZETEST FURNISH wood-spent " + lastWood + "->" + w + " " + woodInv(mod) + " via " + chain);
        }
        lastWood = w;
    }
    private static int heldWood(AltoClef mod) {
        int planks = heldPlanks(mod);
        for (Item i : adris.altoclef.util.helpers.ItemHelper.LOG) planks += 4 * mod.getItemStorage().getItemCount(i);
        return planks + mod.getItemStorage().getItemCount(adris.altoclef.util.helpers.ItemHelper.WOOD_SLAB) / 2; // Ostinato: run96 slabs are wood
    }
    // Ostinato: run96. A held table or a reachable placed one (CraftInTableTask reuses any within 40 blocks); the furnish
    // places its table and then read 4 planks short for the barrel, so the craft looked unfunded mid-way.
    private static boolean tableAvailable(AltoClef mod) {
        if (mod.getItemStorage().hasItem(Items.CRAFTING_TABLE)) return true;
        return mod.getBlockScanner().getNearestBlock(mod.getPlayer().getPos(), adris.altoclef.util.helpers.WorldHelper::canReach, net.minecraft.block.Blocks.CRAFTING_TABLE)
                .map(p -> p.isWithinDistance(mod.getPlayer().getPos(), 32)).orElse(false);
    }
    // Ostinato: run96. The latch outlived the wood: set on the first tick, the craft spent it (table placed, planks into
    // slabs) without a barrel, hasWoodFor read short, and the latched barrel took its own surface climb (surface-giveup at
    // planks=5) instead of the pooled trip. A latch that reads short for 10s straight is dropped, so the pooled trip runs.
    private void latchWood(AltoClef mod, String key, int count) {
        if (hasWoodFor(mod, key, count)) { woodReady.add(key); woodShort.remove(key); return; }
        if (!woodReady.contains(key)) return;
        if (woodShort.merge(key, 1, Integer::sum) > 200) {
            woodReady.remove(key); woodShort.remove(key);
            System.out.println("MAZETEST FURNISH wood-latch-drop " + key + " x" + count + " " + woodInv(mod));
        }
    }
    // Ostinato: run94. A woody item the gather loop still has to craft (barrel, chest, crafting table; not loot, not cladding).
    private boolean woodyExtra(String key) {
        return isWoody(key) && !isCladding(key) && !lootOnly(key) && !skipped.contains(key) && !satisfied.contains(key);
    }
    // Ostinato: run94. Planks for one pooled wood trip: the wrong wood cladding cells plus every woody extra still to craft,
    // with the crafting table counted once.
    private int pooledWoodNeed(AltoClef mod) {
        int n = 0; boolean table = false;
        for (Map.Entry<String, Integer> e : materials.entrySet()) {
            if (!woodyExtra(e.getKey())) continue;
            Item it = McCompat.item(e.getKey());
            int missing = Math.min(e.getValue() - (it == null ? 0 : mod.getItemStorage().getItemCount(it)), stillMissing(mod, e.getKey()));
            if (missing <= 0) continue;
            n += recipePlanks(e.getKey(), missing);
            table |= needsTable(e.getKey());
        }
        if (table && !tableAvailable(mod)) n += 4;
        return n + Math.max(0, plankCladdingMissing(mod));
    }
    // Ostinato: run94. The barrel was the first wood item with 0 wood held: its own gather surfaced, chased a log/slab 97
    // blocks out and was skipped at the first leash breach, so CONTAINER/FILL could not pass. Woody extras and wood cladding
    // now share one leashed planks trip from the room (any log, nearest to home); the crafts then run from held planks.
    private Task pooledWoodGather(AltoClef mod, BlockPos feet, String key, int pooled) {
        Task home = returnHome(mod, feet, "wood-" + key); // Ostinato: run52 gather near the room; the craft keeps its own return
        if (home != null) return home;
        if (needsSurface(mod, feet, key)) { Task climb = surfaceOrGiveUp(mod, feet, key, pooled); if (climb != null) return climb; }
        Task leash = gatherLeash(mod, feet, "wood", true); if (leash != null) return leash; // Ostinato: run66, shared widening
        setDebugState("Gathering any planks x" + pooled + " (for " + key + ")");
        return new adris.altoclef.tasks.resources.CollectPlanksTask(pooled);
    }

    private int surfaceStuckLimit() {
        String state = getDebugState();
        return state != null && state.startsWith("Surfacing") ? 6 : 2;
    }
    /** The sub task still needed for this step, or null when the world already matches it. */
    private Task stepTask(AltoClef mod, MazeFurnishPlan.Step step) {
        lootGathering = false; // Ostinato: run91, set again only by the FILL loot gather
        BlockPos pos = new BlockPos(step.x(), step.y(), step.z());
        BlockState state = mod.getWorld().getBlockState(pos);
        switch (step.kind()) {
            case LIGHT:
            case CONTAINER: {
                List<Block> wanted = blocks(step.blockIds());
                // Ostinato: run48. The pooled gather brings back whatever planks it finds (oak from 300 blocks out), but
                // this step only accepted the plan's cherry_planks: have=[] -> "regather any_planks x105 for cherry_planks"
                // -> gather loop saw 105 held planks, satisfied -> step again, forever. A wood cladding cell takes any planks.
                if (step.kind() == MazeFurnishPlan.Kind.LIGHT && woodCell(step)) wanted = withAllPlanks(wanted);
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
                // Ostinato: run42 audit. A floor insert breaks the block the bot stands on, so the bot drops into the hole
                // and Baritone can never fill the cell it is standing in (BlockItem.canPlace includes the placer's body).
                // Five floor cells timed out and were skipped that way. Step onto a neighbouring floor cell first.
                Task stepOut = stepOutOf(mod, pos);
                if (stepOut != null) return stepOut;
                Block[] have = wanted.stream().filter(b -> mod.getItemStorage().hasItem(b.asItem())).toArray(Block[]::new);
                // Ostinato: interior ceiling. The cell under it is open, so the builder's side-face search wanders
                // after it breaks the stone. Click the underside of the block above from the floor.
                // Ostinato: run58. Wall/corner cells went to Baritone's builder ("Failed to place, wandering timeout") on
                // both passes, so nine cells were skipped. A deferred light now uses the direct task: mine, pick a stand
                // inside the room in reach, click a solid neighbour's face.
                // Ostinato: run96. The builder timed out (15s each) on 29 of room 2's cells with a block above (walls, inner
                // pipes), then the second pass placed them direct (run92: 79 of 84 deferred cells). A light cell under a solid
                // block goes direct first; the builder keeps the floor cells, where it ran 56 lights in 4 min.
                // Ostinato: run97. It did not: room 1's 18 floor cells at y=90 all timed out (15s each). Baritone's GoalBreak
                // refuses a stand above the block (goalBreakFromAbove is off), so a floor cell under open room air has its
                // only break stands inside the solid floor; the builder dug down, fell in and wandered. Every full-block light
                // goes direct now; the ring stand never stands on the target, and wall cells placed in ~1s each that way.
                boolean direct = openBelow(mod, pos) || step.kind() == MazeFurnishPlan.Kind.LIGHT;
                if (have.length > 0 && direct && !thinLight(have)) {
                    // Ostinato: run91. A fresh task each restart of the tree (~1s) reset its bad-stand and walk timers, so an
                    // unreachable stand was walked at until the 30s stuck-defer. Keep one task per cell.
                    if (ceilTask == null || !ceilTask.target().equals(pos) || !java.util.Arrays.equals(ceilHave, have)) { ceilTask = new MazeCeilingPlaceTask(pos, have, this::isStray); ceilHave = have; }
                    return ceilTask;
                }
                if (have.length > 0) return new PlaceBlockTask(pos, have);
                if (have.length == 0) { // Ostinato: ran dry mid-cladding; regather the planned count instead of skipping the block
                    for (Block b : wanted) {
                        String id = net.minecraft.registry.Registries.BLOCK.getId(b).getPath();
                        // Ostinato: run47. A dry wood cell takes any planks, not the one wood the plan named first.
                        if (isWoodCladding(id)) {
                            int pooled = plankCladdingMissing(mod);
                            if (pooled <= 0) continue;
                            // Ostinato: run48. Held planks already cover the wrong cells; never re-gather (that loop was the stall).
                            if (heldPlanks(mod) >= pooled) continue;
                            if (gathered) System.out.println("MAZETEST FURNISH regather any_planks x" + pooled + " for " + id);
                            gathered = false;
                            return new adris.altoclef.tasks.resources.CollectPlanksTask(pooled);
                        }
                        if (!materials.containsKey(id) || !TaskCatalogue.taskExists(b.asItem())) continue;
                        // Ostinato: ask for the cells still wrong, not the plan total, and hide the cladding already
                        // placed so the gather mines natural stone (run42: "Gathering polished_andesite x53" mined the
                        // room's own walls, light 97 -> 89).
                        int missing = stillMissing(mod, id);
                        if (missing <= 0) continue;
                        // Ostinato: run94. A woody craft without its wood takes the leashed planks trip from the room first
                        // (the barrel's own gather chased slabs/logs 97-282 blocks out).
                        if (isWoody(id)) latchWood(mod, id, missing);
                        boolean needWood = isWoody(id) && !woodReady.contains(id);
                        // Ostinato: run94. The barrel was skipped by the pre-gather budget, so this regather flipped gathered,
                        // the pre-gather loop skipped it and flipped it back, every tick (log spam, and the step's placing budget
                        // never ran): an unbounded barrel hunt 282 blocks out. A skipped item gets one 3-min step regather.
                        if (skipped.contains(id)) {
                            int rt = stepRegather.merge(id, 1, Integer::sum);
                            if (rt == 1) System.out.println("MAZETEST FURNISH regather-skipped " + id + " x" + missing);
                            if (rt > 3600) { if (rt == 3601) System.out.println("MAZETEST FURNISH regather-giveup " + id); continue; }
                            return needWood ? pooledWoodGather(mod, mod.getPlayer().getBlockPos(), id, planksFor(mod, id, missing)) : TaskCatalogue.getItemTask(b.asItem(), missing);
                        }
                        if (gathered) System.out.println("MAZETEST FURNISH regather " + id + " x" + missing);
                        hidePlacedCladding(mod, id);
                        gathered = false;
                        return needWood ? pooledWoodGather(mod, mod.getPlayer().getBlockPos(), id, planksFor(mod, id, missing)) : TaskCatalogue.getItemTask(b.asItem(), missing);
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
                // Ostinato: a deferred seal retries with any held throwaway (the planned block's face may be the problem).
                if (sealFallback.contains(pos)) {
                    java.util.List<Block> alt = new ArrayList<>(java.util.Arrays.asList(have));
                    for (Block b : new Block[]{net.minecraft.block.Blocks.COBBLESTONE, net.minecraft.block.Blocks.COBBLED_DEEPSLATE, net.minecraft.block.Blocks.DIRT, net.minecraft.block.Blocks.STONE, net.minecraft.block.Blocks.NETHERRACK})
                        if (!alt.contains(b) && mod.getItemStorage().hasItem(b.asItem())) alt.add(b);
                    have = alt.toArray(new Block[0]);
                }
                // Falls back to throwaways (dirt, cobble) so the shaft is never left open.
                return new PlaceBlockTask(pos, have.length == 0 ? wanted.toArray(new Block[0]) : have, true, false);
            }
            case FILL: {
                if (state.isAir()) {
                    return null; // container never got placed; nothing to fill
                }
                if (MazeBarrelFillTask.filled(pos)) return null; // Ostinato: run53. server container already holds loot
                if (McCompat.block("decorated_pot") == state.getBlock()) {
                    if (!warnedPot) {
                        Debug.logMessage("Maze furnish: decorated pot fills are not supported yet, leaving pots empty");
                        warnedPot = true;
                    }
                    return null;
                }
                // Ostinato: deferred loot gather (see the pre-gather loop). 3 min per item, leashed, farm-aware catalogue tasks.
                BlockPos lootFeet = mod.getPlayer().getBlockPos();
                for (MazeFurnishPlan.Item it : step.contents()) {
                    Item li = McCompat.item(it.id());
                    if (li == null || lootDone.contains(it.id()) || skipped.contains(it.id()) || NO_HUNT_LOOT.contains(it.id())) continue;
                    if (mod.getItemStorage().getItemCount(li) >= it.count() || !TaskCatalogue.obtainableNow(li)) { lootDone.add(it.id()); continue; }
                    if (!it.id().equals(lootKey)) { lootKey = it.id(); lootTicks = 0; System.out.println("MAZETEST FURNISH loot-gather " + it.id() + " x" + it.count() + " have=" + mod.getItemStorage().getItemCount(li)); }
                    if (++lootTicks > 3600 || homeDist(lootFeet) > GATHER_LEASH) {
                        System.out.println("MAZETEST FURNISH loot-skip " + it.id() + " have=" + mod.getItemStorage().getItemCount(li) + "/" + it.count() + " ticks=" + lootTicks + " dist=" + homeDist(lootFeet));
                        lootDone.add(it.id());
                        continue;
                    }
                    lootGathering = true;
                    setDebugState("Gathering " + it.id() + " x" + it.count() + " (loot)");
                    return TaskCatalogue.getItemTask(li, it.count());
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
                // Ostinato: run53. StoreInContainerTask's InteractWithBlockTask fell back to TimeoutWanderTask (explore: a
                // 272-block path over snow) whenever no open screen showed within 5s; fill stayed 0/1 in run52 and run53.
                // MazeBarrelFillTask stands beside the barrel, clicks its visible face unsneaked, shift-clicks, never wanders.
                fill = new MazeBarrelFillTask(pos, targets.toArray(new ItemTarget[0]));
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
    private final java.util.Set<BlockPos> stepOutLogged = new java.util.HashSet<>(); // Ostinato: log a step-out once per cell
    private BlockPos stepOutPos; private int stepOutTicks; // Ostinato: bounded step-out per cell

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

    /**
     * Ostinato: the player's own body blocks a placement into the cell it occupies (BlockItem.canPlace passes the
     * placer's shape). A floor insert breaks the block the bot stands on, so the bot drops into the hole and the
     * builder retries forever. Walk onto a neighbouring floor cell and place from there.
     */
    private Task stepOutOf(AltoClef mod, BlockPos pos) {
        BlockPos feet = mod.getPlayer().getBlockPos();
        boolean inCell = feet.equals(pos);          // floor insert: the bot fell into the hole it broke
        boolean headInCell = feet.equals(pos.down()); // wall insert at head height
        if (!inCell && !headInCell) return null;
        // Ostinato: 5s per cell, then let the normal place path (and its timeout) take over, so a cell we cannot
        // step out of cannot spin forever.
        if (!pos.equals(stepOutPos)) { stepOutPos = pos; stepOutTicks = 0; }
        if (++stepOutTicks > 100) return null;
        BlockPos base = inCell ? pos.up() : feet;
        for (net.minecraft.util.math.Direction d : new net.minecraft.util.math.Direction[]{
                net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.SOUTH,
                net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.WEST}) {
            BlockPos stand = base.offset(d);
            if (standable(mod, stand)) {
                if (stepOutLogged.add(pos)) System.out.println("MAZETEST FURNISH step-out " + pos.toShortString() + " to " + stand.toShortString());
                return new adris.altoclef.tasks.movement.GetToBlockTask(stand, false);
            }
        }
        return null;
    }

    // Ostinato: two open cells over something solid.
    private static boolean standable(AltoClef mod, BlockPos stand) {
        BlockState under = mod.getWorld().getBlockState(stand.down());
        BlockState feet = mod.getWorld().getBlockState(stand);
        BlockState head = mod.getWorld().getBlockState(stand.up());
        boolean solidUnder = !under.isAir() && under.getFluidState().isEmpty() && !under.getCollisionShape(mod.getWorld(), stand.down()).isEmpty();
        boolean clear = (feet.isAir() || feet.getCollisionShape(mod.getWorld(), stand).isEmpty())
                && (head.isAir() || head.getCollisionShape(mod.getWorld(), stand.up()).isEmpty());
        return solidUnder && clear;
    }

    // Ostinato: cells that still need this id (the world does not match the plan yet).
    // Ostinato: run47. A *_planks id the plan uses as cladding; any wood satisfies those steps.
    private boolean isWoodCladding(String id) {
        return id.endsWith("_planks") && isCladding(id);
    }

    // Ostinato: run47. Cells whose chosen block is a plank and that are still wrong, across every wood.
    private int plankCladdingMissing(AltoClef mod) {
        int n = 0;
        for (MazeFurnishPlan.Step s : plan.steps) {
            if (s.kind() == MazeFurnishPlan.Kind.FILL) continue;
            String pick = MazeFurnishPlan.pick(s.blockIds(), MazeFurnishTask::obtainable);
            if (pick == null || !pick.endsWith("_planks")) continue;
            String cur = net.minecraft.registry.Registries.BLOCK.getId(mod.getWorld().getBlockState(new BlockPos(s.x(), s.y(), s.z())).getBlock()).getPath();
            if (!s.blockIds().contains(cur) && !cur.endsWith("_planks")) n++; // Ostinato: run48. Any wood clads a wood cell
        }
        return n;
    }

    // Ostinato: run48. A LIGHT step whose obtainable pick is a plank: wood cladding, any wood satisfies it.
    private static boolean woodCell(MazeFurnishPlan.Step s) {
        String pick = MazeFurnishPlan.pick(s.blockIds(), MazeFurnishTask::obtainable);
        return pick != null && pick.endsWith("_planks");
    }

    private static List<Block> withAllPlanks(List<Block> wanted) {
        List<Block> out = new ArrayList<>(wanted);
        for (Item i : adris.altoclef.util.helpers.ItemHelper.PLANKS) {
            Block b = Block.getBlockFromItem(i);
            if (b != null && !out.contains(b)) out.add(b);
        }
        return out;
    }

    private static int heldPlanks(AltoClef mod) {
        int n = 0;
        for (Item i : adris.altoclef.util.helpers.ItemHelper.PLANKS) n += mod.getItemStorage().getItemCount(i);
        return n;
    }

    private int stillMissing(AltoClef mod, String id) {
        int n = 0;
        for (MazeFurnishPlan.Step s : plan.steps) {
            if (s.kind() == MazeFurnishPlan.Kind.FILL || !s.blockIds().contains(id)) continue;
            BlockPos p = new BlockPos(s.x(), s.y(), s.z());
            String cur = net.minecraft.registry.Registries.BLOCK.getId(mod.getWorld().getBlockState(p).getBlock()).getPath();
            if (!s.blockIds().contains(cur)) n++;
        }
        return n;
    }

    // Ostinato: the nearest source of a cladding block is the cladding already placed. Hide those cells from the
    // block scanner for the duration of the gather, so it mines natural stone instead of its own room.
    private void hidePlacedCladding(AltoClef mod, String id) {
        int n = 0;
        for (MazeFurnishPlan.Step s : plan.steps) {
            if (s.kind() == MazeFurnishPlan.Kind.FILL || !s.blockIds().contains(id)) continue;
            BlockPos p = new BlockPos(s.x(), s.y(), s.z());
            String cur = net.minecraft.registry.Registries.BLOCK.getId(mod.getWorld().getBlockState(p).getBlock()).getPath();
            if (s.blockIds().contains(cur)) { if (!mod.getBlockScanner().isUnreachable(p)) mod.getBlockScanner().requestBlockUnreachable(p, 0); n++; } // Ostinato: run57 skip already-hidden cells (was re-marked every tick -> HARD-CAP spam)
        }
        if (n > 0 && hiddenLogged.add(id)) System.out.println("MAZETEST FURNISH hide-cladding " + id + " cells=" + n);
    }

    // Ostinato: run50. Solid (non-air, non-fluid) block in a planned cell, read from the integrated server world when
    // there is one so the avoider and the seal probe agree (client chunk lag showed placed seals as air).
    // Ostinato: run52. True when the block already there is one this step accepts, so the kit must not mine it.
    private boolean cellMatchesPlan(BlockPos p) {
        if (work == null) return false;
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.world.World w = mc.world;
        if (mc.getServer() != null && mc.world != null) {
            net.minecraft.server.world.ServerWorld sw = mc.getServer().getWorld(mc.world.getRegistryKey());
            if (sw != null) w = sw;
        }
        if (w == null) return false;
        Block have = w.getBlockState(p).getBlock();
        for (MazeFurnishPlan.Step s : work) {
            if (s.x() != p.getX() || s.y() != p.getY() || s.z() != p.getZ()) continue;
            if (blocks(s.blockIds()).contains(have)) return true;
        }
        return false;
    }

    private boolean isSolidCell(BlockPos p) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.world.World w = mc.world;
        if (mc.getServer() != null && mc.world != null) {
            net.minecraft.server.world.ServerWorld sw = mc.getServer().getWorld(mc.world.getRegistryKey());
            if (sw != null) w = sw;
        }
        if (w == null) return false;
        BlockState st = w.getBlockState(p);
        return !st.isAir() && st.getFluidState().isEmpty();
    }

    // Ostinato: run50. Kit gathers (stone pick, spare pick) must not take cobble from the room or its seal shaft.
    private void hideAllPlaced(AltoClef mod) {
        int n = 0;
        for (BlockPos p : plannedCells) if (isSolidCell(p)) { if (!mod.getBlockScanner().isUnreachable(p)) mod.getBlockScanner().requestBlockUnreachable(p, 0); n++; } // Ostinato: run57 skip already-hidden cells
        if (n > 0 && hiddenLogged.add("*kit")) System.out.println("MAZETEST FURNISH hide-placed kit cells=" + n);
    }

    // Ostinato: run50. True when every held pick is stone-or-better with under 12 uses left (no spare).
    private boolean pickWorn(AltoClef mod) {
        int good = 0; boolean worn = false;
        for (int i = 0; i < mod.getPlayer().getInventory().size(); i++) {
            net.minecraft.item.ItemStack st = mod.getPlayer().getInventory().getStack(i);
            if (st.isEmpty() || !java.util.Arrays.asList(PICKS).contains(st.getItem())) continue;
            if (st.getMaxDamage() - st.getDamage() < 12) worn = true; else good++;
        }
        return worn && good == 0;
    }

    private final java.util.Set<String> hiddenLogged = new java.util.HashSet<>(); // Ostinato
    private final java.util.Set<BlockPos> plannedCells = new java.util.HashSet<>(); // Ostinato: never mine these
    private final java.util.Set<BlockPos> airCells = new java.util.HashSet<>(); // Ostinato: run100, the room's carved cells

    // Ostinato: run100. A solid block in a carved cell the plan does not fill is pillar/bridge scaffolding left after the dig.
    private boolean isStray(BlockPos p) {
        if (!airCells.contains(p) || plannedCells.contains(p)) return false;
        BlockState st = AltoClef.getInstance().getWorld().getBlockState(p);
        return !st.isAir() && st.getFluidState().isEmpty() && !st.getCollisionShape(AltoClef.getInstance().getWorld(), p).isEmpty();
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
        return materials != null && work != null && index >= work.size() && repairDone;
    }

    @Override
    protected void onStop(Task interruptTask) {
        MazeRooms.end(); // Ostinato: run101
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
