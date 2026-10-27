package adris.altoclef.maze;

import adris.altoclef.maze.MazeFurnishPlan.Item;
import adris.altoclef.maze.MazeFurnishPlan.Kind;
import adris.altoclef.maze.MazeFurnishPlan.Step;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MazeFurnishPlanTest {

    private static final MazeFurnishPlan PLAN = new MazeFurnishPlan("t", true, List.of(
            new Step(Kind.LIGHT, 0, 0, 0, List.of("sea_lantern", "glowstone"), List.of()),
            new Step(Kind.CONTAINER, 1, 0, 0, List.of("chest"), List.of()),
            new Step(Kind.FILL, 1, 0, 0, List.of(), List.of(new Item("book", 2), new Item("music_disc_13", 1))),
            new Step(Kind.SEAL, 2, 0, 0, List.of("cobblestone"), List.of()),
            new Step(Kind.SEAL, 2, 1, 0, List.of("cobblestone"), List.of())));

    @Test
    void fillRunsAfterPlaceAndSeal() {
        List<Kind> order = PLAN.placeOrder().stream().map(Step::kind).toList();
        Assertions.assertEquals(List.of(Kind.LIGHT, Kind.CONTAINER, Kind.FILL, Kind.SEAL, Kind.SEAL), order);
    }

    @Test
    void materialsPickFirstObtainableAndDropUnobtainableLoot() {
        Set<String> ok = Set.of("glowstone", "chest", "book", "cobblestone");
        Map<String, Integer> m = PLAN.materials(ok::contains);
        Assertions.assertEquals(Map.of("glowstone", 1, "chest", 1, "book", 2, "cobblestone", 2), m);
        Assertions.assertEquals(List.of("glowstone", "chest", "book", "cobblestone"), List.copyOf(m.keySet()));
    }

    // Shape twin of Ostinato's MazeFurnishJob, read through the same reflection path.
    public enum StepKind { LIGHT, CONTAINER, FILL, SEAL }
    public record ItemCount(String itemId, int count) {}
    public record JobStep(StepKind kind, int x, int y, int z, List<String> blockIds, List<ItemCount> contents) {}
    public static final class Job {
        public final boolean survival = true;
        public final List<JobStep> steps = List.of(
                new JobStep(StepKind.FILL, 4, 5, 6, List.of(), List.of(new ItemCount("paper", 3))),
                new JobStep(StepKind.SEAL, 4, 9, 6, List.of("cobblestone"), List.of()));
        public String summary() { return "cell 1,2"; }
    }

    @Test
    void deepslateAboveStoneBecomesCobble() {
        MazeFurnishPlan high = new MazeFurnishPlan("cell", true, List.of(
                new Step(Kind.LIGHT, 6, 74, 136, List.of("polished_deepslate", "cobbled_deepslate"), List.of()),
                new Step(Kind.SEAL, 6, 80, 136, List.of("cobbled_deepslate", "deepslate"), List.of())));
        MazeFurnishPlan fixed = MazeFurnishPlan.stoneIfAboveDeepslate(high);
        Assertions.assertEquals(List.of("cobblestone"), fixed.steps.get(0).blockIds());
        Assertions.assertEquals(List.of("cobblestone"), fixed.steps.get(1).blockIds());
        MazeFurnishPlan low = new MazeFurnishPlan("cell", true, List.of(
                new Step(Kind.LIGHT, 6, -20, 136, List.of("polished_deepslate"), List.of())));
        Assertions.assertEquals(List.of("polished_deepslate"), MazeFurnishPlan.stoneIfAboveDeepslate(low).steps.get(0).blockIds());
    }

    @Test
    void readsOstinatoJobByReflection() throws Exception {
        MazeFurnishPlan plan = MazeFurnishPlan.fromOstinato(new Job());
        Assertions.assertTrue(plan.survival);
        Assertions.assertEquals("cell 1,2", plan.summary);
        Assertions.assertEquals(new Step(Kind.FILL, 4, 5, 6, List.of(), List.of(new Item("paper", 3))), plan.steps.get(0));
        Assertions.assertEquals(Kind.SEAL, plan.steps.get(1).kind());
    }
}
