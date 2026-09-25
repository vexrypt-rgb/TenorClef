package adris.altoclef.tasks.speedrun.testrun2.core;

import adris.altoclef.tasks.speedrun.testrun2.core.SafeHeading.Cell;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class SafeHeadingTest {

    /** Terrain: solid floor at y=63 everywhere unless overridden; feet at y=64. */
    private static final class Grid implements SafeHeading.Terrain {
        final Map<Long, Cell> cells = new HashMap<>();
        final int floorY;

        Grid(int floorY) {
            this.floorY = floorY;
        }

        static long key(int x, int y, int z) {
            return (((long) x & 0xFFFFF) << 40) | (((long) y & 0xFFFFF) << 20) | ((long) z & 0xFFFFF);
        }

        Grid set(int x, int y, int z, Cell c) {
            cells.put(key(x, y, z), c);
            return this;
        }

        /** Remove the floor in a column (a hole down to the void). */
        Grid hole(int x, int z) {
            for (int y = floorY - 10; y <= floorY; y++) set(x, y, z, Cell.OPEN);
            return this;
        }

        @Override
        public Cell at(int x, int y, int z) {
            Cell c = cells.get(key(x, y, z));
            if (c != null) return c;
            return y <= floorY ? Cell.SOLID : Cell.OPEN;
        }
    }

    // Yaw 0 faces +Z. Player stands at block (0,64,0) centre.
    private static final double X = 0.5, Y = 64.0, Z = 0.5;

    @Test
    void flatGroundKeepsHistoricalTurn() {
        Assertions.assertEquals(70f, SafeHeading.pickTurn(new Grid(63), X, Y, Z, 0f));
    }

    @Test
    void cliffAheadOfFirstTurnPicksAnotherHeading() {
        // yaw 0 + 70 → heading toward -X. Remove floor for x <= -1: a cliff on the -X side.
        Grid g = new Grid(63);
        for (int x = -6; x <= -1; x++) for (int z = -6; z <= 6; z++) g.hole(x, z);
        Float turn = SafeHeading.pickTurn(g, X, Y, Z, 0f);
        Assertions.assertNotNull(turn);
        Assertions.assertNotEquals(70f, turn);
        Assertions.assertTrue(SafeHeading.isSafe(g, X, Y, Z, turn));
    }

    @Test
    void pillarTopHasNoSafeHeading() {
        // Standing on a 1x1 pillar over a void: every heading is a fall.
        Grid g = new Grid(63);
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) if (x != 0 || z != 0) g.hole(x, z);
        Assertions.assertNull(SafeHeading.pickTurn(g, X, Y, Z, 0f));
    }

    @Test
    void smallStepDownIsSafe() {
        // Floor drops by 3 (fall-damage free) on the +Z side.
        Grid g = new Grid(63);
        for (int z = 1; z <= 6; z++) for (int x = -6; x <= 6; x++) {
            g.set(x, 63, z, Cell.OPEN).set(x, 62, z, Cell.OPEN).set(x, 61, z, Cell.OPEN);
        }
        Assertions.assertTrue(SafeHeading.isSafe(g, X, Y, Z, 0f));
    }

    @Test
    void fourBlockDropIsUnsafe() {
        Grid g = new Grid(63);
        for (int z = 1; z <= 6; z++) for (int x = -6; x <= 6; x++) {
            for (int y = 59; y <= 63; y++) g.set(x, y, z, Cell.OPEN);
        }
        Assertions.assertFalse(SafeHeading.isSafe(g, X, Y, Z, 0f));
    }

    @Test
    void lavaAheadIsUnsafe() {
        Grid g = new Grid(63).set(0, 63, 2, Cell.LAVA).set(0, 64, 2, Cell.OPEN);
        g.set(0, 63, 2, Cell.OPEN).set(0, 62, 2, Cell.LAVA);
        Assertions.assertFalse(SafeHeading.isSafe(g, X, Y, Z, 0f));
    }

    @Test
    void wallAheadIsSafeBump() {
        Grid g = new Grid(63).set(0, 64, 2, Cell.SOLID).set(0, 65, 2, Cell.SOLID);
        Assertions.assertTrue(SafeHeading.isSafe(g, X, Y, Z, 0f));
    }

    @Test
    void unloadedTerrainCountsAsDrop() {
        SafeHeading.Terrain nothing = (x, y, z) -> Cell.OPEN;
        Assertions.assertNull(SafeHeading.pickTurn(nothing, X, Y, Z, 0f));
    }
}
