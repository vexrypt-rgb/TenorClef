package adris.altoclef.util.helpers;

/**
 * Pure score extras for {@code PlaceBlockNearbyTask#locateClosePlacePos}.
 * Placing a crafting table / container inside the player (or at feet) makes
 * Baritone jump-click forever (S120 / E100 / E108 craft thrash).
 */
public final class NearbyPlacePenalty {

    private NearbyPlacePenalty() {}

    /**
     * @param solid   candidate cell is currently solid (must break first)
     * @param hasBelow solid block under candidate
     * @param inside  candidate intersects the player AABB / feet column
     * @return additive score penalty; {@link Double#POSITIVE_INFINITY} = reject
     */
    public static double penalty(boolean solid, boolean hasBelow, boolean inside) {
        if (inside) return Double.POSITIVE_INFINITY;
        double p = 0;
        if (solid) p += 4;
        if (!hasBelow) p += 10;
        return p;
    }

    /** Reject stand-on / bury placements for containers (craft table thrash). */
    public static boolean rejectContainerSpot(boolean insidePlayer, boolean atFeet, boolean atFeetDown) {
        return insidePlayer || atFeet || atFeetDown;
    }
}
