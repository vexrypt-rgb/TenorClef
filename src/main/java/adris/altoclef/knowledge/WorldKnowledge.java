package adris.altoclef.knowledge;

import adris.altoclef.trackers.BlockScanner;
import adris.altoclef.trackers.CraftingRecipeTracker;
import adris.altoclef.trackers.EntityTracker;
import adris.altoclef.trackers.MiscBlockTracker;
import adris.altoclef.trackers.SimpleChunkTracker;
import adris.altoclef.trackers.storage.ItemStorageTracker;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/**
 * Read-only facade over existing AltoClef trackers and client world state.
 * <p>
 * Phase 3 vertical slice: wrap trackers behind a small interface so new code
 * can depend on {@code WorldKnowledge} instead of {@code AltoClef} getters.
 * Does <strong>not</strong> invent a new tracker system or confidence model
 * (those are Phase 5).
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
}
