package adris.altoclef.util.helpers;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class NearestBlockSelectorTest {

    @Test
    void prefersNearbyJungleOverFarOak() {
        // Player at origin; jungle at (3,5,0), oak at (40,0,0)
        int[] x = {40, 3};
        int[] y = {0, 5};
        int[] z = {0, 0};
        Optional<Integer> idx = NearestBlockSelector.nearestIndex(0, 1, 0, x, y, z);
        assertTrue(idx.isPresent());
        assertEquals(1, idx.get().intValue(), "nearby elevated jungle must beat far flat oak");
    }

    @Test
    void woodTypeOrderDoesNotMatter() {
        // Same positions, oak listed first then jungle — still picks jungle
        int[] x = {40, 3};
        int[] y = {0, 4};
        int[] z = {0, 1};
        assertEquals(1, NearestBlockSelector.nearestIndex(0, 1, 0, x, y, z).orElse(-1).intValue());
        // Reverse order
        int[] x2 = {3, 40};
        int[] y2 = {4, 0};
        int[] z2 = {1, 0};
        assertEquals(0, NearestBlockSelector.nearestIndex(0, 1, 0, x2, y2, z2).orElse(-1).intValue());
    }

    @Test
    void emptyCandidates() {
        assertTrue(NearestBlockSelector.nearestIndex(0, 0, 0, new int[0], new int[0], new int[0]).isEmpty());
        assertTrue(NearestBlockSelector.nearestIndex(0, 0, 0, null, null, null).isEmpty());
    }

    @Test
    void shouldRetargetWhenClearlyCloser() {
        assertTrue(NearestBlockSelector.shouldRetarget(100, 40));
        assertTrue(NearestBlockSelector.shouldRetarget(Double.POSITIVE_INFINITY, 9));
        assertFalse(NearestBlockSelector.shouldRetarget(9, 100));
        assertTrue(NearestBlockSelector.shouldRetarget(25, 9)); // within 4 blocks improvement
    }
}
