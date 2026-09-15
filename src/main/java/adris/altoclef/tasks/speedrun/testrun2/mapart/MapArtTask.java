package adris.altoclef.tasks.speedrun.testrun2.mapart;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasks.speedrun.testrun2.T2Brain;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Sticky;
import adris.altoclef.tasksystem.Task;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;

/**
 * Gather every block in art.txt then place a flat 128×128 (or whatever) layer
 * south-east of the player's feet when the task started.
 */
public class MapArtTask extends Task {

    private enum Phase { KIT, GATHER, BUILD, DONE }

    private final Path dir;
    private MapArtGrid grid;
    private BlockPos origin;
    private Phase phase = Phase.KIT;
    private final T2Sticky sticky = new T2Sticky();
    private int cell;
    private boolean done;

    public MapArtTask(Path dir) {
        this.dir = dir;
    }

    @Override
    protected void onStart() {
        T2Brain.reset();
        sticky.clear();
        grid = MapArtGrid.load(dir);
        AltoClef mod = AltoClef.getInstance();
        origin = mod.getPlayer() != null ? mod.getPlayer().getBlockPos() : BlockPos.ORIGIN;
        phase = Phase.KIT;
        cell = 0;
        done = false;
        Debug.logMessage("MAPART print " + dir.getFileName()
                + (grid == null ? " BAD GRID" : " " + grid.w + "x" + grid.h));
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        Task live = sticky.peek();
        Task fix = T2Brain.help(mod, "MAPART:" + phase, live);
        if (fix != null) return sticky.keep("brain:" + fix.getClass().getSimpleName(), fix);

        if (grid == null) {
            done = true;
            return null;
        }

        if (phase == Phase.KIT) {
            if (count(mod, Items.STONE_PICKAXE) + count(mod, Items.IRON_PICKAXE)
                    + count(mod, Items.WOODEN_PICKAXE) < 1) {
                return sticky.keep("pick", TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1));
            }
            phase = Phase.GATHER;
        }

        if (phase == Phase.GATHER) {
            for (var e : grid.counts.entrySet()) {
                Item item = MapArtGrid.itemOf(e.getKey());
                if (item == null) continue;
                int have = count(mod, item);
                if (have < e.getValue()) {
                    try {
                        return sticky.keep("mat:" + e.getKey(),
                                TaskCatalogue.getItemTask(item, e.getValue()));
                    } catch (Throwable t) {
                        Debug.logWarning("MAPART no catalogue " + e.getKey());
                    }
                }
            }
            Debug.logMessage("MAPART materials ready — placing from " + origin);
            phase = Phase.BUILD;
            cell = 0;
            sticky.clear();
            live = null;
        }

        if (phase == Phase.BUILD) {
            int total = grid.w * grid.h;
            if (live != null) {
                boolean fin = false;
                try { fin = live.isFinished(); } catch (Throwable ignored) {}
                if (!fin) return live;
                cell++;
                sticky.clear();
            }
            if (cell >= total) {
                phase = Phase.DONE;
                done = true;
                Debug.logMessage("MAPART placed " + total + " blocks. Use an empty map on the platform.");
                return null;
            }
            if (cell % 256 == 0) {
                Debug.logMessage("MAPART place " + cell + "/" + total);
            }
            int x = cell % grid.w;
            int z = cell / grid.w;
            BlockPos pos = origin.add(x, 0, z);
            Item item = MapArtGrid.itemOf(grid.id[z][x]);
            if (item == null) {
                cell++;
                return null;
            }
            Task place = placeBlock(pos, item);
            if (place != null) return sticky.keep("p" + cell, place);
            cell++;
            return null;
        }
        return null;
    }

    private static Task placeBlock(BlockPos pos, Item item) {
        try {
            Class<?> cls = Class.forName("adris.altoclef.tasks.construction.PlaceBlockTask");
            net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(item);
            try {
                return (Task) cls.getConstructor(BlockPos.class, net.minecraft.block.Block.class)
                        .newInstance(pos, block);
            } catch (NoSuchMethodException e) {
                return (Task) cls.getConstructor(BlockPos.class).newInstance(pos);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static int count(AltoClef mod, Item item) {
        try {
            return mod.getItemStorage().getItemCount(item);
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    protected void onStop(Task interrupt) {
        sticky.clear();
        McCompat.setMove(false, false);
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof MapArtTask o && o.dir.equals(dir);
    }

    @Override
    protected String toDebugString() {
        return "mapart " + dir.getFileName();
    }
}
