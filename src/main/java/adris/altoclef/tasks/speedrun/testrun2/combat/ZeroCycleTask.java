package adris.altoclef.tasks.speedrun.testrun2.combat;

import adris.altoclef.multiversion.CItems;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.ThrowEnderPearlSimpleProjectileTask;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasks.speedrun.testrun2.T2Brain;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Sticky;
import adris.altoclef.tasksystem.Task;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Real zero-cycle: pearl/climb the pillar the dragon is circling,
 * place beds there, explode on the first descent. Fountain perch
 * is one-cycle — use {@link GroundZeroTask} for that.
 * Never break this pillar's crystal.
 */
public class ZeroCycleTask extends Task {

    private enum Phase { MATS, NODE, TRAVEL, SETUP, POP, DONE }

    private final T2Sticky sticky = new T2Sticky();
    private Phase phase = Phase.MATS;
    private int ticks;
    private boolean done;
    private BlockPos node;

    @Override
    protected void onStart() {
        phase = Phase.MATS;
        ticks = 0;
        done = false;
        node = null;
        sticky.clear();
        Debug.logMessage("ZEROCYCLE pillar node (not fountain)");
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        ticks++;
        handsOffCrystal();
        Task live = sticky.peek();
        Task fix = T2Brain.help(mod, "ZERO:" + phase, live);
        if (fix != null) return sticky.keep("brain:" + fix.getClass().getSimpleName(), fix);
        if (ticks > 20 * 240) {
            done = true;
            return null;
        }

        if (phase == Phase.MATS) {
            try {
                if (mod.getItemStorage().getItemCount(CItems.WHITE_BED) < 5) {
                    Task t = TaskCatalogue.getItemTask(CItems.WHITE_BED, 6);
                    if (t != null) return sticky.keep("beds", t);
                }
                if (mod.getItemStorage().getItemCount(Items.OBSIDIAN) < 2) {
                    Task t = TaskCatalogue.getItemTask(Items.OBSIDIAN, 2);
                    if (t != null) return sticky.keep("obby", t);
                }
                if (mod.getItemStorage().getItemCount(Items.NETHERRACK) < 8
                        && mod.getItemStorage().getItemCount(Items.COBBLESTONE) < 8) {
                    Task t = TaskCatalogue.getItemTask(Items.NETHERRACK, 12);
                    if (t != null) return sticky.keep("fill", t);
                }
            } catch (Throwable ignored) {}
            phase = Phase.NODE;
        }

        EnderDragonEntity dragon = dragon(mod);
        if (phase == Phase.NODE) {
            node = pickNode(mod, dragon);
            if (node == null) {
                Debug.logWarning("ZEROCYCLE no pillar crystal yet");
                return null;
            }
            Debug.logMessage("ZEROCYCLE node " + node.getX() + "," + node.getY() + "," + node.getZ());
            try { adris.altoclef.tasks.speedrun.testrun2.util.Splits.mark("zero-node"); } catch (Throwable ignored) {}
            phase = Phase.TRAVEL;
        }

        if (phase == Phase.TRAVEL) {
            if (node == null) {
                phase = Phase.NODE;
                return null;
            }
            double dx = mod.getPlayer().getX() - (node.getX() + 0.5);
            double dy = mod.getPlayer().getY() - (node.getY() + 0.5);
            double dz = mod.getPlayer().getZ() - (node.getZ() + 0.5);
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > 6) {
                int pearls = 0;
                try { pearls = mod.getItemStorage().getItemCount(Items.ENDER_PEARL); } catch (Throwable ignored) {}
                if (pearls > 0 && d > 16) {
                    return sticky.keep("pearl", new ThrowEnderPearlSimpleProjectileTask(node));
                }
                return sticky.keep("climb", new GetToBlockTask(node));
            }
            McCompat.setMove(false, false);
            phase = Phase.SETUP;
        }

        if (phase == Phase.SETUP) {
            BlockPos stand = node != null ? node : mod.getPlayer().getBlockPos();
            Task place = ZeroSetup.nextPlace(mod, stand);
            if (place != null) return sticky.keep("plat-" + stand.getY(), place);
            Debug.logMessage("ZEROCYCLE platform ready, waiting dragon pass");
            phase = Phase.POP;
        }

        if (phase == Phase.POP) {
            if (dragon == null) {
                done = true;
                return null;
            }
            equipBed(mod);
            ZeroCycle.tick(mod, dragon);
            return null;
        }
        return null;
    }

    private static BlockPos pickNode(AltoClef mod, EnderDragonEntity dragon) {
        EndCrystalEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d dpos = dragon == null ? new Vec3d(0, 80, 0) : dragon.getPos();
        Vec3d dvel = Vec3d.ZERO;
        try {
            if (dragon != null) dvel = dragon.getVelocity();
        } catch (Throwable ignored) {}
        Vec3d ahead = new Vec3d(dpos.x + dvel.x * 8, dpos.y, dpos.z + dvel.z * 8);
        try {
            for (Entity e : mod.getWorld().getOtherEntities(mod.getPlayer(),
                    mod.getPlayer().getBoundingBox().expand(120))) {
                if (!(e instanceof EndCrystalEntity c)) continue;
                if (Math.abs(c.getX()) < 8 && Math.abs(c.getZ()) < 8) continue;
                double dx = c.getX() - ahead.x;
                double dz = c.getZ() - ahead.z;
                double score = dx * dx + dz * dz;
                if (score < bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
        } catch (Throwable ignored) {}
        if (best == null) return null;
        BlockPos c = best.getBlockPos();
        return new BlockPos(c.getX() + 2, c.getY() - 1, c.getZ() + 2);
    }

    private static void equipBed(AltoClef mod) {
        mod.getSlotHandler().forceEquipItem(CItems.WHITE_BED);
    }

    private static EnderDragonEntity dragon(AltoClef mod) {
        try {
            var list = mod.getEntityTracker().getTrackedEntities(EnderDragonEntity.class);
            if (list == null || list.isEmpty()) return null;
            return list.get(0);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Punching this pillar's crystal fails the zero. */
    private static void handsOffCrystal() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.crosshairTarget instanceof EntityHitResult
                && ((EntityHitResult) mc.crosshairTarget).getEntity() instanceof EndCrystalEntity) {
            mc.options.attackKey.setPressed(false);
        }
    }

    @Override
    protected void onStop(Task interrupt) {
        McCompat.setMove(false, false);
        try {
            net.minecraft.client.MinecraftClient.getInstance().options.useKey.setPressed(false);
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ZeroCycleTask;
    }

    @Override
    protected String toDebugString() {
        return "zero-pillar " + phase;
    }
}
