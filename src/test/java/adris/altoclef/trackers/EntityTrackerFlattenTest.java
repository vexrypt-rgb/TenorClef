package adris.altoclef.trackers;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour + cost of the drop-list flattening used by {@link EntityTracker#getDroppedItems()}.
 * The flattening replaces a {@code values().stream().reduce(new ArrayList<>(), ...)} that mutated
 * its own identity value. No Minecraft runtime is required: only the pure helper is exercised.
 */
public class EntityTrackerFlattenTest {

    @Test
    public void emptyMapYieldsEmptyMutableList() {
        List<String> flat = EntityTracker.flattenValues(Collections.emptyMap());
        assertTrue(flat.isEmpty());
        flat.add("x"); // callers may append to the result, as with the old ArrayList identity value
    }

    @Test
    public void preservesBucketOrderAndDuplicates() {
        Map<String, List<String>> buckets = new LinkedHashMap<>();
        buckets.put("a", Arrays.asList("a1", "a2"));
        buckets.put("b", Collections.emptyList());
        buckets.put("c", Arrays.asList("c1", "a1"));

        List<String> expected = new ArrayList<>();
        for (List<String> values : buckets.values()) {
            expected.addAll(values);
        }
        assertEquals(expected, EntityTracker.flattenValues(buckets));
    }

    @Test
    public void measuredAgainstTheStreamReduceItReplaced() {
        Map<String, List<String>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < 40; i++) {
            buckets.put("item" + i, Arrays.asList("a" + i, "b" + i, "c" + i));
        }

        // Warm up both shapes before timing.
        for (int i = 0; i < 20_000; i++) {
            sink = legacyStreamReduce(buckets);
            sink = EntityTracker.flattenValues(buckets);
        }

        long legacyNs = bestOf(buckets, true);
        long loopNs = bestOf(buckets, false);
        System.out.printf("[getDroppedItems] stream+reduce %.1f ns/call, pre-sized loop %.1f ns/call (%.2fx)%n",
                (double) legacyNs, (double) loopNs, (double) loopNs / (double) legacyNs);

        // Correctness must hold regardless of timings; no timing assertion is made (CI noise).
        assertEquals(legacyStreamReduce(buckets), EntityTracker.flattenValues(buckets));
    }

    private static Object sink;
    private static final int ITERATIONS = 50_000;

    private static long bestOf(Map<String, List<String>> buckets, boolean legacy) {
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                sink = legacy ? legacyStreamReduce(buckets) : EntityTracker.flattenValues(buckets);
            }
            best = Math.min(best, System.nanoTime() - start);
        }
        return best / ITERATIONS;
    }

    // The exact shape used by EntityTracker.getDroppedItems() before this change.
    private static List<String> legacyStreamReduce(Map<String, List<String>> buckets) {
        return buckets.values().stream().reduce(new ArrayList<>(), (result, drops) -> {
            result.addAll(drops);
            return result;
        });
    }
}
