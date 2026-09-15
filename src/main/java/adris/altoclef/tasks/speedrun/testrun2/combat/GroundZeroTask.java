package adris.altoclef.tasks.speedrun.testrun2.combat;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasks.speedrun.testrun2.T2Brain;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Sticky;
import adris.altoclef.tasksystem.Task;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Ground Zero (MCSR): zero-cycle at the fountain, not on a pillar node.
 * Cage (bars/fences/leaves) blocks fireball LOS; shooting a crystal puts
 * the dragon in strafe. Then beds on bedrock like {@link ZeroCycleTask}.
 *
 * Not Doogile's exact iron-bar schematic.
 */
public class GroundZeroTask extends Task {

    private enum Phase { MATS, GOTO, CAGE, SHOT, BEDS, POP, DONE }

    private final T2Sticky sticky = new T2Sticky();
    private Phase phase = Phase.MATS;
    private int ticks;
    private boolean done;
    private boolean shot;

    @Override
    protected void onStart() {
        phase = Phase.MATS;
        ticks = 0;
        done = false;
        shot = false;
        sticky.clear();
        Debug.logMessage("GROUNDZERO fountain cage + crystal shot + beds");
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        ticks++;
        Task live = sticky.peek();
        Task fix = T2Brain.help(mod, "GZ:" + phase, live);
        if (fix != null) return sticky.keep("brain:" + fix.getClass().getSimpleName(), fix);
        if (ticks > 20 * 300) {
            done = true;
            return null;
        }

        if (phase == Phase.MATS) {
            Task need = need(mod, Items.WHITE_BED, 8);
            if (need != null) return sticky.keep("beds", need);
            Task bars = cageItem(mod);
            if (bars != null) return sticky.keep("cage", bars);
            phase = Phase.GOTO;
        }

        BlockPos feet = mod.getPlayer().getBlockPos();
        if (phase == Phase.GOTO) {
            if (Math.abs(feet.getX()) > 5 || Math.abs(feet.getZ()) > 5) {
                try {
                    Class<?> cl = Class.forName("adris.altoclef.tasks.movement.GetToBlockTask");
                    Task w = (Task) cl.getConstructor(BlockPos.class).newInstance(new BlockPos(0, feet.getY(), 0));
                    return sticky.keep("fountain", w);
                } catch (Throwable t) {
                    McCompat.setMove(true, false);
                }
                return null;
            }
            McCompat.setMove(false, false);
            phase = Phase.CAGE;
        }

        if (phase == Phase.CAGE) {
            BlockPos a = feet.add(1, 1, 0);
            BlockPos b = feet.add(-1, 1, 0);
            if (mod.getWorld().getBlockState(a).isAir()) {
                Task p = place(a, cageBlock());
                if (p != null) return sticky.keep("bar-a", p);
            }
            if (mod.getWorld().getBlockState(b).isAir()) {
                Task p = place(b, cageBlock());
                if (p != null) return sticky.keep("bar-b", p);
            }
            phase = Phase.SHOT;
        }

        if (phase == Phase.SHOT && !shot) {
            EndCrystalEntity crystal = nearestCrystal(mod);
            if (crystal != null) {
                try {
                    Class<?> look = Class.forName("adris.altoclef.util.helpers.LookHelper");
                    look.getMethod("lookAt", AltoClef.class, Vec3d.class)
                            .invoke(null, mod, crystal.getPos().add(0, 1, 0));
                } catch (Throwable ignored) {}
                try {
                    var mc = net.minecraft.client.MinecraftClient.getInstance();
                    mc.interactionManager.attackEntity(mod.getPlayer(), crystal);
                    mod.getPlayer().swingHand(net.minecraft.util.Hand.MAIN_HAND);
                } catch (Throwable ignored) {}
                shot = true;
                Debug.logMessage("GROUNDZERO crystal punched (strafe)");
            }
            phase = Phase.BEDS;
        }

        if (phase == Phase.BEDS) {
            Task zc = new ZeroCycleTask();
            phase = Phase.POP;
            return sticky.keep("zerocycle", zc);
        }

        if (phase == Phase.POP) {
            if (live == null || live.isFinished()) done = true;
            return live;
        }
        return null;
    }

    private static Task need(AltoClef mod, Item item, int n) {
        try {
            if (mod.getItemStorage().getItemCount(item) >= n) return null;
            return TaskCatalogue.getItemTask(item, n);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Task cageItem(AltoClef mod) {
        Item[] opts = {Items.IRON_BARS, Items.OAK_FENCE, Items.NETHER_BRICK_FENCE, Items.OAK_LEAVES};
        for (Item it : opts) {
            try {
                if (mod.getItemStorage().getItemCount(it) >= 4) return null;
            } catch (Throwable ignored) {}
        }
        try {
            return TaskCatalogue.getItemTask(Items.IRON_BARS, 6);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Block cageBlock() {
        return Blocks.IRON_BARS;
    }

    private static Task place(BlockPos pos, Block block) {
        try {
            Class<?> cl = Class.forName("adris.altoclef.tasks.construction.PlaceBlockTask");
            return (Task) cl.getConstructor(BlockPos.class, Block.class).newInstance(pos, block);
        } catch (Throwable t) {
            return null;
        }
    }

    private static EndCrystalEntity nearestCrystal(AltoClef mod) {
        EndCrystalEntity best = null;
        double bestD = 80 * 80;
        try {
            for (Entity e : mod.getWorld().getOtherEntities(mod.getPlayer(),
                    mod.getPlayer().getBoundingBox().expand(80))) {
                if (!(e instanceof EndCrystalEntity c)) continue;
                double d = c.squaredDistanceTo(mod.getPlayer());
                if (d < bestD) {
                    bestD = d;
                    best = c;
                }
            }
        } catch (Throwable ignored) {}
        return best;
    }

    @Override
    protected void onStop(Task interrupt) {
        McCompat.setMove(false, false);
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof GroundZeroTask;
    }

    @Override
    protected String toDebugString() {
        return "ground-zero " + phase;
    }
}
