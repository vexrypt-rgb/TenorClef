package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import net.minecraft.item.Items;

/** Protect the items every personal task should never dump. */
public final class KitLock {

    private KitLock() {}

    public static void tick(AltoClef mod) {
        if (mod == null) return;
        try {
            // Asked of the behaviour itself, not remembered: a task that pops its state takes the lock with it.
            if (mod.getBehaviour().isProtected(Items.FLINT_AND_STEEL)) return;
            mod.getBehaviour().addProtectedItems(
                    Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
                    Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
                    Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.BUCKET,
                    Items.FLINT_AND_STEEL, Items.FLINT,
                    Items.ENDER_EYE, Items.ENDER_PEARL, Items.BLAZE_ROD,
                    Items.BREAD, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
                    Items.TOTEM_OF_UNDYING, Items.SHIELD,
                    Items.CRAFTING_TABLE, Items.FURNACE
                    // cobble / netherrack are NOT global — last-block place loops if reserved forever
            );
        } catch (Throwable ignored) {}
    }
}
