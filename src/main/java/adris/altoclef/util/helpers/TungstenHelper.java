package adris.altoclef.util.helpers;

import net.minecraft.util.math.Vec3d;

/**
 * MiranCZ AltoClef does not ship Tungsten.
 * Drop this file into src/main/java/adris/altoclef/util/helpers/
 * ONLY if that class is missing (do not overwrite UnionClef's real helper).
 *
 * All methods no-op / return false so TungstenMoveTask uses GetToBlockTask
 * (Baritone) instead of locking a pathfinder that isn't there.
 */
public final class TungstenHelper {

    private TungstenHelper() {}

    public static void setPrimary(boolean primary) {}

    public static boolean isPrimary() {
        return false;
    }

    public static boolean isTungstenLoaded() {
        return false;
    }

    public static boolean isActive() {
        return false;
    }

    public static boolean isLocked() {
        return false;
    }

    public static boolean tryPathTo(Vec3d dest) {
        return false;
    }

    public static void stop() {}
}
