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
 * Nullable stub {@link WorldKnowledge} for wiring tests (Phase 3).
 */
public final class FakeWorldKnowledge implements WorldKnowledge {

    public boolean inGame;
    public ClientPlayerEntity player;
    public ClientWorld world;
    public ItemStorageTracker itemStorage;
    public EntityTracker entityTracker;
    public BlockScanner blockScanner;
    public SimpleChunkTracker chunkTracker;
    public MiscBlockTracker miscBlockTracker;
    public CraftingRecipeTracker craftingRecipeTracker;

    @Override
    public boolean inGame() {
        return inGame;
    }

    @Override
    public ClientPlayerEntity getPlayer() {
        return player;
    }

    @Override
    public ClientWorld getWorld() {
        return world;
    }

    @Override
    public ItemStorageTracker getItemStorage() {
        return itemStorage;
    }

    @Override
    public EntityTracker getEntityTracker() {
        return entityTracker;
    }

    @Override
    public BlockScanner getBlockScanner() {
        return blockScanner;
    }

    @Override
    public SimpleChunkTracker getChunkTracker() {
        return chunkTracker;
    }

    @Override
    public MiscBlockTracker getMiscBlockTracker() {
        return miscBlockTracker;
    }

    @Override
    public CraftingRecipeTracker getCraftingRecipeTracker() {
        return craftingRecipeTracker;
    }
}
