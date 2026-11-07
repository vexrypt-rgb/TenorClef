package adris.altoclef.maze;

import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.misc.PlaceBedAndSetSpawnTask;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Ostinato: one {@code #maze.tunnels} survival-kit run. Works the needs in order (tools first, then food, torches,
 * armor, bed last so spawn is set where the bot ends up, next to the dig face). Each need gets its own sub-task;
 * the task finishes when the list is empty. A need whose task stops without finishing is dropped; Ostinato asks again
 * later.
 */
public class TunnelProvisionTask extends Task {

    private final Map<String, Integer> needs;
    private final Deque<Map.Entry<String, Integer>> queue = new ArrayDeque<>();
    private Task current;
    private String currentKey;
    private int currentTicks;
    // Ostinato: a pickaxe (hard need) gets the long timeout; soft needs (armor, food, torches, bed, iron upgrade) get a
    // short one each and share one budget, so the kit never holds the dig hostage for an hour of armor/torch farming.
    private static final int NEED_TIMEOUT_TICKS = 20 * 60 * 15, SOFT_NEED_TICKS = 20 * 60 * 3, SOFT_BUDGET_TICKS = 20 * 60 * 6;
    // Ostinato: gather leash. Resource tasks wander to the nearest tree/ore; past LEASH blocks from the dig the need is
    // dropped and the bot walks back before the next one.
    private static final int LEASH = 64;
    // Ostinato: sheep roam far from a dig face; bed_home walks the bed back, so its gather gets a wider leash (run76: 2 x dropped at 64).
    private static final int BED_LEASH = 192;
    // Ostinato: the leash was horizontal only; run80's iron-boots ore hunt went 56 blocks straight down (y 74 -> 18) and
    // the dig then started from the cave. VLEASH caps the drop/climb; the round also ends with a walk back (homed).
    // Ostinato: down only. Trees are on the surface; with the dig at y 69 every wood run tripped it at y 102 and the
    // stick need looped walk-out/walk-back for 30+ min with no pick (live tunnels run, dug=89).
    private static final int VLEASH = 32;
    // Ostinato: wall-clock cap for a round while a usable pick is held; no loop shape may hold the dig longer than this.
    private static final long ROUND_CAP_MS = 20 * 60 * 1000L;
    private long roundStartMs;
    private boolean homed;
    private BlockPos home;
    private int softTicks;
    private boolean returning;
    private final Set<String> leashRetried = new HashSet<>(); // Ostinato: needs re-queued once after a leash walk-back
    private int bedTries; // Ostinato: bed item gathers this round (bounded; the leash and timeouts can each end one)

    public TunnelProvisionTask(Map<String, Integer> needs) {
        this.needs = new LinkedHashMap<>(needs);
    }

    @Override
    protected void onStart() {
        // Ostinato: a survival-chain interrupt (GetOutOfWaterTask, mobs, eating) stops and re-starts this same task. Re-running
        // the round setup zeroed every timeout and moved home into the water; run84 sat 6 h at dug=122 restarting an
        // iron_pickaxe ask 2769 times. Resume instead: keep timers + home, re-queue the interrupted need.
        if (started) {
            if (current != null && currentKey != null && !"return".equals(currentKey)) queue.addFirst(Map.entry(currentKey, currentCount));
            current = null;
            returning = false;
            return;
        }
        roundStartMs = System.currentTimeMillis();
        queue.clear();
        String[] order = {"stone_pickaxe", "copper_pickaxe", "iron_pickaxe", "copper_shovel", "stone_shovel", "copper_sword", "stone_sword", "food", "torch", "equip:", "bed"};
        // Ostinato: cheap needs (food, torches) before armor; 4 copper pieces ate the shared soft budget and left torches 32 s (run74).
        for (String o : order) {
            for (Map.Entry<String, Integer> e : needs.entrySet()) {
                if (e.getKey().startsWith(o) && !queue.contains(e)) queue.add(e);
            }
        }
        for (Map.Entry<String, Integer> e : needs.entrySet()) if (!queue.contains(e)) queue.add(e);
        current = null;
        softTicks = 0;
        returning = false;
        leashRetried.clear();
        bedTries = 0;
        homed = false;
        home = mod().getPlayer() == null ? null : mod().getPlayer().getBlockPos();
        started = true;
    }

    private Task make(String key, int count) {
        if (key.equals("food")) return new CollectFoodTask(count);
        if (key.equals("bed")) return new PlaceBedAndSetSpawnTask();
        if (key.equals("bed_item")) return TaskCatalogue.getItemTask("bed", count);
        if (key.equals("bed_home")) return new GetToBlockTask(home);
        if (key.startsWith("equip:")) return new EquipArmorTask(new ItemTarget(key.substring(6), 1));
        if (key.equals("stick")) return TaskCatalogue.getItemTask("stick", count);
        return TaskCatalogue.getItemTask(key, count);
    }

    @Override
    protected Task onTick() {
        boolean usablePick = usablePick();
        if (usablePick && (current != null || !queue.isEmpty()) && !"return".equals(currentKey) && System.currentTimeMillis() - roundStartMs > ROUND_CAP_MS) {
            Debug.logHarness("[tunnels] round cap " + (ROUND_CAP_MS / 60000) + " min with a usable pick; dropping " + queue + " and " + currentKey + " (Ostinato asks again later)");
            queue.clear();
            current = null;
        }
        // Ostinato: a pickaxe ask is hard (long timeout) only while no usable pick is held. With one in hand it is Ostinato's
        // soft spare/upgrade ask; run79 sat 7+ min on a worn-copper spare (stuck pathing to ore) while the held pick could dig.
        boolean hard = currentKey != null && currentKey.endsWith("_pickaxe") && !returning && !usablePick;
        // Ostinato: the bed has its own cap (below) and neither spends nor is skipped by the shared budget. It
        // is queued last, so armor + torches used to eat the whole budget every round and spawn was never set (run73).
        boolean bed = currentKey != null && currentKey.startsWith("bed");
        if (current != null && !hard && !bed && !returning) softTicks++;
        // Ostinato: a pick need (or its sticks) with no usable pick is never leashed: walking back pickless only restarts the
        // same gather, and the hard gate blocks the dig anyway (run82: 73 stick + 13 stone_pickaxe leash trips, 30+ min loop).
        boolean pickChain = !usablePick && currentKey != null && (currentKey.endsWith("_pickaxe") || currentKey.equals("stick"));
        if (current != null && !returning && !pickChain && !"bed_home".equals(currentKey) && home != null && mod().getPlayer() != null
                && (horiz(mod().getPlayer().getBlockPos(), home) > leash() || home.getY() - mod().getPlayer().getBlockPos().getY() > vleash())) {
            Debug.logHarness("[tunnels] need " + currentKey + " left the " + leash() + "/" + vleash() + "-block leash at "
                    + mod().getPlayer().getBlockPos().toShortString() + "; walking back");
            // Ostinato: re-queue the interrupted need once, so it restarts from home instead of being dropped. run74's bed
            // step started 70+ blocks out (where the copper kit left the bot) and was dropped 10 s in without a try.
            if (!"return".equals(currentKey) && leashRetried.add(currentKey)) {
                Debug.logHarness("[tunnels] need " + currentKey + " re-queued after the walk back");
                queue.addFirst(Map.entry(currentKey, currentCount));
            }
            current = new GetToBlockTask(home);
            currentKey = "return";
            currentTicks = 0;
            returning = true;
        }
        if (returning && current != null && current.isFinished()) returning = false;
        // Ostinato: a bed means finding sheep on the surface, shearing/killing 3, then crafting and placing: 6 min, not 3.
        int limit = hard || returning ? NEED_TIMEOUT_TICKS : bed ? SOFT_BUDGET_TICKS : SOFT_NEED_TICKS;
        if (current != null && (current.isFinished() || ++currentTicks > limit || (!hard && !bed && !returning && softTicks > SOFT_BUDGET_TICKS))) {
            // Ostinato: harness-visible (logInternal is muted at NORMAL), with the counts that prove the order sticks -> pick.
            Debug.logHarness("[tunnels] need " + currentKey + (current.isFinished() ? " done" : " timed out") + " have "
                    + have(currentKey) + "/" + currentCount + " sticks=" + have("stick") + " planks=" + have("planks"));
            current = null;
            returning = false;
        }
        while (current == null && !queue.isEmpty()) {
            Map.Entry<String, Integer> e = queue.poll();
            if (!e.getKey().endsWith("_pickaxe") && !e.getKey().startsWith("bed") && softTicks > SOFT_BUDGET_TICKS) {
                Debug.logHarness("[tunnels] soft budget spent; skipping " + e.getKey() + " (Ostinato asks again later)");
                continue;
            }
            // Ostinato: a pickaxe craft needs 2 sticks per pick still to make. Provision them (logs -> planks -> sticks via
            // CollectSticksTask) as their own step BEFORE the pick recipe, so the table never tries MoveItemToSlot with
            // no sticks (run71 froze on that). Also take the best pick the inventory can already make: copper -> iron -> stone.
            if (e.getKey().endsWith("_pickaxe")) {
                String pick = bestPick(e.getKey());
                if (!pick.equals(e.getKey())) e = Map.entry(pick, have(pick) + 1); // one fresh better pick, not the stone count
                int toMake = Math.max(0, e.getValue() - have(e.getKey()));
                int sticksNeeded = 2 * toMake;
                // Ostinato: with no usable pick, craft what the sticks on hand allow first (2 sticks = 1 pick) instead of
                // gathering for the full count; the dig resumes and Ostinato asks for spares later.
                if (toMake > 0 && !usablePick && have("stick") >= 2 && have("stick") < sticksNeeded) {
                    e = Map.entry(e.getKey(), have(e.getKey()) + have("stick") / 2);
                    toMake = have("stick") / 2;
                    sticksNeeded = 2 * toMake;
                }
                if (toMake > 0 && have("stick") < sticksNeeded && !"stick".equals(currentKey)) {
                    Debug.logHarness("[tunnels] pick " + e.getKey() + " needs " + sticksNeeded + " sticks, have " + have("stick") + "; sticks first");
                    queue.addFirst(e);
                    // Ostinato: keep a reserve (one log = 8 sticks) so the next worn stone pick needs no surface wood trip.
                    e = Map.entry("stick", Math.max(8, sticksNeeded));
                }
            }
            // Ostinato: bed = get the bed item (wherever the sheep are), walk back home (the dig face), then place it and
            // sleep there. PlaceBedAndSetSpawnTask alone placed it wherever the gather ended, out of Ostinato's range.
            if (e.getKey().equals("bed") && mod().getItemStorage() != null) {
                if (beds() == 0) {
                    if (bedTries++ >= 2) {
                        Debug.logHarness("[tunnels] no bed item after " + (bedTries - 1) + " gathers; skipping bed (Ostinato asks again later)");
                        continue;
                    }
                    queue.addFirst(e);
                    e = Map.entry("bed_item", 1);
                } else if (home != null && mod().getPlayer() != null && horiz(mod().getPlayer().getBlockPos(), home) > 6
                        && !"bed_home".equals(currentKey)) {
                    queue.addFirst(e);
                    e = Map.entry("bed_home", 1);
                }
            }
            try {
                current = make(e.getKey(), e.getValue());
                Debug.logHarness("[tunnels] need " + e.getKey() + " x" + e.getValue() + " start have " + have(e.getKey())
                        + " sticks=" + have("stick") + " planks=" + have("planks"));
                if (current == null) Debug.logHarness("[tunnels] no task for " + e.getKey());
                currentKey = e.getKey();
                currentCount = e.getValue();
                currentTicks = 0;
            } catch (RuntimeException ex) {
                Debug.logHarness("[tunnels] cannot make task for " + e.getKey() + ": " + ex);
            }
        }
        if (current == null && queue.isEmpty() && !homed && home != null && mod().getPlayer() != null) {
            homed = true;
            BlockPos at = mod().getPlayer().getBlockPos();
            if (horiz(at, home) > 6 || Math.abs(at.getY() - home.getY()) > 3) {
                // Ostinato: hand the bot back at the dig face, not wherever the last gather ended.
                Debug.logHarness("[tunnels] kit done at " + at.toShortString() + "; walking back to " + home.toShortString());
                current = new GetToBlockTask(home);
                currentKey = "return";
                currentCount = 1;
                currentTicks = 0;
                returning = true;
            }
        }
        if (current == null) return null;
        // Ostinato: show the item count (have/want) next to the queue, so "needs={stone_pickaxe=2}" and the kit line agree;
        // the old "(0 more)" was the queue length and read like "0 pickaxes still needed".
        setDebugState("Tunnel kit: " + currentKey + " have " + have(currentKey) + "/" + currentCount + " (" + queue.size() + " needs queued)");
        return current;
    }

    @Override
    public boolean isFinished() {
        return started && current == null && queue.isEmpty();
    }

    private boolean started;
    private int currentCount;

    private static int have(String key) {
        if (key == null || mod().getItemStorage() == null) return 0;
        if (key.equals("bed_item")) return beds();
        if (key.startsWith("equip:") || key.equals("food") || key.startsWith("bed") || key.equals("return")) return 0;
        try {
            return mod().getItemStorage().getItemCount(TaskCatalogue.getItemMatches(key));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** Ostinato: any pickaxe with more than 2 uses left (Ostinato's pickGate "usable"). */
    private static boolean usablePick() {
        if (mod().getItemStorage() == null) return false;
        for (net.minecraft.item.ItemStack s : mod().getItemStorage().getItemStacksPlayerInventory(false)) {
            if (s.isEmpty() || !ItemHelper.stackIn(s, net.minecraft.registry.tag.ItemTags.PICKAXES)) continue;
            if (s.getMaxDamage() <= 0 || s.getMaxDamage() - s.getDamage() > 2) return true;
        }
        return false;
    }

    private int leash() {
        return "bed_item".equals(currentKey) ? BED_LEASH : LEASH;
    }

    private int vleash() {
        return "bed_item".equals(currentKey) ? BED_LEASH : VLEASH;
    }

    private static int beds() {
        return mod().getItemStorage() == null ? 0 : mod().getItemStorage().getItemCount(ItemHelper.BED);
    }

    /** Ostinato: copper -> iron -> stone. Use a better metal only when 3 ingots are already held (no extra gathering). */
    private static String bestPick(String asked) {
        if (!asked.equals("stone_pickaxe")) return asked;
        if (have("copper_ingot") >= 3 && TaskCatalogue.taskExists("copper_pickaxe")) return "copper_pickaxe";
        if (have("iron_ingot") >= 3) return "iron_pickaxe";
        return asked;
    }

    private static adris.altoclef.AltoClef mod() {
        return adris.altoclef.AltoClef.getInstance();
    }

    private static double horiz(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof TunnelProvisionTask && ((TunnelProvisionTask) other).needs.equals(needs);
    }

    @Override
    protected String toDebugString() {
        return "Tunnel survival kit " + needs;
    }
}
