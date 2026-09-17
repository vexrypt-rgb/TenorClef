package adris.altoclef.tasks.speedrun.testrun2.escape;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Sticky;
import adris.altoclef.tasksystem.Task;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * 1x2 nether bore toward a goal. Places netherrack in the cell you just left.
 * Does not follow the axis highways.
 */
public class NetherCoverTunnelTask extends Task {

    private final int goalX;
    private final int goalZ;
    private final int tunnelY;
    private BlockPos prev1;
    private BlockPos prev2;
    private final T2Sticky sticky = new T2Sticky();
    private boolean done;
    private int ticks;

    public NetherCoverTunnelTask(int goalX, int goalZ, int tunnelY) {
        this.goalX = goalX;
        this.goalZ = goalZ;
        this.tunnelY = tunnelY;
    }

    @Override
    protected void onStart() {
        prev1 = null;
        prev2 = null;
        sticky.clear();
        done = false;
        ticks = 0;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        ticks++;
        BlockPos feet = mod.getPlayer().getBlockPos();

        long dx = (long) goalX - feet.getX();
        long dz = (long) goalZ - feet.getZ();
        if (dx * dx + dz * dz <= 16) {
            done = true;
            McCompat.setMove(false, false);
            return null;
        }

        if (count(mod, Items.NETHERRACK) < 8) {
            BlockPos mine = feet.offset(stepDir(dx, dz));
            try {
                if (mod.getWorld().getBlockState(mine).isOf(Blocks.NETHERRACK)) {
                    return sticky.keep("mine-nr", new DestroyBlockTask(mine));
                }
            } catch (Throwable ignored) {}
            try {
                return sticky.keep("get-nr", TaskCatalogue.getItemTask(Items.NETHERRACK, 32));
            } catch (Throwable ignored) {}
        }

        // Fill two cells back so we do not stand inside the block we place.
        if (prev2 != null && manhattan(prev2, feet) >= 2) {
            Task fill = tryFill(mod, prev2);
            if (fill != null) return sticky.keep("fill-" + prev2.getX() + "," + prev2.getZ(), fill);
        }
        if (prev1 == null || !prev1.equals(feet)) {
            prev2 = prev1;
            prev1 = feet;
        }

        if (feet.getY() != tunnelY) {
            if (feet.getY() > tunnelY) {
                return sticky.keep("down", new DestroyBlockTask(feet.down()));
            }
            return sticky.keep("up", new DestroyBlockTask(feet.up()));
        }

        Direction dir = stepDir(dx, dz);
        if (openCorridor(mod, feet, dir, 6)) {
            face(dir);
            McCompat.setMove(true, false);
            return null;
        }
        BlockPos head = feet.offset(dir);
        BlockPos above = head.up();
        try {
            if (!passable(mod, head)) return sticky.keep("head", new DestroyBlockTask(head));
            if (!passable(mod, above)) return sticky.keep("above", new DestroyBlockTask(above));
        } catch (Throwable ignored) {}

        face(dir);
        McCompat.setMove(true, false);
        return null;
    }

    private static boolean openCorridor(AltoClef mod, BlockPos feet, Direction dir, int n) {
        try {
            BlockPos p = feet;
            for (int i = 0; i < n; i++) {
                p = p.offset(dir);
                if (!passable(mod, p) || !passable(mod, p.up())) return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean passable(AltoClef mod, BlockPos p) {
        try {
            var s = mod.getWorld().getBlockState(p);
            //#if MC >= 12000
            return s.isAir() || s.isOf(Blocks.FIRE) || s.isLiquid();
            //#else
            //$$ return s.isAir() || s.getBlock() == Blocks.FIRE || s.getMaterial().isLiquid();
            //#endif
        } catch (Throwable t) {
            try {
                return mod.getWorld().getBlockState(p).isAir();
            } catch (Throwable t2) {
                return false;
            }
        }
    }

    private static Direction stepDir(long dx, long dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void face(Direction dir) {
        float yaw = switch (dir) {
            case EAST -> -90f;
            case WEST -> 90f;
            case SOUTH -> 0f;
            default -> 180f;
        };
        McCompat.setYaw(yaw);
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static Task tryFill(AltoClef mod, BlockPos pos) {
        try {
            var st = mod.getWorld().getBlockState(pos);
            if (!(st.isAir() || st.isOf(Blocks.LAVA) || st.isOf(Blocks.FIRE))) return null;
        } catch (Throwable t) {
            return null;
        }
        if (count(mod, Items.NETHERRACK) < 1) return null;
        try {
            Class<?> cls = Class.forName("adris.altoclef.tasks.construction.PlaceBlockTask");
            try {
                return (Task) cls.getConstructor(BlockPos.class, net.minecraft.block.Block.class)
                        .newInstance(pos, Blocks.NETHERRACK);
            } catch (NoSuchMethodException e) {
                return (Task) cls.getConstructor(BlockPos.class).newInstance(pos);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int count(AltoClef mod, net.minecraft.item.Item item) {
        try {
            return mod.getItemStorage().getItemCount(item);
        } catch (Throwable t) {
            return 0;
        }
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
        return other instanceof NetherCoverTunnelTask o
                && o.goalX == goalX && o.goalZ == goalZ;
    }

    @Override
    protected String toDebugString() {
        return "nether-cover -> " + goalX + "," + goalZ + " y=" + tunnelY;
    }
}
