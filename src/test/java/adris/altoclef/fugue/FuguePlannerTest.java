package adris.altoclef.fugue;

import adris.altoclef.tasksystem.FailureReason;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FuguePlannerTest {

    private static final FugueRecipeBook BOOK = FugueRecipeBook.ironTools();
    private static final FuguePlanner PLANNER = new FuguePlanner(BOOK);

    private static Map<String, Integer> needs(FuguePlan p) {
        return p.getNodes().values().stream().collect(Collectors.toMap(FugueNode::getKey, FugueNode::getNeed));
    }

    @Test
    void emptyInventoryExpandsWholeChainWithAggregatedTotals() {
        FuguePlan p = PLANNER.plan("iron_pickaxe", 1, new FakeWorld());
        Assertions.assertFalse(p.isError(), p.summary());
        Assertions.assertEquals(Map.ofEntries(
                Map.entry("iron_pickaxe", 1), Map.entry("iron_ingot", 3), Map.entry("raw_iron", 3),
                Map.entry("stone_pickaxe", 1), Map.entry("cobblestone", 11), Map.entry("wooden_pickaxe", 1),
                Map.entry("planks", 11), Map.entry("log", 3), Map.entry("stick", 6), Map.entry("crafting_table", 1),
                Map.entry("coal", 1), Map.entry("furnace", 1)), needs(p));
    }

    @Test
    void sharedPrerequisitesAreOneNodeEach() {
        FuguePlan p = PLANNER.plan("iron_pickaxe", 1, new FakeWorld());
        // Four table recipes, one crafting table; furnace + stone pickaxe share one cobblestone node.
        Assertions.assertEquals(1, p.get("crafting_table").getNeed());
        Assertions.assertEquals(11, p.get("cobblestone").getNeed());
        Assertions.assertEquals(8 + 3, p.get("cobblestone").getOps());
        // Sticks: three recipes x2 = 6 -> two crafts (8), not three; planks 3+4+2*2 = 11 -> 3 logs.
        Assertions.assertEquals(2, p.get("stick").getOps());
        Assertions.assertEquals(3, p.get("log").getNeed());
    }

    @Test
    void onlyLeavesAreReadyInitially() {
        FuguePlan p = PLANNER.plan("iron_pickaxe", 1, new FakeWorld());
        Assertions.assertEquals(List.of("log"), p.ready().stream().map(FugueNode::getKey).collect(Collectors.toList()));
        Assertions.assertTrue(p.get("raw_iron").getDeps().contains("stone_pickaxe"));
        Assertions.assertTrue(p.get("coal").getDeps().contains("wooden_pickaxe"));
        Assertions.assertTrue(p.get("iron_ingot").getDeps().containsAll(List.of("raw_iron", "coal", "furnace")));
        // log is upstream of every other node
        Assertions.assertEquals(11, p.get("log").getDownstream());
    }

    @Test
    void alreadySatisfiedGoalIsEmptyPlan() {
        FuguePlan p = PLANNER.plan("iron_pickaxe", 1, new FakeWorld().with("iron_pickaxe", 1));
        Assertions.assertFalse(p.isError());
        Assertions.assertTrue(p.isEmpty());
    }

    @Test
    void fullMaterialsLeaveOnlyTheFinalCraft() {
        FuguePlan p = PLANNER.plan("iron_pickaxe", 1,
                new FakeWorld().with("iron_ingot", 3).with("stick", 2).with("crafting_table", 1));
        Assertions.assertEquals(Map.of("iron_pickaxe", 1), needs(p));
        Assertions.assertTrue(p.get("iron_pickaxe").getDeps().isEmpty());
    }

    @Test
    void partialInventoryReusesHeldToolsAndStation() {
        FuguePlan p = PLANNER.plan("iron_pickaxe", 1,
                new FakeWorld().with("crafting_table", 1).with("stone_pickaxe", 1).with("stick", 2));
        Assertions.assertEquals(Map.of("iron_pickaxe", 1, "iron_ingot", 3, "raw_iron", 3, "coal", 1, "furnace", 1, "cobblestone", 8),
                needs(p));
    }

    @Test
    void inventoryIsAllocatedOnceAcrossConsumers() {
        // Two held sticks cover one recipe, not all three.
        FuguePlan p = PLANNER.plan("iron_pickaxe", 1, new FakeWorld().with("stick", 2));
        Assertions.assertEquals(4, p.get("stick").getNeed());
        // Held planks feed the plan before any log is planned.
        FuguePlan q = PLANNER.plan("iron_pickaxe", 1, new FakeWorld().with("planks", 11));
        Assertions.assertNull(q.get("planks"));
        Assertions.assertNull(q.get("log"));
    }

    @Test
    void nearbyStationSatisfiesPresence() {
        FakeWorld w = new FakeWorld();
        w.stations.add("crafting_table");
        w.stations.add("furnace");
        FuguePlan p = PLANNER.plan("iron_pickaxe", 1, w);
        Assertions.assertNull(p.get("crafting_table"));
        Assertions.assertNull(p.get("furnace"));
        Assertions.assertEquals(3, p.get("cobblestone").getNeed());
    }

    @Test
    void higherTierPickaxeSatisfiesLowerRequirement() {
        FuguePlan p = PLANNER.plan("raw_iron", 3, new FakeWorld().with("diamond_pickaxe", 1));
        Assertions.assertEquals(Map.of("raw_iron", 3), needs(p));
    }

    @Test
    void cycleIsRejected() {
        FugueRecipeBook book = FugueRecipeBook.builder()
                .recipe("a", FugueRecipe.Kind.CRAFT_INV, 1, null, 0, "b", 1)
                .recipe("b", FugueRecipe.Kind.CRAFT_INV, 1, null, 0, "a", 1)
                .build();
        FuguePlan p = new FuguePlanner(book).plan("a", 1, new FakeWorld());
        Assertions.assertTrue(p.isError());
        Assertions.assertEquals(FailureReason.PRECONDITION_FAILED, p.getError());
        Assertions.assertTrue(p.getErrorMessage().contains("a -> b -> a"), p.getErrorMessage());
    }

    @Test
    void toolCycleIsRejected() {
        // An ore that needs the pickaxe it is an ingredient of.
        FugueRecipeBook book = FugueRecipeBook.builder()
                .mine("ore", 3)
                .recipe("iron_pickaxe", FugueRecipe.Kind.CRAFT_INV, 1, null, 0, "ore", 3)
                .build();
        FuguePlan p = new FuguePlanner(book).plan("iron_pickaxe", 1, new FakeWorld());
        Assertions.assertTrue(p.isError());
        Assertions.assertTrue(p.getErrorMessage().contains("cycle"));
    }

    @Test
    void cycleBrokenByInventoryIsFine() {
        FugueRecipeBook book = FugueRecipeBook.builder()
                .recipe("a", FugueRecipe.Kind.CRAFT_INV, 1, null, 0, "b", 1)
                .recipe("b", FugueRecipe.Kind.CRAFT_INV, 1, null, 0, "a", 1)
                .build();
        FuguePlan p = new FuguePlanner(book).plan("a", 1, new FakeWorld().with("b", 1));
        Assertions.assertFalse(p.isError());
        Assertions.assertEquals(Map.of("a", 1), needs(p));
    }

    @Test
    void missingRecipeIsExplicitResourceMissing() {
        FuguePlan p = PLANNER.plan("diamond", 1, new FakeWorld());
        Assertions.assertTrue(p.isError());
        Assertions.assertEquals(FailureReason.RESOURCE_MISSING, p.getError());
        FugueRecipeBook noLogs = FugueRecipeBook.builder().craftInv("planks", 4, "log", 1).build();
        FuguePlan q = new FuguePlanner(noLogs).plan("planks", 4, new FakeWorld());
        Assertions.assertEquals(FailureReason.RESOURCE_MISSING, q.getError());
        Assertions.assertTrue(q.getErrorMessage().contains("log"));
    }

    @Test
    void depthIsBounded() {
        FugueRecipeBook.Builder b = FugueRecipeBook.builder();
        for (int i = 0; i < 20; i++) b.recipe("k" + i, FugueRecipe.Kind.CRAFT_INV, 1, null, 0, "k" + (i + 1), 1);
        b.mine("k20", 0);
        FuguePlan p = new FuguePlanner(b.build()).plan("k0", 1, new FakeWorld());
        Assertions.assertTrue(p.isError());
        Assertions.assertTrue(p.getErrorMessage().contains("deeper"));
    }

    @Test
    void planningIsDeterministic() {
        FakeWorld w = new FakeWorld().with("stick", 3).with("cobblestone", 5);
        String a = PLANNER.plan("iron_pickaxe", 1, w).summary();
        for (int i = 0; i < 5; i++) Assertions.assertEquals(a, PLANNER.plan("iron_pickaxe", 1, w).summary());
    }

    @Test
    void largerGoalCountScalesSmeltingAndFuel() {
        FuguePlan p = PLANNER.plan("iron_ingot", 9, new FakeWorld().with("stone_pickaxe", 1));
        Assertions.assertEquals(9, p.get("raw_iron").getNeed());
        Assertions.assertEquals(2, p.get("coal").getNeed()); // 9 smelts -> 2 coal
    }
}
