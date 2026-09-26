package adris.altoclef.util.helpers;

import adris.altoclef.movement.TungstenMovement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * testrun2's view of Tungsten, backed by the real {@link TungstenMovement} bridge.
 * (This used to be an all-false stub, so @testrun2 never used Tungsten even with the jar present.)
 *
 * "Primary" = the travel mover preference is not BARITONE and the Tungsten jar is bound.
 * Everything falls back to Baritone when Tungsten is missing.
 */
public final class TungstenHelper {

    /** Callers like combat ask every tick with a moving target; each request restarts the search. */
    private static final long MIN_REQUEST_GAP_MS = 1000L;
    private static long lastRequestMs;
    private static BlockPos lastTarget;

    private TungstenHelper() {}

    public static void setPrimary(boolean primary) {
        TungstenMovement.setTravelMover(primary ? TungstenMovement.TravelMover.TUNGSTEN
                : TungstenMovement.TravelMover.BARITONE);
    }

    public static boolean isPrimary() {
        return TungstenMovement.getTravelMover() != TungstenMovement.TravelMover.BARITONE
                && TungstenMovement.isAvailable();
    }

    public static boolean isTungstenLoaded() {
        return TungstenMovement.isAvailable();
    }

    public static boolean isActive() {
        return TungstenMovement.isAvailable() && TungstenMovement.isPathing();
    }

    public static boolean isLocked() {
        return isActive();
    }

    public static boolean tryPathTo(Vec3d dest) {
        if (dest == null || !isPrimary()) return false;
        BlockPos pos = new BlockPos(MathHelper.floor(dest.x), MathHelper.floor(dest.y), MathHelper.floor(dest.z));
        long now = System.currentTimeMillis();
        boolean sameTarget = lastTarget != null && lastTarget.getSquaredDistance(pos) <= 2;
        if (now - lastRequestMs < MIN_REQUEST_GAP_MS || (sameTarget && TungstenMovement.isPathing())) {
            return TungstenMovement.isPathing();
        }
        lastRequestMs = now;
        lastTarget = pos;
        return TungstenMovement.requestPathTo(pos);
    }

    public static void stop() {
        lastTarget = null;
        if (TungstenMovement.isAvailable()) TungstenMovement.cancel();
    }
}
