package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Optional;

/**
 * Ostinato: #allblocks. Concrete of one color: concrete powder hardens as soon as it touches water, so the task places
 * powder on the ground next to a water block and mines the concrete it turns into (needs a pickaxe, which
 * MineAndCollectTask gets). Powder comes from the catalogue (crafted from sand, gravel and the dye).
 */
public class CollectConcreteTask extends ResourceTask {

    private final Item concrete;
    private final Item powder;
    private final Block concreteBlock;
    private final Block powderBlock;
    private final int count;
    private BlockPos spot;

    public CollectConcreteTask(Item concrete, Item powder, int count) {
        super(new ItemTarget(concrete, count));
        this.concrete = concrete;
        this.powder = powder;
        this.concreteBlock = Block.getBlockFromItem(concrete);
        this.powderBlock = Block.getBlockFromItem(powder);
        this.count = count;
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
        spot = null;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        // Hardened (or already placed) concrete: mine it.
        if (mod.getBlockScanner().anyFound(concreteBlock)) {
            setDebugState("Mining hardened concrete");
            return new MineAndCollectTask(concrete, count, new Block[]{concreteBlock}, MiningRequirement.WOOD);
        }
        if (mod.getItemStorage().getItemCount(powder) < 1) {
            setDebugState("Getting concrete powder");
            return TaskCatalogue.getItemTask(powder, Math.max(1, count - mod.getItemStorage().getItemCount(concrete)));
        }
        if (spot == null || !isSpot(mod, spot)) spot = findSpot(mod);
        if (spot == null) {
            setDebugState("Looking for water to harden the powder in");
            return new TimeoutWanderTask();
        }
        setDebugState("Placing powder next to water at " + spot.toShortString());
        return new PlaceBlockTask(spot, powderBlock);
    }

    /** An open cell with solid ground under it and water beside it. */
    private static boolean isSpot(AltoClef mod, BlockPos p) {
        if (!mod.getWorld().getBlockState(p).isAir() || !WorldHelper.isSolidBlock(p.down())) return false;
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (mod.getWorld().getBlockState(p.offset(d)).getBlock() == Blocks.WATER) return true;
        }
        return false;
    }

    private static BlockPos findSpot(AltoClef mod) {
        Optional<BlockPos> water = mod.getBlockScanner().getNearestBlock(w -> neighbourSpot(mod, w) != null, Blocks.WATER);
        return water.map(w -> neighbourSpot(mod, w)).orElse(null);
    }

    private static BlockPos neighbourSpot(AltoClef mod, BlockPos water) {
        if (WorldHelper.getCurrentDimension() != adris.altoclef.util.Dimension.OVERWORLD) return null;
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos p = water.offset(d);
            if (isSpot(mod, p)) return p;
        }
        return null;
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectConcreteTask task && task.concrete == concrete;
    }

    @Override
    protected String toDebugStringName() {
        return "Collect concrete (powder + water): " + concrete;
    }
}
