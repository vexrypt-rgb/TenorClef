package adris.altoclef.tasks.speedrun.testrun2.combat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vanilla 1.21-ish attack damage for anything the speedrun might hold.
 * Fists are 1. A netherite hoe still beats a stick.
 *
 * Attack speed is folded in as a DPS score so an iron axe is not always
 * preferred over an iron sword (axe hits harder, sword hits more often).
 */
public final class WeaponPicker {

    private WeaponPicker() {}

    private static final Map<Item, Stats> TABLE = new LinkedHashMap<>();

    static {
        // swords: dmg / speed 1.6
        add(Items.NETHERITE_SWORD, 8, 1.6);
        add(Items.DIAMOND_SWORD, 7, 1.6);
        add(Items.IRON_SWORD, 6, 1.6);
        add(Items.STONE_SWORD, 5, 1.6);
        add(Items.GOLDEN_SWORD, 4, 1.6);
        add(Items.WOODEN_SWORD, 4, 1.6);
        // axes: slower
        add(Items.NETHERITE_AXE, 10, 1.0);
        add(Items.DIAMOND_AXE, 9, 1.0);
        add(Items.IRON_AXE, 9, 0.9);
        add(Items.STONE_AXE, 9, 0.8);
        add(Items.GOLDEN_AXE, 7, 1.0);
        add(Items.WOODEN_AXE, 7, 0.8);
        // picks
        add(Items.NETHERITE_PICKAXE, 6, 1.2);
        add(Items.DIAMOND_PICKAXE, 5, 1.2);
        add(Items.IRON_PICKAXE, 4, 1.2);
        add(Items.STONE_PICKAXE, 3, 1.2);
        add(Items.GOLDEN_PICKAXE, 2, 1.2);
        add(Items.WOODEN_PICKAXE, 2, 1.2);
        // shovels
        add(Items.NETHERITE_SHOVEL, 6.5, 1.0);
        add(Items.DIAMOND_SHOVEL, 5.5, 1.0);
        add(Items.IRON_SHOVEL, 4.5, 1.0);
        add(Items.STONE_SHOVEL, 3.5, 1.0);
        add(Items.GOLDEN_SHOVEL, 2.5, 1.0);
        add(Items.WOODEN_SHOVEL, 2.5, 1.0);
        // hoes — barely a weapon
        add(Items.NETHERITE_HOE, 1, 4.0);
        add(Items.DIAMOND_HOE, 1, 4.0);
        add(Items.IRON_HOE, 1, 3.0);
        add(Items.STONE_HOE, 1, 2.0);
        add(Items.GOLDEN_HOE, 1, 1.0);
        add(Items.WOODEN_HOE, 1, 1.0);
        // misc
        add(Items.TRIDENT, 9, 1.1);
        Item mace = adris.altoclef.tasks.speedrun.testrun2.McCompat.item("MACE");
        if (mace != null) add(mace, 6, 0.6);
        add(Items.FLINT_AND_STEEL, 1, 4.0);
    }

    private static void add(Item item, double damage, double speed) {
        TABLE.put(item, new Stats(damage, speed, damage * speed));
    }

    public static Stats stats(Item item) {
        return TABLE.getOrDefault(item, new Stats(1, 4.0, 4.0)); // fist
    }

    public static double dps(Item item) {
        if (!TABLE.containsKey(item)) {
            try {
                double d = adris.altoclef.tasks.speedrun.testrun2.compat.ModCompat.attackDamage(item);
                if (d > 1.05) return d * 1.4;
            } catch (Throwable ignored) {}
        }
        return stats(item).dps;
    }

    public static boolean isKnownWeapon(Item item) {
        return TABLE.containsKey(item);
    }

    public static boolean isAxe(Item item) {
        return item == Items.WOODEN_AXE || item == Items.STONE_AXE || item == Items.IRON_AXE
                || item == Items.GOLDEN_AXE || item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE;
    }

    public record Stats(double damage, double speed, double dps) {}
}
