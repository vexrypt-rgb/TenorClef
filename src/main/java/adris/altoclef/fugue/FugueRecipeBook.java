package adris.altoclef.fugue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The recipes Fugue v1 plans over. Deliberately small: the early-game tool chain up to an iron pickaxe, using the
 * catalogue keys {@code TaskCatalogue} already serves, so every node maps 1:1 onto a stock catalogue task.
 * Anything outside this book is unsupported and Fugue refuses it up front.
 */
public final class FugueRecipeBook {

    /** Vanilla: one coal or charcoal smelts 8 items. */
    public static final int SMELTS_PER_FUEL = 8;
    public static final String FUEL = "coal";

    /** Pickaxe catalogue keys by mining tier (index = tier). Golden pickaxes mine at wood tier. */
    static final String[] PICKAXE_BY_TIER = {null, "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "diamond_pickaxe", "netherite_pickaxe"};

    private final Map<String, FugueRecipe> recipes;

    public FugueRecipeBook(Map<String, FugueRecipe> recipes) {
        this.recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
    }

    public FugueRecipe get(String key) { return recipes.get(key); }
    public boolean supports(String key) { return recipes.containsKey(key); }
    public Set<String> keys() { return recipes.keySet(); }

    /** Best pickaxe tier the inventory holds (0 = none). */
    public static int heldPickaxeTier(FugueWorldView view) {
        for (int t = PICKAXE_BY_TIER.length - 1; t >= 1; t--) {
            if (view.count(PICKAXE_BY_TIER[t]) > 0) return t;
        }
        return view.count("golden_pickaxe") > 0 ? 1 : 0;
    }

    /** Cheapest pickaxe that reaches {@code tier}. */
    public static String pickaxeForTier(int tier) {
        return PICKAXE_BY_TIER[Math.max(1, Math.min(tier, PICKAXE_BY_TIER.length - 1))];
    }

    public static Builder builder() { return new Builder(); }

    /** The v1 book: iron pickaxe and everything under it (vanilla recipes, coal fuel only). */
    public static FugueRecipeBook ironTools() {
        return builder()
                .mine("log", 0)
                .craftInv("planks", 4, "log", 1)
                .craftInv("stick", 4, "planks", 2)
                .craftInv("crafting_table", 1, "planks", 4)
                .craftTable("wooden_pickaxe", 1, "planks", 3, "stick", 2)
                .mine("cobblestone", 1)
                .mine("coal", 1)
                .craftTable("stone_pickaxe", 1, "cobblestone", 3, "stick", 2)
                .craftTable("furnace", 1, "cobblestone", 8)
                .mine("raw_iron", 2)
                .smelt("iron_ingot", "raw_iron")
                .craftTable("iron_pickaxe", 1, "iron_ingot", 3, "stick", 2)
                .build();
    }

    public static final class Builder {
        private final Map<String, FugueRecipe> map = new LinkedHashMap<>();

        public Builder mine(String key, int toolTier) {
            map.put(key, new FugueRecipe(key, FugueRecipe.Kind.MINE, 1, Map.of(), null, toolTier));
            return this;
        }

        public Builder craftInv(String key, int out, Object... inputs) {
            map.put(key, new FugueRecipe(key, FugueRecipe.Kind.CRAFT_INV, out, pairs(inputs), null, 0));
            return this;
        }

        public Builder craftTable(String key, int out, Object... inputs) {
            map.put(key, new FugueRecipe(key, FugueRecipe.Kind.CRAFT_TABLE, out, pairs(inputs), "crafting_table", 0));
            return this;
        }

        public Builder smelt(String key, String input) {
            Map<String, Integer> in = new LinkedHashMap<>();
            in.put(input, 1);
            map.put(key, new FugueRecipe(key, FugueRecipe.Kind.SMELT, 1, in, "furnace", 0));
            return this;
        }

        /** Arbitrary recipe (tests: cycles, unusual shapes). */
        public Builder recipe(String key, FugueRecipe.Kind kind, int out, String station, int toolTier, Object... inputs) {
            map.put(key, new FugueRecipe(key, kind, out, pairs(inputs), station, toolTier));
            return this;
        }

        public FugueRecipeBook build() { return new FugueRecipeBook(map); }

        private static Map<String, Integer> pairs(Object... kv) {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
            return m;
        }
    }
}
