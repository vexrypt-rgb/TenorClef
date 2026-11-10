package adris.altoclef.trackers;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link BlockScanner.NearestPerKey}, the bounded per-type selection {@code scanCloseBlocks} now performs
 * inline while walking the 17x16x17 box around the player (once per client tick, on the client thread).
 *
 * <p>The first two tests are real tests: the selection must return exactly the {@code cap} nearest cells of each type.
 * The third is a cost comparison against the previous implementation (a {@code HashMap<Block, HashSet<BlockPos>>}
 * holding every non-air cell, then a bounded priority queue per type), replicating that code in the test so the two
 * run on the same input. It measures the collection machinery only — {@code World.getBlockState} is excluded from
 * both sides — and makes no timing assertion (CI noise); the printed numbers are the point.
 *
 * <p>The distance metric is injected rather than taken from {@code BaritoneHelper}, whose heuristic reads
 * {@code BaritoneAPI.getSettings()} and therefore needs a bootstrapped registry that a unit test does not have. The
 * metric below has the same Manhattan-plus-diagonal shape, which is all the selection depends on.
 */
public class CloseBlockScanCostTest {

    private static final int CAP = 40;
    /** Types in the box: one dominant (walls/floor), a few medium, a long tail of rare ones. */
    private static final int TYPES = 20;

    private static Object sink;

    // ------------------------------------------------------------------ correctness

    @Test
    public void selectionIsExactlyTheNearestCells() {
        Box box = new Box(new Random(1234), 0.5, 0.5, 0.5);
        BlockScanner.NearestPerKey<Integer> collector = new BlockScanner.NearestPerKey<>(CAP, box);
        for (int i = 0; i < box.size; i++) {
            collector.offer(box.key[i], box.pos[i]);
        }
        Map<Integer, BlockPos[]> got = collector.drain();

        for (int type = 0; type < TYPES; type++) {
            List<Double> expected = new ArrayList<>();
            for (int i = 0; i < box.size; i++) {
                if (box.key[i] == type) expected.add(box.dist(i));
            }
            Collections.sort(expected);

            BlockPos[] actual = got.get(type);
            if (expected.isEmpty()) {
                assertTrue(actual == null || actual.length == 0, "type " + type + " should not be retained");
                continue;
            }
            int want = Math.min(CAP, expected.size());
            assertEquals(want, actual.length, "type " + type + " retained count");

            // Distances are what the selection is defined by. Comparing distances rather than positions keeps this
            // robust to ties: symmetric cells are equidistant, and either one is a correct answer for the cap.
            List<Double> actualDistances = new ArrayList<>(actual.length);
            for (BlockPos p : actual) {
                actualDistances.add(box.dist(p.getX(), p.getY(), p.getZ()));
            }
            Collections.sort(actualDistances);
            for (int i = 0; i < want; i++) {
                assertEquals(expected.get(i), actualDistances.get(i), 1e-9,
                        "type " + type + " rank " + i);
            }
        }
    }

    @Test
    public void selectionKeepsEverythingUnderTheCap() {
        // Fewer cells than the cap must all survive, and a single type must not be dropped.
        Box box = new Box(new Random(99), 0, 0, 0);
        BlockScanner.NearestPerKey<Integer> collector = new BlockScanner.NearestPerKey<>(CAP, box);
        for (int i = 0; i < 5; i++) {
            collector.offer(7, new BlockPos(i, 0, 0));
        }
        Map<Integer, BlockPos[]> got = collector.drain();
        assertEquals(1, got.size());
        assertEquals(5, got.get(7).length);
    }

    // ------------------------------------------------------------------ cost

    @Test
    public void perTickSelectionCost() {
        Box box = new Box(new Random(7), 0.5, 3.5, 0.5);
        System.out.printf("[scanCloseBlocks] box=%d cells, non-air=%d (%.0f%%), %d types%n",
                box.volume, box.size, 100.0 * box.size / box.volume, TYPES);

        // Warm up both paths.
        for (int i = 0; i < 300; i++) {
            sink = oldScan(box);
            sink = newScan(box);
        }

        long oldNs = bestOf(box, true);
        long newNs = bestOf(box, false);
        System.out.printf("[scanCloseBlocks] old %.1f us/tick, new %.1f us/tick (%.2fx)%n",
                oldNs / 1000.0, newNs / 1000.0, (double) newNs / (double) oldNs);

        // The two must retain the same amount of data, otherwise the comparison is not like for like.
        assertEquals(oldRetained(box), newRetained(box), "retained position count");
    }

    private static long bestOf(Box box, boolean old) {
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 7; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < 200; i++) {
                sink = old ? oldScan(box) : newScan(box);
            }
            best = Math.min(best, System.nanoTime() - start);
        }
        return best / 200;
    }

    /** Replica of the previous implementation: every non-air cell into a per-type set, then keep the 40 nearest. */
    private static Map<Integer, HashSet<BlockPos>> oldScan(Box box) {
        HashMap<Integer, HashSet<BlockPos>> map = new HashMap<>();
        for (int i = 0; i < box.size; i++) {
            HashSet<BlockPos> set = map.get(box.key[i]);
            if (set == null) {
                set = new HashSet<>();
                map.put(box.key[i], set);
            }
            set.add(box.pos[i]);
        }
        HashMap<Integer, HashSet<BlockPos>> tracked = new HashMap<>();
        for (Map.Entry<Integer, HashSet<BlockPos>> entry : map.entrySet()) {
            getFirstFewPositions(entry.getValue(), box);
            tracked.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
        }
        return tracked;
    }

    private static Map<Integer, HashSet<BlockPos>> newScan(Box box) {
        BlockScanner.NearestPerKey<Integer> collector = new BlockScanner.NearestPerKey<>(CAP, box);
        for (int i = 0; i < box.size; i++) {
            collector.offer(box.key[i], box.pos[i]);
        }
        HashMap<Integer, HashSet<BlockPos>> tracked = new HashMap<>();
        for (Map.Entry<Integer, BlockPos[]> entry : collector.drain().entrySet()) {
            HashSet<BlockPos> set = tracked.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
            Collections.addAll(set, entry.getValue());
        }
        return tracked;
    }

    private static int oldRetained(Box box) {
        int total = 0;
        for (HashSet<BlockPos> set : oldScan(box).values()) total += set.size();
        return total;
    }

    private static int newRetained(Box box) {
        int total = 0;
        for (HashSet<BlockPos> set : newScan(box).values()) total += set.size();
        return total;
    }

    /** Copy of {@code BlockScanner.getFirstFewPositions} (that method is private and only used by the rescan now). */
    private static void getFirstFewPositions(HashSet<BlockPos> set, Box box) {
        if (set.size() <= CAP) {
            return;
        }
        Queue<BlockPos> queue = new PriorityQueue<>(Comparator.comparingDouble((pos) ->
                -box.dist(pos.getX(), pos.getY(), pos.getZ())));
        for (BlockPos pos : set) {
            queue.add(pos);
            if (queue.size() > CAP) {
                queue.poll();
            }
        }
        set.clear();
        for (int i = 0; i < CAP && !queue.isEmpty(); i++) {
            set.add(queue.poll());
        }
    }

    /**
     * The box {@code scanCloseBlocks} walks: x in [cx-8, cx+8], y in [cy-8, cy+7], z in [cz-8, cz+8], with air cells
     * left out. Air fraction is tuned to a maze interior (mostly solid walls, floor and ceiling around the player).
     */
    private static final class Box implements BlockScanner.NearestPerKey.Metric {
        final int volume;
        final int size;
        final BlockPos[] pos;
        final int[] key;
        final double px, py, pz;

        Box(Random random, double px, double py, double pz) {
            this.px = px;
            this.py = py;
            this.pz = pz;
            int cx = (int) Math.floor(px), cy = (int) Math.floor(py), cz = (int) Math.floor(pz);
            volume = 17 * 16 * 17;
            List<BlockPos> positions = new ArrayList<>(volume);
            List<Integer> keys = new ArrayList<>(volume);
            for (int x = cx - 8; x <= cx + 8; x++) {
                for (int y = cy - 8; y < cy + 8; y++) {
                    for (int z = cz - 8; z <= cz + 8; z++) {
                        if (random.nextDouble() < 0.30) continue; // air
                        positions.add(new BlockPos(x, y, z));
                        keys.add(typeOf(random));
                    }
                }
            }
            size = positions.size();
            pos = positions.toArray(new BlockPos[0]);
            key = new int[size];
            for (int i = 0; i < size; i++) key[i] = keys.get(i);
        }

        private static int typeOf(Random random) {
            double r = random.nextDouble();
            if (r < 0.55) return 0;   // dominant: the maze's own walls
            if (r < 0.75) return 1;
            if (r < 0.85) return 2;
            if (r < 0.92) return 3;
            return 4 + random.nextInt(TYPES - 4);
        }

        @Override
        public double distance(int x, int y, int z) {
            // Block centre, like WorldHelper.toVec3d. Same shape as Baritone's generic heuristic:
            // diagonal steps cost sqrt(2), straight steps cost 1, vertical costs 1.
            double dx = Math.abs(x + 0.5 - px);
            double dz = Math.abs(z + 0.5 - pz);
            double dy = Math.abs(y + 0.5 - py);
            double straight = Math.min(dx, dz);
            double diagonal = Math.max(dx, dz) - straight;
            return straight * Math.sqrt(2) + diagonal + dy;
        }

        double dist(int i) {
            return dist(pos[i].getX(), pos[i].getY(), pos[i].getZ());
        }

        double dist(int x, int y, int z) {
            return distance(x, y, z);
        }
    }
}
