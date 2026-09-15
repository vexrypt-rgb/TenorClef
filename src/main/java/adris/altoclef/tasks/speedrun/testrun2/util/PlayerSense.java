package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Nearby-player pause. Off unless a task calls {@link #enable()}.
 * Do not use on testrun2 / singleplayer.
 */
public final class PlayerSense {

    private static boolean enabled;
    private static boolean frozen;
    private static int sinceLog;

    private PlayerSense() {}

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
        frozen = false;
    }

    public static boolean frozen() {
        return enabled && frozen;
    }

    public static void tick(AltoClef mod) {
        frozen = false;
        if (!enabled) return;
        if (mod.getPlayer() == null || mod.getWorld() == null) return;
        try {
            for (PlayerEntity p : mod.getWorld().getPlayers()) {
                if (p == null || p == mod.getPlayer()) continue;
                if (p.squaredDistanceTo(mod.getPlayer()) < 48 * 48) {
                    frozen = true;
                    if (sinceLog <= 0) {
                        Debug.logMessage("SENSE other player nearby — pausing personal task");
                        RunLog.line("pause other-player");
                        sinceLog = 20 * 15;
                    }
                    break;
                }
            }
        } catch (Throwable ignored) {}
        if (sinceLog > 0) sinceLog--;
    }
}
