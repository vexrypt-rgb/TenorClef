package adris.altoclef.maze;

import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.misc.PlaceBedAndSetSpawnTask;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;

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
    private static final int NEED_TIMEOUT_TICKS = 20 * 60 * 15;

    public TunnelProvisionTask(Map<String, Integer> needs) {
        this.needs = new LinkedHashMap<>(needs);
    }

    @Override
    protected void onStart() {
        queue.clear();
        String[] order = {"iron_pickaxe", "equip:", "food", "torch", "bed"};
        for (String o : order) {
            for (Map.Entry<String, Integer> e : needs.entrySet()) {
                if (e.getKey().startsWith(o) && !queue.contains(e)) queue.add(e);
            }
        }
        for (Map.Entry<String, Integer> e : needs.entrySet()) if (!queue.contains(e)) queue.add(e);
        current = null;
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
        if (current != null && (current.isFinished() || ++currentTicks > NEED_TIMEOUT_TICKS)) {
            Debug.logInternal("[tunnels] need " + currentKey + (current.isFinished() ? " done" : " timed out"));
            current = null;
        }
        while (current == null && !queue.isEmpty()) {
            Map.Entry<String, Integer> e = queue.poll();
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
