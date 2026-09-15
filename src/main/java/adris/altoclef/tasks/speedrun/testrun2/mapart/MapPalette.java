package adris.altoclef.tasks.speedrun.testrun2.mapart;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * 1.16 map colors → cheap solid blocks (concrete / wool / terracotta).
 * RGB is the vanilla MapColor base (shade 1).
 */
public final class MapPalette {

    public static final class Swatch {
        public final String id;
        public final Item item;
        public final int r, g, b;

        Swatch(String id, Item item, int r, int g, int b) {
            this.id = id;
            this.item = item;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    public static final Swatch[] ALL = {
            sw("white_concrete", Items.WHITE_CONCRETE, 255, 255, 255),
            sw("light_gray_concrete", Items.LIGHT_GRAY_CONCRETE, 153, 153, 153),
            sw("gray_concrete", Items.GRAY_CONCRETE, 76, 76, 76),
            sw("black_concrete", Items.BLACK_CONCRETE, 25, 25, 25),
            sw("red_concrete", Items.RED_CONCRETE, 153, 51, 51),
            sw("orange_concrete", Items.ORANGE_CONCRETE, 216, 127, 51),
            sw("yellow_concrete", Items.YELLOW_CONCRETE, 229, 229, 51),
            sw("lime_concrete", Items.LIME_CONCRETE, 127, 204, 25),
            sw("green_concrete", Items.GREEN_CONCRETE, 102, 127, 51),
            sw("cyan_concrete", Items.CYAN_CONCRETE, 76, 127, 153),
            sw("light_blue_concrete", Items.LIGHT_BLUE_CONCRETE, 102, 153, 216),
            sw("blue_concrete", Items.BLUE_CONCRETE, 51, 76, 178),
            sw("purple_concrete", Items.PURPLE_CONCRETE, 127, 63, 178),
            sw("magenta_concrete", Items.MAGENTA_CONCRETE, 178, 76, 216),
            sw("pink_concrete", Items.PINK_CONCRETE, 242, 127, 165),
            sw("brown_concrete", Items.BROWN_CONCRETE, 102, 76, 51),
            sw("white_wool", Items.WHITE_WOOL, 255, 255, 255),
            sw("orange_wool", Items.ORANGE_WOOL, 216, 127, 51),
            sw("terracotta", Items.TERRACOTTA, 150, 92, 66),
            sw("white_terracotta", Items.WHITE_TERRACOTTA, 209, 177, 161),
            sw("orange_terracotta", Items.ORANGE_TERRACOTTA, 161, 83, 37),
            sw("red_terracotta", Items.RED_TERRACOTTA, 142, 60, 46),
            sw("brown_terracotta", Items.BROWN_TERRACOTTA, 77, 51, 35),
            sw("yellow_terracotta", Items.YELLOW_TERRACOTTA, 186, 133, 35),
            sw("sandstone", Items.SANDSTONE, 247, 233, 163),
            sw("oak_planks", Items.OAK_PLANKS, 143, 119, 72),
            sw("stone", Items.STONE, 112, 112, 112),
            sw("cobblestone", Items.COBBLESTONE, 112, 112, 112),
            sw("dirt", Items.DIRT, 151, 109, 77),
            sw("oak_leaves", Items.OAK_LEAVES, 0, 124, 0),
            sw("ice", Items.PACKED_ICE, 160, 160, 255),
            sw("snow_block", Items.SNOW_BLOCK, 255, 255, 255),
            sw("iron_block", Items.IRON_BLOCK, 167, 167, 167),
            sw("gold_block", Items.GOLD_BLOCK, 250, 238, 77),
            sw("netherrack", Items.NETHERRACK, 111, 2, 0),
            sw("warped_planks", Items.WARPED_PLANKS, 58, 142, 140),
            sw("crimson_planks", Items.CRIMSON_PLANKS, 148, 63, 97),
    };

    private MapPalette() {}

    private static Swatch sw(String id, Item item, int r, int g, int b) {
        return new Swatch(id, item, r, g, b);
    }

    public static Swatch nearest(int r, int g, int b) {
        Swatch best = ALL[0];
        long bestD = Long.MAX_VALUE;
        for (Swatch s : ALL) {
            if (s.item == null) continue;
            long dr = r - s.r, dg = g - s.g, db = b - s.b;
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }
}
