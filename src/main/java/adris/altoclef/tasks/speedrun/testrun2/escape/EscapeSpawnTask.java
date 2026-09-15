package adris.altoclef.tasks.speedrun.testrun2.escape;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.compound.ConstructNetherPortalBucketTask;
import adris.altoclef.tasks.movement.EnterNetherPortalTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.speedrun.testrun2.T2Brain;
import adris.altoclef.tasks.speedrun.testrun2.catalog.ExtraCatalogue;
import adris.altoclef.tasks.speedrun.testrun2.util.Splits;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Sticky;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Random OW point inside +/-30M. Nether 8:1 in a 1x2 tunnel filled behind
 * with netherrack. Then portal out and seal a hole. No time cap.
 */
public class EscapeSpawnTask extends Task {

    public static final int OW_BORDER = 29999000;
    public static final int NETHER_BORDER = 3749000;
    public static final int SPAWN_AVOID = 10000;
    public static final int AXIS_AVOID = 256;
    public static final int TUNNEL_Y = 48;

    private enum Phase { KIT, TO_NETHER, TUNNEL, TO_OVERWORLD, LAST_MILE, BASE, DONE }

    private final T2Sticky sticky = new T2Sticky();
    private Phase phase = Phase.KIT;
    private int destOwX, destOwZ;
    private int destNeX, destNeZ;
    private int ticks;
    private boolean done;
    private SecretBaseTask base;

    @Override
    protected void onStart() {
        T2Brain.reset();
        sticky.clear();
        phase = Phase.KIT;
        ticks = 0;
        done = false;
        base = null;
        pickDest();
        writeDestFile();
        adris.altoclef.tasks.speedrun.testrun2.util.PlayerSense.enable();
        Debug.logMessage("ESCAPE nether-cover to a random +/-30M OW point. Dest only in escape_dest.txt.");
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        if (adris.altoclef.tasks.speedrun.testrun2.util.QueueWatch.blocked()) {
            return null;
        }
        ticks++;

        Task live = sticky.peek();
        Task fix = T2Brain.help(mod, "ESC:" + phase, live);
        if (fix != null) return sticky.keep("brain", fix);

        Dimension dim = Dimension.OVERWORLD;
        try { dim = WorldHelper.getCurrentDimension(); } catch (Throwable ignored) {}

        if (phase == Phase.KIT) {
            Task kit = kit(mod);
            if (kit != null) return sticky.keep("kit", kit);
            phase = (dim == Dimension.NETHER) ? Phase.TUNNEL : Phase.TO_NETHER;
        }

        if (phase == Phase.TO_NETHER) {
            if (dim == Dimension.NETHER) {
                phase = Phase.TUNNEL;
            } else {
                try {
                if (mod.getBlockScanner().anyFound(net.minecraft.block.Blocks.NETHER_PORTAL)) {
                    return sticky.keep("portal-in", new EnterNetherPortalTask(Dimension.NETHER));
                }
            } catch (Throwable ignored) {}
                return sticky.keep("portal-in", new ConstructNetherPortalBucketTask());
            }
        }

        if (phase == Phase.TUNNEL) {
            if (dim != Dimension.NETHER) {
                phase = Phase.TO_NETHER;
                return null;
            }
            int x = mod.getPlayer().getBlockX();
            int z = mod.getPlayer().getBlockZ();
            long dx = (long) destNeX - x;
            long dz = (long) destNeZ - z;
            if (dx * dx + dz * dz <= 64) {
                phase = Phase.TO_OVERWORLD;
                sticky.clear();
                Debug.logMessage("ESCAPE nether dest reached — portal out.");
                Splits.mark("nether_dest");
            } else {
                if (ticks % (20 * 120) == 0) {
                    long left = (long) Math.sqrt(dx * dx + dz * dz);
                    Debug.logMessage("ESCAPE nether tunnel ~" + left + "m left");
                }
                return sticky.keep("bore", new NetherCoverTunnelTask(destNeX, destNeZ, TUNNEL_Y));
            }
        }

        if (phase == Phase.TO_OVERWORLD) {
            if (dim == Dimension.OVERWORLD) {
                phase = Phase.LAST_MILE;
            } else {
                try {
                if (mod.getBlockScanner().anyFound(net.minecraft.block.Blocks.NETHER_PORTAL)) {
                    return sticky.keep("portal-out", new EnterNetherPortalTask(Dimension.OVERWORLD));
                }
            } catch (Throwable ignored) {}
                return sticky.keep("portal-out", new ConstructNetherPortalBucketTask());
            }
        }

        if (phase == Phase.LAST_MILE) {
            int x = mod.getPlayer().getBlockX();
            int z = mod.getPlayer().getBlockZ();
            long dx = (long) destOwX - x;
            long dz = (long) destOwZ - z;
            if (dx * dx + dz * dz <= 48L * 48L) {
                phase = Phase.BASE;
                sticky.clear();
                Debug.logMessage("ESCAPE on site — sealing hole.");
            } else {
                int y = 64;
                try { y = mod.getPlayer().getBlockY(); } catch (Throwable ignored) {}
                return sticky.keep("ow", new GetToBlockTask(new BlockPos(destOwX, y, destOwZ)));
            }
        }

        if (phase == Phase.BASE) {
            if (base == null) base = new SecretBaseTask();
            if (base.isFinished()) {
                phase = Phase.DONE;
                done = true;
                Debug.logMessage("ESCAPE sealed. Stay put.");
                Splits.mark("base_sealed");
                return null;
            }
            return sticky.keep("base", base);
        }

        return null;
    }

    private Task kit(AltoClef mod) {
        int pick = count(mod, Items.STONE_PICKAXE) + count(mod, Items.IRON_PICKAXE)
                + count(mod, Items.DIAMOND_PICKAXE);
        if (pick < 1) {
            if (count(mod, Items.WOODEN_PICKAXE) < 1) {
                Task t = ExtraCatalogue.get("wooden_pickaxe", 1);
                return t != null ? t : TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
            }
            return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
        }
        if (count(mod, Items.WATER_BUCKET) < 1 && count(mod, Items.BUCKET) < 1) {
            Task w = ExtraCatalogue.get("water_bucket", 1);
            return w != null ? w : TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
        }
        if (count(mod, Items.FLINT_AND_STEEL) < 1 && count(mod, Items.FLINT) < 1) {
            return TaskCatalogue.getItemTask(Items.FLINT, 1);
        }
        if (count(mod, Items.BREAD) + count(mod, Items.COOKED_BEEF) + count(mod, Items.APPLE) < 8) {
            Task food = ExtraCatalogue.get("food", 16);
            if (food != null) return food;
        }
        return null;
    }

    private void pickDest() {
        Random rng = new Random();
        int x, z;
        int guard = 0;
        do {
            x = rng.nextInt(OW_BORDER * 2 + 1) - OW_BORDER;
            z = rng.nextInt(OW_BORDER * 2 + 1) - OW_BORDER;
            guard++;
        } while (guard < 50 && ((Math.abs(x) < SPAWN_AVOID && Math.abs(z) < SPAWN_AVOID)
                || Math.abs(x) < AXIS_AVOID || Math.abs(z) < AXIS_AVOID));
        destOwX = x;
        destOwZ = z;
        destNeX = clamp(x / 8, -NETHER_BORDER, NETHER_BORDER);
        destNeZ = clamp(z / 8, -NETHER_BORDER, NETHER_BORDER);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void writeDestFile() {
        try {
            Path dir;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.runDirectory != null) {
                dir = mc.runDirectory.toPath().resolve("altoclef");
            } else {
                dir = Path.of("altoclef");
            }
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("escape_dest.txt"),
                    "ow " + destOwX + " " + destOwZ + "\n"
                            + "nether " + destNeX + " " + destNeZ + " y=" + TUNNEL_Y + "\n",
                    StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
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
        adris.altoclef.tasks.speedrun.testrun2.util.PlayerSense.disable();
        sticky.clear();
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof EscapeSpawnTask;
    }

    @Override
    protected String toDebugString() {
        return "escape ph=" + phase;
    }
}
