package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

/**
 * Get out of a cave. Dest must be AIR with sky light, not a block inside stone.
 */
public class SurfaceBailTask extends Task {

    private Task inner;
    private int ticks;
    private boolean done;
    private int lastY = Integer.MIN_VALUE;
    private int noClimb;

    public static boolean underground(AltoClef mod) {
        try {
            if (mod.getPlayer() == null || mod.getWorld() == null) return false;
            if (mod.getPlayer().isTouchingWater() || mod.getPlayer().isSubmergedInWater()) return false;
            BlockPos feet = mod.getPlayer().getBlockPos();
            int sky = mod.getWorld().getLightLevel(LightType.SKY, feet);
            int y = feet.getY();
            return sky <= 1 || (y < 55 && sky <= 4);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean nearSpawner(AltoClef mod) {
        try {
            var found = mod.getBlockScanner().getNearestBlock(net.minecraft.block.Blocks.SPAWNER);
            if (found == null || found.isEmpty()) return false;
            BlockPos p = found.get();
            return mod.getPlayer().getPos().squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) < 12 * 12;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int sky(AltoClef mod) {
        try {
            return mod.getWorld().getLightLevel(LightType.SKY, mod.getPlayer().getBlockPos());
        } catch (Throwable t) {
            return 15;
        }
    }

    @Override
    protected void onStart() {
        ticks = 0;
        done = false;
        inner = null;
        noClimb = 0;
        lastY = Integer.MIN_VALUE;
        retarget(AltoClef.getInstance());
    }

    @Override
    protected Task onTick() {
        ticks++;
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) {
            done = true;
            return null;
        }
        int y = mod.getPlayer().getBlockY();
        if (y == lastY) noClimb++;
        else noClimb = 0;
        lastY = y;

        if (sky(mod) >= 13 && y >= 63 && ticks > 20) {
            done = true;
            return null;
        }
        if (noClimb > 20 * 8 || ticks % (20 * 12) == 0) {
            retarget(mod);
            noClimb = 0;
        }
        if (inner == null) retarget(mod);
        return inner;
    }

    private void retarget(AltoClef mod) {
        BlockPos dest = findSky(mod);
        if (dest != null && dest.getY() != mod.getPlayer().getBlockY()) {
            inner = new GetToBlockTask(dest);
            Debug.logMessage("TESRUN2 surface-bail dest=" + dest);
        } else {
            inner = new TimeoutWanderTask();
            Debug.logMessage("TESRUN2 surface-bail wander (no air sky dest)");
        }
    }

    private BlockPos findSky(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return null;
        BlockPos from = mod.getPlayer().getBlockPos();
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int r = 0; r <= 24; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = 0; dy <= 24; dy += 2) {
                        BlockPos p = new BlockPos(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
                        if (!openSky(mod, p)) continue;
                        int score = Math.abs(dx) + Math.abs(dz) + dy;
                        if (score < bestScore) {
                            bestScore = score;
                            best = p;
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private boolean openSky(AltoClef mod, BlockPos p) {
        try {
            BlockState st = mod.getWorld().getBlockState(p);
            if (!st.isAir()) return false;
            return mod.getWorld().getLightLevel(LightType.SKY, p) >= 13;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SurfaceBailTask;
    }

    @Override
    protected String toDebugString() {
        return "surface-bail";
    }
}
