package adris.altoclef.tasks.speedrun.bastion;

import adris.altoclef.AltoClef;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Very lightweight bastion type detection.
 *
 * Full structure-template matching is complex; this uses simple
 * block-pattern heuristics that are good enough for routing decisions.
 *
 * Detection priority: Treasure > Housing > Bridge > Stables > Unknown
 */
public class BastionDetector {

    /**
     * Try to detect a bastion near the player and classify its type.
     * Returns empty if no bastion-like structure is found nearby.
     */
    public static Optional<DetectedBastion> detect(AltoClef mod, double searchRadius) {
        // Look for characteristic blocks
        Optional<BlockPos> blackstone = mod.getBlockScanner().getNearestBlock(
                Blocks.POLISHED_BLACKSTONE_BRICKS,
                Blocks.BLACKSTONE,
                Blocks.GILDED_BLACKSTONE,
                Blocks.CHISELED_POLISHED_BLACKSTONE
        );

        if (blackstone.isEmpty()) return Optional.empty();

        BlockPos origin = blackstone.get();
        if (mod.getPlayer().squaredDistanceTo(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5)
                > searchRadius * searchRadius) {
            return Optional.empty();
        }

        BastionType type = classify(mod, origin);
        return Optional.of(new DetectedBastion(type, origin));
    }

    private static BastionType classify(AltoClef mod, BlockPos origin) {
        // Extremely rough heuristics – can be improved later with better scanning.

        // Treasure often has a lot of gold blocks / gilded blackstone in a concentrated area
        long goldish = countNearby(mod, origin, 25,
                Blocks.GOLD_BLOCK, Blocks.GILDED_BLACKSTONE);
        if (goldish >= 8) {
            return BastionType.TREASURE;
        }

        // Stables tend to have many hay bales / chains / open hoglin areas
        long hay = countNearby(mod, origin, 30, Blocks.HAY_BLOCK);
        if (hay >= 6) {
            return BastionType.STABLES;
        }

        // Bridge has long bridges / magma / open void sections – hard to detect simply
        // Housing is the common “default” when we see blackstone but no strong signals
        // For now treat ambiguous as HOUSING (still valuable)
        return BastionType.HOUSING;
    }

    private static long countNearby(AltoClef mod, BlockPos center, int radius, net.minecraft.block.Block... blocks) {
        // This is a simplified placeholder.
        // Real implementation would use BlockScanner’s known locations or a small volume scan.
        int count = 0;
        for (net.minecraft.block.Block b : blocks) {
            Optional<BlockPos> p = mod.getBlockScanner().getNearestBlock(b);
            if (p.isPresent() && p.get().isWithinDistance(center, radius)) {
                count++;
            }
        }
        return count;
    }

    public static class DetectedBastion {
        public final BastionType type;
        public final BlockPos origin;

        public DetectedBastion(BastionType type, BlockPos origin) {
            this.type = type;
            this.origin = origin;
        }
    }
}
