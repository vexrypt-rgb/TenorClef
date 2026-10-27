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
        String[] order = {"stone_pickaxe", "iron_pickaxe", "equip:", "food", "torch", "bed"};
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
            try {
                current = make(e.getKey(), e.getValue());
                if (current == null) Debug.logInternal("[tunnels] no task for " + e.getKey());
                currentKey = e.getKey();
                currentTicks = 0;
            } catch (RuntimeException ex) {
                Debug.logInternal("[tunnels] cannot make task for " + e.getKey() + ": " + ex);
            }
        }
        if (current == null) return null;
        setDebugState("Tunnel kit: " + currentKey + " (" + queue.size() + " more)");
        return current;
    }

    @Override
    public boolean isFinished() {
        return started && current == null && queue.isEmpty();
    }

    private boolean started;

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
