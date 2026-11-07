package adris.altoclef.allblocks;

import adris.altoclef.TaskCatalogue;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ostinato: #allblocks. Every item in the registry, sorted into what the run gathers and what it leaves out by rule.
 * <p>
 * Left out by rule (never attempted, listed as skipped with the reason): creative-only and command-only items, spawn
 * eggs, and items a survival player cannot get at all. Everything else is a candidate. A candidate without a
 * TaskCatalogue path is reported as unsupported, not hidden. Candidates are ordered by the rough cost in
 * {@code allblocks/catalogue-26.3.txt} (cheapest first); items without a cost keep registry order at the end.
 */
public final class AllBlocksPlan {

    /** Creative inventory or commands only. */
    private static final Set<String> COMMAND_ONLY = new HashSet<>(Arrays.asList(
            "command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
            "structure_block", "structure_void", "jigsaw", "barrier", "light", "debug_stick", "test_block",
            "test_instance_block", "knowledge_book"));

    /** No survival way to hold one as an item. */
    private static final Set<String> UNOBTAINABLE = new HashSet<>(Arrays.asList(
            "air", "bedrock", "end_portal_frame", "reinforced_deepslate", "budding_amethyst", "spawner",
            "trial_spawner", "vault", "farmland", "dirt_path", "petrified_oak_slab", "chorus_plant", "frogspawn",
            "player_head", "suspicious_sand", "suspicious_gravel", "infested_stone", "infested_cobblestone",
            "infested_stone_bricks", "infested_mossy_stone_bricks", "infested_cracked_stone_bricks",
            "infested_chiseled_stone_bricks", "infested_deepslate"));

    public static final class Entry {
        public final String id;
        public final Item item;
        /** Null for a candidate; the rule that leaves it out otherwise. */
        public final String excluded;
        public final double cost;

        Entry(String id, Item item, String excluded, double cost) {
            this.id = id;
            this.item = item;
            this.excluded = excluded;
            this.cost = cost;
        }

        public boolean candidate() {
            return excluded == null;
        }

        public boolean placeable() {
            return item instanceof BlockItem;
        }

        public Block block() {
            return item instanceof BlockItem bi ? bi.getBlock() : null;
        }

        /** Null when TaskCatalogue has a path for the item, else why not (the code path that fails). */
        public String unsupportedReason() {
            if (TaskCatalogue.taskExists(item)) return null;
            String[] gap = GAPS.get(id);
            return "[" + (gap != null ? gap[0] : "unknown") + "] " + (gap != null ? gap[1] + "; " : "")
                    + "TaskCatalogue.taskExists(" + id + ") is false (no hand-written entry and no resolvable line in"
                    + " allblocks/catalogue-26.3.txt)";
        }

        /** Root-cause category of an unsupported item ("silk-touch", "copper-aging", ...), or null when supported. */
        public String gapCategory() {
            if (TaskCatalogue.taskExists(item)) return null;
            String[] gap = GAPS.get(id);
            return gap != null ? gap[0] : "unknown";
        }
    }

    private AllBlocksPlan() {}

    /** Why this id is left out by rule, or null. */
    public static String exclusion(String id) {
        if (id.endsWith("_spawn_egg")) return "creative-only (spawn egg)";
        if (COMMAND_ONLY.contains(id)) return "creative/command-only";
        if (UNOBTAINABLE.contains(id)) return "unobtainable in survival";
        return null;
    }

    public static List<Entry> build() {
        Map<String, Double> cost = readCosts();
        List<Entry> entries = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds()) {
            if (!id.getNamespace().equals("minecraft")) continue;
            Item item = Registries.ITEM.get(id);
            String name = id.getPath();
            entries.add(new Entry(name, item, exclusion(name), cost.getOrDefault(name, Double.MAX_VALUE)));
        }
        // Stable: equal costs keep registry order.
        entries.sort((a, b) -> Double.compare(a.cost, b.cost));
        return Collections.unmodifiableList(entries);
    }

    /** "gap" lines of the generated catalogue: item id to {category, why}. */
    private static final Map<String, String[]> GAPS = readGaps();

    private static Map<String, String[]> readGaps() {
        Map<String, String[]> gaps = new HashMap<>();
        try (InputStream in = AllBlocksPlan.class.getResourceAsStream("/allblocks/catalogue-26.3.txt")) {
            if (in == null) return gaps;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String l; (l = r.readLine()) != null; ) {
                if (!l.startsWith("gap ")) continue;
                String[] p = l.split(" ", 4);
                if (p.length == 4) gaps.put(p[1], new String[]{p[2], p[3]});
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return gaps;
    }

    private static Map<String, Double> readCosts() {
        Map<String, Double> cost = new HashMap<>();
        try (InputStream in = AllBlocksPlan.class.getResourceAsStream("/allblocks/catalogue-26.3.txt")) {
            if (in == null) return cost;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String l; (l = r.readLine()) != null; ) {
                if (!l.startsWith("order ")) continue;
                String[] p = l.split(" ");
                double c = Double.parseDouble(p[2]);
                if (c >= 0) cost.put(p[1], c);
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return cost;
    }
}
