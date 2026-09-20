package adris.altoclef.knowledge;

import adris.altoclef.trackers.BlockScanner;
import adris.altoclef.trackers.CraftingRecipeTracker;
import adris.altoclef.trackers.EntityTracker;
import adris.altoclef.trackers.MiscBlockTracker;
import adris.altoclef.trackers.SimpleChunkTracker;
import adris.altoclef.trackers.storage.ItemStorageTracker;
import net.minecraft.block.Block;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Read-only facade over existing AltoClef trackers and client world state.
 * <p>
 * Phase 3: wrap trackers behind a small interface.
 * Phase 5: optional {@link KnowledgeFact} APIs with confidence / age / source —
 * does <strong>not</strong> replace the tracker system. Direct getters remain.
 */
public interface WorldKnowledge {

    /** Same as {@link adris.altoclef.AltoClef#inGame()}. */
    boolean inGame();

    ClientPlayerEntity getPlayer();

    ClientWorld getWorld();

    ItemStorageTracker getItemStorage();

    EntityTracker getEntityTracker();

    BlockScanner getBlockScanner();

    SimpleChunkTracker getChunkTracker();

    MiscBlockTracker getMiscBlockTracker();

    CraftingRecipeTracker getCraftingRecipeTracker();

    // ---- Phase 5: knowledge facts (defaults = unknown; live impl overrides) ----

    /**
     * Current observation clock (client world time when available).
     * Used for freshness / decay; fakes may return 0.
     */
    default long currentTick() {
        return 0L;
    }

    /** Player eye/feet position as a SENSOR fact when in-game. */
    default KnowledgeFact<Vec3d> playerPositionFact() {
        return KnowledgeFact.unknown();
    }

    /** Player health as a SENSOR fact when in-game. */
    default KnowledgeFact<Float> playerHealthFact() {
        return KnowledgeFact.unknown();
    }

    /**
     * Whether {@code entity} is currently alive (SENSOR), or last remembered
     * alive state from the fact cache (MEMORY with decay).
     */
    default KnowledgeFact<Boolean> entityAliveFact(Entity entity) {
        return KnowledgeFact.unknown();
    }

    /** Nearest entity of the given types via EntityTracker (SCANNER), with MEMORY fallback. */
    @SuppressWarnings("rawtypes")
    default KnowledgeFact<Entity> nearestEntityFact(Class... entityTypes) {
        return KnowledgeFact.unknown();
    }

    /**
     * Whether {@code pos} currently matches one of {@code expected} blocks (SENSOR),
     * else last remembered presence (MEMORY).
     */
    default KnowledgeFact<Boolean> blockPresentFact(BlockPos pos, Block... expected) {
        return KnowledgeFact.unknown();
    }

    /** Last-seen / nearest block position from BlockScanner (SCANNER), with MEMORY fallback. */
    default KnowledgeFact<BlockPos> lastSeenBlockFact(Block... blocks) {
        return KnowledgeFact.unknown();
    }
}
