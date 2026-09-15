package adris.altoclef.tasks.speedrun.testrun2.combat;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.speedrun.testrun2.SpeedrunOpt;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 1.9+ attribute swap used in modern MC PvP:
 *
 *   Attack cooldown is a player attribute that recharges at the rate of the
 *   *currently held* item's attack-speed. Attack *damage* is read from the
 *   item in the hand when the attack packet is processed.
 *
 *   So: hold a fast item (sword / hoe) until the bar is full, swap to a heavy
 *   item (axe / mace / netherite whatever) and click the same tick.
 *   Result is axe-tier damage on a sword-tier cooldown.
 *
 *   If only one item exists we just hit with it. If we have a mace and enough
 *   fall distance, we swap to the mace instead (smash bonus dwarfs the swap).
 *
 *   This is vanilla packet order, not a mixin. Some multiplayer anticheats
 *   still treat rapid hotbar swaps as suspicious — fine for singleplayer RSG.
 */
public final class AttributeSwap {

    private AttributeSwap() {}

    public record Pair(Item charger, Item hitter, boolean same) {}

    public static Pair plan(AltoClef mod) {
        Item charger = Items.AIR;
        Item hitter = Items.AIR;
        double bestSpeed = -1;
        double bestDmg = -1;
        try {
            for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
                if (stack == null || stack.isEmpty()) continue;
                Item it = stack.getItem();
                WeaponPicker.Stats s = WeaponPicker.stats(it);
                if (s.speed() > bestSpeed) {
                    bestSpeed = s.speed();
                    charger = it;
                }
                if (s.damage() > bestDmg) {
                    bestDmg = s.damage();
                    hitter = it;
                }
            }
        } catch (Throwable ignored) {}
        if (hitter == Items.AIR) hitter = charger;
        if (charger == Items.AIR) charger = hitter;
        return new Pair(charger, hitter, charger == hitter);
    }

    public static boolean shouldMaceSmash(AltoClef mod, PlayerEntity me) {
        if (!SpeedrunOpt.MACE_SMASH) return false;
        try {
            Item mace = adris.altoclef.tasks.speedrun.testrun2.McCompat.item("MACE");
            if (mace == null || !mod.getItemStorage().hasItem(mace)) return false;
        } catch (Throwable t) {
            return false;
        }
        // Smash bonus starts mattering around 1.5+ blocks of fall.
        return !me.isOnGround() && me.fallDistance >= 1.5f;
    }

    public static void equip(AltoClef mod, Item item) {
        if (item == null || item == Items.AIR) return;
        try {
            var handler = mod.getSlotHandler();
            handler.getClass().getMethod("forceEquipItem", Item.class).invoke(handler, item);
        } catch (Throwable t) {
            try {
                mod.getSlotHandler().getClass()
                        .getMethod("forceEquipItem", Item[].class)
                        .invoke(mod.getSlotHandler(), (Object) new Item[]{item});
            } catch (Throwable ignored) {}
        }
    }
}
