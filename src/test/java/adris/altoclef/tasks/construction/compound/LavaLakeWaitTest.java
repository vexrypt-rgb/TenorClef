package adris.altoclef.tasks.construction.compound;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** S310: the y=17 area-64 pool from testrun-20261103-102039 must not wait 3 minutes. */
public class LavaLakeWaitTest {

    @Test
    public void wideCavernAtY17WaitsTwentySeconds() {
        Assertions.assertEquals(LavaLakeWait.WIDE_CAVERN_MS, LavaLakeWait.waitMs(17, 64));
    }

    @Test
    public void smallPoolAtY17KeepsTheShaftWait() {
        Assertions.assertEquals(LavaLakeWait.DEEP_MS, LavaLakeWait.waitMs(17, 8));
    }

    @Test
    public void surfaceAndMidUnchanged() {
        Assertions.assertEquals(LavaLakeWait.SURFACE_MS, LavaLakeWait.waitMs(64, 10));
        Assertions.assertEquals(LavaLakeWait.MID_MS, LavaLakeWait.waitMs(33, 64));
    }

    @Test
    public void deepslateAndLowShaftStayLong() {
        Assertions.assertEquals(LavaLakeWait.DEEPSLATE_MS, LavaLakeWait.waitMs(-55, 64));
        Assertions.assertEquals(LavaLakeWait.DEEP_MS, LavaLakeWait.waitMs(9, 64));
    }
}
