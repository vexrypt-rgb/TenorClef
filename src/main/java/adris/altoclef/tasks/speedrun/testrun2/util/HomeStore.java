package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.util.math.BlockPos;

public final class HomeStore {

    public static final String FILE = "home.txt";

    public int x, y, z;
    public String dim = "OVERWORLD";

    public static HomeStore load() {
        String raw = GameFiles.read(FILE);
        if (raw == null) return null;
        try {
            String[] p = raw.trim().split("\\s+");
            HomeStore h = new HomeStore();
            h.dim = p[0];
            h.x = Integer.parseInt(p[1]);
            h.y = Integer.parseInt(p[2]);
            h.z = Integer.parseInt(p[3]);
            return h;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void saveHere(AltoClef mod) {
        if (mod.getPlayer() == null) return;
        String dim = "OVERWORLD";
        try { dim = String.valueOf(WorldHelper.getCurrentDimension()); } catch (Throwable ignored) {}
        BlockPos p = mod.getPlayer().getBlockPos();
        GameFiles.write(FILE, dim + " " + p.getX() + " " + p.getY() + " " + p.getZ() + "\n");
    }

    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }

    public boolean sameDim(AltoClef mod) {
        try {
            return String.valueOf(WorldHelper.getCurrentDimension()).equalsIgnoreCase(dim);
        } catch (Throwable t) {
            return true;
        }
    }
}
