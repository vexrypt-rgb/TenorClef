package adris.altoclef.planner;

import java.util.List;

/**
 * Strategic objective above tasks (Phase 7). Not a GOAP / HTN node.
 */
public interface Goal {

    /** Stable short id (e.g. {@code acquire:cobblestone}). */
    String getId();

    /** Human-readable description. */
    String getDescription();

    /** Requirements this goal needs (may be empty if satisfaction is custom). */
    List<Requirement> getRequirements();

    /** Whether the goal is already met given inventory (and optional predicates). */
    boolean isSatisfied(InventoryView inventory);
}
