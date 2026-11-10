package adris.altoclef.trackers.storage;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cost of the idiom {@link ItemStorageTracker} used to run on every {@code getItemCount}/{@code hasItem} call, i.e. on
 * every task's {@code isFinished}/{@code isStartable}: {@code Arrays.stream(slots).mapToInt(...).reduce(0, Integer::sum)}.
 *
 * <p>What this measures: the JDK cost of that pipeline (empty-array allocation is not included — it is the caller's
 * {@code new Slot[0]}) against the plain loop that replaced it, for the two shapes that actually occur: no screen open
 * (0 conversion slots) and a furnace-like screen (2 conversion slots).
 *
 * <p>What this does NOT measure: the surrounding inventory lookups, or the end-to-end effect on the client tick. No
 * timing assertion is made (CI noise); only the equality assertion is a real test.
 */
public class ConversionSlotScanCostTest {

    private static final int ITERATIONS = 200_000;
    private static Object sink;

    @Test
    public void emptyAndTwoSlotScanCost() {
        Object[] none = new Object[0];
        Object[] two = {"fuel", "material"};

        assertEquals(viaStream(none), viaLoop(none));
        assertEquals(viaStream(two), viaLoop(two));

        // Warm both shapes up before timing.
        for (int i = 0; i < 50_000; i++) {
            sink = viaStream(none);
            sink = viaLoop(none);
            sink = viaStream(two);
            sink = viaLoop(two);
        }

        long emptyStream = bestOf(none, true);
        long emptyLoop = bestOf(none, false);
        long twoStream = bestOf(two, true);
        long twoLoop = bestOf(two, false);

        System.out.printf("[conversionSlots] 0 slots: stream %.1f ns/call, loop %.1f ns/call (%.2fx)%n",
                (double) emptyStream, (double) emptyLoop, (double) emptyLoop / (double) emptyStream);
        System.out.printf("[conversionSlots] 2 slots: stream %.1f ns/call, loop %.1f ns/call (%.2fx)%n",
                (double) twoStream, (double) twoLoop, (double) twoLoop / (double) twoStream);
    }

    private static long bestOf(Object[] slots, boolean stream) {
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                sink = stream ? viaStream(slots) : viaLoop(slots);
            }
            best = Math.min(best, System.nanoTime() - start);
        }
        return best / ITERATIONS;
    }

    // The exact shape ItemStorageTracker.getItemCount(Item...) used before this change.
    private static int viaStream(Object[] slots) {
        return Arrays.stream(slots).mapToInt(ConversionSlotScanCostTest::countOf).reduce(0, Integer::sum);
    }

    // The shape that replaced it.
    private static int viaLoop(Object[] slots) {
        int total = 0;
        for (Object slot : slots) {
            total += countOf(slot);
        }
        return total;
    }

    // A per-slot body that is identical on both sides, so the difference measured is the pipeline itself.
    private static int countOf(Object slot) {
        return slot == null ? 0 : 1;
    }
}
