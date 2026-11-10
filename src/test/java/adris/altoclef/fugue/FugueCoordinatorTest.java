package adris.altoclef.fugue;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.TaskFailure;
import adris.altoclef.tasksystem.TaskResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static adris.altoclef.fugue.FugueCoordinator.Decision.Type.*;

public class FugueCoordinatorTest {

    private static final FugueRecipeBook BOOK = FugueRecipeBook.ironTools();
    private final List<String> log = new ArrayList<>();

    /** Grace 0 so a single failing report is judged at once; the grace period has its own tests. */
    private FugueCoordinator coordinator(String goal, int n, FugueCoordinator.Config cfg) {
        if (cfg == null) cfg = new FugueCoordinator.Config();
        cfg.failureGraceTicks = 0;
        return new FugueCoordinator(goal, n, new FuguePlanner(BOOK), cfg, log::add);
    }

    private static FugueCoordinator.ChildReport running() {
        return new FugueCoordinator.ChildReport(false, TaskResult.RUNNING, null);
    }

    private static FugueCoordinator.ChildReport report(boolean finished, TaskResult r, TaskFailure f) {
        return new FugueCoordinator.ChildReport(finished, r, f);
    }

    /** Drive to completion with a child that always does its job on the tick after it starts. */
    private static FugueCoordinator.Decision runToEnd(FugueCoordinator c, FakeWorld w, int maxTicks) {
        FugueCoordinator.Decision d = c.step(w, null);
        for (int i = 0; i < maxTicks && (d.type == RUN || d.type == RELEASE); i++) {
            if (d.type == RUN) w.produce(BOOK, d.key, d.target);
            d = c.step(w, d.type == RUN ? running() : null);
        }
        return d;
    }

    @Test
    void emptyInventoryReachesGoalInDependencyOrder() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("iron_pickaxe", 1, null);
        FugueCoordinator.Decision d = runToEnd(c, w, 200);
        Assertions.assertEquals(SUCCEEDED, d.type, String.join("\n", log));
        Assertions.assertEquals(1, w.count("iron_pickaxe"));
        Assertions.assertEquals(12, c.getNodeStarts());
        Assertions.assertEquals(0, c.getFailedAttempts());
        // Every node started only after the nodes it depends on (as planned at the start).
        FuguePlan initial = new FuguePlanner(BOOK).plan("iron_pickaxe", 1, new FakeWorld());
        Set<String> done = new HashSet<>();
        for (String key : c.getSequence()) {
            for (String dep : initial.get(key).getDeps()) Assertions.assertTrue(done.contains(dep), key + " before " + dep);
            done.add(key);
        }
        Assertions.assertEquals("log", c.getSequence().get(0));
        Assertions.assertEquals("iron_pickaxe", c.getSequence().get(c.getSequence().size() - 1));
        // Gathered in one pass each: 3 logs, 11 cobblestone (no second cobblestone trip for the furnace).
        Assertions.assertEquals(1, c.getSequence().stream().filter("cobblestone"::equals).count());
        Assertions.assertEquals(1, c.getSequence().stream().filter("log"::equals).count());
    }

    @Test
    void goalAlreadyHeldSucceedsWithoutStartingAnything() {
        FugueCoordinator c = coordinator("iron_pickaxe", 1, null);
        Assertions.assertEquals(SUCCEEDED, c.step(new FakeWorld().with("iron_pickaxe", 1), null).type);
        Assertions.assertEquals(0, c.getNodeStarts());
    }

    @Test
    void terminalStatusIsSticky() {
        FakeWorld w = new FakeWorld().with("iron_pickaxe", 1);
        FugueCoordinator c = coordinator("iron_pickaxe", 1, null);
        c.step(w, null);
        w.inv.clear();
        Assertions.assertEquals(SUCCEEDED, c.step(w, null).type);
    }

    @Test
    void childFinishedWithoutPostconditionIsNotSuccess() {
        FakeWorld w = new FakeWorld().with("iron_ingot", 3).with("stick", 2).with("crafting_table", 1);
        FugueCoordinator c = coordinator("iron_pickaxe", 1, null);
        FugueCoordinator.Decision d = c.step(w, null);
        Assertions.assertEquals(RUN, d.type);
        Assertions.assertEquals("iron_pickaxe", d.key);
        // The stock task says it is finished, but nothing was crafted.
        d = c.step(w, report(true, TaskResult.SUCCESS, null));
        Assertions.assertEquals(FAILED, d.type);
        Assertions.assertEquals(FugueCoordinator.Status.FAILED, c.getStatus());
        Assertions.assertEquals(1, c.getFailedAttempts());
        Assertions.assertEquals(0, c.getNodeSuccesses());
    }

    @Test
    void finishedWithProgressIsRetriedThenSucceeds() {
        FakeWorld w = new FakeWorld().with("stone_pickaxe", 1);
        FugueCoordinator c = coordinator("cobblestone", 8, null);
        FugueCoordinator.Decision d = c.step(w, null);
        Assertions.assertEquals("cobblestone", d.key);
        w.add("cobblestone", 5); // partial
        d = c.step(w, report(true, TaskResult.SUCCESS, null));
        Assertions.assertEquals(RELEASE, d.type);
        d = c.step(w, null);
        Assertions.assertEquals(RUN, d.type);
        Assertions.assertTrue(d.fresh);
        Assertions.assertEquals(8, d.target);
        w.add("cobblestone", 3);
        Assertions.assertEquals(SUCCEEDED, c.step(w, running()).type);
        Assertions.assertEquals(1, c.getRetries());
    }

    @Test
    void transientFailureRetriesUpToLimitThenFails() {
        FakeWorld w = new FakeWorld().with("stone_pickaxe", 1);
        FugueCoordinator c = coordinator("raw_iron", 3, null);
        TaskFailure noPath = new TaskFailure(FailureReason.NO_PATH, "no path", false);
        FugueCoordinator.Decision d = c.step(w, null);
        d = c.step(w, report(false, TaskResult.FAILURE, noPath));
        Assertions.assertEquals(RELEASE, d.type);
        d = c.step(w, null);
        Assertions.assertEquals(RUN, d.type);
        d = c.step(w, report(false, TaskResult.FAILURE, noPath));
        Assertions.assertEquals(FAILED, d.type);
        Assertions.assertEquals(FailureReason.NO_PATH, c.getFailure().getReason());
        Assertions.assertEquals(2, c.getFailedAttempts());
        Assertions.assertFalse(c.isBlocked());
    }

    @Test
    void nonTransientFailureWithoutProgressGivesUpImmediately() {
        FakeWorld w = new FakeWorld().with("stone_pickaxe", 1);
        FugueCoordinator c = coordinator("raw_iron", 3, null);
        c.step(w, null);
        FugueCoordinator.Decision d = c.step(w, report(false, TaskResult.FAILURE,
                new TaskFailure(FailureReason.PRECONDITION_FAILED, "nope", false)));
        Assertions.assertEquals(FAILED, d.type);
        Assertions.assertEquals(1, c.getFailedAttempts());
    }

    @Test
    void recoverableChildFailureIsLeftToTheChild() {
        FakeWorld w = new FakeWorld().with("stone_pickaxe", 1);
        FugueCoordinator c = coordinator("raw_iron", 3, null);
        c.step(w, null);
        FugueCoordinator.Decision d = c.step(w, report(false, TaskResult.FAILURE,
                new TaskFailure(FailureReason.NO_PATH, "retrying", true)));
        Assertions.assertEquals(RUN, d.type);
        Assertions.assertFalse(d.fresh);
        Assertions.assertEquals(RUN, c.step(w, report(false, TaskResult.RETRY, null)).type);
    }

    @Test
    void momentaryChildFailureIsRiddenOut() {
        // A grandchild's ABORT copied up for a few ticks, then the child retargets (live: stale pickup target).
        FakeWorld w = new FakeWorld().with("wooden_pickaxe", 1);
        FugueCoordinator c = new FugueCoordinator("cobblestone", 8, new FuguePlanner(BOOK), new FugueCoordinator.Config(), log::add);
        c.step(w, null);
        TaskFailure gone = new TaskFailure(FailureReason.TARGET_UNAVAILABLE, "target gone", false);
        for (int i = 0; i < 5; i++) {
            FugueCoordinator.Decision d = c.step(w, report(false, TaskResult.FAILURE, gone));
            Assertions.assertEquals(RUN, d.type);
            Assertions.assertFalse(d.fresh);
        }
        Assertions.assertEquals(RUN, c.step(w, running()).type);
        Assertions.assertEquals(0, c.getFailedAttempts());
        // A failure that persists past the grace period ends the attempt.
        FugueCoordinator.Decision d = null;
        for (int i = 0; i <= 40 && (d == null || d.type == RUN); i++) d = c.step(w, report(false, TaskResult.FAILURE, gone));
        Assertions.assertEquals(RELEASE, d.type);
        Assertions.assertEquals(1, c.getFailedAttempts());
    }

    @Test
    void finishedChildFailureSkipsTheGracePeriod() {
        FakeWorld w = new FakeWorld().with("wooden_pickaxe", 1);
        FugueCoordinator c = new FugueCoordinator("cobblestone", 8, new FuguePlanner(BOOK), new FugueCoordinator.Config(), log::add);
        c.step(w, null);
        FugueCoordinator.Decision d = c.step(w, report(true, TaskResult.FAILURE,
                new TaskFailure(FailureReason.PRECONDITION_FAILED, "nope", false)));
        Assertions.assertEquals(FAILED, d.type);
    }

    @Test
    void failuresThatMadeProgressDoNotExhaustTheNode() {
        FakeWorld w = new FakeWorld().with("wooden_pickaxe", 1);
        FugueCoordinator c = coordinator("cobblestone", 8, null);
        TaskFailure gone = new TaskFailure(FailureReason.TARGET_UNAVAILABLE, "target gone", false);
        c.step(w, null);
        for (int i = 0; i < 6; i++) {
            w.add("cobblestone", 1);
            Assertions.assertEquals(RELEASE, c.step(w, report(false, TaskResult.FAILURE, gone)).type, "attempt " + i);
            Assertions.assertEquals(RUN, c.step(w, null).type);
        }
        // Two consecutive failures with no progress still give up.
        Assertions.assertEquals(RELEASE, c.step(w, report(false, TaskResult.FAILURE, gone)).type);
        c.step(w, null);
        Assertions.assertEquals(FAILED, c.step(w, report(false, TaskResult.FAILURE, gone)).type);
        Assertions.assertEquals(8, c.getFailedAttempts());
    }

    @Test
    void blockedChildEndsObjectiveAsBlocked() {
        FakeWorld w = new FakeWorld().with("stone_pickaxe", 1);
        FugueCoordinator c = coordinator("raw_iron", 3, null);
        c.step(w, null);
        FugueCoordinator.Decision d = c.step(w, report(false, TaskResult.BLOCKED,
                new TaskFailure(FailureReason.INVENTORY_FULL, "full", true)));
        Assertions.assertEquals(FAILED, d.type);
        Assertions.assertTrue(c.isBlocked());
        Assertions.assertEquals(FailureReason.INVENTORY_FULL, c.getFailure().getReason());
    }

    @Test
    void cancelledChildIsAFailedAttemptNotSuccess() {
        FakeWorld w = new FakeWorld().with("stone_pickaxe", 1);
        FugueCoordinator c = coordinator("raw_iron", 3, null);
        c.step(w, null);
        FugueCoordinator.Decision d = c.step(w, report(false, TaskResult.CANCELLED, null));
        Assertions.assertNotEquals(SUCCEEDED, d.type);
        Assertions.assertEquals(1, c.getFailedAttempts());
        Assertions.assertEquals(0, c.getNodeSuccesses());
    }

    @Test
    void noProgressWatchdogAbandonsAttempt() {
        FugueCoordinator.Config cfg = new FugueCoordinator.Config();
        cfg.noProgressTicks = 5;
        FakeWorld w = new FakeWorld().with("stone_pickaxe", 1);
        FugueCoordinator c = coordinator("raw_iron", 3, cfg);
        c.step(w, null);
        FugueCoordinator.Decision d = null;
        int ticks = 0;
        do { d = c.step(w, running()); ticks++; } while (d.type == RUN && ticks < 20);
        Assertions.assertEquals(RELEASE, d.type); // TIMEOUT is transient: one retry
        Assertions.assertEquals(6, ticks);
        Assertions.assertEquals(FailureReason.TIMEOUT, c.getState("raw_iron").lastFailure.getReason());
        // Progress resets the watchdog.
        c.step(w, null);
        for (int i = 0; i < 4; i++) c.step(w, running());
        w.add("raw_iron", 1);
        for (int i = 0; i < 4; i++) Assertions.assertEquals(RUN, c.step(w, running()).type);
    }

    @Test
    void stolenInputReopensItsNode() {
        FakeWorld w = new FakeWorld().with("wooden_pickaxe", 1).with("stick", 2).with("coal", 1);
        w.stations.add("crafting_table");
        FugueCoordinator c = coordinator("iron_ingot", 3, null);
        FugueCoordinator.Decision d = c.step(w, null);
        Assertions.assertEquals("cobblestone", d.key);
        Assertions.assertEquals(11, d.target); // stone pickaxe + furnace in one trip
        w.produce(BOOK, d.key, d.target);
        d = c.step(w, running());
        Assertions.assertEquals("stone_pickaxe", d.key);
        w.produce(BOOK, d.key, d.target);       // 8 cobblestone left for the furnace
        w.add("cobblestone", -6);               // stolen / used as scaffolding
        d = c.step(w, running());
        Assertions.assertEquals("cobblestone", d.key, "furnace input re-netted from what is actually held");
        Assertions.assertEquals(8, d.target);
        Assertions.assertEquals(1, c.getReopens());
        // And the objective still completes.
        Assertions.assertEquals(SUCCEEDED, runToEndFrom(c, w, d, 50).type, String.join("\n", log));
    }

    private static FugueCoordinator.Decision runToEndFrom(FugueCoordinator c, FakeWorld w, FugueCoordinator.Decision d, int maxTicks) {
        for (int i = 0; i < maxTicks && (d.type == RUN || d.type == RELEASE); i++) {
            if (d.type == RUN) w.produce(BOOK, d.key, d.target);
            d = c.step(w, d.type == RUN ? running() : null);
        }
        return d;
    }

    @Test
    void invalidatedSucceededNodeIsReopenedAtNextPlan() {
        FakeWorld w = new FakeWorld().with("wooden_pickaxe", 1).with("stick", 4).with("crafting_table", 1);
        FugueCoordinator c = coordinator("furnace", 1, null);
        // furnace needs 8 cobblestone
        FugueCoordinator.Decision d = c.step(w, null);
        Assertions.assertEquals("cobblestone", d.key);
        Assertions.assertEquals(8, d.target);
        w.add("cobblestone", 8);
        d = c.step(w, running());
        Assertions.assertEquals("furnace", d.key);
        // The furnace craft fails (no progress, transient) after scaffolding ate some cobblestone.
        w.add("cobblestone", -5);
        d = c.step(w, report(false, TaskResult.FAILURE, new TaskFailure(FailureReason.NO_PATH, "x", false)));
        Assertions.assertEquals(RELEASE, d.type);
        d = c.step(w, null);
        Assertions.assertEquals("cobblestone", d.key, "inputs are re-netted from current inventory");
        Assertions.assertEquals(8, d.target);
        Assertions.assertEquals(1, c.getReopens());
    }

    @Test
    void schedulerPrefersNearerReadySource() {
        FakeWorld w = new FakeWorld().with("wooden_pickaxe", 1).with("stick", 4).with("crafting_table", 1);
        w.distances.put("coal", 6.0);
        w.distances.put("cobblestone", 30.0);
        FugueCoordinator c = coordinator("iron_ingot", 3, null);
        Assertions.assertEquals("coal", c.step(w, null).key);
        FakeWorld w2 = new FakeWorld().with("wooden_pickaxe", 1).with("stick", 4).with("crafting_table", 1);
        w2.distances.put("coal", 50.0);
        w2.distances.put("cobblestone", 3.0);
        Assertions.assertEquals("cobblestone", coordinator("iron_ingot", 3, null).step(w2, null).key);
    }

    @Test
    void runningNodeIsNotPreemptedByCheaperChoice() {
        FakeWorld w = new FakeWorld().with("wooden_pickaxe", 1).with("stick", 4).with("crafting_table", 1);
        w.distances.put("coal", 50.0);
        w.distances.put("cobblestone", 3.0);
        FugueCoordinator c = coordinator("iron_ingot", 3, null);
        Assertions.assertEquals("cobblestone", c.step(w, null).key);
        w.distances.put("coal", 1.0);
        for (int i = 0; i < 10; i++) {
            FugueCoordinator.Decision d = c.step(w, running());
            Assertions.assertEquals("cobblestone", d.key);
            Assertions.assertFalse(d.fresh);
        }
    }

    @Test
    void tableCraftsAreBatchedByDownstreamThenStation() {
        FakeWorld w = new FakeWorld().with("wooden_pickaxe", 1).with("stick", 4).with("cobblestone", 11).with("coal", 1);
        w.stations.add("crafting_table");
        FugueCoordinator c = coordinator("iron_pickaxe", 1, null);
        FugueCoordinator.Decision d = c.step(w, null);
        Assertions.assertEquals("stone_pickaxe", d.key); // unlocks raw_iron -> ingot -> pickaxe
        w.produce(BOOK, d.key, d.target);
        d = c.step(w, running());
        Assertions.assertEquals("furnace", d.key);       // same table, before walking off to mine
    }

    @Test
    void unsupportedGoalFailsExplicitly() {
        FugueCoordinator c = coordinator("diamond", 1, null);
        FugueCoordinator.Decision d = c.step(new FakeWorld(), null);
        Assertions.assertEquals(FAILED, d.type);
        Assertions.assertEquals(FailureReason.RESOURCE_MISSING, d.failure.getReason());
    }

    @Test
    void nodeStartBudgetStopsLivelock() {
        FugueCoordinator.Config cfg = new FugueCoordinator.Config();
        cfg.maxNodeStarts = 3;
        FakeWorld w = new FakeWorld().with("stone_pickaxe", 1);
        FugueCoordinator c = coordinator("raw_iron", 30, cfg);
        FugueCoordinator.Decision d = c.step(w, null);
        for (int i = 0; i < 50 && d.type != FAILED; i++) {
            // each attempt makes a little progress then the child gives up
            if (d.type == RUN) {
                w.add("raw_iron", 1);
                d = c.step(w, report(true, TaskResult.SUCCESS, null));
            } else {
                d = c.step(w, null);
            }
        }
        Assertions.assertEquals(FAILED, d.type);
        Assertions.assertTrue(c.getNodeStarts() <= 4, "starts=" + c.getNodeStarts());
    }

    @Test
    void planningOverheadIsRecorded() {
        FugueCoordinator c = coordinator("iron_pickaxe", 1, null);
        runToEnd(c, new FakeWorld(), 200);
        Assertions.assertTrue(c.getReplans() >= 12);
        Assertions.assertTrue(c.getPlanNanosMax() > 0);
        Assertions.assertTrue(c.metrics().contains("seq=log>"));
    }
}
