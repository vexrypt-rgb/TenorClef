package adris.altoclef.fugue;

import java.util.List;

/**
 * Everything Fugue reads from the game. Live: {@link LiveFugueView}; tests: a map fake. Client thread.
 *
 * <p>Read-only except for the v1.1 stall-recovery pair {@link #exclude}/{@link #forget}, which mark one mining target
 * unreachable through the existing block blacklist and undo it. Fugue issues no movement through this interface.
 */
public interface FugueWorldView {

    /** Items matching the catalogue key the player holds (inventory plus open crafting/furnace input slots). */
    int count(String catalogueKey);

    /** A usable station block of this catalogue key ("crafting_table", "furnace") is known and close enough to reuse. */
    boolean stationNearby(String catalogueKey);

    /** Distance in blocks to the nearest known source block for a MINE node, or {@code Double.POSITIVE_INFINITY}. */
    double distanceToSource(String catalogueKey);

    /** No navigation is running (the stopped child's pathing has been cancelled). */
    default boolean navigationIdle() { return true; }

    /**
     * Marks a mining target ("x,y,z") unreachable so target selection skips it; with {@code wetNeighbours}, also the
     * known gather resources around the bot (the flooded spot the child could not work from). Returns
     * the positions this call made unreachable (empty if the target already was, or cannot be parsed).
     */
    default List<String> exclude(String target, boolean wetNeighbours) { return List.of(); }

    /** Undoes {@link #exclude} for one position. */
    default void forget(String target) {}
}
