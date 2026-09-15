package adris.altoclef.tasks.speedrun.stronghold;

import adris.altoclef.AltoClef;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Detect nether fossils in chunk (0, 0) and estimate the fossil origin X.
 *
 * Full 14-type pattern matching is complex; this uses a practical heuristic:
 * - Collect bone / coal-ore-in-fossil blocks in chunk 0,0
 * - Origin ≈ minimum (x + z*0.01) bone block (NW-ish start of structure)
 * - Origin X mod 16 is the divine key
 *
 * Good enough for bot divine; can be replaced with exact pattern tables later.
 */
public final class DivineFossilDetector {

    private DivineFossilDetector() {}

    public static final class Detection {
        public final int fossilX;
        public final BlockPos originBlock;
        public final int boneCount;
        public final DivineTables.DivineResult result;

        public Detection(int fossilX, BlockPos originBlock, int boneCount) {
            this.fossilX = fossilX;
            this.originBlock = originBlock;
            this.boneCount = boneCount;
            this.result = DivineTables.lookup(fossilX);
        }

        @Override
        public String toString() {
            return "Divine fossilX=" + fossilX + " origin=" + originBlock + " bones=" + boneCount;
        }
    }

    /**
     * Scan Nether chunk (0,0) for fossil bone blocks.
     * Returns empty if not in Nether or no fossil found.
     */
    public static Optional<Detection> detect(AltoClef mod) {
        World world = mod.getWorld();
        if (world == null) return Optional.empty();

        // Must be in nether – dimension check via AltoClef helper preferred, but we scan anyway
        List<BlockPos> bones = new ArrayList<>();

        // Chunk (0,0): blocks x 0..15, z 0..15, y full nether range
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 32; y <= 110; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isFossilBlock(world, pos)) {
                        bones.add(pos);
                    }
                }
            }
        }

        if (bones.isEmpty()) {
            return Optional.empty();
        }

        // Heuristic origin: lowest Y, then smallest X, then smallest Z
        BlockPos origin = bones.get(0);
        for (BlockPos b : bones) {
            if (b.getY() < origin.getY()
                    || (b.getY() == origin.getY() && b.getX() < origin.getX())
                    || (b.getY() == origin.getY() && b.getX() == origin.getX() && b.getZ() < origin.getZ())) {
                origin = b;
            }
        }

        int fossilX = Math.floorMod(origin.getX(), 16);
        return Optional.of(new Detection(fossilX, origin, bones.size()));
    }

    /**
     * Quick check: any bone block in chunk 0,0? Cheaper than full detect.
     */
    public static boolean hasFossilInChunk00(AltoClef mod) {
        World world = mod.getWorld();
        if (world == null) return false;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 40; y <= 100; y += 2) {
                    if (isFossilBlock(world, new BlockPos(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isFossilBlock(World world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return state.isOf(Blocks.BONE_BLOCK)
                || state.isOf(Blocks.COAL_ORE); // some fossil variants include coal in older data; nether fossils are bone
    }
}
