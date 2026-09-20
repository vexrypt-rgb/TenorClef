package adris.altoclef.util.helpers;

import java.util.Optional;

/**
 * Pure nearest-block pick among typed candidates.
 * <p>
 * Uses squared Euclidean distance so wood/ore type never biases the choice —
 * {@code GoalBlock} heuristics penalize Y heavily and can prefer a far oak over
 * a nearby elevated jungle log.
 */
public final class NearestBlockSelector {

    private NearestBlockSelector() {}

    /**
     * @param fromX/Y/Z origin (typically player eye / feet)
     * @param candX/Y/Z parallel arrays of candidate block positions (same length)
     * @return index of nearest candidate, or empty if none
     */
    public static Optional<Integer> nearestIndex(
            double fromX, double fromY, double fromZ,
            int[] candX, int[] candY, int[] candZ
    ) {
        if (candX == null || candY == null || candZ == null) return Optional.empty();
        int n = Math.min(candX.length, Math.min(candY.length, candZ.length));
        if (n == 0) return Optional.empty();
        int best = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double d = squaredDistance(fromX, fromY, fromZ, candX[i] + 0.5, candY[i] + 0.5, candZ[i] + 0.5);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best < 0 ? Optional.empty() : Optional.of(best);
    }

    /** Prefer {@code next} over {@code current} when clearly closer (wood-agnostic retarget). */
    public static boolean shouldRetarget(double currentSq, double nextSq) {
        if (Double.isInfinite(currentSq) || currentSq < 0) return true;
        if (Double.isInfinite(nextSq) || nextSq < 0) return false;
        // Switch if next is at least ~12.5% closer (or any improvement under 4 blocks).
        return nextSq + 1e-9 < currentSq * 0.875 || (nextSq < currentSq && nextSq <= 16.0);
    }

    public static double squaredDistance(
            double ax, double ay, double az,
            double bx, double by, double bz
    ) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double squaredDistanceBlock(
            double fromX, double fromY, double fromZ,
            int blockX, int blockY, int blockZ
    ) {
        return squaredDistance(fromX, fromY, fromZ, blockX + 0.5, blockY + 0.5, blockZ + 0.5);
    }
}
