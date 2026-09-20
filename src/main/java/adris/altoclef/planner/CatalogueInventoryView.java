package adris.altoclef.planner;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.knowledge.WorldKnowledge;
import net.minecraft.item.Item;

/**
 * Live {@link InventoryView} over WorldKnowledge / ItemStorage + TaskCatalogue matches.
 */
public final class CatalogueInventoryView implements InventoryView {

    private final AltoClef mod;

    public CatalogueInventoryView(AltoClef mod) {
        this.mod = mod;
    }

    @Override
    public int getCount(String catalogueKey) {
        if (mod == null || catalogueKey == null || catalogueKey.isBlank()) {
            return 0;
        }
        if (!TaskCatalogue.taskExists(catalogueKey)) {
            return 0;
        }
        Item[] matches = TaskCatalogue.getItemMatches(catalogueKey);
        if (matches == null || matches.length == 0) {
            return 0;
        }
        WorldKnowledge knowledge = mod.getWorldKnowledge();
        if (knowledge != null && knowledge.getItemStorage() != null) {
            return knowledge.getItemStorage().getItemCountInventoryOnly(matches);
        }
        return mod.getItemStorage().getItemCountInventoryOnly(matches);
    }
}
