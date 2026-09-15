package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import adris.altoclef.util.helpers.WorldHelper;

public final class Waypoints {

    public static final String FILE = "waypoints.txt";
    private static int lastX = Integer.MIN_VALUE;
    private static int lastZ = Integer.MIN_VALUE;

    private Waypoints() {}

    public static void tick(AltoClef mod) {
        if (mod.getPlayer() == null) return;
        int x = mod.getPlayer().getBlockX();
        int z = mod.getPlayer().getBlockZ();
        if (lastX == Integer.MIN_VALUE) {
            lastX = x;
            lastZ = z;
            return;
        }
        long dx = (long) x - lastX;
        long dz = (long) z - lastZ;
        if (dx * dx + dz * dz < 10_000L * 10_000L) return;
        lastX = x;
        lastZ = z;
        String dim = "?";
        try { dim = String.valueOf(WorldHelper.getCurrentDimension()); } catch (Throwable ignored) {}
        GameFiles.append(FILE, dim + " " + x + " " + mod.getPlayer().getBlockY() + " " + z);
    }
}
