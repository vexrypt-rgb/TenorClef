package adris.altoclef.tasks.speedrun.stronghold;

import net.minecraft.util.math.BlockPos;

/**
 * Ranked candidate stronghold chunk from Ninjabrain-style math.
 */
public final class StrongholdPrediction {
    /** Chunk coordinates (chunkX, chunkZ) */
    public final int chunkX;
    public final int chunkZ;
    /** Posterior weight (normalized probability ~0..1) */
    public final double probability;
    /** Distance from player when computed */
    public final double distance;

    public StrongholdPrediction(int chunkX, int chunkZ, double probability, double distance) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.probability = probability;
        this.distance = distance;
    }

    /** Classic 8,8 block in the chunk (starter staircase target). */
    public BlockPos eightEight() {
        return new BlockPos(chunkX * 16 + 8, 40, chunkZ * 16 + 8);
    }

    /** Chunk origin (0,0 of chunk) – what eyes point at after snapping. */
    public BlockPos chunkOrigin() {
        return new BlockPos(chunkX * 16, 40, chunkZ * 16);
    }

    @Override
    public String toString() {
        return String.format("SH[chunk %d %d | %.1f%% | ~%.0f blocks]",
                chunkX, chunkZ, probability * 100.0, distance);
    }
}
