package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Ostinato: wood as a farm. After a chop round, plant saplings on open ground near the trees that were cut, so the wood a
 * long-running job (forever-expanding maze rooms) keeps coming back to regrows instead of receding. Plants held saplings
 * first; with none held it breaks a few leaves for them (hand, ~5% each). Bounded: {@code maxTicks} total, never blocks.
 */
public class ReplantTreesTask extends Task {

    private static final Block[] GROUND = {Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT,
            Blocks.MOSS_BLOCK, Blocks.MUD};
    private static final int RADIUS = 10;

    private final int want;
    private final int maxTicks;
    private int planted, ticks, spotTicks;
    private BlockPos spot;
    private boolean done;
    private Task gather;

    public ReplantTreesTask(int want, int maxTicks) {
        this.want = want;
        this.maxTicks = maxTicks;
    }

    @Override
    protected void onStart() {
        planted = 0;
        ticks = 0;
        spot = null;
        done = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (++ticks > maxTicks || planted >= want) { done = true; return null; }
        if (spot != null) {
            if (is(mod.getWorld().getBlockState(spot).getBlock(), ItemHelper.SAPLINGS)) { planted++; spot = null; }
            else if (!mod.getWorld().getBlockState(spot).isAir() || ++spotTicks > 300) spot = null;
        }
        if (!mod.getItemStorage().hasItem(ItemHelper.SAPLINGS)) {
            // Leaves are next to the logs just cut; a short hand-break round usually drops one or two.
            if (gather == null) gather = new CollectSaplingsTask(Math.max(1, want - planted));
            setDebugState("Breaking leaves for saplings (" + planted + "/" + want + " planted)");
            return gather;
        }
        if (spot == null) {
            spot = findSpot(mod);
            spotTicks = 0;
            if (spot == null) { done = true; return null; }
        }
        setDebugState("Replanting a sapling (" + planted + "/" + want + ")");
        return new InteractWithBlockTask(new ItemTarget(ItemHelper.SAPLINGS, 1), Direction.UP, spot.down(), true);
    }

    /** Open, sky-lit ground with no log or sapling within 2 blocks (room for a tree to grow). */
    private static BlockPos findSpot(AltoClef mod) {
        BlockPos c = mod.getPlayer().getBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) for (int dy = -4; dy <= 4; dy++) {
            BlockPos s = c.add(dx, dy, dz);
            if (!mod.getWorld().getBlockState(s).isAir() || !mod.getWorld().getBlockState(s.up()).isAir()) continue;
            Block g = mod.getWorld().getBlockState(s.down()).getBlock();
            boolean ok = false;
            for (Block b : GROUND) if (b == g) { ok = true; break; }
            if (!ok || !mod.getWorld().isSkyVisible(s)) continue;
            if (crowded(mod, s)) continue;
            double d = s.getSquaredDistance(c);
            if (d < bestD) { bestD = d; best = s.toImmutable(); }
        }
        return best;
    }

    private static boolean crowded(AltoClef mod, BlockPos s) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 1; dy++) {
            Block b = mod.getWorld().getBlockState(s.add(dx, dy, dz)).getBlock();
            if (is(b, ItemHelper.LOG) || is(b, ItemHelper.SAPLINGS)) return true;
        }
        return false;
    }

    // Ostinato: block tags differ across the supported versions; item lists do not.
    private static boolean is(Block b, Item[] items) {
        Item it = b.asItem();
        for (Item i : items) if (i == it) return true;
        return false;
    }

    public int planted() {
        return planted;
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ReplantTreesTask t && t.want == want;
    }

    @Override
    protected String toDebugString() {
        return "Replanting trees x" + want;
    }
}
