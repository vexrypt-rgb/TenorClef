package adris.altoclef.allblocks;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ostinato: #allblocks survival kit, checked before every gather. In order:
 * <ol>
 *   <li>A pickaxe with life left. Copper first, then iron, then stone. A wooden pickaxe does not count, so the run
 *   never digs stone bare-handed or with a pick that is about to break.</li>
 *   <li>A sword: copper, then iron, then stone. Night mobs killed every early run that had none.</li>
 *   <li>Food in the inventory (food score {@value #FOOD_MIN} or more).</li>
 *   <li>A bed (unless one is placed nearby), for the night: fetching wool after dark got the bot killed.</li>
 *   <li>Armor in every slot: copper, then iron, then leather.</li>
 * </ol>
 * Each need gets {@value #NEED_MINUTES} minutes; a need that runs out of time cools down for
 * {@value #COOLDOWN_MINUTES} minutes and the next choice is tried. Only logs are punched by hand, to make the first
 * pickaxe.
 */
final class AllBlocksKit {

    static final int FOOD_MIN = 10;
    static final int NEED_MINUTES = 12;
    static final int COOLDOWN_MINUTES = 30;
    private static final int TICKS_PER_MINUTE = 20 * 60;
    private static final double MIN_DURABILITY = 0.12;

    private static final String[] PICKS = {"copper_pickaxe", "iron_pickaxe", "stone_pickaxe"};
    private static final String[] GOOD_PICKS = {"netherite_pickaxe", "diamond_pickaxe", "iron_pickaxe", "copper_pickaxe"};
    private static final String[] SWORDS = {"copper_sword", "iron_sword", "stone_sword"};
    private static final String[] ANY_SWORD = {"netherite_sword", "diamond_sword", "iron_sword", "copper_sword", "stone_sword", "golden_sword"};
    private static final String[] ARMOR_TIERS = {"copper", "iron", "leather"};
    private static final String[] SLOTS = {"helmet", "chestplate", "leggings", "boots"};
    private static final String[] ALL_TIERS = {"netherite", "diamond", "iron", "copper", "chainmail", "golden", "leather", "turtle"};

    private final Map<String, Long> cooldownUntil = new HashMap<>();
    private String need;
    private Task needTask;
    private long needStart;

    /** The task for the first unmet need, or null when the kit is complete (or every missing piece is cooling down). */
    Task tick(AltoClef mod, long now) {
        if (needTask != null) {
            boolean met = met(mod, need);
            if (met || needTask.isFinished() || now - needStart > (long) NEED_MINUTES * TICKS_PER_MINUTE) {
                if (!met) {
                    cooldownUntil.put(need, now + (long) COOLDOWN_MINUTES * TICKS_PER_MINUTE);
                    Debug.logMessage("ALLBLOCKS kit: gave up on " + need + " for " + COOLDOWN_MINUTES + " min");
                } else {
                    Debug.logMessage("ALLBLOCKS kit: " + need + " ok");
                    // Not asked again for a minute: a need that flickers between met and unmet must not stop the bot
                    // every tick.
                    cooldownUntil.put(need, now + TICKS_PER_MINUTE);
                }
                need = null;
                needTask = null;
            } else {
                return needTask;
            }
        }
        String next = nextNeed(mod, now);
        if (next == null) return null;
        Task t = make(next);
        if (t == null) {
            cooldownUntil.put(next, now + (long) COOLDOWN_MINUTES * TICKS_PER_MINUTE);
            Debug.logMessage("ALLBLOCKS kit: no task for " + next);
            return null;
        }
        need = next;
        needTask = t;
        needStart = now;
        Debug.logMessage("ALLBLOCKS kit: getting " + next);
        return t;
    }

    /** Holds the running need's timer (the bot is asleep or otherwise not working on it). */
    void hold() {
        if (needTask != null) needStart++;
    }

    String current() {
        return need;
    }

    private String nextNeed(AltoClef mod, long now) {
        if (!hasPick(mod, GOOD_PICKS)) {
            for (String p : PICKS) {
                if (p.equals("stone_pickaxe") && hasPick(mod, new String[]{"stone_pickaxe"})) break;
                if (ready(p, now) && TaskCatalogue.taskExists(p)) return p;
            }
        }
        if (!hasPick(mod, ANY_SWORD)) {
            for (String sw : SWORDS) {
                if (ready(sw, now) && TaskCatalogue.taskExists(sw)) return sw;
            }
        }
        if (StorageHelper.calculateInventoryFoodScore() < FOOD_MIN && ready("food", now)) return "food";
        if (!hasBed(mod) && ready("bed", now) && TaskCatalogue.taskExists("bed")) return "bed";
        for (String slot : SLOTS) {
            if (wearing(slot)) continue;
            for (String tier : ARMOR_TIERS) {
                String piece = "equip:" + tier + "_" + slot;
                if (ready(piece, now) && TaskCatalogue.taskExists(tier + "_" + slot)) return piece;
            }
        }
        return null;
    }

    private boolean ready(String key, long now) {
        return cooldownUntil.getOrDefault(key, 0L) <= now;
    }

    private static Task make(String key) {
        if (key.equals("food")) return new CollectFoodTask(FOOD_MIN * 3);
        if (key.startsWith("equip:")) return new EquipArmorTask(new ItemTarget(key.substring(6), 1));
        return TaskCatalogue.getItemTask(key, 1);
    }

    private static boolean met(AltoClef mod, String key) {
        if (key.equals("food")) return StorageHelper.calculateInventoryFoodScore() >= FOOD_MIN;
        if (key.equals("bed")) return hasBed(mod);
        if (key.startsWith("equip:")) return wearing(key.substring(key.lastIndexOf('_') + 1));
        return hasPick(mod, new String[]{key});
    }

    /**
     * A bed carried. A placed one does not count for the kit: "within 24 blocks" flickered as the bot walked past the
     * edge, and each flip restarted the kit and stopped the bot in its tracks. (The night step still sleeps in a
     * placed bed close by.)
     */
    static boolean hasBed(AltoClef mod) {
        return mod.getItemStorage().hasItem(adris.altoclef.util.helpers.ItemHelper.BED);
    }

    /** A tool of one of these kinds with more than {@value #MIN_DURABILITY} of its durability left. */
    static boolean hasPick(AltoClef mod, String[] kinds) {
        for (String k : kinds) {
            Item item = McCompat.item(k);
            if (item == null) continue;
            for (int i = 0; i < mod.getPlayer().getInventory().size(); i++) {
                ItemStack st = mod.getPlayer().getInventory().getStack(i);
                if (st.getItem() != item) continue;
                if (st.getMaxDamage() <= 0 || 1.0 - (double) st.getDamage() / st.getMaxDamage() > MIN_DURABILITY) return true;
            }
        }
        return false;
    }

    static boolean wearing(String slot) {
        List<Item> any = new ArrayList<>();
        for (String tier : ALL_TIERS) any.add(McCompat.item(tier + "_" + slot));
        if (slot.equals("helmet")) any.add(McCompat.item("turtle_helmet"));
        return StorageHelper.isArmorEquipped(any.stream().filter(Objects::nonNull).toArray(Item[]::new));
    }

    /** One line for status: what the kit lacks right now. */
    static String summary(AltoClef mod) {
        StringBuilder sb = new StringBuilder();
        sb.append(hasPick(mod, GOOD_PICKS) ? "pick ok" : hasPick(mod, new String[]{"stone_pickaxe"}) ? "stone pick" : "NO pick");
        sb.append(", food ").append(StorageHelper.calculateInventoryFoodScore());
        int armor = 0;
        for (String s : SLOTS) if (wearing(s)) armor++;
        sb.append(", armor ").append(armor).append("/4");
        return sb.toString();
    }
}
