package adris.altoclef.allblocks;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.BlockPos;

/**
 * Ostinato: {@code #allblocks tour}. Walks to two fixed spots and looks over the chest room, then the block grid, so a
 * screenshot shows them. Logs {@code ALLBLOCKS TOUR <name>} once it is in place and holds still for 10 seconds.
 */
public class AllBlocksTourTask extends Task {

    private static final int HOLD_TICKS = 200;

    private final BlockPos origin;
    private final BlockPos[] spots;
    private final float[][] looks;
    private final String[] names = {"chests", "grid"};
    private int index;
    private int held;

    public AllBlocksTourTask(BlockPos origin, int chests, int cells) {
        this.origin = origin;
        int chestRows = Math.max(1, (chests + AllBlocks.CHESTS_PER_ROW - 1) / AllBlocks.CHESTS_PER_ROW);
        int gridRows = Math.max(1, (cells + AllBlocks.CELLS_PER_ROW - 1) / AllBlocks.CELLS_PER_ROW);
        // In front of the chest rows (north of them, the -z side), facing south across the labels.
        this.spots = new BlockPos[]{
                origin.add(Math.min(chests, AllBlocks.CHESTS_PER_ROW) - 1, 0, -3),
                origin.add(Math.min(cells, AllBlocks.CELLS_PER_ROW) - 1, 0, AllBlocks.GRID_Z - 3)};
        this.looks = new float[][]{{0f, 20f + 5f * chestRows}, {0f, 25f + 3f * gridRows}};
    }

    @Override
    protected void onStart() {
        index = 0;
        held = 0;
    }

    @Override
    protected Task onTick() {
        if (index >= spots.length) return null;
        AltoClef mod = AltoClef.getInstance();
        BlockPos spot = spots[index];
        if (!mod.getPlayer().getBlockPos().equals(spot) && held == 0) {
            setDebugState("Walking to the " + names[index] + " view at " + spot.toShortString());
            return new GetToBlockTask(spot);
        }
        mod.getPlayer().setYaw(looks[index][0]);
        mod.getPlayer().setPitch(looks[index][1]);
        if (held == 0) Debug.logMessage("ALLBLOCKS TOUR " + names[index] + " at " + spot.toShortString());
        if (++held >= HOLD_TICKS) {
            index++;
            held = 0;
        }
        setDebugState("Holding the " + names[index < names.length ? index : names.length - 1] + " view");
        return null;
    }

    @Override
    public boolean isFinished() {
        return index >= spots.length;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof AllBlocksTourTask t && t.origin.equals(origin);
    }

    @Override
    protected String toDebugString() {
        return "All blocks tour";
    }
}
