package adris.altoclef.maze;

/**
 * Ostinato: 2026-11-02. The carved room, in world blocks, taken from the furnish job.
 * Rooms are 5x5 up through 13x5 and 11x11, with a clear height of 4 to 6. Nothing here assumes 7.
 * No Minecraft types, so the span is unit tested on every version line.
 */
public final class MazeRoomBounds {

    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    private MazeRoomBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /**
     * @param triples x,y,z repeating. Empty or short input returns null (an older job with no carved cells).
     */
    public static MazeRoomBounds fromTriples(int[] triples) {
        if (triples == null || triples.length < 3) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int n = 0;
        for (int i = 0; i + 2 < triples.length; i += 3) {
            int x = triples[i], y = triples[i + 1], z = triples[i + 2];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            n++;
        }
        if (n == 0) return null;
        return new MazeRoomBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Blocks from the low corner to the high corner, inclusive. A single cell is 1. */
    public int spanX() {
        return maxX - minX + 1;
    }

    public int spanY() {
        return maxY - minY + 1;
    }

    public int spanZ() {
        return maxZ - minZ + 1;
    }

    /** Interior clear height when the triples are the carved air cells. */
    public int clearHeight() {
        return spanY();
    }

    public int centerX() {
        return (minX + maxX) / 2;
    }

    public int centerZ() {
        return (minZ + maxZ) / 2;
    }

    /** One above the highest carved cell: the roof, outside the room, so a leash walks to the shell. */
    public int roofY() {
        return maxY + 1;
    }
}
