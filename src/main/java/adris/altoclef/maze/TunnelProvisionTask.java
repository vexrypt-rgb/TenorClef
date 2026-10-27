package adris.altoclef.maze;

import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.misc.PlaceBedAndSetSpawnTask;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ostinato: one {@code #maze.tunnels} survival-kit run. Works the needs in order (tools first, then armor, food,
 * torches, bed last so spawn is set where the bot ends up, next to the dig face). Each need gets its own sub-task;
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
    private BlockPos home;
    private int softTicks;
    private boolean returning;

    public TunnelProvisionTask(Map<String, Integer> needs) {
        this.needs = new LinkedHashMap<>(needs);
    }

    @Override
    protected void onStart() {
        queue.clear();
        String[] order = {"stone_pickaxe", "copper_pickaxe", "iron_pickaxe", "copper_shovel", "stone_shovel", "copper_sword", "stone_sword", "equip:", "food", "torch", "bed"};
        for (String o : order) {
            for (Map.Entry<String, Integer> e : needs.entrySet()) {
                if (e.getKey().startsWith(o) && !queue.contains(e)) queue.add(e);
            }
        }
        for (Map.Entry<String, Integer> e : needs.entrySet()) if (!queue.contains(e)) queue.add(e);
        current = null;
        softTicks = 0;
        returning = false;
        home = mod().getPlayer() == null ? null : mod().getPlayer().getBlockPos();
        started = true;
    }

    private Task make(String key, int count) {
        if (key.equals("food")) return new CollectFoodTask(count);
        if (key.equals("bed")) return new PlaceBedAndSetSpawnTask();
        if (key.startsWith("equip:")) return new EquipArmorTask(new ItemTarget(key.substring(6), 1));
        if (key.equals("stick")) return TaskCatalogue.getItemTask("stick", count);
        return TaskCatalogue.getItemTask(key, count);
    }

    @Override
    protected Task onTick() {
        boolean hard = currentKey != null && currentKey.endsWith("_pickaxe") && !returning;
        if (current != null && !hard && !returning) softTicks++;
        if (current != null && !returning && home != null && mod().getPlayer() != null
                && horiz(mod().getPlayer().getBlockPos(), home) > LEASH) {
            Debug.logInternal("[tunnels] need " + currentKey + " left the " + LEASH + "-block leash; walking back");
            current = new GetToBlockTask(home);
            currentKey = "return";
            currentTicks = 0;
            returning = true;
        }
        if (returning && current != null && current.isFinished()) returning = false;
        int limit = hard || returning ? NEED_TIMEOUT_TICKS : SOFT_NEED_TICKS;
        if (current != null && (current.isFinished() || ++currentTicks > limit || (!hard && !returning && softTicks > SOFT_BUDGET_TICKS))) {
            Debug.logInternal("[tunnels] need " + currentKey + (current.isFinished() ? " done" : " timed out"));
            current = null;
            returning = false;
        }
        while (current == null && !queue.isEmpty()) {
            Map.Entry<String, Integer> e = queue.poll();
            if (!e.getKey().endsWith("_pickaxe") && softTicks > SOFT_BUDGET_TICKS) {
                Debug.logInternal("[tunnels] soft budget spent; skipping " + e.getKey() + " (Ostinato asks again later)");
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
                if (toMake > 0 && have("stick") < sticksNeeded && !"stick".equals(currentKey)) {
                    Debug.logInternal("[tunnels] pick " + e.getKey() + " needs " + sticksNeeded + " sticks, have " + have("stick") + "; sticks first");
                    queue.addFirst(e);
                    e = Map.entry("stick", sticksNeeded);
                }
            }
            try {
                current = make(e.getKey(), e.getValue());
                if (current == null) Debug.logInternal("[tunnels] no task for " + e.getKey());
                currentKey = e.getKey();
                currentCount = e.getValue();
                currentTicks = 0;
            } catch (RuntimeException ex) {
                Debug.logInternal("[tunnels] cannot make task for " + e.getKey() + ": " + ex);
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
        if (key.startsWith("equip:") || key.equals("food") || key.equals("bed") || key.equals("return")) return 0;
        try {
            return mod().getItemStorage().getItemCount(TaskCatalogue.getItemMatches(key));
        } catch (RuntimeException ex) {
            return 0;
        }
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
