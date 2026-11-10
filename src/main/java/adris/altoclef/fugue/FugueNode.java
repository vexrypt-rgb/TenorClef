package adris.altoclef.fugue;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One item Fugue still has to produce in the current plan. Identity is the catalogue key: all demand for an item,
 * from every consumer, is aggregated into a single node, so shared prerequisites are gathered once.
 */
public final class FugueNode {

    private final String key;
    private final FugueRecipe recipe;
    /** Items that must come from production (demand left after inventory allocation). */
    int need;
    /** Operations planned (crafts, smelts, blocks mined). */
    int ops;
    /** Produced but not yet claimed by a consumer (craft rounding: 2 planks give 4 sticks). */
    int surplus;
    /** Catalogue keys whose nodes must finish before this one (only edges that caused production). */
    final Set<String> deps = new LinkedHashSet<>();
    /** Nodes that (transitively) wait on this one. */
    int downstream;

    FugueNode(String key, FugueRecipe recipe) {
        this.key = key;
        this.recipe = recipe;
    }

    public String getKey() { return key; }
    public FugueRecipe getRecipe() { return recipe; }
    public FugueRecipe.Kind getKind() { return recipe.getKind(); }
    public int getNeed() { return need; }
    public int getOps() { return ops; }
    public Set<String> getDeps() { return Collections.unmodifiableSet(deps); }
    public int getDownstream() { return downstream; }

    @Override
    public String toString() {
        return key + "x" + need + "(" + recipe.getKind() + (deps.isEmpty() ? "" : " after " + deps) + ")";
    }
}
