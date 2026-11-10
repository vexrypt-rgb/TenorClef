package adris.altoclef.maze;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ostinato: 2026-11-02. Survival block ids the maze furnisher will gather and place for backrooms themes.
 * No Minecraft types, so the list and the nether-white rewrite are unit tested on every version line.
 *
 * <p>Smooth quartz and quartz generate in the Nether. Prismarine and sea lanterns come from ocean monuments.
 * White concrete needs dye, sand and gravel. None of those belong in an overworld room loop. A plan that names
 * them is rewritten onto polished diorite, diorite and calcite, which a survival bot can mine or craft beside the room.
 */
public final class MazePalette {

    /**
     * White tiles, best first for the bot. Polished diorite is a 2x2 craft from diorite the dig already finds.
     * Calcite is last: it is real overworld stone, but it generates in geodes, so it must not block a craftable white.
     */
    public static final List<String> WHITE_TILES = List.of("polished_diorite", "diorite", "calcite");

    /**
     * Ids a furnish job may name. Claude's room palettes should stay inside this list.
     * Order is the handoff order: whites, stone, wood, warehouse, maintenance, pool, farm, light.
     */
    public static final List<String> SUPPORTED = List.of(
            "polished_diorite", "diorite", "calcite",
            "polished_andesite", "andesite", "polished_granite", "granite", "tuff", "polished_tuff", "tuff_bricks",
            "cobblestone", "stone", "smooth_stone", "sandstone", "dirt", "sand",
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks", "mangrove_planks", "cherry_planks",
            "stripped_oak_log", "stripped_spruce_log", "stripped_birch_log", "stripped_jungle_log", "stripped_acacia_log", "stripped_dark_oak_log", "stripped_mangrove_log", "stripped_cherry_log",
            "barrel", "chest",
            "iron_bars", "chain", "lantern", "lightning_rod",
            "copper_block", "cut_copper", "cut_copper_slab", "cut_copper_stairs",
            "water",
            "farmland", "wheat", "sugar_cane",
            "torch");

    /** Nether quartz, monument prismarine, and white concrete. Dropped, then replaced with {@link #WHITE_TILES}. */
    private static final Set<String> NETHER_OR_MONUMENT_WHITE = Set.of(
            "quartz", "quartz_block", "smooth_quartz", "quartz_bricks", "quartz_pillar", "chiseled_quartz_block",
            "quartz_slab", "quartz_stairs", "smooth_quartz_slab", "smooth_quartz_stairs",
            "white_concrete", "white_concrete_powder",
            "prismarine", "prismarine_bricks", "dark_prismarine", "prismarine_slab", "prismarine_stairs", "prismarine_wall",
            "sea_lantern");

    private MazePalette() {}

    /** True when this id would send the bot to the Nether or a monument, or into a white-concrete craft. */
    public static boolean rejects(String id) {
        return id != null && NETHER_OR_MONUMENT_WHITE.contains(id);
    }

    /**
     * The ids to place, best first. Rejected whites are removed. If any were removed, the overworld white tiles
     * are appended. When calcite shares a list with diorite or polished diorite, calcite moves behind them.
     */
    public static List<String> survivalIds(List<String> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        boolean replaced = false;
        for (String id : blockIds) {
            if (id == null || id.isEmpty()) continue;
            if (rejects(id)) {
                replaced = true;
                continue;
            }
            if (!out.contains(id)) out.add(id);
        }
        if (replaced) {
            for (String white : WHITE_TILES) {
                if (!out.contains(white)) out.add(white);
            }
        }
        if (out.contains("calcite") && (out.contains("polished_diorite") || out.contains("diorite"))) {
            out.remove("calcite");
            int at = out.size();
            for (int i = 0; i < out.size(); i++) {
                if (out.get(i).equals("polished_diorite") || out.get(i).equals("diorite")) at = i + 1;
            }
            out.add(at, "calcite");
        }
        return List.copyOf(out);
    }

    /** Item id the gather loop should ask for. Water and crops are not their block id. */
    public static String gatherId(String blockId) {
        if (blockId == null) return "";
        return switch (blockId) {
            case "water" -> "water_bucket";
            case "wheat" -> "wheat_seeds";
            case "farmland" -> "dirt";
            default -> blockId;
        };
    }

    /** A pool, crop or tilled floor. Placed by {@code MazeFeaturePlaceTask}, not the ceiling clicker. */
    public static boolean feature(String blockId) {
        return "water".equals(blockId) || "wheat".equals(blockId) || "sugar_cane".equals(blockId) || "farmland".equals(blockId);
    }

    /**
     * How many of {@link #gatherId} to ask for up front. One water bucket is refilled per dry pool cell;
     * asking for one bucket per cell walks to a river that many times before the first source is placed.
     */
    public static int gatherCount(String blockId, int cells) {
        if ("water".equals(blockId) || "water_bucket".equals(blockId)) return 1;
        return Math.max(0, cells);
    }
}
