package adris.altoclef.tasks.speedrun.testrun2.combat;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/**
 * Standard beginner zero platform (Doogile / T_Wagz OG):
 * staircase 2, bridge 1 left, two blocks up, two blast-proof
 * with a bed between them.
 */
public final class ZeroSetup {

    public static final class Step {
        public final BlockPos pos;
        public final Block block;
        public Step(BlockPos pos, Block block) {
            this.pos = pos;
            this.block = block;
        }
    }

    private ZeroSetup() {}

    /**
     * @param stand block the player stands on at the pillar (not the crystal)
     */
    public static Step[] plan(BlockPos stand) {
        int fx = Math.abs(stand.getX()) >= Math.abs(stand.getZ())
                ? Integer.signum(stand.getX()) : 0;
        int fz = Math.abs(stand.getZ()) > Math.abs(stand.getX())
                ? Integer.signum(stand.getZ()) : 0;
        if (fx == 0 && fz == 0) fx = 1;
        int lx = -fz;
        int lz = fx;
        Block fill = Blocks.NETHERRACK;
        Block hard = Blocks.OBSIDIAN;
        BlockPos s = stand;
        BlockPos stair1 = s.add(fx, 1, fz);
        BlockPos stair2 = s.add(fx * 2, 2, fz * 2);
        BlockPos side = stair2.add(lx, 0, lz);
        BlockPos up1 = side.up();
        BlockPos obbyA = side.up(2);
        BlockPos bed = obbyA.add(lx, 0, lz);
        BlockPos obbyB = bed.add(lx, 0, lz);
        return new Step[]{
                new Step(stair1, fill),
                new Step(stair2, fill),
                new Step(side, fill),
                new Step(up1, fill),
                new Step(obbyA, hard),
                new Step(obbyB, hard),
                new Step(bed, Blocks.WHITE_BED)
        };
    }

    public static Task nextPlace(AltoClef mod, BlockPos stand) {
        for (Step s : plan(stand)) {
            try {
                if (mod.getWorld().getBlockState(s.pos).isAir()) {
                    return place(s.pos, s.block);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static boolean finished(AltoClef mod, BlockPos stand) {
        return nextPlace(mod, stand) == null;
    }

    public static BlockPos bedPos(BlockPos stand) {
        Step[] p = plan(stand);
        return p[p.length - 1].pos;
    }

    public static Item fillItem() {
        return Items.NETHERRACK;
    }

    public static Item hardItem() {
        return Items.OBSIDIAN;
    }

    private static Task place(BlockPos pos, Block block) {
        try {
            return (Task) Class.forName("adris.altoclef.tasks.construction.PlaceBlockTask")
                    .getConstructor(BlockPos.class, Block.class).newInstance(pos, block);
        } catch (Throwable t) {
            return null;
        }
    }
}
