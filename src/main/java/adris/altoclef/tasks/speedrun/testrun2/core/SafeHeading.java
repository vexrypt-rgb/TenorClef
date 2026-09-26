package adris.altoclef.tasks.speedrun.testrun2.core;

/**
 * Picks a turn for a blind "turn and walk" nudge that will not walk off a ledge or into lava.
 * <p>
 * Pure geometry over a {@link Terrain} probe so it can be unit-tested without Minecraft.
 * Observed failure it prevents: 2026-09-24 02:32, NETHER y=95, the S100 nudge turned 70° and
 * held forward for 2s; the bot walked off a ledge and died ("fell from a high place", 33 blocks).
 */
public final class SafeHeading {

    /** Turn offsets tried in order; the first is the historical +70°. */
    static final float[] TURNS = {70f, -70f, 140f, -140f, 180f};
    /** Blocks ahead that must be walkable. A 2s walk nudge covers ~4-8 blocks. */
    static final int LOOKAHEAD = 9;
    /** Largest drop accepted per step: 3 blocks is fall-damage free. */
    static final int MAX_DROP = 3;

    public enum Cell { OPEN, SOLID, LAVA }

    /** Terrain lookup by block coordinates. Unloaded chunks must report OPEN (treated as a drop). */
    public interface Terrain {
        Cell at(int x, int y, int z);
    }

    private SafeHeading() {}

    /**
     * @return the first turn offset (degrees) whose heading is safe, or {@code null} if none is.
     */
    public static Float pickTurn(Terrain terrain, double x, double y, double z, float yaw) {
        for (float turn : TURNS) {
            if (isSafe(terrain, x, y, z, yaw + turn)) return turn;
        }
        return null;
    }

    /** Minecraft yaw convention: 0° = +Z (south), 90° = -X (west). */
    public static boolean isSafe(Terrain terrain, double x, double y, double z, float yawDeg) {
        double r = Math.toRadians(yawDeg);
        double dx = -Math.sin(r);
        double dz = Math.cos(r);
        int feetY = (int) Math.floor(y);
        for (int d = 1; d <= LOOKAHEAD; d++) {
            int cx = (int) Math.floor(x + dx * d);
            int cz = (int) Math.floor(z + dz * d);
            Cell feet = terrain.at(cx, feetY, cz);
            if (feet == Cell.LAVA || terrain.at(cx, feetY + 1, cz) == Cell.LAVA) return false;
            if (feet == Cell.SOLID) {
                // A wall ends the walk; a one-block step does not (the bot steps/jumps up and
                // keeps going), so keep probing from the higher floor. s263t fell y71->30 into
                // Nether lava after a step was taken as "safe" without looking past it.
                if (terrain.at(cx, feetY + 1, cz) != Cell.OPEN) return true;
                feetY++;
                continue;
            }
            boolean ground = false;
            for (int k = 1; k <= MAX_DROP + 1; k++) {
                Cell below = terrain.at(cx, feetY - k, cz);
                if (below == Cell.LAVA) return false;
                if (below == Cell.SOLID) {
                    ground = true;
                    break;
                }
            }
            if (!ground) return false;
        }
        return true;
    }
}
