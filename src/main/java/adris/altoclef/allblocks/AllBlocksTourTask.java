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
    private static final int VIEW_HEIGHT = 4;

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
        // North of the chest rows and of the grid (the -z side), facing south. Raised 4 blocks (the bot pillars up):
        // from the ground, trees and uneven terrain in a forest hid the grid.
        this.spots = new BlockPos[]{
                origin.add(Math.min(chests, AllBlocks.CHESTS_PER_ROW) - 1, VIEW_HEIGHT, -3),
                // An odd x is a gap of the layout: the pillar never stands on a chest, label or cell.
                origin.add((Math.min(cells, AllBlocks.CELLS_PER_ROW) - 1) | 1, VIEW_HEIGHT, AllBlocks.GRID_Z - 3)};
        this.looks = new float[][]{{0f, 40f + 4f * chestRows}, {0f, 45f + 3f * gridRows}};
    }

    @Override
    protected void onStart() {
        // Nothing to reset: mob defense and eating interrupt the tour, and starting over from the first view each
        // time kept it from ever reaching the grid.
    }

    @Override
    protected Task onTick() {
        if (index >= spots.length) return null;
        AltoClef mod = AltoClef.getInstance();
        BlockPos spot = spots[index];
        if (!mod.getPlayer().getBlockPos().equals(spot)) {
            // Pushed off the spot (a fight, a fall): walk back and hold the view afresh.
            held = 0;
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
