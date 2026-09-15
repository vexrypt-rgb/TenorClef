package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/**
 * Per-tick sensors. Each firing is a unique code so a pasted log
 * tells you the stall without reading 20 minutes of HIST.
 */
public final class T2Probe {

    private static int lastX = Integer.MIN_VALUE;
    private static int lastY;
    private static int lastZ;
    private static int sameXz;
    private static int groundFlips;
    private static boolean lastGround = true;
    private static int wetStill;
    private static int guiTicks;
    private static int childAge;
    private static String lastChild = "";
    private static int lastInv;
    private static long lastPhaseAt;
    private static String lastPhase = "";
    private static int yPeak;

    private T2Probe() {}

    public static void reset() {
        lastX = Integer.MIN_VALUE;
        sameXz = 0;
        groundFlips = 0;
        wetStill = 0;
        guiTicks = 0;
        childAge = 0;
        lastChild = "";
        lastInv = 0;
        lastPhase = "";
        yPeak = 0;
    }

    public static void tick(AltoClef mod, String phase, Task child) {
        if (mod.getPlayer() == null) return;
        int x = mod.getPlayer().getBlockX();
        int y = mod.getPlayer().getBlockY();
        int z = mod.getPlayer().getBlockZ();
        boolean ground = true;
        boolean wet = false;
        try {
            ground = mod.getPlayer().isOnGround();
            wet = mod.getPlayer().isTouchingWater() || mod.getPlayer().isSubmergedInWater();
        } catch (Throwable ignored) {}

        if (x == lastX && z == lastZ) {
            sameXz++;
            if (ground != lastGround) groundFlips++;
        } else {
            sameXz = 0;
            groundFlips = 0;
        }
        lastGround = ground;
        lastX = x;
        lastY = y;
        lastZ = z;
        if (y > yPeak) yPeak = y;

        String childName = child == null ? "-" : child.getClass().getSimpleName();
        if (childName.equals(lastChild)) childAge++;
        else {
            lastChild = childName;
            childAge = 0;
        }

        int inv = hashInv(mod);
        if (inv != lastInv) {
            lastInv = inv;
            childAge = 0;
        }

        if (!phase.equals(lastPhase)) {
            long now = System.currentTimeMillis();
            if (lastPhaseAt > 0 && now - lastPhaseAt < 2000 && !lastPhase.isEmpty()) {
                fire(T2Codes.E112_PHASE_FLAP, "ph " + lastPhase + "->" + phase + " in " + (now - lastPhaseAt) + "ms");
            }
            lastPhase = phase;
            lastPhaseAt = now;
        }

        // E100 jump in place
        if (sameXz > 20 * 4 && groundFlips >= 6 && !wet) {
            fire(T2Codes.E100_JUMP_PLACE, "jumps=" + groundFlips + " @" + x + "," + y + "," + z
                    + " ph=" + phase + " child=" + childName);
            groundFlips = 0;
        }

        // E102 water still
        double spd = 0;
        try {
            var v = mod.getPlayer().getVelocity();
            spd = Math.sqrt(v.x * v.x + v.z * v.z);
        } catch (Throwable ignored) {}
        if (wet && spd < 0.02) wetStill++;
        else wetStill = 0;
        if (wetStill > 20 * 6) {
            fire(T2Codes.E102_WATER_STILL, "spd=" + String.format(java.util.Locale.ROOT, "%.3f", spd)
                    + " @" + x + "," + y + "," + z);
            wetStill = 0;
        }

        // E103 starve
        int hun = 20;
        try { hun = mod.getPlayer().getHungerManager().getFoodLevel(); } catch (Throwable ignored) {}
        boolean food = hasFood(mod);
        if (hun <= 2 && !food) {
            fire(T2Codes.E103_STARVE, "hun=" + hun + " ph=" + phase);
        }

        // E104 GUI
        boolean screen = false;
        try {
            var mc = MinecraftClient.getInstance();
            screen = mc != null && mc.currentScreen != null
                    && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen);
        } catch (Throwable ignored) {}
        if (screen) guiTicks++;
        else guiTicks = 0;
        if (guiTicks > 20 * 8) {
            String sn = "?";
            try { sn = MinecraftClient.getInstance().currentScreen.getClass().getSimpleName(); } catch (Throwable ignored) {}
            fire(T2Codes.E104_GUI, sn + " open " + (guiTicks / 20) + "s ph=" + phase);
            guiTicks = 20 * 4;
        }

        // E105 lava-only construct
        if ("ConstructNetherPortalBucketTask".equals(childName)
                && count(mod, Items.LAVA_BUCKET) >= 1
                && count(mod, Items.WATER_BUCKET) < 1) {
            fire(T2Codes.E105_LAVA_NO_WATER, "construct with lava, no water");
        }

        // E106 forbidden child
        if (childName.contains("UnstickWalk")) {
            fire(T2Codes.E106_UNSTICK_CHILD, "UnstickWalk must not be a child ph=" + phase);
        }

        // E107 portal no ignition
        if ("PORTAL".equals(phase)
                && count(mod, Items.FLINT_AND_STEEL) < 1
                && count(mod, Items.FLINT) < 1
                && childAge > 20 * 20) {
            fire(T2Codes.E107_NO_FLINT, "portal " + (childAge / 20) + "s no flint child=" + childName);
        }

        // E108 table under feet
        try {
            BlockPos feet = mod.getPlayer().getBlockPos();
            if (mod.getWorld().getBlockState(feet).isOf(Blocks.CRAFTING_TABLE)
                    || mod.getWorld().getBlockState(feet.down()).isOf(Blocks.CRAFTING_TABLE)) {
                if (groundFlips >= 2 || sameXz > 20 * 3) {
                    fire(T2Codes.E108_TABLE_FEET, "@" + x + "," + y + "," + z);
                }
            }
        } catch (Throwable ignored) {}

        // E109: iron/stone only. Punching logs with fists is bootstrap.
        if (childName.contains("CollectIron") || childName.contains("Smelt")
                || (childName.contains("Mine") && !childName.contains("Log")
                && phase != null && !"BOOTSTRAP".equals(phase) && !"LOOT".equals(phase))) {
            if (count(mod, Items.WOODEN_PICKAXE) + count(mod, Items.STONE_PICKAXE)
                    + count(mod, Items.IRON_PICKAXE) < 1) {
                fire(T2Codes.E109_NO_TOOL, "mine/collect with no pick child=" + childName);
            }
        }

        // E110 piglin, no gold
        if (WorldHelper.getCurrentDimension() == Dimension.NETHER
                && count(mod, Items.GOLDEN_HELMET) < 1
                && piglinNear(mod)) {
            fire(T2Codes.E110_PIGLIN_NAKED, "piglin near, no gold helm");
        }

        // E111 null child
        if (child == null && !"DONE".equals(phase) && !"END".equals(phase)) {
            fire(T2Codes.E111_NULL_CHILD, "ph=" + phase);
        }

        // E116 hole
        if (yPeak - y >= 8 && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD
                && !"IRON".equals(phase)) {
            fire(T2Codes.E116_HOLE, "drop " + (yPeak - y) + " from " + yPeak + " to " + y + " ph=" + phase);
            yPeak = y;
        }

        // E118 stale child
        if (childAge > 20 * 90 && child != null) {
            fire(T2Codes.E118_CHILD_STALE, childName + " " + (childAge / 20) + "s no inv change ph=" + phase);
            childAge = 20 * 30;
        }

        // E119 portal ignored
        if (!"PORTAL".equals(phase) && !"NETHER".equals(phase) && !"END".equals(phase)
                && !"DONE".equals(phase)
                && count(mod, Items.IRON_PICKAXE) >= 1) {
            try {
                if (mod.getBlockScanner().anyFound(Blocks.NETHER_PORTAL)) {
                    fire(T2Codes.E119_PORTAL_IGNORED, "portal exists ph=" + phase);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void fire(String code, String msg) {
        T2Log.warn(code, msg);
        T2History.note("[" + code + "] " + msg);
    }

    private static int count(AltoClef mod, net.minecraft.item.Item item) {
        try {
            return mod.getItemStorage().getItemCount(item);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean hasFood(AltoClef mod) {
        return count(mod, Items.BREAD) + count(mod, Items.APPLE) + count(mod, Items.COOKED_BEEF)
                + count(mod, Items.COOKED_PORKCHOP) + count(mod, Items.COOKED_CHICKEN)
                + count(mod, Items.CARROT) + count(mod, Items.BAKED_POTATO) > 0;
    }

    private static boolean piglinNear(AltoClef mod) {
        try {
            var list = mod.getEntityTracker().getTrackedEntities(PiglinEntity.class);
            if (list == null || mod.getPlayer() == null) return false;
            var me = mod.getPlayer().getPos();
            for (var e : list) {
                if (e != null && e.isAlive() && me.distanceTo(e.getPos()) < 16) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static int hashInv(AltoClef mod) {
        return count(mod, Items.OAK_LOG) + count(mod, Items.IRON_INGOT) * 3
                + count(mod, Items.IRON_PICKAXE) * 17 + count(mod, Items.WATER_BUCKET) * 23
                + count(mod, Items.LAVA_BUCKET) * 29 + count(mod, Items.BLAZE_ROD) * 31
                + count(mod, Items.ENDER_PEARL) * 37 + count(mod, Items.ENDER_EYE) * 41;
    }
}
