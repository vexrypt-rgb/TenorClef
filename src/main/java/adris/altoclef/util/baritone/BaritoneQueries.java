package adris.altoclef.util.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.utils.IWorldQueries;

/** Ostinato: the pathfinder's world questions through the typed driver API (IBaritone.getWorldQueries()). */
public final class BaritoneQueries {
    private BaritoneQueries() {}

    public static IWorldQueries get() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getWorldQueries();
    }
}
