package adris.altoclef.planner;

/**
 * Builds a {@link Plan} for a {@link Goal} given inventory.
 */
public interface Planner {

    Plan plan(Goal goal, InventoryView inventory);
}
