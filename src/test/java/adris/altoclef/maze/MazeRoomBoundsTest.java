package adris.altoclef.maze;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Ostinato: 2026-11-02. Room span comes from the carved cells. A 7x7 is not assumed. */
public class MazeRoomBoundsTest {

    @Test
    void elevenByElevenBySixIsNotSeven() {
        MazeRoomBounds room = MazeRoomBounds.fromTriples(box(0, 0, 0, 11, 6, 11));
        Assertions.assertNotNull(room);
        Assertions.assertEquals(11, room.spanX());
        Assertions.assertEquals(11, room.spanZ());
        Assertions.assertEquals(6, room.clearHeight());
        Assertions.assertNotEquals(7, room.spanX());
        Assertions.assertEquals(5, room.centerX());
        Assertions.assertEquals(6, room.roofY());
    }

    @Test
    void thirteenByFiveAndFiveByFiveKeepTheirOwnSpan() {
        MazeRoomBounds wide = MazeRoomBounds.fromTriples(box(100, 64, -20, 13, 5, 5));
        MazeRoomBounds small = MazeRoomBounds.fromTriples(box(100, 64, -20, 5, 4, 5));
        Assertions.assertEquals(13, wide.spanX());
        Assertions.assertEquals(5, wide.spanZ());
        Assertions.assertEquals(5, wide.clearHeight());
        Assertions.assertEquals(5, small.spanX());
        Assertions.assertEquals(4, small.clearHeight());
        Assertions.assertEquals(100, small.minX);
        Assertions.assertEquals(104, small.maxX);
    }

    @Test
    void emptyJobHasNoBounds() {
        Assertions.assertNull(MazeRoomBounds.fromTriples(null));
        Assertions.assertNull(MazeRoomBounds.fromTriples(new int[0]));
        Assertions.assertNull(MazeRoomBounds.fromTriples(new int[]{1, 2}));
    }

    @Test
    void aSixHighFloorStandReachesTheCeilingFace() {
        // Feet 6 below the ceiling block, eye at +1.62. The face is inside 4.5; the centre is not.
        int ceiling = 80;
        double eyeY = ceiling - 6 + 1.62;
        Assertions.assertTrue(MazeReach.reaches(0.5, eyeY, 0.5, 0, ceiling, 0));
        double centre = Math.abs((ceiling + 0.5) - eyeY);
        Assertions.assertTrue(centre > 4.1, "the old centre cutoff of 4.1 rejected this stand");
        Assertions.assertFalse(MazeReach.reaches(0.5, ceiling - 8 + 1.62, 0.5, 0, ceiling, 0));
        Assertions.assertEquals(8, MazeReach.STAND_DOWN);
    }

    /** Inclusive box of sizeX x sizeY x sizeZ starting at the origin corner. */
    private static int[] box(int ox, int oy, int oz, int sizeX, int sizeY, int sizeZ) {
        int[] triples = new int[sizeX * sizeY * sizeZ * 3];
        int i = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    triples[i++] = ox + x;
                    triples[i++] = oy + y;
                    triples[i++] = oz + z;
                }
            }
        }
        return triples;
    }
}
