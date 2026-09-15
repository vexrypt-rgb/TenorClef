package adris.altoclef.tasks.speedrun.testrun2.compat;

import net.minecraft.item.Item;

/**
 * Optional hook for a later jar loader. A mod can implement this
 * and call {@link ModCompat#registerDamage} for items that do not
 * use vanilla attribute modifiers (guns, magic staves, …).
 */
public interface TenorPlugin {

    String id();

    default void registerItems() {}

    default void registerDamage(Item item, double attackDamage) {
        ModCompat.registerDamage(item, attackDamage);
    }
}
