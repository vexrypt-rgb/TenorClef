package adris.altoclef.maze;

/**
 * Ostinato: 2026-11-02. How far a survival player can click, and how far under a ceiling that still is.
 * No Minecraft types. The old check measured to the block centre and rejected the floor of a 6-high room:
 * eye at 1.62, ceiling centre 6.5 above the feet, distance 4.88, and the cutoff was 4.1.
 * The face itself is 4.38 away, inside the vanilla 4.5 reach.
 */
public final class MazeReach {

    /** Vanilla survival block-interaction distance. */
    public static final double SURVIVAL = 4.5;

    /**
     * Feet this far under a ceiling cell are still searched. Clear height 6 puts the floor stand 6 below the
     * ceiling block; 8 leaves a block of slack for the shell under that floor. This is not a room width.
     */
    public static final int STAND_DOWN = 8;

    private MazeReach() {}

    /** Distance from an eye to the nearest point of the block's 1x1x1 box. */
    public static double faceDistance(double ex, double ey, double ez, int bx, int by, int bz) {
        double x = clamp(ex, bx, bx + 1.0);
        double y = clamp(ey, by, by + 1.0);
        double z = clamp(ez, bz, bz + 1.0);
        double dx = ex - x, dy = ey - y, dz = ez - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static boolean reaches(double ex, double ey, double ez, int bx, int by, int bz) {
        return faceDistance(ex, ey, ez, bx, by, bz) <= SURVIVAL;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
