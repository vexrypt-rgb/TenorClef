package adris.altoclef.tasks.speedrun.testrun2.compat;

import adris.altoclef.Debug;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modded swords/armor: read the item registry + attribute modifiers.
 * Plugins can call {@link #registerDamage} for weird items.
 * Does not require a jar loader — Fabric already registered the Item.
 */
public final class ModCompat {

    private static final Map<Item, Double> DAMAGE = new ConcurrentHashMap<>();
    private static boolean scanned;

    private ModCompat() {}

    public static void registerDamage(Item item, double attackDamage) {
        if (item != null) DAMAGE.put(item, attackDamage);
    }

    public static double attackDamage(Item item) {
        if (item == null) return 1;
        Double d = DAMAGE.get(item);
        if (d != null) return d;
        scanOnce();
        d = DAMAGE.get(item);
        if (d != null) return d;
        return probeStack(new ItemStack(item));
    }

    public static boolean isArmor(Item item) {
        if (item == null) return false;
        try {
            return Class.forName("net.minecraft.item.ArmorItem").isInstance(item);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isToolish(Item item) {
        if (item == null) return false;
        String n = item.getClass().getName().toLowerCase();
        if (n.contains("sword") || n.contains("axe") || n.contains("tool")
                || n.contains("dagger") || n.contains("spear") || n.contains("halberd")) {
            return true;
        }
        try {
            return Class.forName("net.minecraft.item.SwordItem").isInstance(item)
                    || Class.forName("net.minecraft.item.ToolItem").isInstance(item);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void scanOnce() {
        if (scanned) return;
        scanned = true;
        try {
            Object reg = registry();
            if (reg == null) return;
            Iterable<?> it;
            try {
                it = (Iterable<?>) reg.getClass().getMethod("iterator").invoke(reg);
            } catch (Throwable t) {
                return;
            }
            int n = 0;
            for (Object o : it) {
                if (!(o instanceof Item item)) continue;
                if (!isToolish(item) && !isArmor(item)) continue;
                double dmg = probeStack(new ItemStack(item));
                if (dmg > 1.05) DAMAGE.put(item, dmg);
                n++;
            }
            Debug.logMessage("MODCOMPAT scanned " + n + " tool/armor items");
        } catch (Throwable t) {
            Debug.logWarning("MODCOMPAT scan " + t.getMessage());
        }
    }

    private static Object registry() {
        try {
            Class<?> r = Class.forName("net.minecraft.registry.Registries");
            return r.getField("ITEM").get(null);
        } catch (Throwable ignored) {}
        try {
            Class<?> r = Class.forName("net.minecraft.util.registry.Registry");
            return r.getField("ITEM").get(null);
        } catch (Throwable ignored) {}
        return null;
    }

    /** Vanilla + most Fabric mods put attack damage on the stack attributes. */
    private static double probeStack(ItemStack stack) {
        try {
            var attrs = stack.getAttributeModifiers(
                    net.minecraft.entity.EquipmentSlot.MAINHAND);
            for (var e : attrs.entries()) {
                String key = String.valueOf(e.getKey()).toLowerCase();
                if (!key.contains("attack_damage") && !key.contains("attackdamage")) continue;
                Object v = e.getValue();
                try {
                    double base = ((Number) v.getClass().getMethod("getValue").invoke(v)).doubleValue();
                    return 1.0 + base;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return 1.0;
    }
}
