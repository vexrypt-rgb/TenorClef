package adris.altoclef.fugue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Stage 9 measurement, not a performance gate: where the live cold first plan (4.9–6.9 ms in v1) goes. Run alone
 * ({@code --tests '*FuguePlanColdStartTest'}) for a cold JVM; the numbers are printed to the test's system-out.
 */
public class FuguePlanColdStartTest {

    @Test
    void coldVersusWarmPlanning() {
        long t0 = System.nanoTime();
        FugueRecipeBook book = FugueRecipeBook.ironTools();
        long t1 = System.nanoTime();
        FuguePlanner planner = new FuguePlanner(book);
        long t2 = System.nanoTime();
        FuguePlan first = planner.plan("iron_pickaxe", 1, new FakeWorld());
        long t3 = System.nanoTime();
        FuguePlan second = planner.plan("iron_pickaxe", 1, new FakeWorld());
        long t4 = System.nanoTime();
        int n = 2000;
        for (int i = 0; i < n; i++) planner.plan("iron_pickaxe", 1, new FakeWorld().with("log", i % 4));
        long t5 = System.nanoTime();
        System.out.printf("FUGUE-COLD bookUs=%d plannerUs=%d firstPlanUs=%d secondPlanUs=%d steadyPlanUs=%.1f%n",
                (t1 - t0) / 1000, (t2 - t1) / 1000, (t3 - t2) / 1000, (t4 - t3) / 1000, (t5 - t4) / 1000.0 / n);
        Assertions.assertFalse(first.isError());
        Assertions.assertEquals(first.summary(), second.summary());
    }
}
