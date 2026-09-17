package adris.altoclef.tasks.speedrun.testrun2.combat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

/**
 * Bow vs crossbow vs firework-crossbow. Speeds from vanilla 1.16:
 * bow full draw 3.0, crossbow arrow 3.15, crossbow rocket 1.6.
 */
public final class BowLead {

    public static final double BOW_SPEED = 3.0;
    public static final double CROSSBOW_ARROW = 3.15;
    public static final double CROSSBOW_ROCKET = 1.6;
    public static final double ARROW_GRAVITY = 0.05;
    public static final double ROCKET_GRAVITY = 0.0;
    public static final double ARROW_SPEED = 3.0;
    public static final double GRAVITY = 0.05;

    private BowLead() {}

    public static Vec3d aimPoint(PlayerEntity me, LivingEntity target) {
        ItemStack hand = ItemStack.EMPTY;
        try { hand = me.getMainHandStack(); } catch (Throwable ignored) {}
        return aimPoint(me, target, hand, 1f);
    }

    public static Vec3d aimPoint(PlayerEntity me, LivingEntity target, ItemStack weapon, float bowCharge) {
        if (me == null || target == null) return Vec3d.ZERO;
        Vec3d eye = me.getPos().add(0, me.getStandingEyeHeight(), 0);
        Vec3d body = target.getPos().add(0, target.getHeight() * 0.55, 0);
        Vec3d vel;
        try { vel = target.getVelocity(); } catch (Throwable t) { vel = Vec3d.ZERO; }
        double speed = speedOf(weapon, bowCharge);
        double g = gravityOf(weapon);
        double dist = eye.distanceTo(body);
        double t = Math.max(0.15, dist / Math.max(0.4, speed));
        Vec3d future = body.add(vel.x * t, vel.y * t * 0.35, vel.z * t);
        return future.add(0, 0.5 * g * t * t, 0);
    }

    public static double speedOf(ItemStack stack, float bowCharge) {
        if (stack == null || stack.isEmpty()) return BOW_SPEED;
        if (stack.getItem() == Items.CROSSBOW) {
            return fireworkLoaded(stack) ? CROSSBOW_ROCKET : CROSSBOW_ARROW;
        }
        if (stack.getItem() == Items.BOW) {
            float c = Math.max(0.1f, Math.min(1f, bowCharge));
            return BOW_SPEED * c;
        }
        return BOW_SPEED;
    }

    public static double gravityOf(ItemStack stack) {
        if (stack != null && stack.getItem() == Items.CROSSBOW && fireworkLoaded(stack)) {
            return ROCKET_GRAVITY;
        }
        return ARROW_GRAVITY;
    }

    public static boolean isCrossbow(ItemStack stack) {
        return stack != null && stack.getItem() == Items.CROSSBOW;
    }

    public static boolean isBow(ItemStack stack) {
        return stack != null && stack.getItem() == Items.BOW;
    }

    public static boolean charged(ItemStack stack) {
        if (!isCrossbow(stack)) return false;
        try {
            return (Boolean) Class.forName("net.minecraft.item.CrossbowItem")
                    .getMethod("isCharged", ItemStack.class)
                    .invoke(null, stack);
        } catch (Throwable t) {
            // 1.20.5+ dropped getOrCreateTag; fall back to component dump
            try {
                Object comps = stack.getClass().getMethod("getComponents").invoke(stack);
                return String.valueOf(comps).toLowerCase().contains("charged");
            } catch (Throwable t2) {
                return false;
            }
        }
    }

    public static boolean fireworkLoaded(ItemStack stack) {
        if (!isCrossbow(stack)) return false;
        try {
            Class<?> cb = Class.forName("net.minecraft.item.CrossbowItem");
            return (Boolean) cb.getMethod("hasProjectile", ItemStack.class, net.minecraft.item.Item.class)
                    .invoke(null, stack, Items.FIREWORK_ROCKET);
        } catch (Throwable t) {
            try {
                Object comps = stack.getClass().getMethod("getComponents").invoke(stack);
                return String.valueOf(comps).toLowerCase().contains("firework");
            } catch (Throwable t2) {
                return false;
            }
        }
    }
}