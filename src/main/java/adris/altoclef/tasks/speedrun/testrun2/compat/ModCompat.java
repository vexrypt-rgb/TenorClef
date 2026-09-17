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

    /**
     * Vanilla + most Fabric mods put attack damage on the stack attributes.
     * Uses reflection so 1.20.5+ (no getAttributeModifiers(EquipmentSlot)) still compiles.
     */
    private static double probeStack(ItemStack stack) {
        try {
            Class<?> slotCl = Class.forName("net.minecraft.entity.EquipmentSlot");
            Object main = Enum.valueOf((Class<Enum>) (Class<?>) slotCl, "MAINHAND");
            Object attrs = stack.getClass()
                    .getMethod("getAttributeModifiers", slotCl)
                    .invoke(stack, main);
            if (attrs != null) {
                Iterable<?> entries;
                try {
                    entries = (Iterable<?>) attrs.getClass().getMethod("entries").invoke(attrs);
                } catch (Throwable t) {
                    entries = (Iterable<?>) attrs;
                }
                for (Object e : entries) {
                    Object key;
                    Object val;
                    try {
                        key = e.getClass().getMethod("getKey").invoke(e);
                        val = e.getClass().getMethod("getValue").invoke(e);
                    } catch (Throwable t) {
                        continue;
                    }
                    String ks = String.valueOf(key).toLowerCase();
                    if (!ks.contains("attack_damage") && !ks.contains("attackdamage")) continue;
                    try {
                        double base = ((Number) val.getClass().getMethod("getValue").invoke(val)).doubleValue();
                        return 1.0 + base;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        // 1.20.5+ component dump fallback
        try {
            Object comps = stack.getClass().getMethod("getComponents").invoke(stack);
            String s = String.valueOf(comps).toLowerCase();
            if (s.contains("attack_damage") || s.contains("attackdamage")) {
                // best-effort: leave default if we cannot parse a number
            }
        } catch (Throwable ignored) {}
        return 1.0;
    }
}