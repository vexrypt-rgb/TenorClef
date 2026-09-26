package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.item.Items;

import java.util.ArrayDeque;

/**
 * Ring buffer of what the bot just did. Printed on child/phase/dim change
 * and dumped by {@code @t2status}. Codes stay on one line for grep.
 */
public final class T2History {

    private static final int MAX = 80;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static String lastChild = "";
    private static String lastPhase = "";
    private static String lastDim = "";
    private static int lastX, lastY, lastZ;
    private static int ticks;
    private static String lastNote = "";
    private static final java.util.Map<String, Long> NOTE_AT = new java.util.HashMap<>();

    private T2History() {}

    public static void tick(AltoClef mod, String phase, Task child) {
        ticks++;
        if (mod.getPlayer() == null) return;
        String childName = child == null ? "-" : child.getClass().getSimpleName();
        String dim = "?";
        try {
            dim = String.valueOf(WorldHelper.getCurrentDimension());
        } catch (Throwable ignored) {}
        int x = mod.getPlayer().getBlockX();
        int y = mod.getPlayer().getBlockY();
        int z = mod.getPlayer().getBlockZ();

        if (!phase.equals(lastPhase)) {
            push("PHASE " + lastPhase + " -> " + phase);
            lastPhase = phase;
        }
        if (!dim.equals(lastDim)) {
            push("DIM " + lastDim + " -> " + dim + " at " + x + "," + y + "," + z);
            lastDim = dim;
        }
        if (!childName.equals(lastChild)) {
            boolean portalNoise =
                    lastChild.contains("Construct") && (childName.contains("GetTo") || childName.contains("Enter") || childName.contains("Craft"))
                    || lastChild.contains("Craft") && childName.contains("Construct")
                    || lastChild.contains("Enter") && (childName.contains("Construct") || childName.contains("Surface"))
                    || lastChild.contains("Surface") && childName.contains("Enter")
                    || lastChild.contains("GetTo") && childName.contains("Construct");
            // A->B->A ping-pong: count it instead of printing 20 CHILD lines a second.
            boolean flip = childName.equals(prevChild);
            if (flip) {
                if (flipCount++ == 0) flipStartTick = ticks;
                if (flipCount % 100 == 0) push("FLIP " + lastChild + "<->" + childName + " x" + flipCount + "/"
                        + (ticks - flipStartTick) / 20 + "s " + snapshot(mod, phase, x, y, z));
            } else {
                if (flipCount > 0) push("FLIP ended x" + flipCount);
                flipCount = 0;
                if (!portalNoise) {
                    push("CHILD " + lastChild + " -> " + childName
                            + " " + snapshot(mod, phase, x, y, z));
                }
            }
            prevChild = lastChild;
            lastChild = childName;
        }
        int dx = x - lastX, dy = y - lastY, dz = z - lastZ;
        if (ticks > 20 && (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 24)) {
            push("JUMP " + lastX + "," + lastY + "," + lastZ
                    + " -> " + x + "," + y + "," + z);
        }
        lastX = x;
        lastY = y;
        lastZ = z;

        if (ticks % (20 * 5) == 0) {
            push("SNAP " + snapshot(mod, phase, x, y, z)
                    + " child=" + childName
                    + " dbg=" + (child == null ? "-" : child.toString())
                    + " " + mobs(mod));
        }
    }

    private static String prevChild = "";
    private static int flipCount, flipStartTick;

    public static void note(String msg) {
        long now = System.currentTimeMillis();
        Long prev = NOTE_AT.get(msg);
        if (msg.equals(lastNote) || (prev != null && now - prev < 4000)) return;
        NOTE_AT.put(msg, now);
        lastNote = msg;
        push(msg);
    }

    public static String dump() {
        StringBuilder sb = new StringBuilder("T2 [HIST] last ");
        sb.append(LINES.size()).append(" events:\n");
        for (String line : LINES) {
            sb.append("  ").append(line).append('\n');
        }
        String out = sb.toString();
        Debug.logMessage(out);
        return out;
    }

    public static void reset() {
        LINES.clear();
        lastChild = "";
        lastPhase = "";
        lastDim = "";
        ticks = 0;
        lastNote = "";
    }

    private static void push(String msg) {
        String line = SpeedrunClock.now() + " " + msg;
        if (LINES.size() >= MAX) LINES.removeFirst();
        LINES.addLast(line);
        Debug.logMessage("T2 [HIST] " + line);
    }

    private static String snapshot(AltoClef mod, String phase, int x, int y, int z) {
        int pick = 0, buck = 0, iron = 0, ironOre = 0, rods = 0, pearls = 0, eyes = 0;
        try {
            pick = mod.getItemStorage().getItemCount(Items.IRON_PICKAXE);
            buck = mod.getItemStorage().getItemCount(Items.BUCKET)
                    + mod.getItemStorage().getItemCount(Items.WATER_BUCKET)
                    + mod.getItemStorage().getItemCount(Items.LAVA_BUCKET);
            // TRAP (fixed 2026-09-20). This used to be `IRON_INGOT + RAW_IRON` under the
            // label "iron=", while ModernSpeedrunTask's T2 [NOW] line prints `iron=` as
            // IRON_INGOT alone. On 1.16.1 the preprocessor rewrites RAW_IRON -> IRON_ORE,
            // so the SAME field name meant two different things one line apart:
            //   T2 [HIST] ... SNAP ... iron=19     <- 19 raw ore, 0 ingots
            //   T2 [NOW]  ... iron=0               <- the actual ingot count
            // Reading the SNAP line as "19 ingots" makes a stalled IRON phase look
            // healthy and sends you hunting a phantom bug. Keep `iron=` ingots ONLY, and
            // report unmelted ore separately as `ore=`.
            iron = mod.getItemStorage().getItemCount(Items.IRON_INGOT);
            ironOre = mod.getItemStorage().getItemCount(Items.IRON_ORE);
            rods = mod.getItemStorage().getItemCount(Items.BLAZE_ROD);
            pearls = mod.getItemStorage().getItemCount(Items.ENDER_PEARL);
            eyes = mod.getItemStorage().getItemCount(Items.ENDER_EYE);
        } catch (Throwable ignored) {}
        int sky = -1;
        try {
            sky = mod.getWorld().getLightLevel(net.minecraft.world.LightType.SKY, mod.getPlayer().getBlockPos());
        } catch (Throwable ignored) {}
        boolean wet = false, ground = true;
        try {
            wet = mod.getPlayer().isTouchingWater();
            ground = mod.getPlayer().isOnGround();
        } catch (Throwable ignored) {}
        return "ph=" + phase
                + " @" + x + "," + y + "," + z
                + " sky=" + sky
                + " wet=" + wet
                + " ground=" + ground
                + " pick=" + pick + " buck=" + buck
                + " iron=" + iron + " ore=" + ironOre
                + " rods=" + rods + " pearls=" + pearls + " eyes=" + eyes;
    }

    private static String mobs(AltoClef mod) {
        StringBuilder sb = new StringBuilder("mobs=");
        try {
            var list = mod.getEntityTracker().getTrackedEntities(net.minecraft.entity.mob.HostileEntity.class);
            if (list == null || list.isEmpty() || mod.getPlayer() == null) return "mobs=none";
            var me = mod.getPlayer().getPos();
            int n = 0;
            for (var e : list) {
                if (e == null || !e.isAlive()) continue;
                double d = me.distanceTo(e.getPos());
                if (d > 24) continue;
                if (n > 0) sb.append(',');
                sb.append(e.getClass().getSimpleName().replace("Entity", ""))
                        .append('@')
                        .append(String.format(java.util.Locale.ROOT, "%.1f", d));
                if (++n >= 4) break;
            }
            if (n == 0) return "mobs=none";
        } catch (Throwable t) {
            return "mobs=?";
        }
        return sb.toString();
    }
}
