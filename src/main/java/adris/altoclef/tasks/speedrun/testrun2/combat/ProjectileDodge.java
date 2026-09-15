package adris.altoclef.tasks.speedrun.testrun2.combat;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Input;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Vec3d;

/**
 * If an arrow/trident is closing and will pass near the player, step sideways.
 */
public final class ProjectileDodge {

    private ProjectileDodge() {}

    public static boolean tick(AltoClef mod) {
        PlayerEntity me = mod.getPlayer();
        if (me == null || mod.getWorld() == null) return false;
        Vec3d my = me.getPos().add(0, 0.9, 0);
        Entity threat = null;
        double bestClose = 0;
        try {
            for (Entity e : mod.getWorld().getOtherEntities(me, me.getBoundingBox().expand(28))) {
                if (e == null || e == me) continue;
                if (!(e instanceof ProjectileEntity)) continue;
                if (e instanceof PersistentProjectileEntity p && p.isOnGround()) continue;
                try {
                    Entity owner = ((ProjectileEntity) e).getOwner();
                    if (owner == me) continue;
                } catch (Throwable ignored) {}
                Vec3d pos = e.getPos();
                Vec3d vel = e.getVelocity();
                double speed = vel.length();
                if (speed < 0.15) continue;
                Vec3d toMe = my.subtract(pos);
                if (toMe.lengthSquared() > 28 * 28) continue;
                double closing = vel.normalize().dotProduct(toMe.normalize());
                if (closing < 0.55) continue;
                double miss = missDistance(pos, vel, my);
                if (miss < 1.35 && closing > bestClose) {
                    bestClose = closing;
                    threat = e;
                }
            }
        } catch (Throwable ignored) {}
        if (threat == null) return false;
        Vec3d vel = threat.getVelocity();
        double lx = -vel.z;
        double lz = vel.x;
        double len = Math.sqrt(lx * lx + lz * lz);
        if (len < 1e-4) {
            T2Input.walkTurn();
            return true;
        }
        lx /= len;
        lz /= len;
        float yaw = (float) (Math.toDegrees(Math.atan2(-lx, lz)));
        McCompat.setYaw(yaw);
        McCompat.setMove(true, false);
        T2Input.walkTurn();
        return true;
    }

    /** Distance from point P to the ray origin+ t*dir, t>=0. */
    private static double missDistance(Vec3d origin, Vec3d dir, Vec3d p) {
        Vec3d d = dir.normalize();
        Vec3d w = p.subtract(origin);
        double t = w.dotProduct(d);
        if (t < 0) t = 0;
        Vec3d closest = origin.add(d.multiply(t));
        return closest.distanceTo(p);
    }
}
