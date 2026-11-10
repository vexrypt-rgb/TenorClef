package adris.altoclef.fugue;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Picks the next ready node. Deterministic: static cost by kind plus a travel estimate for mining, then
 * tie-breaks on (more downstream work unlocked, same station as the previous node, catalogue key).
 * Selection only happens between nodes; a running node is never preempted for a cheaper one.
 */
public final class FugueScheduler {

    static final double COST_CRAFT_INV = 1;
    static final double COST_CRAFT_TABLE = 3;
    /** Extra when the station must be made first (it is then a dependency, so this only orders equals). */
    static final double COST_NO_STATION = 5;
    static final double COST_SMELT = 10;
    static final double COST_MINE = 20;
    /** Assumed distance to an unseen source block. */
    static final double UNKNOWN_DISTANCE = 64;

    public FugueNode select(List<FugueNode> ready, Set<String> excluded, FugueWorldView view, String lastStation) {
        Comparator<FugueNode> order = Comparator
                .comparingDouble((FugueNode n) -> cost(n, view))
                .thenComparing(Comparator.comparingInt(FugueNode::getDownstream).reversed())
                .thenComparing(n -> lastStation != null && lastStation.equals(n.getRecipe().getStation()) ? 0 : 1)
                .thenComparing(FugueNode::getKey);
        FugueNode best = null;
        for (FugueNode n : ready) {
            if (excluded.contains(n.getKey())) continue;
            if (best == null || order.compare(n, best) < 0) best = n;
        }
        return best;
    }

    public double cost(FugueNode n, FugueWorldView view) {
        FugueRecipe r = n.getRecipe();
        switch (r.getKind()) {
            case CRAFT_INV:
                return COST_CRAFT_INV;
            case CRAFT_TABLE:
                return COST_CRAFT_TABLE + (view.stationNearby(r.getStation()) ? 0 : COST_NO_STATION);
            case SMELT:
                return COST_SMELT + (view.stationNearby(r.getStation()) ? 0 : COST_NO_STATION);
            case MINE:
            default:
                double d = view.distanceToSource(n.getKey());
                // Whole blocks only, so float noise in a position never reorders two equal choices.
                return COST_MINE + Math.floor(Double.isFinite(d) ? Math.min(d, UNKNOWN_DISTANCE) : UNKNOWN_DISTANCE);
        }
    }
}
