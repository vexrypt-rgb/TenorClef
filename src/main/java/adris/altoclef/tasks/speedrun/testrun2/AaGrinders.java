package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.resources.CollectBlazeRodsTask;
import adris.altoclef.tasks.resources.TradeWithPiglinsTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Maps a catalog Goal to one AltoClef task. Catalogue-only — no invented
 * task classes that 1.16.5 may not have.
 */
public final class AaGrinders {

    private AaGrinders() {}

    public static Task forGoal(AltoClef mod, AdvancementCatalog.Goal g) {
        if (g == null) return null;
        if (AdvancementProbe.done(g.id)) return null;
        return switch (g.kind) {
            case ITEM -> item(mod, g);
            case GRIND -> grind(mod, g);
            case HARD -> new TimeoutWanderTask();
            case ROUTE -> null;
        };
    }

    private static Task item(AltoClef mod, AdvancementCatalog.Goal g) {
        if (g.item == null) return null;
        Item it = named(g.item);
        if (it != null && count(mod, it) >= Math.max(1, g.count)) return null;
        return catalogue(g.item, Math.max(1, g.count), it);
    }

    private static Task grind(AltoClef mod, AdvancementCatalog.Goal g) {
        String k = g.item == null ? "" : g.item;
        return switch (k) {
            case "netherite" -> netheriteSuit(mod);
            case "wither" -> witherKit(mod);
            case "cure" -> cureKit(mod);
            case "lodestone" -> lodestone(mod);
            case "anchor" -> anchor(mod);
            case "strider" -> catalogue("warped_fungus_on_a_stick", 1, Items.WARPED_FUNGUS_ON_A_STICK);
            case "bastion", "bastion_loot" -> new TimeoutWanderTask();
            case "distract" -> catalogue("gold_ingot", 8, Items.GOLD_INGOT);
            case "all_potions" -> potionKit(mod);
            case "gateway" -> catalogue("ender_pearl", 4, Items.ENDER_PEARL);
            case "respawn" -> catalogue("end_crystal", 4, Items.END_CRYSTAL);
            case "trade" -> tradeKit(mod);
            case "golem" -> golemKit(mod);
            case "honey", "honey_bottle" -> catalogue("honey_bottle", 1, Items.HONEY_BOTTLE);
            case "banner" -> catalogue("white_banner", 1, Items.WHITE_BANNER);
            case "raid" -> raidKit(mod);
            case "breed" -> catalogue("wheat", 16, Items.WHEAT);
            case "tame" -> catalogue("bone", 16, Items.BONE);
            case "diet" -> farmBook(mod);
            case "silk_nest" -> catalogue("shears", 1, Items.SHEARS);
            case "goat" -> catalogue("wheat", 8, Items.WHEAT);
            case "copper" -> catalogue("honeycomb", 1, null);
            case "axolotl_fight" -> catalogue("axolotl_bucket", 1, null);
            case "jukebox" -> catalogue("jukebox", 1, Items.JUKEBOX);
            case "allay", "allay_cake" -> catalogue("note_block", 1, Items.NOTE_BLOCK);
            case "lead" -> catalogue("lead", 4, Items.LEAD);
            case "sculk_catalyst" -> catalogue("sculk_catalyst", 1, null);
            case "trim", "trim_all" -> catalogue("iron_chestplate", 1, Items.IRON_CHESTPLATE);
            case "crafter" -> catalogue("crafter", 1, null);
            case "copper_bulb" -> catalogue("copper_bulb", 1, null);
            default -> item(mod, g);
        };
    }

    public static Task netheriteSuit(AltoClef mod) {
        if (count(mod, Items.NETHERITE_INGOT) < 4) return catalogue("netherite_ingot", 4, Items.NETHERITE_INGOT);
        if (count(mod, Items.NETHERITE_HELMET) < 1) return catalogue("netherite_helmet", 1, Items.NETHERITE_HELMET);
        if (count(mod, Items.NETHERITE_CHESTPLATE) < 1) return catalogue("netherite_chestplate", 1, Items.NETHERITE_CHESTPLATE);
        if (count(mod, Items.NETHERITE_LEGGINGS) < 1) return catalogue("netherite_leggings", 1, Items.NETHERITE_LEGGINGS);
        if (count(mod, Items.NETHERITE_BOOTS) < 1) return catalogue("netherite_boots", 1, Items.NETHERITE_BOOTS);
        return null;
    }

    public static Task witherKit(AltoClef mod) {
        if (count(mod, Items.WITHER_SKELETON_SKULL) < 3) {
            return catalogue("wither_skeleton_skull", 3, Items.WITHER_SKELETON_SKULL);
        }
        if (count(mod, Items.SOUL_SAND) < 4 && count(mod, Items.SOUL_SOIL) < 4) {
            return catalogue("soul_sand", 4, Items.SOUL_SAND);
        }
        if (count(mod, Items.NETHER_STAR) < 1) return catalogue("nether_star", 1, Items.NETHER_STAR);
        if (count(mod, Items.BEACON) < 1) return catalogue("beacon", 1, Items.BEACON);
        return null;
    }

    public static Task potionKit(AltoClef mod) {
        if (count(mod, Items.BLAZE_ROD) < 8) return new CollectBlazeRodsTask(8);
        if (count(mod, Items.ENDER_PEARL) < 8) {
            return new TradeWithPiglinsTask(24, new ItemTarget(Items.ENDER_PEARL, 8));
        }
        if (count(mod, Items.NETHER_WART) < 16) return catalogue("nether_wart", 16, Items.NETHER_WART);
        if (count(mod, Items.GHAST_TEAR) < 1) return catalogue("ghast_tear", 1, Items.GHAST_TEAR);
        if (count(mod, Items.MAGMA_CREAM) < 4) return catalogue("magma_cream", 4, Items.MAGMA_CREAM);
        if (count(mod, Items.SPIDER_EYE) < 4) return catalogue("spider_eye", 4, Items.SPIDER_EYE);
        if (count(mod, Items.SUGAR) < 8) return catalogue("sugar", 8, Items.SUGAR);
        if (count(mod, Items.GLOWSTONE_DUST) < 8) return catalogue("glowstone_dust", 8, Items.GLOWSTONE_DUST);
        if (count(mod, Items.REDSTONE) < 16) return catalogue("redstone", 16, Items.REDSTONE);
        if (count(mod, Items.FERMENTED_SPIDER_EYE) < 1) {
            return catalogue("fermented_spider_eye", 1, Items.FERMENTED_SPIDER_EYE);
        }
        if (count(mod, Items.GOLDEN_CARROT) < 4) return catalogue("golden_carrot", 4, Items.GOLDEN_CARROT);
        if (count(mod, Items.PUFFERFISH) < 1) return catalogue("pufferfish", 1, Items.PUFFERFISH);
        if (count(mod, Items.BREWING_STAND) < 1) return catalogue("brewing_stand", 1, Items.BREWING_STAND);
        if (count(mod, Items.GLASS_BOTTLE) < 8) return catalogue("glass_bottle", 8, Items.GLASS_BOTTLE);
        return null;
    }

    public static Task raidKit(AltoClef mod) {
        if (count(mod, Items.CROSSBOW) < 1) return catalogue("crossbow", 1, Items.CROSSBOW);
        if (count(mod, Items.ARROW) < 32) return catalogue("arrow", 32, Items.ARROW);
        if (count(mod, Items.TOTEM_OF_UNDYING) < 1) return catalogue("totem_of_undying", 1, Items.TOTEM_OF_UNDYING);
        if (count(mod, Items.WHITE_BANNER) < 1) return catalogue("white_banner", 1, Items.WHITE_BANNER);
        return new TimeoutWanderTask();
    }

    public static Task farmBook(AltoClef mod) {
        if (count(mod, Items.APPLE) < 1) return catalogue("apple", 1, Items.APPLE);
        if (count(mod, Items.BREAD) < 1) return catalogue("bread", 1, Items.BREAD);
        if (count(mod, Items.COOKED_BEEF) < 1) return catalogue("cooked_beef", 1, Items.COOKED_BEEF);
        if (count(mod, Items.COOKED_PORKCHOP) < 1) return catalogue("cooked_porkchop", 1, Items.COOKED_PORKCHOP);
        if (count(mod, Items.COOKED_CHICKEN) < 1) return catalogue("cooked_chicken", 1, Items.COOKED_CHICKEN);
        if (count(mod, Items.COOKED_MUTTON) < 1) return catalogue("cooked_mutton", 1, Items.COOKED_MUTTON);
        if (count(mod, Items.COOKED_COD) < 1) return catalogue("cooked_cod", 1, Items.COOKED_COD);
        if (count(mod, Items.COOKED_SALMON) < 1) return catalogue("cooked_salmon", 1, Items.COOKED_SALMON);
        if (count(mod, Items.CARROT) < 1) return catalogue("carrot", 1, Items.CARROT);
        if (count(mod, Items.POTATO) < 1) return catalogue("potato", 1, Items.POTATO);
        if (count(mod, Items.BAKED_POTATO) < 1) return catalogue("baked_potato", 1, Items.BAKED_POTATO);
        if (count(mod, Items.BEETROOT) < 1) return catalogue("beetroot", 1, Items.BEETROOT);
        if (count(mod, Items.MELON_SLICE) < 1) return catalogue("melon_slice", 1, Items.MELON_SLICE);
        if (count(mod, Items.SWEET_BERRIES) < 1) return catalogue("sweet_berries", 1, Items.SWEET_BERRIES);
        if (count(mod, Items.DRIED_KELP) < 1) return catalogue("dried_kelp", 1, Items.DRIED_KELP);
        if (count(mod, Items.COOKIE) < 1) return catalogue("cookie", 1, Items.COOKIE);
        if (count(mod, Items.PUMPKIN_PIE) < 1) return catalogue("pumpkin_pie", 1, Items.PUMPKIN_PIE);
        if (count(mod, Items.MUSHROOM_STEW) < 1) return catalogue("mushroom_stew", 1, Items.MUSHROOM_STEW);
        if (count(mod, Items.HONEY_BOTTLE) < 1) return catalogue("honey_bottle", 1, Items.HONEY_BOTTLE);
        return null;
    }

    private static Task cureKit(AltoClef mod) {
        if (count(mod, Items.GOLDEN_APPLE) < 1) return catalogue("golden_apple", 1, Items.GOLDEN_APPLE);
        if (count(mod, Items.FERMENTED_SPIDER_EYE) < 1) {
            return catalogue("fermented_spider_eye", 1, Items.FERMENTED_SPIDER_EYE);
        }
        if (count(mod, Items.BREWING_STAND) < 1) return catalogue("brewing_stand", 1, Items.BREWING_STAND);
        if (count(mod, Items.BLAZE_ROD) < 2) return new CollectBlazeRodsTask(2);
        return new TimeoutWanderTask();
    }

    private static Task lodestone(AltoClef mod) {
        if (count(mod, Items.LODESTONE) < 1) return catalogue("lodestone", 1, Items.LODESTONE);
        if (count(mod, Items.COMPASS) < 1) return catalogue("compass", 1, Items.COMPASS);
        return null;
    }

    private static Task anchor(AltoClef mod) {
        if (count(mod, Items.RESPAWN_ANCHOR) < 1) return catalogue("respawn_anchor", 1, Items.RESPAWN_ANCHOR);
        if (count(mod, Items.GLOWSTONE) < 4) return catalogue("glowstone", 4, Items.GLOWSTONE);
        return null;
    }

    private static Task tradeKit(AltoClef mod) {
        if (count(mod, Items.EMERALD) < 8) return catalogue("emerald", 8, Items.EMERALD);
        return new TimeoutWanderTask();
    }

    private static Task golemKit(AltoClef mod) {
        if (count(mod, Items.IRON_BLOCK) < 4) return catalogue("iron_block", 4, Items.IRON_BLOCK);
        if (count(mod, Items.CARVED_PUMPKIN) < 1 && count(mod, Items.PUMPKIN) < 1) {
            return catalogue("pumpkin", 1, Items.PUMPKIN);
        }
        return null;
    }

    private static Task catalogue(String name, int n, Item item) {
        try {
            if (item != null) return TaskCatalogue.getItemTask(item, n);
        } catch (Throwable ignored) {}
        try {
            return TaskCatalogue.getItemTask(name, n);
        } catch (Throwable t) {
            return new TimeoutWanderTask();
        }
    }

    private static Item named(String name) {
        if (name == null) return null;
        try {
            return (Item) Items.class.getField(name.toUpperCase()).get(null);
        } catch (Throwable t) {
            return McCompat.item(name.toUpperCase());
        }
    }

    public static int count(AltoClef mod, Item item) {
        if (item == null) return 0;
        try {
            return mod.getItemStorage().getItemCount(item);
        } catch (Throwable t) {
            return 0;
        }
    }
}
