package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

public final class Place {

    private Place() {}

    /**
     * A task placing exactly {@code block} at {@code pos}, or null when none is carried:
     * {@link PlaceBlockTask} waits forever for a block it was not told to collect.
     */
    public static Task ifHeld(AltoClef mod, BlockPos pos, Block block) {
        if (mod.getItemStorage().getItemCount(block.asItem()) < 1) return null;
        return new PlaceBlockTask(pos, block);
    }
}
