package adris.altoclef.tasks.speedrun.testrun2;

import net.minecraft.util.math.BlockPos;

/** 8:1 nether travel. Use when you already know overworld stronghold XZ. */
public final class NetherScale {

    private NetherScale() {}

    public static BlockPos overworldToNether(BlockPos ow) {
        return new BlockPos(ow.getX() / 8, ow.getY(), ow.getZ() / 8);
    }

    public static BlockPos netherToOverworld(BlockPos nether) {
        return new BlockPos(nether.getX() * 8, nether.getY(), nether.getZ() * 8);
    }

    public static int overworldDistFromNether(int netherDx, int netherDz) {
        return Math.abs(netherDx) * 8 + Math.abs(netherDz) * 8;
    }
}
