package adris.altoclef.tasks.speedrun.testrun2.core;

import adris.altoclef.AltoClef;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Do not spend the last N placeables on bridging / hole escape.
 * Only used by {@code @t2core}. Other commands never call this.
 */
public final class T2Reserve {

    public static final int COBBLE_MIN = 16;
    public static final int COBBLE_FILL = 48;
    public static final int DIRT_MIN = 8;

    private T2Reserve() {}

    public static void install(AltoClef mod) {
        try {
            mod.getBehaviour().push();
            mod.getBehaviour().addProtectedItems(
                    Items.COBBLESTONE, Items.DIRT, Items.NETHERRACK,
                    Items.STONE_PICKAXE, Items.IRON_PICKAXE,
                    Items.WOODEN_PICKAXE, Items.CRAFTING_TABLE);
        } catch (Throwable ignored) {}
    }

    public static void uninstall(AltoClef mod) {
        try {
            mod.getBehaviour().pop();
        } catch (Throwable ignored) {}
    }

    public static int count(AltoClef mod, Item item) {
        try {
            return mod.getItemStorage().getItemCount(item);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** True only when below the floor — hysteresis so we do not re-get after each block. */
    public static boolean belowFloor(AltoClef mod, Item item, int floor) {
        return count(mod, item) < floor;
    }
}
