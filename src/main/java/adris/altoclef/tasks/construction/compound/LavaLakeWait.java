package adris.altoclef.tasks.construction.compound;

/**
 * Ostinato: S310. How long to keep looking for surface lava before taking a lower lake.
 *
 * <p>S216 waits 3 minutes before a deep lake because a y=9 shaft cost 35 minutes and a kit.
 * testrun-20261103-102039 had the opposite shape: an open pool, area 64, top at y=17. The
 * 3-minute surface wander never found a higher lake, walked into a skeleton, and the run
 * dropped back to iron. A wide open top at or above y=12 is a cavern pool, so the wait is
 * short. A small pool or anything below y=12 keeps the long wait.
 */
public final class LavaLakeWait {

    public static final int SAFE_Y = 40;
    public static final int MID_Y = 25;
    /** Open still-source count that means a pool you can stand beside, not a shaft. */
    public static final int WIDE_AREA = 12;
    public static final int WIDE_MIN_Y = 12;

    public static final long SURFACE_MS = 0L;
    public static final long MID_MS = 45_000L;
    public static final long WIDE_CAVERN_MS = 20_000L;
    public static final long DEEP_MS = 180_000L;
    public static final long DEEPSLATE_MS = 360_000L;

    private LavaLakeWait() {}

    public static long waitMs(int topY, int surfaceArea) {
        if (topY >= SAFE_Y) return SURFACE_MS;
        if (topY >= MID_Y) return MID_MS;
        if (topY >= WIDE_MIN_Y && surfaceArea >= WIDE_AREA) return WIDE_CAVERN_MS;
        if (topY < 0) return DEEPSLATE_MS;
        return DEEP_MS;
    }
}
