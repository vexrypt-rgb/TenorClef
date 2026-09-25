package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.item.Items;

/**
 * One-line diagnostics. Codes are stable — paste T2-Exx when a run dies.
 *
 * Ixx info    Wxx warn    Exx error (bot stuck / wrong action)
 * E10 water   E20 combat  E30 stall   E40 closer
 * E50 death   E60 portal  E70 throw   E80 phase-restart
 */
public final class T2Log {

    private static String last = "";
    private static int lastTick;
    private static final java.util.Map<String, Long> CODE_AT = new java.util.HashMap<>();

    private T2Log() {}

    public static void info(String code, String msg) {
        once(code, msg, false);
    }

    public static void warn(String code, String msg) {
        once(code, msg, true);
    }

    /** State-change diag: only suppress exact duplicate line (not whole code for 5s). */
    public static void force(String code, String msg) {
        String line = "T2 [" + code + "] " + msg;
        if (line.equals(last)) return;
        last = line;
        CODE_AT.put(code, System.currentTimeMillis());
        Debug.logWarning(line);
        T2Fault.record(code, msg);
        T2History.note("FORCE " + code + " " + msg);
    }

    private static void once(String code, String msg, boolean warn) {
        String line = "T2 [" + code + "] " + msg;
        long now = System.currentTimeMillis();
        Long prev = CODE_AT.get(code);
        if (line.equals(last) || (prev != null && now - prev < 5000)) return;
        CODE_AT.put(code, now);
        last = line;
        if (warn) {
            Debug.logWarning(line);
            T2Fault.record(code, msg);
        } else {
            Debug.logMessage(line);
        }
    }

    public static String pulse(AltoClef mod, String phase, Task child, int tick) {
        if (mod.getPlayer() == null) return phase;
        String dim = "?";
        try {
            dim = String.valueOf(WorldHelper.getCurrentDimension());
        } catch (Throwable ignored) {}
        int y = mod.getPlayer().getBlockY();
        int hp = (int) mod.getPlayer().getHealth();
        int hunger = 0;
        try {
            hunger = mod.getPlayer().getHungerManager().getFoodLevel();
        } catch (Throwable ignored) {}
        String childName = child == null ? "-" : child.getClass().getSimpleName();
        boolean wet = false;
        try {
            wet = mod.getPlayer().isSubmergedInWater();
        } catch (Throwable ignored) {}
        // `iron=` must mean IRON_INGOT and nothing else, in EVERY emitter, because
        // `T2 [NOW]` uses that definition. The old form added RAW_IRON, which the
        // preprocessor rewrites to IRON_ORE on 1.16.1, so the same label meant
        // "ingots" on one line and "ingots + unmelted ore" on the next — see trap 9
        // in the project memory. Ore gets its own label.
        int iron = mod.getItemStorage().getItemCount(Items.IRON_INGOT);
        int ore = mod.getItemStorage().getItemCount(Items.IRON_ORE);
        int pick = mod.getItemStorage().getItemCount(Items.IRON_PICKAXE);
        int water = mod.getItemStorage().getItemCount(Items.WATER_BUCKET);
        int lava = mod.getItemStorage().getItemCount(Items.LAVA_BUCKET);
        int empty = mod.getItemStorage().getItemCount(Items.BUCKET);
        int flint = mod.getItemStorage().getItemCount(Items.FLINT)
                + mod.getItemStorage().getItemCount(Items.FLINT_AND_STEEL);
        lastTick = tick;
        return "T2 [I02] t=" + SpeedrunClock.now()
                + " ph=" + phase
                + " dim=" + dim
                + " y=" + y
                + " hp=" + hp
                + " hun=" + hunger
                + " wet=" + wet
                + " iron=" + iron
                + " ore=" + ore
                + " pick=" + pick
                + " w=" + water + " l=" + lava + " b=" + empty
                + " fns=" + flint
                + " child=" + childName;
    }
}
