package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.GetOutOfWaterTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/**
 * Tiny rule solver. Probe names the problem; this picks one fix.
 *
 * Overlay-first (close GUI, walk, swim) so we never replace
 * {@code ConstructNetherPortalBucketTask} with UnstickWalk.
 * A real child is only returned when the current goal is wrong
 * (no water for a lava cast, starving, piglin with no gold).
 */
public final class T2Solve {

    private static String lastFix = "";
    private static int cool;
    private static int sameXz;
    private static int lastX = Integer.MIN_VALUE;
    private static int lastZ;
    private static int flips;
    private static boolean lastGround = true;
    private static int guiAge;

    private T2Solve() {}

    public static void reset() {
        lastFix = "";
        cool = 0;
        sameXz = 0;
        lastX = Integer.MIN_VALUE;
        flips = 0;
        guiAge = 0;
        try { adris.altoclef.tasks.speedrun.testrun2.core.T2Input.releaseAll(); } catch (Throwable ignored) {}
    }

    public static Task tick(AltoClef mod, String phase, Task child) {
        if (mod.getPlayer() == null) return null;
        HolePillar.coolTick();
        if (HolePillar.consumeSameXzReset()) {
            sameXz = 0;
            flips = 0;
        }
        if (cool > 0) cool--;

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
            if (ground != lastGround) flips++;
        } else {
            sameXz = 0;
            flips = 0;
        }
        lastX = x;
        lastZ = z;
        lastGround = ground;

        String childName = child == null ? "-" : child.getClass().getSimpleName();
        boolean portalWork = "PORTAL".equals(phase) && childName.contains("Construct");

        // 1. GUI. Craft/furnace stay open UNLESS we are jump-stuck on the table.
        if (guiOpen()) {
            guiAge++;
            if (workGui()) {
                if (!wet && (flips >= 4 || sameXz > 20 * 4 || guiAge > 20 * 12)) {
                    act("S120", "craft-gui jump @" + x + "," + y + "," + z + " child=" + childName);
                    McCompat.closeScreen();
                    adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
                    adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
                    cancelPath(mod);
                    guiAge = 0;
                    flips = 0;
                }
                return null;
            }
            if (guiAge > 20 * 5) {
                act("S104", "close unknown GUI after 5s");
                McCompat.closeScreen();
                guiAge = 0;
            }
            return null;
        }
        guiAge = 0;

        // Only when jump-stuck ON the table. A standing wooden-pick craft
        // is same-XZ for >2s on purpose â€” do not cancel it.
        if (tableUnder(mod) && flips >= 4 && !wet
                && !childName.contains("StepOff")) {
            act("S108", "step off table @" + x + "," + y + "," + z);
            McCompat.closeScreen();
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
            cancelPath(mod);
            return new StepOffTableTask();
        }

        // 3. Water still / bob stall — escape to shore before mining resumes.
        double spd = speed(mod);
        if (wet && spd < 0.03 && sameXz > 20 * 3) {
            act("S102", "swim out spd=" + String.format(java.util.Locale.ROOT, "%.3f", spd));
            cancelPath(mod);
            try {
                mod.getClientBaritone().getInputOverrideHandler().setInputForceState(
                        baritone.api.utils.input.Input.CLICK_LEFT, false);
            } catch (Throwable ignored) {}
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.swim();
            return new GetOutOfWaterTask();
        }

        // Thrash detector: CollectIron <-> HolePillar at same xz.
        HolePillar.noteChildFlip(mod, childName);

        // 4. 1x1 shaft only. Pause CollectIron so it cannot mine the pillar.
        // While holding OR on failCool, never hand control back / never re-arm S130.
        if (HolePillar.holding()) {
            return new HolePillarTask();
        }
        boolean walking = childName.contains("GetToBlock") || childName.contains("Wander") || childName.contains("HolePillar");
        boolean wouldPillar = !wet && !"PORTAL".equals(phase)
                && !childName.contains("Craft") && !childName.contains("StepOff")
                && !walking
                && HolePillar.boxed(mod) && HolePillar.hasPlace(mod)
                && (flips >= 3 || sameXz > 20 * 2);
        if (wouldPillar && HolePillar.busy()) {
            HolePillar.logSuppress(mod, "would-S130 but busy cool=" + HolePillar.failCoolLeft()
                    + " flips=" + flips + " sameXz=" + sameXz + " child=" + childName);
        }
        if (!wet && !"PORTAL".equals(phase)
                && !childName.contains("Craft") && !childName.contains("StepOff")
                && !walking
                && !HolePillar.busy()
                && HolePillar.boxed(mod) && HolePillar.hasPlace(mod)
                && (flips >= 3 || sameXz > 20 * 2)) {
            String trig = flips >= 3 ? ("flips=" + flips) : ("sameXz=" + sameXz);
            HolePillar.logStart(mod, phase, childName, trig);
            act("S130", "pillar-out @" + x + "," + y + "," + z + " ph=" + phase + " " + trig);
            cool = 20 * 4;
            cancelPath(mod);
            return new HolePillarTask();
        }
        if ("S130".equals(lastFix) && cool > 0 && HolePillar.holding()) {
            return new HolePillarTask();
        }

        // 5. Jump in place - walk. Do not pillar a tunnel.
        if (!wet && flips >= 6 && sameXz > 20 * 2 && !"BOOTSTRAP".equals(phase)
                && !childName.contains("StepOff") && !walking) {
            if (HolePillar.hasPlace(mod) && HolePillar.boxed(mod) && !HolePillar.busy()) {
                HolePillar.logStart(mod, phase, childName, "jump-pit flips=" + flips);
                act("S130", "jump-pit pillar @" + x + "," + y + "," + z);
                cool = 20 * 4;
                cancelPath(mod);
                return new HolePillarTask();
            }
            if (HolePillar.busy()) {
                HolePillar.logSuppress(mod, "jump-pit-busy child=" + childName);
            }
            act("S100", "stop jump-walk @" + x + "," + z + " ph=" + phase + " child=" + childName);
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
            flips = 0;
            if (childName.contains("Craft") || childName.contains("Collect") || childName.contains("Mine")) {
                McCompat.closeScreen();
                adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
                cancelPath(mod);
            } else if (!portalWork && !childName.contains("Construct")) {
                adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
            }
            return null;
        }

        // 5. Lava and no water during construct â€” log only. Do not swap the child.
        if (count(mod, Items.LAVA_BUCKET) >= 1 && count(mod, Items.WATER_BUCKET) < 1
                && ("PORTAL".equals(phase) || childName.contains("Construct"))) {
            act("S105", "need water (overlay only, not swapping construct)");
            return null;
        }

        // 6. Starve
        int hun = 20;
        try { hun = mod.getPlayer().getHungerManager().getFoodLevel(); } catch (Throwable ignored) {}
        if (hun <= 4 && !hasFood(mod) && !portalWork && !childName.contains("Craft")) {
            act("S103", "hun=" + hun + " starving (overlay only)");
            return null;
        }

        // 7. Piglin, no gold â€” log only. Replacing the tunnel with @get helm is how escape dies.
        if (WorldHelper.getCurrentDimension() == Dimension.NETHER
                && count(mod, Items.GOLDEN_HELMET) < 1
                && piglinNear(mod)
                && !childName.contains("Gold")) {
            act("S110", "piglin nearby, no gold helm (overlay only)");
            return null;
        }

        // 8. Null child in a live phase
        if (child == null && "PORTAL".equals(phase)) {
            act("S111", "portal child missing â€” parent must set construct");
            return null;
        }

        // 9. Actually replace the child. Overlay-only is why wood sat 90s.
        if (!portalWork && !"IRON".equals(phase) && !"PORTAL".equals(phase)
                && !childName.contains("Wander")
                && sameXz > holdFor(phase, childName)) {
            act("S140", "replace " + childName + " after " + (sameXz / 20) + "s same xz");
            sameXz = 0;
            flips = 0;
            cancelPath(mod);
            return new TimeoutWanderTask();
        }

        return null;
    }

    private static int holdFor(String phase, String child) {
        if ("BOOTSTRAP".equals(phase) && (child.contains("Mine") || child.contains("Collect"))) return 20 * 8;
        if ("IRON".equals(phase) && child.contains("Collect")) return 20 * 25;
        if ("PORTAL".equals(phase)) return 20 * 90;
        return 20 * 40;
    }

    private static void cancelPath(AltoClef mod) {
        try {
            mod.getClientBaritone().getPathingBehavior().cancelEverything();
        } catch (Throwable ignored) {}
    }

    private static void act(String code, String plan) {
        if (code.equals(lastFix) && cool > 0) return;
        lastFix = code;
        cool = 20 * 6;
        T2Log.warn(code, plan);
        T2History.note("SOLVE " + code + " " + plan);
    }

    private static boolean workGui() {
        try {
            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.currentScreen == null) return false;
            String n = mc.currentScreen.getClass().getSimpleName();
            return n.contains("Craft") || n.contains("Inventor") || n.contains("Furnace")
                    || n.contains("Anvil") || n.contains("Chest") || n.contains("Barrel")
                    || n.contains("Shulker") || n.contains("Hopper") || n.contains("Merchant")
                    || n.contains("Enchant") || n.contains("Brew") || n.contains("Smith")
                    || n.contains("Grind") || n.contains("Loom") || n.contains("Container")
                    || n.contains("Handled");
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean guiOpen() {
        try {
            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.currentScreen == null) return false;
            String n = mc.currentScreen.getClass().getSimpleName();
            if (n.contains("Chat") || n.contains("T2Menu") || n.contains("GameMenu")
                    || n.contains("Death") || n.contains("Title") || n.contains("Pause")) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean tableUnder(AltoClef mod) {
        try {
            BlockPos feet = mod.getPlayer().getBlockPos();
            return mod.getWorld().getBlockState(feet).isOf(Blocks.CRAFTING_TABLE)
                    || mod.getWorld().getBlockState(feet.down()).isOf(Blocks.CRAFTING_TABLE);
        } catch (Throwable t) {
            return false;
        }
    }

    private static double speed(AltoClef mod) {
        try {
            var v = mod.getPlayer().getVelocity();
            return Math.sqrt(v.x * v.x + v.z * v.z);
        } catch (Throwable t) {
            return 1;
        }
    }

    private static int count(AltoClef mod, net.minecraft.item.Item item) {
        try {
            return mod.getItemStorage().getItemCount(item);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean hasFood(AltoClef mod) {
        return count(mod, Items.BREAD) + count(mod, Items.APPLE)
                + count(mod, Items.COOKED_BEEF) + count(mod, Items.COOKED_PORKCHOP)
                + count(mod, Items.COOKED_CHICKEN) + count(mod, Items.CARROT) > 0;
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
}
