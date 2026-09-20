package adris.altoclef.util.helpers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NearbyPlacePenaltyTest {

    @Test
    void insideRejected() {
        assertTrue(Double.isInfinite(NearbyPlacePenalty.penalty(false, true, true)));
    }

    @Test
    void flatOpenPreferred() {
        assertEquals(0.0, NearbyPlacePenalty.penalty(false, true, false));
        assertEquals(10.0, NearbyPlacePenalty.penalty(false, false, false));
        assertEquals(4.0, NearbyPlacePenalty.penalty(true, true, false));
    }

    @Test
    void containerRejectsFeet() {
        assertTrue(NearbyPlacePenalty.rejectContainerSpot(true, false, false));
        assertTrue(NearbyPlacePenalty.rejectContainerSpot(false, true, false));
        assertTrue(NearbyPlacePenalty.rejectContainerSpot(false, false, true));
        assertFalse(NearbyPlacePenalty.rejectContainerSpot(false, false, false));
    }
}
