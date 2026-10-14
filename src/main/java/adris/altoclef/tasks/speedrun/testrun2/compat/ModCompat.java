package adris.altoclef.tasks.speedrun.testrun2.compat;

import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.item.Item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attack damage of weapons the vanilla table in WeaponPicker does not list, such as modded swords.
 * Plugins can call {@link #registerDamage} for items whose damage the game does not expose.
 */
public final class ModCompat {

    private static final Map<Item, Double> DAMAGE = new ConcurrentHashMap<>();

    private ModCompat() {}

    public static void registerDamage(Item item, double attackDamage) {
        if (item != null) DAMAGE.put(item, attackDamage);
    }

    /**
     * Damage of one hit with {@code item}; 1 is a bare fist. Before 1.21.2 only the tool material's share
     * is known, which still ranks the tiers correctly.
     */
    public static double attackDamage(Item item) {
        if (item == null) return 1;
        Double registered = DAMAGE.get(item);
        return registered != null ? registered : 1.0 + ItemHelper.meleeDamageOf(item);
    }
}
