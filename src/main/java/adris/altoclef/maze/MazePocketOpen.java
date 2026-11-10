package adris.altoclef.maze;

/**
 * Which solid to mine so a sandwiched seal hole can be clicked.
 *
 * <p>Ostinato: run141. Seal {@code -35,94,-22} was air between dirt and coal ore. The stand at
 * {@code target.y - 2} was the cobblestone itself, so the bot walked at a solid (no-progress dist 3.5)
 * and skipped the cell. Candidates are ordered lid, block under the hole, four sides, then the
 * target-column blocks between the player and the hole, nearest first.
 */
public final class MazePocketOpen {

    private MazePocketOpen() {}

    /**
     * @param solid full block
     * @param place one of the blocks this step places (a finished seal material)
     * @param hit the current eye can already raycast it
     * @param bedrock never mined
     * @param column sits in the target column between the player and the hole, so a place-block there is
     *                the ceiling the old fallback tried to stand inside
     * @return index to mine, or -1 when nothing is safe to open
     */
    public static int choose(boolean[] solid, boolean[] place, boolean[] hit, boolean[] bedrock, boolean[] column) {
        int n = solid.length;
        for (int i = 0; i < n; i++) {
            if (hit[i] && mineable(solid[i], place[i], bedrock[i], column[i])) return i;
        }
        // Nothing in reach. Walk to the lid or the block under the hole when it is not a seal we placed.
        for (int i = 0; i < n; i++) {
            if (!column[i] && mineable(solid[i], place[i], bedrock[i], false)) return i;
        }
        return -1;
    }

    private static boolean mineable(boolean solid, boolean place, boolean bedrock, boolean column) {
        if (!solid || bedrock) return false;
        return column || !place;
    }
}
