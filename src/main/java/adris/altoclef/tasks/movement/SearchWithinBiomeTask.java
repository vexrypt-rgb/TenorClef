package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.multiversion.world.WorldVer;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
//#if MC >= 11605
import net.minecraft.registry.RegistryKey;
//#endif

/**
 * Explores/Loads all chunks of a biome.
 */
public class SearchWithinBiomeTask extends SearchChunksExploreTask {

    //#if MC >= 11605
    private final RegistryKey<Biome> _toSearch;

    public SearchWithinBiomeTask(RegistryKey<Biome> toSearch) {
        _toSearch = toSearch;
    }
    //#else
    //$$ private final Biome _toSearch;
    //$$
    //$$ public SearchWithinBiomeTask(Biome toSearch) {
    //$$     _toSearch = toSearch;
    //$$ }
    //#endif

    @Override
    protected boolean isChunkWithinSearchSpace(AltoClef mod, ChunkPos pos) {
        return WorldVer.isBiomeAtPos(mod.getWorld(),_toSearch,pos.getStartPos().add(1,1,1));
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof SearchWithinBiomeTask task) {
            return task._toSearch == _toSearch;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Searching for+within biome: " + _toSearch;
    }
}