package adris.altoclef.tasks.speedrun.testrun2.catalog;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fills holes in Miran TaskCatalogue without editing that file.
 *
 * 1. Aliases ("food" → bread, "eye_of_ender" → ender_eye, …)
 * 2. Direct Item lookup when the string key is missing but Items.FOO exists
 * 3. Skip items that do not exist on this client (1.16 vs 26.x)
 */
public final class ExtraCatalogue {

    private static final Map<String, String> ALIAS = new LinkedHashMap<>();

    static {
        // names the bot already printed as missing
        ALIAS.put("food", "bread");
        ALIAS.put("foods", "bread");
        ALIAS.put("cooked_food", "cooked_beef");
        ALIAS.put("log", "oak_log");
        ALIAS.put("logs", "oak_log");
        ALIAS.put("plank", "oak_planks");
        ALIAS.put("planks", "oak_planks");
        ALIAS.put("wood", "oak_log");
        ALIAS.put("cobble", "cobblestone");
        ALIAS.put("stone_pick", "stone_pickaxe");
        ALIAS.put("iron_pick", "iron_pickaxe");
        ALIAS.put("wood_pick", "wooden_pickaxe");
        ALIAS.put("wooden_pick", "wooden_pickaxe");
        ALIAS.put("eye", "ender_eye");
        ALIAS.put("eyes", "ender_eye");
        ALIAS.put("pearl", "ender_pearl");
        ALIAS.put("pearls", "ender_pearl");
        ALIAS.put("rod", "blaze_rod");
        ALIAS.put("rods", "blaze_rod");
        ALIAS.put("bucket_water", "water_bucket");
        ALIAS.put("bucket_lava", "lava_bucket");
        ALIAS.put("fns", "flint_and_steel");
        ALIAS.put("flintsteel", "flint_and_steel");
        ALIAS.put("gold_helm", "golden_helmet");
        ALIAS.put("gold_helmet", "golden_helmet");
        ALIAS.put("gapple", "golden_apple");
        ALIAS.put("gapple", "golden_apple");
        ALIAS.put("steak", "cooked_beef");
        ALIAS.put("pork", "cooked_porkchop");

        // 1.17+
        ALIAS.put("axolotl", "axolotl_bucket");
        ALIAS.put("glow_ink", "glow_ink_sac");
        ALIAS.put("glow_frame", "glow_item_frame");
        ALIAS.put("goat_horn", "goat_horn");
        ALIAS.put("copper", "copper_ingot");
        ALIAS.put("raw_cu", "raw_copper");
        ALIAS.put("deepslate", "cobbled_deepslate");
        ALIAS.put("amethyst", "amethyst_shard");

        // 1.19
        ALIAS.put("echo", "echo_shard");
        ALIAS.put("recovery", "recovery_compass");
        ALIAS.put("warden", "sculk_catalyst");
        ALIAS.put("allay", "allay_spawn_egg");
        ALIAS.put("froglight", "pearlescent_froglight");
        ALIAS.put("tadpole", "tadpole_bucket");

        // 1.20
        ALIAS.put("sniffer", "sniffer_egg");
        ALIAS.put("torchflower", "torchflower_seeds");
        ALIAS.put("pitcher", "pitcher_pod");
        ALIAS.put("sherd", "angler_pottery_sherd");
        ALIAS.put("trim", "wayfinder_armor_trim_smithing_template");
        ALIAS.put("cherry", "cherry_log");
        ALIAS.put("bamboo_wood", "bamboo_block");

        // 1.21 trials
        ALIAS.put("trial", "trial_key");
        ALIAS.put("ominous_key", "ominous_trial_key");
        ALIAS.put("breeze", "breeze_rod");
        ALIAS.put("mace", "mace");
        ALIAS.put("heavy_core", "heavy_core");
        ALIAS.put("wind", "wind_charge");
        ALIAS.put("scute", "armadillo_scute");
        ALIAS.put("wolf_armor", "wolf_armor");
        ALIAS.put("crafter", "crafter");
        ALIAS.put("copper_bulb", "copper_bulb");
        ALIAS.put("vault", "trial_key");

        // 1.21.6 / garden
        ALIAS.put("dried_ghast", "dried_ghast");
        ALIAS.put("creaking", "creaking_heart");
        ALIAS.put("pale_log", "pale_oak_log");
        ALIAS.put("resin", "resin_clump");
        ALIAS.put("eyeblossom", "open_eyeblossom");

        // 1.21.11 mounts
        ALIAS.put("spear", "iron_spear");
        ALIAS.put("nautilus", "nautilus_armor");

        // 26.2 chaos cubed
        ALIAS.put("sulfur", "sulfur");
        ALIAS.put("cinnabar", "cinnabar");
        ALIAS.put("uh_oh", "tnt");
    }

    private ExtraCatalogue() {}

    public static String resolve(String raw) {
        if (raw == null) return null;
        String k = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        String mapped = ALIAS.get(k);
        return mapped != null ? mapped : k;
    }

    public static Task get(String raw, int count) {
        int n = Math.max(1, count);
        String name = resolve(raw);

        Task t = tryOfficial(name, n);
        if (t != null) return t;

        Item item = McCompat.item(name.toUpperCase(Locale.ROOT));
        if (item != null) {
            t = tryOfficialItem(item, n);
            if (t != null) return t;
        }

        // last resort: mine the block of the same name if it exists
        Block block = McCompat.block(name.toUpperCase(Locale.ROOT));
        if (block != null && block != Blocks.AIR && item != null) {
            try {
                return new adris.altoclef.tasks.resources.MineAndCollectTask(
                        item, n, new Block[]{block},
                        adris.altoclef.util.MiningRequirement.HAND);
            } catch (Throwable ignored) {}
        }

        Debug.logWarning("XGET miss key=" + raw + " resolved=" + name
                + " item=" + (item != null) + " official=" + officialExists(name));
        return null;
    }

    public static boolean exists(String raw) {
        String name = resolve(raw);
        if (officialExists(name)) return true;
        return McCompat.item(name.toUpperCase(Locale.ROOT)) != null;
    }

    public static void dump() {
        Debug.logMessage("XCAT aliases=" + ALIAS.size() + " minor=" + McCompat.gameMinor());
        int ok = 0, miss = 0;
        for (Map.Entry<String, String> e : ALIAS.entrySet()) {
            boolean has = exists(e.getValue());
            if (has) ok++;
            else miss++;
            if (!has) {
                Debug.logMessage("  skip " + e.getKey() + " -> " + e.getValue() + " (not on this client)");
            }
        }
        Debug.logMessage("XCAT on-this-client=" + ok + " skipped=" + miss);
    }

    private static Task tryOfficial(String name, int n) {
        try {
            if (!TaskCatalogue.taskExists(name)) return null;
            return TaskCatalogue.getItemTask(name, n);
        } catch (Throwable t) {
            try {
                return TaskCatalogue.getItemTask(name, n);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    private static Task tryOfficialItem(Item item, int n) {
        try {
            if (TaskCatalogue.taskExists(item)) {
                return TaskCatalogue.getItemTask(item, n);
            }
        } catch (Throwable ignored) {}
        try {
            return TaskCatalogue.getItemTask(item, n);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean officialExists(String name) {
        try {
            return TaskCatalogue.taskExists(name);
        } catch (Throwable t) {
            return false;
        }
    }

    public static int aliasCount() {
        return ALIAS.size();
    }
}
