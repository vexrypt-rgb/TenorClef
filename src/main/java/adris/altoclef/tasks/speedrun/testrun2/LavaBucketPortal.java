package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/**
 * Prefer a bucket portal when we already have water + nearby surface lava.
 * Never starts if the water bucket is missing — that was a lava-bath bug.
 */
public final class LavaBucketPortal {

    private LavaBucketPortal() {}

    public static boolean ready(AltoClef mod) {
        if (mod.getPlayer() == null) return false;
        boolean water = mod.getItemStorage().hasItem(Items.WATER_BUCKET);
        if (!water) return false;
        try {
            var lavaOpt = mod.getBlockScanner().getNearestBlock(Blocks.LAVA);
            if (lavaOpt == null || lavaOpt.isEmpty()) return false;
            BlockPos lava = lavaOpt.get();
            return mod.getPlayer().getPos().squaredDistanceTo(lava.getX() + 0.5, lava.getY() + 0.5, lava.getZ() + 0.5)
                    < 24 * 24;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Task start(AltoClef mod) {
        String[] names = {
                "adris.altoclef.tasks.movement.ConstructNetherPortalBucketTask",
                "adris.altoclef.tasks.speedrun.beatgame.ConstructNetherPortalBucketTask",
                "adris.altoclef.tasks.nether.ConstructNetherPortalBucketTask"
        };
        for (String n : names) {
            try {
                Class<?> c = Class.forName(n);
                try {
                    return (Task) c.getConstructor().newInstance();
                } catch (NoSuchMethodException e) {
                    return (Task) c.getConstructor(AltoClef.class).newInstance(mod);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
