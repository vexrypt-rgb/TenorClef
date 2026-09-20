package adris.altoclef.planner;

/**
 * Minimal inventory read surface for planning (pure Java).
 * Live game uses {@link CatalogueInventoryView}; tests use a map fake.
 */
public interface InventoryView {

    /**
     * How many of the given catalogue key (or opaque item id) the agent holds.
     * Unknown keys → 0.
     */
    int getCount(String catalogueKey);
}
