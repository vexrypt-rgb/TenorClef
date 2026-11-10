package adris.altoclef.fugue;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.TaskFailure;
import adris.altoclef.tasksystem.TaskResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static adris.altoclef.fugue.FugueCoordinator.Decision.Type.*;

/** v1.1 stall classification and recovery in {@link FugueCoordinator}, tick by tick. */
public class FugueRecoveryTest {

    private static final FugueRecipeBook BOOK = FugueRecipeBook.ironTools();
    private final List<String> log = new ArrayList<>();

    /** Short windows so the tests stay small: stall 100, trap 40 with 2 escapes, watchdog 1000, release 20. */
    private FugueCoordinator coordinator(String goal, int n, boolean recovery, Consumer<FugueCoordinator.Config> tweak) {
        FugueCoordinator.Config cfg = new FugueCoordinator.Config();
        cfg.failureGraceTicks = 0;
        cfg.recovery = recovery;
        cfg.stallTicks = 100;
        cfg.trapTicks = 40;
        cfg.trapEscapes = 2;
        cfg.noProgressTicks = 1000;
        cfg.releaseTimeoutTicks = 20;
        if (tweak != null) tweak.accept(cfg);
        return new FugueCoordinator(goal, n, new FuguePlanner(BOOK), cfg, log::add);
    }

    private static FugueCoordinator.ChildReport on(String target) {
        return on(target, false);
    }

    private static FugueCoordinator.ChildReport on(String target, boolean escaping) {
        return new FugueCoordinator.ChildReport(false, TaskResult.RUNNING, null, target, escaping);
    }

    private static FugueCoordinator.ChildReport released() {
        return FugueCoordinator.ChildReport.detached(true);
    }

    /** Steps with the same report until the decision is not a plain RUN; returns it. */
    private static FugueCoordinator.Decision until(FugueCoordinator c, FakeWorld w, FugueCoordinator.ChildReport r, int max) {
        FugueCoordinator.Decision d = null;
        for (int i = 0; i < max; i++) {
            d = c.step(w, r);
            if (d.type != RUN) return d;
        }
        return d;
    }

    private String logs() {
        return String.join("\n", log);
    }

    // 1
    @Test
    void genuineProgressOnOneTargetNeverStalls() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 64, true, null);
        Assertions.assertEquals(RUN, c.step(w, null).type);
        for (int i = 0; i < 900; i++) {
            if (i % 80 == 0) w.add("log", 1); // an item every 80 ticks, under the 100-tick window
            Assertions.assertEquals(RUN, c.step(w, on("1,64,1")).type, logs());
        }
        Assertions.assertEquals(0, c.getStalls());
        Assertions.assertEquals(0, c.getRecoveries());
    }

    // 2
    @Test
    void workWithoutAMiningTargetIsNotAStall() {
        // Crafting, smelting, picking up drops, or standing still: no DestroyBlockTask target is visible.
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        for (int i = 0; i < 900; i++) Assertions.assertEquals(RUN, c.step(w, on(null)).type);
        // A short pause on a target is also fine.
        for (int i = 0; i < 90; i++) Assertions.assertEquals(RUN, c.step(w, on("5,60,5")).type);
        Assertions.assertEquals(0, c.getStalls());
    }

    // 3
    @Test
    void movementWithoutProgressIsLeftToTheV1Watchdog() {
        // Targets keep changing (the child roams) but nothing is gained: not one of the two stall classes; the v1
        // no-progress watchdog ends the attempt as before.
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        FugueCoordinator.Decision d = null;
        for (int i = 0; i < 1100; i++) {
            d = c.step(w, on((i / 50) + ",64,0"));
            if (d.type != RUN) break;
        }
        Assertions.assertEquals(RELEASE, d.type, logs());
        Assertions.assertEquals(FailureReason.TIMEOUT, d.failure.getReason());
        Assertions.assertEquals(0, c.getRecoveries());
    }

    // 4
    @Test
    void repeatedSameTargetIsExcludedAndTheChildReplaced() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        FugueCoordinator.Decision d = until(c, w, on("1,64,1"), 200);
        Assertions.assertEquals(RECOVER, d.type, logs());
        Assertions.assertTrue(c.isRecovering());
        Assertions.assertTrue(w.excluded.isEmpty(), "nothing is excluded before the child lets go");
        d = c.step(w, released());
        Assertions.assertEquals(RUN, d.type);
        Assertions.assertTrue(d.fresh, "the retry is a new child");
        Assertions.assertTrue(w.excluded.contains("1,64,1"));
        Assertions.assertEquals(1, c.getRecoveries());
        Assertions.assertTrue(logs().contains("stall REPEATED_TARGET_FAILURE log target=1,64,1"), logs());
    }

    @Test
    void waterEscapeLoopIsAnEnvironmentalTrapCaughtEarly() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        FugueCoordinator.Decision d = null;
        int t;
        for (t = 0; t < 200; t++) {
            boolean esc = (t / 10) % 2 == 1; // "get out of water" <-> "Getting to block", as in the v1 idx2 loop
            d = c.step(w, on("1596,63,-126", esc));
            if (d.type != RUN) break;
        }
        Assertions.assertEquals(RECOVER, d.type, logs());
        Assertions.assertTrue(t < 100, "trap fires on the 40-tick window, before the 100-tick stall window (t=" + t + ")");
        Assertions.assertTrue(logs().contains("stall ENVIRONMENTAL_TRAP"), logs());
    }

    @Test
    void trapRecoveryAlsoExcludesWetNeighboursAndForgetsThemAll() {
        FakeWorld w = new FakeWorld();
        w.wet.put("579,57,-51", List.of("579,56,-51", "580,57,-51"));
        FugueCoordinator c = coordinator("cobblestone", 3, true, null);
        w.with("wooden_pickaxe", 1);
        c.step(w, null);
        FugueCoordinator.Decision d = null;
        for (int t = 0; t < 200; t++) {
            d = c.step(w, on("579,57,-51", (t / 10) % 2 == 1));
            if (d.type != RUN) break;
        }
        Assertions.assertEquals(RECOVER, d.type, logs());
        c.step(w, released());
        Assertions.assertEquals(java.util.Set.of("579,57,-51", "579,56,-51", "580,57,-51"), w.excluded);
        Assertions.assertTrue(logs().contains("+2 neighbour(s)"), logs());
        c.releaseExclusions(w);
        Assertions.assertTrue(w.excluded.isEmpty());
        Assertions.assertEquals(3, w.forgotten.size());
    }

    @Test
    void longEscapeClassifiedAsRepeatedTargetStillExcludesWetNeighbours() {
        // One escape that lasts (the pilot's "get out of water: Completing goal" leaf) never reaches 2 rising edges.
        FakeWorld w = new FakeWorld();
        w.wet.put("579,57,-51", List.of("579,56,-51"));
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        c.step(w, on("579,57,-51", false));
        FugueCoordinator.Decision d = until(c, w, on("579,57,-51", true), 200);
        Assertions.assertEquals(RECOVER, d.type, logs());
        Assertions.assertTrue(logs().contains("stall REPEATED_TARGET_FAILURE"), logs());
        c.step(w, released());
        Assertions.assertEquals(java.util.Set.of("579,57,-51", "579,56,-51"), w.excluded);
    }

    @Test
    void repeatedTargetRecoveryExcludesOnlyTheTarget() {
        FakeWorld w = new FakeWorld();
        w.wet.put("1,64,1", List.of("1,63,1"));
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        until(c, w, on("1,64,1"), 200);
        c.step(w, released());
        Assertions.assertEquals(java.util.Set.of("1,64,1"), w.excluded);
    }

    // 5 + 7
    @Test
    void recoveryThatReachesAnotherTargetResumesProgressAndTheNodeSucceeds() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        Assertions.assertEquals(RECOVER, until(c, w, on("1,64,1"), 200).type);
        Assertions.assertEquals(RUN, c.step(w, released()).type);
        for (int i = 0; i < 5; i++) c.step(w, on("9,64,9"));
        w.add("log", 1);
        Assertions.assertEquals(RUN, c.step(w, on("9,64,8")).type);
        w.add("log", 2);
        FugueCoordinator.Decision d = c.step(w, on("9,64,7"));
        Assertions.assertEquals(SUCCEEDED, d.type, logs());
        Assertions.assertEquals(1, c.getRecoveriesResumed());
        Assertions.assertTrue(logs().contains("recovery RESUMED log"), logs());
        Assertions.assertTrue(w.excluded.isEmpty(), "exclusions are forgotten when the objective ends");
        Assertions.assertEquals(List.of("1,64,1"), w.forgotten);
    }

    // 6 + 11
    @Test
    void progressOnADifferentNodeAfterARecoveryIsNotCountedAsResumed() {
        // Smoke run: a log recovery fired with 2/3 logs held; the replan then went to planks, which crafted at once.
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("planks", 8, true, null);
        c.step(w, null);
        Assertions.assertEquals(RECOVER, until(c, w, on("1,64,1"), 200).type, logs());
        w.with("log", 2);
        c.step(w, released());
        Assertions.assertTrue(logs().contains("replan moved on from log to planks"), logs());
        w.with("planks", 8);
        Assertions.assertEquals(SUCCEEDED, c.step(w, null).type, logs());
        Assertions.assertEquals(0, c.getRecoveriesResumed());
        Assertions.assertFalse(logs().contains("recovery RESUMED"), logs());
    }

    @Test
    void recoveryThatDoesNotResolveIsBoundedPerNodeThenV1LimitsApply() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        Assertions.assertEquals(RECOVER, until(c, w, on("1,64,1"), 200).type);
        c.step(w, released());
        Assertions.assertEquals(RECOVER, until(c, w, on("2,64,2"), 200).type, "a second, different target may be recovered");
        c.step(w, released());
        // Third stall on yet another target: the node's budget (2) is spent, so the attempt keeps running...
        FugueCoordinator.Decision d = until(c, w, on("3,64,3"), 300);
        Assertions.assertEquals(RUN, d.type, logs());
        Assertions.assertEquals(2, c.getRecoveries());
        Assertions.assertTrue(logs().contains("no recovery left"), logs());
        // ...until the v1 watchdog ends it, consuming the v1 failure budget as before.
        d = until(c, w, on("3,64,3"), 1000);
        Assertions.assertEquals(RELEASE, d.type, logs());
        Assertions.assertEquals(FailureReason.TIMEOUT, d.failure.getReason());
    }

    @Test
    void objectiveRecoveryBudgetIsSharedAcrossNodes() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, cfg -> cfg.maxRecoveries = 1);
        c.step(w, null);
        Assertions.assertEquals(RECOVER, until(c, w, on("1,64,1"), 200).type);
        c.step(w, released());
        Assertions.assertEquals(RUN, until(c, w, on("2,64,2"), 300).type);
        Assertions.assertEquals(1, c.getRecoveries());
        Assertions.assertEquals(2, c.getStalls());
    }

    // 8 (coordinator side; FugueTask side in FugueRecoveryTaskTest)
    @Test
    void noRecoveryBeforeTheWindow() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        for (int i = 0; i < 95; i++) Assertions.assertEquals(RUN, c.step(w, on("1,64,1")).type);
        c.releaseExclusions(w); // what FugueTask.onStop does on cancel
        Assertions.assertTrue(w.excluded.isEmpty());
        Assertions.assertEquals(0, c.getRecoveries());
    }

    // 10
    @Test
    void navigationThatIsNotReleasedFailsExplicitlyWithoutExcludingOrRestarting() {
        FakeWorld w = new FakeWorld();
        w.navIdle = false;
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        Assertions.assertEquals(RECOVER, until(c, w, on("1,64,1"), 200).type);
        int starts = c.getNodeStarts();
        FugueCoordinator.Decision d = null;
        for (int i = 0; i < 30; i++) {
            d = c.step(w, released());
            if (d.type != RECOVER) break;
            Assertions.assertEquals(starts, c.getNodeStarts(), "no new child while navigation is still owned");
        }
        Assertions.assertEquals(FAILED, d.type, logs());
        Assertions.assertTrue(d.failure.getMessage().contains("did not release control"), d.failure.getMessage());
        Assertions.assertTrue(w.excluded.isEmpty());
        Assertions.assertEquals(starts, c.getNodeStarts());
    }

    @Test
    void childStillInstalledHoldsTheRecovery() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        until(c, w, on("1,64,1"), 200);
        // Old child refused to stop (a forcing task): FugueTask reports detached(false).
        for (int i = 0; i < 10; i++) Assertions.assertEquals(RECOVER, c.step(w, FugueCoordinator.ChildReport.detached(false)).type);
        Assertions.assertTrue(w.excluded.isEmpty());
        Assertions.assertEquals(RUN, c.step(w, released()).type, "proceeds once it is gone");
    }

    // 12
    @Test
    void noSafeExclusionMeansOneRestartThenNoMoreRecoveryForTheNode() {
        FakeWorld w = new FakeWorld();
        w.excludeWorks = false;
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        Assertions.assertEquals(RECOVER, until(c, w, on("1,64,1"), 200).type);
        Assertions.assertEquals(RUN, c.step(w, released()).type);
        Assertions.assertTrue(logs().contains("COULD-NOT-EXCLUDE"), logs());
        Assertions.assertEquals(RUN, until(c, w, on("1,64,1"), 300).type, "no second recovery");
        Assertions.assertEquals(1, c.getRecoveries());
    }

    @Test
    void childReturningToAnExcludedTargetIsIneffectiveAndNotOscillated() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        until(c, w, on("1,64,1"), 200);
        c.step(w, released());
        FugueCoordinator.Decision d = until(c, w, on("1,64,1"), 300);
        Assertions.assertEquals(RUN, d.type, logs());
        Assertions.assertEquals(1, c.getRecoveriesIneffective());
        Assertions.assertEquals(1, c.getRecoveries(), "no recovery ping-pong on a target already excluded");
    }

    // 13
    @Test
    void recoveryDoesNotConsumeTheFailureBudgetAndCountsAStart() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        until(c, w, on("1,64,1"), 200);
        c.step(w, released());
        Assertions.assertEquals(0, c.getFailedAttempts());
        Assertions.assertEquals(0, c.getRetries());
        Assertions.assertEquals(2, c.getNodeStarts());
        Assertions.assertEquals(1, c.getRecoveries());
        Assertions.assertEquals(0, c.getState("log").failedAttempts);
        Assertions.assertEquals(1, c.getState("log").recoveries);
        // A transient child failure after the recovery still uses the v1 rules.
        FugueCoordinator.Decision d = c.step(w, new FugueCoordinator.ChildReport(true, TaskResult.FAILURE,
                new TaskFailure(FailureReason.NO_PATH, "no path", false), null, false));
        Assertions.assertEquals(RELEASE, d.type);
        Assertions.assertEquals(1, c.getFailedAttempts());
    }

    // 14
    @Test
    void dependencyOrderAndSuccessSurviveAChildReplacement() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("wooden_pickaxe", 1, true, null);
        FugueCoordinator.Decision d = c.step(w, null);
        Assertions.assertEquals("log", d.key);
        until(c, w, on("1,64,1"), 200);
        d = c.step(w, released());
        Assertions.assertEquals("log", d.key, "the same node is retried, its dependants still wait");
        for (int i = 0; i < 200 && (d.type == RUN || d.type == RELEASE); i++) {
            if (d.type == RUN) w.produce(BOOK, d.key, d.target);
            d = c.step(w, d.type == RUN ? on(null) : null);
        }
        Assertions.assertEquals(SUCCEEDED, d.type, logs());
        Assertions.assertEquals(1, w.count("wooden_pickaxe"));
        Assertions.assertEquals("log", c.getSequence().get(0));
        Assertions.assertEquals("log", c.getSequence().get(1));
        Assertions.assertEquals("wooden_pickaxe", c.getSequence().get(c.getSequence().size() - 1));
    }

    // 16
    @Test
    void recoveryOffIsExactlyV1() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, false, null);
        c.step(w, null);
        FugueCoordinator.Decision d = until(c, w, on("1596,63,-126", true), 1200);
        Assertions.assertEquals(RELEASE, d.type, "v1: only the no-progress watchdog ends the attempt");
        Assertions.assertEquals(FailureReason.TIMEOUT, d.failure.getReason());
        Assertions.assertEquals(0, c.getStalls());
        Assertions.assertFalse(c.metrics().contains("recoveries="), "v1 metrics line unchanged");
        Assertions.assertTrue(w.excluded.isEmpty());
    }

    @Test
    void successRequiresTheHeldCountNotARecovery() {
        FakeWorld w = new FakeWorld();
        FugueCoordinator c = coordinator("log", 3, true, null);
        c.step(w, null);
        until(c, w, on("1,64,1"), 200);
        FugueCoordinator.Decision d = c.step(w, released());
        Assertions.assertNotEquals(SUCCEEDED, d.type);
        Assertions.assertEquals(FugueCoordinator.Status.RUNNING, c.getStatus());
        // Moving away (a new target) is not success either.
        for (int i = 0; i < 50; i++) Assertions.assertEquals(RUN, c.step(w, on("8,70,8")).type);
        Assertions.assertEquals(0, w.count("log"));
    }
}
