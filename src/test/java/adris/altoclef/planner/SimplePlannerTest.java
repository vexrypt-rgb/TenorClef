package adris.altoclef.planner;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 7: SimplePlanner builds linear catalogue-backed plans.
 */
public class SimplePlannerTest {

    private static InventoryView mapInv(Map<String, Integer> m) {
        return key -> m.getOrDefault(key, 0);
    }

    @Test
    void acquireWhenEmptyIsOneCollectStep() {
        SimplePlanner planner = new SimplePlanner();
        AcquireItemGoal goal = new AcquireItemGoal("cobblestone", 64);
        Plan plan = planner.plan(goal, mapInv(Map.of()));
        Assertions.assertEquals(1, plan.size());
        PlanStep step = plan.getSteps().get(0);
        Assertions.assertEquals("cobblestone", step.getCatalogueKey());
        Assertions.assertEquals(64, step.getCount());
        Assertions.assertEquals(PlanStatus.READY, plan.getStatus());
    }

    @Test
    void acquirePartialCountsRemaining() {
        Map<String, Integer> inv = new HashMap<>();
        inv.put("cobblestone", 20);
        Plan plan = SimplePlanner.INSTANCE.plan(new AcquireItemGoal("cobblestone", 64), mapInv(inv));
        Assertions.assertEquals(1, plan.size());
        Assertions.assertEquals(44, plan.getSteps().get(0).getCount());
    }

    @Test
    void acquireAlreadySatisfiedIsEmpty() {
        Plan plan = SimplePlanner.INSTANCE.plan(
                new AcquireItemGoal("dirt", 8),
                mapInv(Map.of("dirt", 8)));
        Assertions.assertTrue(plan.isEmpty());
        Assertions.assertEquals(PlanStatus.EMPTY, plan.getStatus());
    }

    @Test
    void goalRequirementsAndSatisfaction() {
        AcquireItemGoal goal = new AcquireItemGoal("log", 4);
        Assertions.assertEquals("acquire:log", goal.getId());
        Assertions.assertEquals(1, goal.getRequirements().size());
        Assertions.assertEquals(Requirement.Kind.ITEM_COUNT, goal.getRequirements().get(0).getKind());
        Assertions.assertFalse(goal.isSatisfied(mapInv(Map.of())));
        Assertions.assertTrue(goal.isSatisfied(mapInv(Map.of("log", 4))));
        Assertions.assertEquals(2, goal.remaining(mapInv(Map.of("log", 2))));
    }

    @Test
    void genericRequirementFallback() {
        Goal custom = new Goal() {
            @Override public String getId() { return "custom"; }
            @Override public String getDescription() { return "custom"; }
            @Override public java.util.List<Requirement> getRequirements() {
                return java.util.List.of(
                        Requirement.item("stick", 4),
                        Requirement.predicate("near_table"));
            }
            @Override public boolean isSatisfied(InventoryView inventory) {
                return inventory.getCount("stick") >= 4;
            }
        };
        Plan plan = SimplePlanner.INSTANCE.plan(custom, mapInv(Map.of("stick", 1)));
        Assertions.assertEquals(1, plan.size());
        Assertions.assertEquals("stick", plan.getSteps().get(0).getCatalogueKey());
        Assertions.assertEquals(3, plan.getSteps().get(0).getCount());
    }
}
