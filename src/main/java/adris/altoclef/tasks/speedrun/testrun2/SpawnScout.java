package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

public final class SpawnScout {

    public record Result(String biome, int trees, boolean village, boolean portal, int lava, boolean resetWorthy) {}

    private SpawnScout() {}

    public static Result scan(AltoClef mod) {
        Result empty = new Result("?", 0, false, false, 0, false);
        if (mod.getPlayer() == null || mod.getWorld() == null) return empty;
        BlockPos feet = mod.getPlayer().getBlockPos();

        int trees = 0;
        int lava = 0;
        try {
            for (int dx = -16; dx <= 16; dx += 2) {
                for (int dz = -16; dz <= 16; dz += 2) {
                    BlockPos p = feet.add(dx, 0, dz);
                    var top = mod.getWorld().getBlockState(p);
                    boolean woodHit = false;
                    for (int dy = 1; dy <= 8 && !woodHit; dy++) {
                        var wood = mod.getWorld().getBlockState(p.up(dy));
                        if (wood.isOf(Blocks.OAK_LOG) || wood.isOf(Blocks.BIRCH_LOG)
                                || wood.isOf(Blocks.SPRUCE_LOG) || wood.isOf(Blocks.JUNGLE_LOG)
                                || wood.isOf(Blocks.ACACIA_LOG) || wood.isOf(Blocks.DARK_OAK_LOG)
                                || isLog(wood, "CHERRY_LOG") || isLog(wood, "MANGROVE_LOG")) {
                            woodHit = true;
                        }
                    }
                    if (woodHit) { trees++; continue; }
                    var wood = mod.getWorld().getBlockState(p.up(3));
                    if (wood.isOf(Blocks.OAK_LOG) || wood.isOf(Blocks.BIRCH_LOG)
                            || wood.isOf(Blocks.SPRUCE_LOG) || wood.isOf(Blocks.JUNGLE_LOG)
                            || wood.isOf(Blocks.ACACIA_LOG) || wood.isOf(Blocks.DARK_OAK_LOG)
                            || isLog(wood, "CHERRY_LOG") || isLog(wood, "MANGROVE_LOG")) {
                        trees++;
                    }
                    if (top.isOf(Blocks.LAVA) || mod.getWorld().getBlockState(p.down()).isOf(Blocks.LAVA)) {
                        lava++;
                    }
                }
            }
        } catch (Throwable ignored) {}

        boolean village = false;
        boolean portal = false;
        try {
            village = mod.getBlockScanner().anyFound(Blocks.BELL, Blocks.HAY_BLOCK, Blocks.COMPOSTER);
            portal = mod.getBlockScanner().anyFound(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN);
        } catch (Throwable ignored) {}

        String biome = "?";
        try {
            biome = McCompat.biomePath(mod.getWorld(), feet);
        } catch (Throwable ignored) {}

        boolean ocean = biome.contains("ocean") && !biome.contains("beach");
        boolean reset = ocean && !village && !portal && trees < 3;

        Debug.logMessage("TESRUN2 spawn biome=" + biome
                + " trees~=" + trees
                + " villageHint=" + village
                + " rpHint=" + portal
                + " surfaceLava=" + lava
                + (reset ? "  *** RESET-WORTHY (ocean, no loot) ***" : "  playable"));
        return new Result(biome, trees, village, portal, lava, reset);
    }

    private static boolean isLog(BlockState wood, String name) {
        var b = McCompat.block(name);
        return b != null && wood.isOf(b);
    }
}
