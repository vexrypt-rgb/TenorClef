package adris.altoclef.util.helpers;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Pure shore-stand selection for scooping liquids from dry footing.
 * No Minecraft types — callers adapt BlockPos / world queries.
 * <p>
 * Stand candidates are the four horizontal neighbors of {@code liquid}
 * at the same Y. A candidate is valid when:
 * <ul>
 *   <li>{@code solidUnder} — block under the stand feet is solid</li>
 *   <li>{@code airFeet} — stand feet cell is air (not liquid / solid)</li>
 *   <li>{@code airHead} — stand head cell (feet+1) is air</li>
 *   <li>not itself the liquid column ({@code isLiquidColumn} false)</li>
 * </ul>
 * Among valid candidates, picks the one closest (squared) to the player.
 */
public final class ShoreStandSelector {

    private ShoreStandSelector() {}

    /** Horizontal offsets: +X, -X, +Z, -Z. */
    public static final int[][] HORIZONTAL = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /**
     * @param liquidX/Y/Z liquid source block
     * @param playerX/Y/Z player feet block (for nearest pick)
     * @param solidUnder  true if block at (x,y-1,z) is solid footing
     * @param airFeet     true if block at (x,y,z) is air
     * @param airHead     true if block at (x,y+1,z) is air
     * @param isLiquidColumn true if stand cell is the liquid we're collecting
     * @return Optional of int[3] {x,y,z} stand feet, or empty
     */
    public static Optional<int[]> select(
            int liquidX, int liquidY, int liquidZ,
            int playerX, int playerY, int playerZ,
            Predicate<int[]> solidUnder,
            Predicate<int[]> airFeet,
            Predicate<int[]> airHead,
            Predicate<int[]> isLiquidColumn
    ) {
        int[] best = null;
        long bestDist = Long.MAX_VALUE;
        for (int[] off : HORIZONTAL) {
            int[] stand = {liquidX + off[0], liquidY + off[1], liquidZ + off[2]};
            if (isLiquidColumn.test(stand)) continue;
            if (!solidUnder.test(stand)) continue;
            if (!airFeet.test(stand)) continue;
            if (!airHead.test(stand)) continue;
            long dx = (long) stand[0] - playerX;
            long dy = (long) stand[1] - playerY;
            long dz = (long) stand[2] - playerZ;
            long d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = stand;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best);
    }

    /**
     * Whether the player should escape water before trying to mine/scoop:
     * wet and not on solid ground.
     */
    public static boolean shouldEscapeBeforeInteract(boolean touchingOrSubmerged, boolean onGround) {
        return touchingOrSubmerged && !onGround;
    }

    /**
     * Whether scoop/interact is allowed from current footing:
     * either dry, or shallow water with ground contact.
     */
    public static boolean canScoopFromFooting(boolean touchingOrSubmerged, boolean onGround) {
        return !touchingOrSubmerged || onGround;
    }
}
