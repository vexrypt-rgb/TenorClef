package adris.altoclef.trackers.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The per-tick cost of {@code InventorySubTracker.updateState()}.
 *
 * <p>{@code TrackerManager.tick()} marks every tracker dirty on every client tick, so this ran 46 slot reads and
 * rebuilt four {@code HashMap}s plus one {@code ArrayList} per distinct item, every tick, for an inventory that
 * usually has not changed since the previous tick. The change adds an exact (item identity + count, not hashed)
 * snapshot compare in front of the rebuild, and caches the immutable {@code Slot} wrappers for the current screen.
 *
 * <p>What this test measures: the map/list machinery only — the model below is the same code shape as production,
 * over {@code String} keys instead of {@code Item}, because {@code Item} needs a bootstrapped registry. The slot
 * reads ({@code StorageHelper.getItemStackInSlot}) are identical on both sides and are therefore excluded; the
 * absolute numbers are not client-tick numbers.
 *
 * <p>What this test asserts for real: (1) the fast path publishes maps equal to a full rebuild for any content, and
 * (2) when the content is unchanged it publishes <em>the same objects</em>, i.e. it did not rebuild.
 */
public class InventorySnapshotCostTest {

    private static final int SLOTS = 46;
    /** Distinct item types, so the maps hold a realistic number of buckets. */
    private static final String[] POOL = {
            "pickaxe", "axe", "shovel", "dirt", "cobblestone", "torch", "log", "planks",
            "stick", "iron_ingot", "coal", "bread", "bed", "barrel", "glass", "wool"
    };
    private static final Object EMPTY = "air";

    private static Object sink;

    private static Object[] mazeInventory() {
        // 46 slots, ~30 occupied, the rest air — a mid-run survival inventory.
        Object[] contents = new Object[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            contents[i] = (i % 3 == 0) ? EMPTY : POOL[i % POOL.length];
        }
        return contents;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Model of the pre-change implementation: build everything, every call.
    // ---------------------------------------------------------------------------------------------------------

    static final class Full {
        Map<Object, List<Object>> slotsPlayer = new HashMap<>();
        Map<Object, List<Object>> slotsContainer = new HashMap<>();
        Map<Object, Integer> countsPlayer = new HashMap<>();
        Map<Object, Integer> countsContainer = new HashMap<>();

        Full(Object[] contents) {
            SlotLike[] slots = new SlotLike[contents.length];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = new SlotLike(i);
            }
            for (SlotLike slot : slots) {
                Object item = contents[slot.window];
                int count = EMPTY.equals(item) ? 0 : 1 + (slot.window % 17);
                register(item, count, slot, slot.window >= 9,
                        slotsPlayer, slotsContainer, countsPlayer, countsContainer);
            }
        }
    }

    static final class SlotLike {
        final int window;

        SlotLike(int window) {
            this.window = window;
        }

        // Slot is a value object (window slot + inventory slot); the model needs the same equality, otherwise
        // two rebuilds that produce the same content would compare unequal on the slot instances alone.
        @Override
        public boolean equals(Object o) {
            return o instanceof SlotLike other && other.window == window;
        }

        @Override
        public int hashCode() {
            return window;
        }
    }

    static void register(Object item, int count, Object slot, boolean player,
                         Map<Object, List<Object>> slotsPlayer, Map<Object, List<Object>> slotsContainer,
                         Map<Object, Integer> countsPlayer, Map<Object, Integer> countsContainer) {
        if (player) {
            countsPlayer.merge(item, count, Integer::sum);
        } else {
            countsContainer.merge(item, count, Integer::sum);
        }
        Map<Object, List<Object>> toAdd = player ? slotsPlayer : slotsContainer;
        toAdd.computeIfAbsent(item, k -> new ArrayList<>()).add(slot);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Model of the new implementation: compare, rebuild only if changed.
    // ---------------------------------------------------------------------------------------------------------

    static final class Fast {
        private Map<Object, List<Object>> slotsPlayer = new HashMap<>();
        private Map<Object, List<Object>> slotsContainer = new HashMap<>();
        private Map<Object, Integer> countsPlayer = new HashMap<>();
        private Map<Object, Integer> countsContainer = new HashMap<>();

        private final Object[] lastItems = new Object[SLOTS];
        private final int[] lastCounts = new int[SLOTS];
        private boolean hasSnapshot;
        private int rebuilds;

        void update(Object[] contents) {
            boolean changed = !hasSnapshot;
            for (int i = 0; i < contents.length; i++) {
                Object item = contents[i];
                int count = EMPTY.equals(item) ? 0 : 1 + (i % 17);
                if (!changed && (lastItems[i] != item || lastCounts[i] != count)) {
                    changed = true;
                }
                lastItems[i] = item;
                lastCounts[i] = count;
            }
            hasSnapshot = true;
            if (!changed) {
                return;
            }
            rebuilds++;
            // Fresh maps, published by swapping the references — same as production.
            Map<Object, List<Object>> sp = new HashMap<>();
            Map<Object, List<Object>> sc = new HashMap<>();
            Map<Object, Integer> cp = new HashMap<>();
            Map<Object, Integer> cc = new HashMap<>();
            for (int i = 0; i < contents.length; i++) {
                register(contents[i], lastCounts[i], new SlotLike(i), i >= 9, sp, sc, cp, cc);
            }
            slotsPlayer = sp;
            slotsContainer = sc;
            countsPlayer = cp;
            countsContainer = cc;
        }
    }

    private static void assertSameContent(Full expected, Map<Object, List<Object>> sp, Map<Object, List<Object>> sc,
                                          Map<Object, Integer> cp, Map<Object, Integer> cc) {
        assertEquals(expected.slotsPlayer, sp, "player slots");
        assertEquals(expected.slotsContainer, sc, "container slots");
        assertEquals(expected.countsPlayer, cp, "player counts");
        assertEquals(expected.countsContainer, cc, "container counts");
    }

    @Test
    public void fastPathPublishesExactlyWhatAFullRebuildWould() {
        Random random = new Random(20261030L);
        Object[] contents = mazeInventory();
        Fast fast = new Fast();

        for (int round = 0; round < 5_000; round++) {
            // Random mutation: swap one slot's item, or change nothing.
            if (round > 0 && random.nextInt(4) != 0) {
                int slot = random.nextInt(SLOTS);
                contents[slot] = random.nextBoolean() ? EMPTY : POOL[random.nextInt(POOL.length)];
            }
            fast.update(contents);
            Full reference = new Full(contents);
            assertSameContent(reference, fast.slotsPlayer, fast.slotsContainer,
                    fast.countsPlayer, fast.countsContainer);
        }
    }

    @Test
    public void unchangedContentDoesNotRebuild() {
        Object[] contents = mazeInventory();
        Fast fast = new Fast();
        fast.update(contents);
        assertEquals(1, fast.rebuilds, "first update must build");

        Map<Object, List<Object>> published = fast.slotsPlayer;
        int[] publishedCounts = fast.countsPlayer.values().stream().mapToInt(Integer::intValue).toArray();

        for (int i = 0; i < 100; i++) {
            fast.update(contents);
        }
        assertEquals(1, fast.rebuilds, "100 unchanged updates must not rebuild");
        assertSame(published, fast.slotsPlayer, "the published map must be kept, not replaced");
        assertEquals(java.util.Arrays.toString(publishedCounts),
                java.util.Arrays.toString(fast.countsPlayer.values().stream().mapToInt(Integer::intValue).toArray()));

        // One real change must rebuild exactly once, and the result must still match a full rebuild.
        contents[3] = POOL[5];
        fast.update(contents);
        assertEquals(2, fast.rebuilds);
        assertNotSame(published, fast.slotsPlayer);
        assertSameContent(new Full(contents), fast.slotsPlayer, fast.slotsContainer,
                fast.countsPlayer, fast.countsContainer);
    }

    @Test
    public void perTickCostOfBothShapes() {
        Object[] contents = mazeInventory();
        Fast fast = new Fast();
        fast.update(contents);

        // Warm up.
        for (int i = 0; i < 20_000; i++) {
            new Full(contents);
            fast.update(contents);
        }

        long full = bestOf(5, () -> new Full(contents));
        long unchanged = bestOf(5, () -> fast.update(contents));
        long rebuild = bestOf(5, () -> {
            contents[0] = contents[0] == EMPTY ? POOL[1] : EMPTY;
            fast.update(contents);
        });

        System.out.printf("[inventorySnapshot] full rebuild  : %.1f ns/tick%n", (double) full);
        System.out.printf("[inventorySnapshot] unchanged     : %.1f ns/tick (%.2fx)%n",
                (double) unchanged, (double) unchanged / (double) full);
        System.out.printf("[inventorySnapshot] changed+rebuild: %.1f ns/tick (%.2fx)%n",
                (double) rebuild, (double) rebuild / (double) full);
    }

    @Test
    public void allocationPerTick() {
        java.lang.management.ThreadMXBean threads = java.lang.management.ManagementFactory.getThreadMXBean();
        if (!(threads instanceof com.sun.management.ThreadMXBean sun)) {
            System.out.println("[inventorySnapshot] allocation: not measurable on this JVM");
            return;
        }
        Object[] contents = mazeInventory();
        Fast fast = new Fast();
        fast.update(contents);
        long tid = Thread.currentThread().getId();

        // Warm up both shapes (and let the JIT decide what to scalar-replace) before counting.
        for (int i = 0; i < 20_000; i++) {
            sink = new Full(contents);
            fast.update(contents);
        }

        long before = sun.getThreadAllocatedBytes(tid);
        for (int i = 0; i < 20_000; i++) {
            sink = new Full(contents);
        }
        long fullBytes = (sun.getThreadAllocatedBytes(tid) - before) / 20_000;

        before = sun.getThreadAllocatedBytes(tid);
        for (int i = 0; i < 20_000; i++) {
            fast.update(contents);
        }
        long unchangedBytes = (sun.getThreadAllocatedBytes(tid) - before) / 20_000;

        System.out.printf("[inventorySnapshot] allocation: full rebuild %d B/tick, unchanged %d B/tick%n",
                fullBytes, unchangedBytes);
        // The fast path must not allocate a map, a list or a Slot wrapper for an unchanged screen. (It may still
        // allocate nothing at all; the assertion is the direction, not a byte count.)
        org.junit.jupiter.api.Assertions.assertTrue(unchangedBytes < fullBytes / 4,
                "unchanged path must allocate far less than a rebuild: " + unchangedBytes + " vs " + fullBytes);
    }

    private static long bestOf(int rounds, Runnable body) {
        long best = Long.MAX_VALUE;
        for (int round = 0; round < rounds; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < 20_000; i++) {
                body.run();
            }
            best = Math.min(best, System.nanoTime() - start);
        }
        return best / 20_000;
    }
}
