package adris.altoclef.chains;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * S309: a drowning escape stays owned after the eyes clear the surface, and a bob that never
 * reaches dry ground gives up instead of holding the chain forever.
 */
public class DrowningEscapeLatchTest {

    @Test
    public void holdsAfterEyesClearUntilDryFooting() {
        DrowningEscapeLatch latch = new DrowningEscapeLatch();
        long t = 1_000_000L;
        Assertions.assertEquals(DrowningEscapeLatch.Decision.HOLD,
                latch.tick(true, false, 0, 63, 0, t));
        // Eyes above water, still touching it: the old chain released here.
        Assertions.assertEquals(DrowningEscapeLatch.Decision.HOLD,
                latch.tick(false, false, 1, 63, 0, t + 400));
        // One dry tick is not enough.
        Assertions.assertEquals(DrowningEscapeLatch.Decision.HOLD,
                latch.tick(false, true, 2, 63, 0, t + 500));
        Assertions.assertEquals(DrowningEscapeLatch.Decision.DONE_DRY,
                latch.tick(false, true, 2, 63, 0, t + 500 + DrowningEscapeLatch.DRY_CONFIRM_MS));
        Assertions.assertFalse(latch.isLatched());
    }

    @Test
    public void bobBackIntoWaterResetsDryConfirm() {
        DrowningEscapeLatch latch = new DrowningEscapeLatch();
        long t = 5_000L;
        latch.tick(true, false, 0, 62, 0, t);
        Assertions.assertEquals(DrowningEscapeLatch.Decision.HOLD,
                latch.tick(false, true, 0, 63, 0, t + 1_000));
        // Sank again before 750 ms. Confirm must restart.
        Assertions.assertEquals(DrowningEscapeLatch.Decision.HOLD,
                latch.tick(true, false, 0, 62, 0, t + 1_400));
        Assertions.assertEquals(DrowningEscapeLatch.Decision.HOLD,
                latch.tick(false, true, 1, 63, 0, t + 1_500));
        Assertions.assertEquals(DrowningEscapeLatch.Decision.DONE_DRY,
                latch.tick(false, true, 1, 63, 0, t + 1_500 + DrowningEscapeLatch.DRY_CONFIRM_MS));
    }

    @Test
    public void noProgressGivesUpAndCooldownIgnoresRetrigger() {
        DrowningEscapeLatch latch = new DrowningEscapeLatch();
        long t = 10_000L;
        Assertions.assertEquals(DrowningEscapeLatch.Decision.HOLD,
                latch.tick(true, false, 5, 60, 5, t));
        // Bobbing in place (under 3 blocks) until the stuck window.
        Assertions.assertEquals(DrowningEscapeLatch.Decision.HOLD,
                latch.tick(true, false, 6, 61, 5, t + DrowningEscapeLatch.STUCK_MS - 1));
        Assertions.assertEquals(DrowningEscapeLatch.Decision.GIVE_UP,
                latch.tick(true, false, 6, 61, 5, t + DrowningEscapeLatch.STUCK_MS));
        Assertions.assertEquals(DrowningEscapeLatch.Decision.IDLE,
                latch.tick(true, false, 6, 61, 5, t + DrowningEscapeLatch.STUCK_MS + 1));
        Assertions.assertEquals(DrowningEscapeLatch.Decision.HOLD,
                latch.tick(true, false, 6, 61, 5,
                        t + DrowningEscapeLatch.STUCK_MS + DrowningEscapeLatch.COOLDOWN_MS));
    }

    @Test
    public void movementResetsStuckTimer() {
        DrowningEscapeLatch latch = new DrowningEscapeLatch();
        long t = 20_000L;
        latch.tick(true, false, 0, 62, 0, t);
        long moved = t + DrowningEscapeLatch.STUCK_MS - 1_000;
        Assertions.assertEquals(DrowningEscapeLatch.Decision.HOLD,
                latch.tick(true, false, 4, 62, 0, moved));
        // Another stuck window from the new anchor, not from the original trigger.
        Assertions.assertEquals(DrowningEscapeLatch.Decision.HOLD,
                latch.tick(true, false, 4, 63, 0, moved + DrowningEscapeLatch.STUCK_MS - 1));
        Assertions.assertEquals(DrowningEscapeLatch.Decision.GIVE_UP,
                latch.tick(true, false, 5, 63, 0, moved + DrowningEscapeLatch.STUCK_MS));
    }

    @Test
    public void idleUntilTrigger() {
        DrowningEscapeLatch latch = new DrowningEscapeLatch();
        Assertions.assertEquals(DrowningEscapeLatch.Decision.IDLE,
                latch.tick(false, false, 0, 64, 0, 0));
        Assertions.assertFalse(latch.isLatched());
    }
}
