package adris.altoclef.tasks.speedrun.testrun2;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.Set;

/**
 * Items the run must not throw, lava, or deposit.
 * Wire into your fork's throwaway classifier if it has one
 * (often `ItemHelper.canThrowAway` / `isProtected`).
 */
public final class KeepList {

    private KeepList() {}

    public static final Set<Item> KEEP = Set.of(
            Items.IRON_PICKAXE, Items.IRON_SWORD, Items.IRON_AXE,
            Items.STONE_PICKAXE, Items.WOODEN_PICKAXE,
            Items.SHIELD,
            Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET,
            Items.FLINT_AND_STEEL, Items.FIRE_CHARGE, Items.FLINT,
            Items.IRON_INGOT, Items.RAW_IRON,
            Items.GOLD_INGOT, Items.GOLD_BLOCK, Items.GOLD_NUGGET,
            Items.OBSIDIAN, Items.CRYING_OBSIDIAN,
            Items.ENDER_EYE, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.BLAZE_POWDER,
            Items.CRAFTING_TABLE, Items.FURNACE,
            Items.WHITE_BED, Items.ORANGE_BED, Items.MAGENTA_BED, Items.LIGHT_BLUE_BED,
            Items.YELLOW_BED, Items.LIME_BED, Items.PINK_BED, Items.GRAY_BED,
            Items.LIGHT_GRAY_BED, Items.CYAN_BED, Items.PURPLE_BED, Items.BLUE_BED,
            Items.BROWN_BED, Items.GREEN_BED, Items.RED_BED, Items.BLACK_BED,
            Items.OAK_BOAT, Items.BIRCH_BOAT, Items.SPRUCE_BOAT,
            Items.COBBLESTONE, Items.NETHERRACK,
            Items.BREAD, Items.COOKED_PORKCHOP, Items.COOKED_BEEF, Items.GOLDEN_CARROT,
            Items.ENDER_CHEST, Items.DIAMOND, Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE
    );

    public static boolean keep(Item item) {
        return item != null && KEEP.contains(item);
    }
}
