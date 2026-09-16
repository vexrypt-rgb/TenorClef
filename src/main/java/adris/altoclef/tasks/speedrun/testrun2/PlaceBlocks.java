package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Any cheap full-cube the bot may pillar / bridge with.
 * Dirt running out while cobble sits in the backpack is not "out of blocks".
 */
public final class PlaceBlocks {

    private PlaceBlocks() {}

    public static final Item[] ALL = {
            Items.COBBLESTONE, Items.DIRT, Items.NETHERRACK, Items.STONE,
            Items.ANDESITE, Items.GRANITE, Items.DIORITE,
            Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
            Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS,
            Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG,
            Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG,
            Items.GRAVEL, Items.SANDSTONE, Items.COBBLESTONE_SLAB,
            Items.BLACKSTONE, Items.BASALT, Items.SOUL_SOIL,
            Items.TERRACOTTA
    };

    public static boolean isPlace(Item it) {
        if (it == null) return false;
        for (Item w : ALL) if (it == w) return true;
        String n = "";
        try { n = it.toString().toLowerCase(); } catch (Throwable ignored) {}
        return n.contains("plank") || n.contains("_log") || n.contains("cobble")
                || n.contains("netherrack") || n.contains("blackstone")
                || n.contains("deepslate") || n.contains("tuff");
    }

    public static int count(AltoClef mod) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = stack(mod, i);
            if (st != null && !st.isEmpty() && isPlace(st.getItem())) n += st.getCount();
        }
        return n;
    }

    public static int hotbarSlot(AltoClef mod) {
        for (int i = 0; i < 9; i++) {
            ItemStack st = stack(mod, i);
            if (st != null && !st.isEmpty() && isPlace(st.getItem())) return i;
        }
        return -1;
    }

    /** Pull a placeable into the hotbar. Returns hotbar slot or -1. */
    public static int equip(AltoClef mod) {
        int hot = hotbarSlot(mod);
        if (hot >= 0) {
            select(mod, hot);
            return hot;
        }
        Item found = null;
        for (int i = 9; i < 36; i++) {
            ItemStack st = stack(mod, i);
            if (st != null && !st.isEmpty() && isPlace(st.getItem())) {
                found = st.getItem();
                break;
            }
        }
        if (found == null) return -1;
        try {
            mod.getSlotHandler().forceEquipItem(found);
        } catch (Throwable ignored) {}
        hot = hotbarSlot(mod);
        if (hot >= 0) {
            select(mod, hot);
            return hot;
        }
        select(mod, 1);
        return hotbarSlot(mod);
    }

    private static ItemStack stack(AltoClef mod, int i) {
        try {
            try {
                return mod.getPlayer().inventory.getStack(i);
            } catch (Throwable t) {
                return mod.getPlayer().getInventory().getStack(i);
            }
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private static void select(AltoClef mod, int slot) {
        try { mod.getPlayer().inventory.selectedSlot = slot; } catch (Throwable ignored) {
            try { mod.getPlayer().getInventory().selectedSlot = slot; } catch (Throwable ignored2) {}
        }
    }
}
