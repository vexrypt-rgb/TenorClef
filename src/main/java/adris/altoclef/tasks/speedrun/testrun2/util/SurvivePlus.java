package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** Totem / gapple when HP is low. Eat stays in SurviveTick. */
public final class SurvivePlus {

    private SurvivePlus() {}

    private static int cool;

    public static void tick(AltoClef mod) {
        if (cool > 0) {
            cool--;
            return;
        }
        PlayerEntity me = mod.getPlayer();
        if (me == null) return;
        float hp = 20f;
        try { hp = me.getHealth(); } catch (Throwable ignored) {}
        if (hp > 8f) return;
        Item prefer = null;
        try {
            if (mod.getItemStorage().hasItem(Items.TOTEM_OF_UNDYING)) prefer = Items.TOTEM_OF_UNDYING;
            else if (hp <= 6f && mod.getItemStorage().hasItem(Items.ENCHANTED_GOLDEN_APPLE))
                prefer = Items.ENCHANTED_GOLDEN_APPLE;
            else if (hp <= 6f && mod.getItemStorage().hasItem(Items.GOLDEN_APPLE))
                prefer = Items.GOLDEN_APPLE;
        } catch (Throwable ignored) {}
        if (prefer == null) return;
        cool = 40;
        try {
            if (prefer == Items.TOTEM_OF_UNDYING) {
                // offhand if the API exists; otherwise forceEquip
                mod.getSlotHandler().getClass().getMethod("forceEquipItemToOffhand", Item.class)
                        .invoke(mod.getSlotHandler(), prefer);
            } else {
                mod.getSlotHandler().getClass().getMethod("forceEquipItem", Item.class)
                        .invoke(mod.getSlotHandler(), prefer);
            }
        } catch (Throwable t) {
            try {
                mod.getSlotHandler().getClass().getMethod("forceEquipItem", Item.class)
                        .invoke(mod.getSlotHandler(), prefer);
            } catch (Throwable ignored) {}
        }
    }
}
