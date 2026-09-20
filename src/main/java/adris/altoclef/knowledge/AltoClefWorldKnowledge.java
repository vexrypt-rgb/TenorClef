package adris.altoclef.knowledge;

import adris.altoclef.AltoClef;
import adris.altoclef.trackers.BlockScanner;
import adris.altoclef.trackers.CraftingRecipeTracker;
import adris.altoclef.trackers.EntityTracker;
import adris.altoclef.trackers.MiscBlockTracker;
import adris.altoclef.trackers.SimpleChunkTracker;
import adris.altoclef.trackers.storage.ItemStorageTracker;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/**
 * {@link WorldKnowledge} backed by the live {@link AltoClef} singleton container.
 * Trackers remain owned/constructed by AltoClef; this only re-exposes them.
 */
public final class AltoClefWorldKnowledge implements WorldKnowledge {

    private final AltoClef mod;

    public AltoClefWorldKnowledge(AltoClef mod) {
        if (mod == null) {
            throw new IllegalArgumentException("mod");
        }
        this.mod = mod;
    }

    @Override
    public boolean inGame() {
        return AltoClef.inGame();
    }

    @Override
    public ClientPlayerEntity getPlayer() {
        return mod.getPlayer();
    }

    @Override
    public ClientWorld getWorld() {
        return mod.getWorld();
    }

    @Override
    public ItemStorageTracker getItemStorage() {
        return mod.getItemStorage();
    }

    @Override
    public EntityTracker getEntityTracker() {
        return mod.getEntityTracker();
    }

    @Override
    public BlockScanner getBlockScanner() {
        return mod.getBlockScanner();
    }

    @Override
    public SimpleChunkTracker getChunkTracker() {
        return mod.getChunkTracker();
    }

    @Override
    public MiscBlockTracker getMiscBlockTracker() {
        return mod.getMiscBlockTracker();
    }

    @Override
    public CraftingRecipeTracker getCraftingRecipeTracker() {
        return mod.getCraftingRecipeTracker();
    }
}
