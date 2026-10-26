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
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        // Gather first, so the bot is not walking out of the sealed room for one more glowstone.
        for (Map.Entry<String, Integer> need : materials.entrySet()) {
            Item item = McCompat.item(need.getKey());
            if (item != null && mod.getItemStorage().getItemCount(item) < need.getValue() && TaskCatalogue.taskExists(item)) {
                setDebugState("Gathering " + need.getKey() + " x" + need.getValue());
                return TaskCatalogue.getItemTask(item, need.getValue());
            }
        }
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
                if (!state.isAir()) {
                    return new DestroyBlockTask(pos);
                }
                Block[] have = wanted.stream().filter(b -> mod.getItemStorage().hasItem(b.asItem())).toArray(Block[]::new);
                return have.length == 0 ? null : new PlaceBlockTask(pos, have);
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
