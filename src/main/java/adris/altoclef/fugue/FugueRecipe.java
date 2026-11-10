package adris.altoclef.fugue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One way to make a catalogue item, as Fugue sees it: what one operation consumes, what it yields, and what it
 * needs present (a station block or a pickaxe tier). Pure data; the stock catalogue task still does the work.
 */
public final class FugueRecipe {

    public enum Kind {
        /** Break blocks in the world (needs {@link #getToolTier()}). */
        MINE,
        /** 2x2 crafting in the player inventory. */
        CRAFT_INV,
        /** 3x3 crafting at a crafting table. */
        CRAFT_TABLE,
        /** Furnace smelting; one fuel item per {@link FugueRecipeBook#SMELTS_PER_FUEL} operations. */
        SMELT
    }

    private final String output;
    private final Kind kind;
    private final int outputPerOp;
    private final Map<String, Integer> inputs;
    private final String station;
    private final int toolTier;

    FugueRecipe(String output, Kind kind, int outputPerOp, Map<String, Integer> inputs, String station, int toolTier) {
        this.output = output;
        this.kind = kind;
        this.outputPerOp = Math.max(1, outputPerOp);
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        this.station = station;
        this.toolTier = toolTier;
    }

    public String getOutput() { return output; }
    public Kind getKind() { return kind; }
    public int getOutputPerOp() { return outputPerOp; }
    /** Consumed per operation, in a fixed (insertion) order so planning is deterministic. */
    public Map<String, Integer> getInputs() { return inputs; }
    /** Catalogue key of the block that must be present ("crafting_table", "furnace"), or null. */
    public String getStation() { return station; }
    /** Minimum pickaxe tier for {@link Kind#MINE} (0 = bare hand). */
    public int getToolTier() { return toolTier; }

    @Override
    public String toString() {
        return output + "<-" + kind + inputs + (station != null ? "@" + station : "") + (toolTier > 0 ? " tier" + toolTier : "");
    }
}
