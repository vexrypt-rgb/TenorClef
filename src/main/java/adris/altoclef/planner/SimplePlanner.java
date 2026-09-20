package adris.altoclef.planner;

import java.util.ArrayList;
import java.util.List;

/**
 * Linear catalogue-backed planner (Phase 7 v1 — not GOAP / HTN).
 * <p>
 * For {@link AcquireItemGoal}: if already satisfied → empty plan; else one
 * collect step for the remaining count via TaskCatalogue key.
 * Unknown goals → empty plan.
 */
public class SimplePlanner implements Planner {

    public static final SimplePlanner INSTANCE = new SimplePlanner();

    @Override
    public Plan plan(Goal goal, InventoryView inventory) {
        if (goal == null) {
            return Plan.empty();
        }
        if (goal.isSatisfied(inventory)) {
            return Plan.empty();
        }
        if (goal instanceof AcquireItemGoal acquire) {
            return planAcquire(acquire, inventory);
        }
        // Generic fallback: one step per ITEM_COUNT requirement still unmet
        List<PlanStep> steps = new ArrayList<>();
        for (Requirement req : goal.getRequirements()) {
            if (req.getKind() != Requirement.Kind.ITEM_COUNT) {
                continue;
            }
            int have = inventory != null ? inventory.getCount(req.getId()) : 0;
            int need = Math.max(0, req.getCount() - have);
            if (need > 0) {
                steps.add(new PlanStep(req.getId(), need));
            }
        }
        return new Plan(steps);
    }

    private Plan planAcquire(AcquireItemGoal goal, InventoryView inventory) {
        int need = goal.remaining(inventory);
        if (need <= 0) {
            return Plan.empty();
        }
        // v1: single linear collect step (catalogue will craft/mine as needed)
        return new Plan(List.of(new PlanStep(goal.getItemKey(), need,
                "collect " + need + " " + goal.getItemKey())));
    }
}
