package adris.altoclef.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TravelTraceTest {

    @Test
    void arrivedRecordCarriesAllFields() {
        TravelTrace t = new TravelTrace("GoalBlock", 0, 64, 0, 3, 64, 0, "TUNGSTEN");
        t.tick(0, 64, 0, false);
        t.tick(0, 64, 0, true);
        t.dispatched("BARITONE", true);
        t.tick(1, 64, 0, true);
        t.tick(3, 64, 0, true);
        String line = t.finish(true, null);
        assertEquals(4, t.ticks());
        assertEquals(2, t.firstPathingTick());
        assertEquals(3.0, t.traveled(), 1e-9);
        assertEquals(2, t.stallTicks());
        assertTrue(line.contains("requested=TUNGSTEN executed=BARITONE fallback=true"), line);
        assertTrue(line.contains("outcome=ARRIVED reason=- verified=true"), line);
        assertTrue(line.contains("remaining=0.0"), line);
    }

    @Test
    void failureAndTeleportAndIdempotentFinish() {
        TravelTrace t = new TravelTrace("GoalBlock", 0, 64, 0, 100, 64, 0, "BARITONE");
        t.tick(0, 64, 0, false);
        t.tick(500, 64, 0, false); // teleport: not counted as travel
        String line = t.finish(false, "TIMEOUT");
        assertEquals(0.0, t.traveled(), 1e-9);
        assertTrue(line.contains("executed=NONE fallback=false pathFoundTick=-1"), line);
        assertTrue(line.contains("outcome=FAILED reason=TIMEOUT verified=false"), line);
        t.tick(1, 64, 0, true);
        assertEquals(2, t.ticks());
        assertSame(line, t.finish(true, null));
    }

    @Test
    void fallbackIsSticky() {
        TravelTrace t = new TravelTrace("g", 0, 0, 0, 0, 0, 0, "BARITONE");
        t.dispatched("CUSTOM_GOAL_PROCESS", true);
        t.dispatched("BARITONE", false);
        assertTrue(t.fallback());
        assertEquals("BARITONE", t.executed());
    }
}
