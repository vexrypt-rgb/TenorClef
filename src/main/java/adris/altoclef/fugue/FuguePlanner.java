package adris.altoclef.fugue;

import adris.altoclef.tasksystem.FailureReason;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bill-of-materials netting over a {@link FugueRecipeBook}.
 * <ul>
 *   <li>Inventory is a ledger: each held item is allocated to at most one consumer, so two recipes that both want
 *       sticks cannot both count the same two sticks.</li>
 *   <li>Demand for an item from every consumer lands on one node; craft rounding surplus is handed to the next
 *       consumer before another operation is planned.</li>
 *   <li>Stations and pickaxes are presence requirements: held (or, for stations, placed nearby) satisfies them,
 *       otherwise one is planned once, however many recipes need it.</li>
 *   <li>Bounded: cycles, depth and node count are rejected with an explicit error rather than looping.</li>
 * </ul>
 * Pure and deterministic: the same book, goal and view always give the same plan.
 */
public final class FuguePlanner {

    public static final int MAX_NODES = 32;
    public static final int MAX_DEPTH = 12;

    private final FugueRecipeBook book;

    public FuguePlanner(FugueRecipeBook book) {
        this.book = book;
    }

    public FugueRecipeBook getBook() { return book; }

    public FuguePlan plan(String goal, int count, FugueWorldView view) {
        Netting n = new Netting(view);
        try {
            n.demand(goal, count, new ArrayDeque<>());
        } catch (PlanError e) {
            return FuguePlan.error(e.reason, e.getMessage());
        }
        computeDownstream(n.nodes);
        return new FuguePlan(n.nodes, null, null);
    }

    private static void computeDownstream(Map<String, FugueNode> nodes) {
        Map<String, List<String>> dependents = new HashMap<>();
        for (FugueNode node : nodes.values()) {
            for (String d : node.deps) dependents.computeIfAbsent(d, k -> new ArrayList<>()).add(node.getKey());
        }
        for (FugueNode node : nodes.values()) {
            Deque<String> stack = new ArrayDeque<>(dependents.getOrDefault(node.getKey(), List.of()));
            java.util.Set<String> seen = new java.util.HashSet<>();
            while (!stack.isEmpty()) {
                String k = stack.pop();
                if (seen.add(k)) stack.addAll(dependents.getOrDefault(k, List.of()));
            }
            node.downstream = seen.size();
        }
    }

    private static final class PlanError extends RuntimeException {
        final FailureReason reason;
        PlanError(FailureReason reason, String message) {
            super(message, null, false, false);
            this.reason = reason;
        }
    }

    private final class Netting {
        final FugueWorldView view;
        final Map<String, Integer> avail = new HashMap<>();
        final Map<String, FugueNode> nodes = new LinkedHashMap<>();
        final int heldTier;

        Netting(FugueWorldView view) {
            this.view = view;
            this.heldTier = FugueRecipeBook.heldPickaxeTier(view);
        }

        /** Allocate {@code qty} of {@code key} from inventory; plan production for the rest. True if production was needed. */
        boolean demand(String key, int qty, Deque<String> path) {
            if (qty <= 0) return false;
            checkPath(key, path);
            int have = avail.computeIfAbsent(key, view::count);
            int take = Math.min(have, qty);
            avail.put(key, have - take);
            int rest = qty - take;
            if (rest == 0) return false;
            produce(key, rest, path);
            return true;
        }

        /** A station block or tool that must exist but is not consumed. */
        boolean requirePresence(String key, boolean placeable, Deque<String> path) {
            checkPath(key, path);
            if (view.count(key) > 0 || (placeable && view.stationNearby(key))) return false;
            if (nodes.containsKey(key)) return true; // presence is not additive: one table serves every recipe
            produce(key, 1, path);
            return true;
        }

        void produce(String key, int rest, Deque<String> path) {
            FugueRecipe recipe = book.get(key);
            if (recipe == null) {
                throw new PlanError(FailureReason.RESOURCE_MISSING, "no recipe for " + key + " (missing " + rest + ")");
            }
            FugueNode node = nodes.get(key);
            if (node == null) {
                if (nodes.size() >= MAX_NODES) throw new PlanError(FailureReason.PRECONDITION_FAILED, "plan exceeds " + MAX_NODES + " nodes");
                node = new FugueNode(key, recipe);
                nodes.put(key, node);
            }
            node.need += rest;
            int fromSurplus = Math.min(node.surplus, rest);
            node.surplus -= fromSurplus;
            rest -= fromSurplus;
            if (rest == 0) return;
            int ops = (rest + recipe.getOutputPerOp() - 1) / recipe.getOutputPerOp();
            node.surplus += ops * recipe.getOutputPerOp() - rest;
            int prevOps = node.ops;
            node.ops += ops;

            path.push(key);
            for (Map.Entry<String, Integer> in : recipe.getInputs().entrySet()) {
                if (demand(in.getKey(), in.getValue() * ops, path)) node.deps.add(in.getKey());
            }
            if (recipe.getKind() == FugueRecipe.Kind.SMELT) {
                int fuel = ceilDiv(node.ops, FugueRecipeBook.SMELTS_PER_FUEL) - ceilDiv(prevOps, FugueRecipeBook.SMELTS_PER_FUEL);
                if (demand(FugueRecipeBook.FUEL, fuel, path)) node.deps.add(FugueRecipeBook.FUEL);
            }
            if (recipe.getStation() != null && requirePresence(recipe.getStation(), true, path)) {
                node.deps.add(recipe.getStation());
            }
            if (recipe.getToolTier() > heldTier) {
                String pick = FugueRecipeBook.pickaxeForTier(recipe.getToolTier());
                if (requirePresence(pick, false, path)) node.deps.add(pick);
            }
            path.pop();
        }

        void checkPath(String key, Deque<String> path) {
            if (path.contains(key)) {
                List<String> cycle = new ArrayList<>(path);
                java.util.Collections.reverse(cycle);
                cycle.add(key);
                throw new PlanError(FailureReason.PRECONDITION_FAILED, "dependency cycle " + String.join(" -> ", cycle));
            }
            if (path.size() >= MAX_DEPTH) {
                throw new PlanError(FailureReason.PRECONDITION_FAILED, "plan deeper than " + MAX_DEPTH + " at " + key);
            }
        }
    }

    private static int ceilDiv(int a, int b) {
        return a <= 0 ? 0 : (a + b - 1) / b;
    }
}
