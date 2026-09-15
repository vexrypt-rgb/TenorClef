package adris.altoclef.tasks.speedrun.testrun2.combat;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

/**
 * RSG End "zero cycle": damage the dragon on the first perch so you
 * do not wait for another circling.
 *
 * Human version: beds on the fountain + optional bow/crossbow shot
 * as the head arrives so the dragon hangs for the explosions.
 *
 * This module:
 *   1. Detects perch-ish dragon (near 0,0 podium, sitting phase).
 *   2. Aims a lead shot at the head (bow 3.0 / crossbow 3.15).
 *   3. If a bed is in hand, right-clicks to explode it (End rule).
 *
 * It will not TAS a 2+2 bed pattern. It will fire and pop beds on time.
 */
public final class ZeroCycle {

    private static int bedClicks;
    private static int shots;
    private static int hold;

    private ZeroCycle() {}

    public static boolean perched(EnderDragonEntity dragon) {
        if (dragon == null) return false;
        try {
            Object mgr = dragon.getClass().getMethod("getPhaseManager").invoke(dragon);
            Object cur = mgr.getClass().getMethod("getCurrent").invoke(mgr);
            Object type = cur.getClass().getMethod("getType").invoke(cur);
            String n = String.valueOf(type).toLowerCase();
            if (n.contains("sitting") || n.contains("land") || n.contains("hover")) return true;
        } catch (Throwable ignored) {}
        Vec3d p = dragon.getPos();
        return Math.abs(p.x) < 20 && Math.abs(p.z) < 20 && p.y > 60;
    }

    public static Vec3d headPoint(EnderDragonEntity dragon) {
        try {
            Object head = dragon.getClass().getMethod("getPart", int.class).invoke(dragon, 0);
            if (head instanceof net.minecraft.entity.Entity e) return e.getPos();
        } catch (Throwable ignored) {}
        try {
            var parts = dragon.getClass().getMethod("getBodyParts").invoke(dragon);
            if (parts instanceof Object[] arr && arr.length > 0 && arr[0] instanceof net.minecraft.entity.Entity e) {
                return e.getPos();
            }
        } catch (Throwable ignored) {}
        return dragon.getPos().add(0, 2.5, 0);
    }

    /**
     * @return true if this tick handled look/shoot/bed (caller should not melee).
     */
    public static boolean tick(AltoClef mod, LivingEntity target) {
        if (!(target instanceof EnderDragonEntity dragon)) return false;
        PlayerEntity me = mod.getPlayer();
        if (me == null) return false;
        if (!perched(dragon) && me.getPos().distanceTo(dragon.getPos()) > 40) return false;

        ItemStack hand = ItemStack.EMPTY;
        try { hand = me.getMainHandStack(); } catch (Throwable ignored) {}

        Vec3d head = headPoint(dragon);
        Vec3d aim = head;
        try {
            Vec3d vel = dragon.getVelocity();
            double spd = BowLead.speedOf(hand, charge(me));
            double dist = me.getPos().distanceTo(head);
            double t = Math.max(0.15, dist / Math.max(0.4, spd));
            aim = head.add(vel.x * t, 0.5 * BowLead.gravityOf(hand) * t * t, vel.z * t);
        } catch (Throwable ignored) {}

        try {
            Class<?> look = Class.forName("adris.altoclef.util.helpers.LookHelper");
            look.getMethod("lookAt", AltoClef.class, Vec3d.class).invoke(null, mod, aim);
        } catch (Throwable ignored) {}

        boolean bow = BowLead.isBow(hand) || BowLead.isCrossbow(hand);
        boolean bed = isBed(hand);

        var opts = net.minecraft.client.MinecraftClient.getInstance().options;
        boolean window = perched(dragon) || me.getPos().distanceTo(head) < 12;
        if (bow && window) {
            hold++;
            if (BowLead.isCrossbow(hand) && BowLead.charged(hand)) {
                opts.useKey.setPressed(hold <= 2);
                if (hold == 1) shots++;
                if (hold > 4) hold = 0;
                return true;
            }
            if (BowLead.isBow(hand) || (BowLead.isCrossbow(hand) && !BowLead.charged(hand))) {
                boolean draw = hold < 22;
                opts.useKey.setPressed(draw);
                if (!draw) hold = 0;
                return true;
            }
        }
        if (bed && window) {
            hold++;
            opts.useKey.setPressed(hold % 8 != 0);
            bedClicks++;
            if (bedClicks == 1) Debug.logMessage("ZEROCYCLE bed explode perched");
            return true;
        }
        opts.useKey.setPressed(false);
        return false;
    }

    private static boolean isBed(ItemStack hand) {
        if (hand == null || hand.isEmpty()) return false;
        try {
            return Class.forName("net.minecraft.item.BedItem").isInstance(hand.getItem());
        } catch (Throwable t) {
            String id = String.valueOf(hand.getItem());
            return id.endsWith("_bed") || id.endsWith(".bed");
        }
    }

    private static float charge(PlayerEntity me) {
        try {
            if (BowLead.isCrossbow(me.getMainHandStack())) return 1f;
            return Math.min(1f, me.getItemUseTime() / 20f);
        } catch (Throwable t) {
            return 1f;
        }
    }
}
