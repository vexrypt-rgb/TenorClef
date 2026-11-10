package adris.altoclef.fugue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Map-backed {@link FugueWorldView} plus a crude "the stock task did its job" simulator. */
final class FakeWorld implements FugueWorldView {

    final Map<String, Integer> inv = new HashMap<>();
    final Set<String> stations = new HashSet<>();
    final Map<String, Double> distances = new HashMap<>();
    // v1.1 recovery hooks
    final Set<String> excluded = new HashSet<>();
    final java.util.List<String> forgotten = new java.util.ArrayList<>();
    boolean navIdle = true;
    boolean excludeWorks = true;

    FakeWorld with(String key, int n) {
        inv.put(key, n);
        return this;
    }

    @Override public int count(String key) { return inv.getOrDefault(key, 0); }
    @Override public boolean stationNearby(String key) { return stations.contains(key); }
    @Override public double distanceToSource(String key) { return distances.getOrDefault(key, Double.POSITIVE_INFINITY); }

    @Override public boolean navigationIdle() { return navIdle; }
    /** Wet neighbours a trap recovery would also exclude, per target. */
    final Map<String, java.util.List<String>> wet = new HashMap<>();

    @Override public java.util.List<String> exclude(String target, boolean wetNeighbours) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!excludeWorks || target == null || !excluded.add(target)) return out;
        out.add(target);
        if (wetNeighbours) for (String n : wet.getOrDefault(target, java.util.List.of())) if (excluded.add(n)) out.add(n);
        return out;
    }
    @Override public void forget(String target) { excluded.remove(target); forgotten.add(target); }

    void add(String key, int n) {
        inv.put(key, Math.max(0, count(key) + n));
    }

    /**
     * Pretend the catalogue task for {@code key} ran to {@code target}: consume inputs per operation, place the
     * station it used if it came from inventory (as DoStuffInContainerTask does), and add the output.
     */
    void produce(FugueRecipeBook book, String key, int target) {
        FugueRecipe r = book.get(key);
        int missing = target - count(key);
        if (missing <= 0) return;
        int ops = (missing + r.getOutputPerOp() - 1) / r.getOutputPerOp();
        for (Map.Entry<String, Integer> in : r.getInputs().entrySet()) add(in.getKey(), -in.getValue() * ops);
        if (r.getKind() == FugueRecipe.Kind.SMELT) add(FugueRecipeBook.FUEL, -((ops + 7) / 8));
        if (r.getStation() != null && !stations.contains(r.getStation()) && count(r.getStation()) > 0) {
            add(r.getStation(), -1);
            stations.add(r.getStation());
        }
        add(key, ops * r.getOutputPerOp());
    }
}
