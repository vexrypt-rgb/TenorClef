package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.util.helpers.WorldHelper;

public final class DeathWatch {

    public static final String FILE = "last_death.txt";

    private static int lastX, lastY, lastZ;
    private static String lastDim = "OVERWORLD";
    private static boolean wasAlive = true;

    private DeathWatch() {}

    public static void tick(AltoClef mod) {
        if (mod.getPlayer() == null) return;
        float hp = 20f;
        try { hp = mod.getPlayer().getHealth(); } catch (Throwable ignored) {}
        lastX = mod.getPlayer().getBlockX();
        lastY = mod.getPlayer().getBlockY();
        lastZ = mod.getPlayer().getBlockZ();
        try { lastDim = String.valueOf(WorldHelper.getCurrentDimension()); } catch (Throwable ignored) {}
        boolean alive = hp > 0f;
        if (wasAlive && !alive) {
            GameFiles.write(FILE, lastDim + " " + lastX + " " + lastY + " " + lastZ + "\n");
            GameFiles.write("autopsy.txt",
                    lastDim + " " + lastX + " " + lastY + " " + lastZ + "\n"
                            + adris.altoclef.tasks.speedrun.testrun2.T2Fault.recent() + "\n");
            Debug.logMessage("DEATH saved " + FILE + " + autopsy.txt");
            RunLog.line("death " + lastDim + " " + lastX + " " + lastY + " " + lastZ);
            adris.altoclef.tasks.speedrun.testrun2.T2Fault.record("E50",
                    lastDim + " " + lastX + "," + lastY + "," + lastZ);
        }
        wasAlive = alive;
    }

    public static HomeStore lastDeath() {
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
}
