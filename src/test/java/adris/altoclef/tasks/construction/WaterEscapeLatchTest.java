package adris.altoclef.tasks.construction;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskChain;
import adris.altoclef.tasksystem.TaskRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tick-driven tests of the mining / water-escape handoff. {@link Miner} reproduces the branch order of
 * {@code DestroyBlockTask.onTick} (escape held, bob escape, mine, mining-while-wet escape, path) over scripted player
 * state, with the real {@link WaterEscapeLatch} and the real {@link Task#tick} runner. {@code legacy} is the pre-latch
 * handling, kept to pin the measured defect.
 */
public class WaterEscapeLatchTest {

    private static final TaskChain CHAIN = new TaskChain(new TaskRunner(null)) {
        @Override protected void onStop() {}
        @Override public void onInterrupt(TaskChain other) {}
        @Override protected void onTick() {}
        @Override public float getPriority() { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "test"; }
    };

    /** Stand-in for GetOutOfWaterTask: same equality rule, finishes only on its own stable-dry predicate. */
    static final class Escape extends Task {
        boolean stableDry;
        int starts, stops;
        void giveUp() { fail(FailureReason.TIMEOUT, "escape gave up", false); }
        @Override protected void onStart() { starts++; }
        @Override protected Task onTick() { return null; }
        @Override protected void onStop(Task interruptTask) { stops++; }
        @Override protected boolean isEqual(Task other) { return other instanceof Escape; }
        @Override protected String toDebugString() { return "escape"; }
        @Override public boolean isFinished() { return stableDry; }
    }

    /** DestroyBlockTask's water/mine/path branches over scripted state. */
    static final class Miner extends Task {
        final boolean legacy;
        final WaterEscapeLatch latch = new WaterEscapeLatch();
        boolean wet, grounded, reach;
        int bobTicks;
        boolean isMining;
        Escape lastEscape;
        int navCancels, goalsSet, mineTicks, escapesIssued, unreachableReports;
        String branch = "";

        Miner(boolean legacy) { this.legacy = legacy; }

        @Override protected void onStart() { latch.parentRestarted(); }

        @Override protected Task onTick() {
            if (!legacy) {
                Task held = latch.held();
                if (held != null) { branch = "HELD"; return held; }
                if (latch.releasePending(getSub())) { branch = "RELEASE"; return null; }
            }
            if (wet && !grounded) {
                bobTicks++;
                isMining = false;
                if (WaterEscapeLatch.bobEscapeDue(wet, grounded, reach, bobTicks)) {
                    bobTicks = 0;
                    navCancels++;
                    branch = "BOB_ESCAPE";
                    return issue();
                }
            } else {
                bobTicks = 0;
            }
            if (reach && grounded) {
                branch = "MINE";
                isMining = true;
                mineTicks++;
                return null;
            }
            if (isMining && wet) {
                isMining = false;
                branch = "MINING_WET_ESCAPE";
                return issue();
            }
            isMining = false;
            branch = "PATH";
            goalsSet++;
            return null;
        }

        private Task issue() {
            escapesIssued++;
            lastEscape = new Escape();
            if (!legacy && latch.issued(lastEscape)) unreachableReports++;
            return lastEscape;
        }

        @Override protected void onStop(Task interruptTask) {}
        @Override protected boolean isEqual(Task other) { return other == this; }
        @Override protected String toDebugString() { return "miner"; }
        @Override public boolean isFinished() { return false; }
    }

    private Miner m;

    @BeforeEach
    void fresh() {
        m = new Miner(false);
    }

    private void tick(boolean wet, boolean grounded, boolean reach) {
        m.wet = wet;
        m.grounded = grounded;
        m.reach = reach;
        m.tick(CHAIN);
    }

    /** The measured pond cycle: target out of reach under a 1-deep pond; Baritone's steps lift the bot off the floor. */
    private void pondStep(int airborne, int grounded) {
        for (int i = 0; i < airborne; i++) tick(true, false, false);
        for (int i = 0; i < grounded; i++) tick(true, true, false);
    }

    @Test
    void legacyReproducesMeasuredThrash() {
        m = new Miner(true);
        for (int cycle = 0; cycle < 5; cycle++) pondStep(20, 2);
        // Every airborne tick re-cancels navigation; every wet ground contact drops the escape and paths back.
        Assertions.assertEquals(100, m.navCancels);
        Assertions.assertTrue(m.goalsSet >= 5, "pathed back toward the buried target on each ground contact");
        Assertions.assertEquals("PATH", m.branch);
    }

    @Test
    void shallowWaterGroundContactKeepsEscape() {
        tick(true, false, false);
        Escape e = m.lastEscape;
        Assertions.assertEquals("BOB_ESCAPE", m.branch);
        tick(true, true, false); // touches the pond floor, still wet
        tick(true, true, false);
        Assertions.assertEquals("HELD", m.branch);
        Assertions.assertTrue(e.isActive());
        Assertions.assertEquals(0, e.stops);
        Assertions.assertEquals(0, m.goalsSet, "no path toward the target while escaping");
    }

    @Test
    void navigationCancelledOncePerEscape() {
        for (int cycle = 0; cycle < 5; cycle++) pondStep(20, 2);
        Assertions.assertEquals(1, m.navCancels);
        Assertions.assertEquals(1, m.escapesIssued);
        Assertions.assertEquals(1, m.lastEscape.starts, "escape started once, not restarted");
    }

    @Test
    void flickerDoesNotEndEscape() {
        tick(true, false, false);
        Escape e = m.lastEscape;
        for (int i = 0; i < 10; i++) tick(i % 2 == 0, true, false); // dry/wet alternation; stable-dry never holds
        Assertions.assertEquals("HELD", m.branch);
        Assertions.assertTrue(e.isActive());
    }

    @Test
    void stableExitReleasesAndPaths() {
        tick(true, false, false);
        Escape e = m.lastEscape;
        tick(false, true, false);
        e.stableDry = true; // GetOutOfWaterTask: dry and grounded for 750 ms
        tick(false, true, false);
        Assertions.assertEquals("RELEASE", m.branch);
        Assertions.assertEquals(1, e.stops, "runner stopped the finished escape");
        Assertions.assertFalse(e.isActive());
        tick(false, true, false);
        Assertions.assertEquals("PATH", m.branch);
    }

    @Test
    void minesAfterEscape() {
        tick(true, false, false);
        m.lastEscape.stableDry = true;
        tick(false, true, false); // release
        tick(false, true, true);
        Assertions.assertEquals("MINE", m.branch);
        Assertions.assertEquals(1, m.mineTicks);
    }

    @Test
    void underwaterCollectionNeverEscapes() {
        for (int i = 0; i < 50; i++) tick(true, true, true); // standing on the floor, submerged, block in reach
        Assertions.assertEquals(50, m.mineTicks);
        Assertions.assertEquals(0, m.escapesIssued);
    }

    @Test
    void bobbingInReachWaitsBeforeEscaping() {
        for (int i = 0; i < WaterEscapeLatch.WATER_BOB_ESCAPE_TICKS - 1; i++) tick(true, false, true);
        Assertions.assertEquals(0, m.escapesIssued);
        tick(true, false, true);
        Assertions.assertEquals(1, m.escapesIssued);
    }

    @Test
    void wetWhileMiningEscapesAndHolds() {
        tick(true, true, true);
        Assertions.assertEquals("MINE", m.branch);
        tick(true, true, false); // lost reach while wet
        Assertions.assertEquals("MINING_WET_ESCAPE", m.branch);
        tick(true, true, false);
        Assertions.assertEquals("HELD", m.branch);
    }

    @Test
    void repeatedWaterReentryReportsTargetUnreachable() {
        for (int entry = 1; entry <= 3; entry++) {
            tick(true, false, false); // swims back in toward the target
            Assertions.assertEquals("BOB_ESCAPE", m.branch);
            Assertions.assertEquals(entry <= 1 + WaterEscapeLatch.ALLOWED_REENTRIES ? 0 : 1, m.unreachableReports,
                    "entry " + entry);
            m.lastEscape.stableDry = true;
            tick(false, true, false); // released
            tick(false, true, false); // paths back
            Assertions.assertEquals("PATH", m.branch);
        }
        Assertions.assertEquals(1, m.unreachableReports);
        Assertions.assertEquals(3, m.escapesIssued);
    }

    @Test
    void escapeThatGivesUpIsReleased() {
        tick(true, false, false);
        Escape e = m.lastEscape;
        e.giveUp();
        tick(true, true, false);
        Assertions.assertNotEquals("HELD", m.branch, "a failed escape is not held forever");
    }

    @Test
    void escapeEndingWhileStillAfloatHandsOverCleanly() {
        tick(true, false, false);
        Escape first = m.lastEscape;
        first.giveUp(); // terminal result while the bot is still wet and airborne
        tick(true, false, false);
        Assertions.assertEquals("RELEASE", m.branch);
        Assertions.assertFalse(first.isActive(), "the ended escape is stopped, not left running");
        tick(true, false, false);
        Assertions.assertEquals("BOB_ESCAPE", m.branch);
        Assertions.assertNotSame(first, m.lastEscape);
        Assertions.assertSame(m.lastEscape, m.getSub(), "the escape the latch holds is the one the runner runs");
        Assertions.assertTrue(m.lastEscape.isActive());
        tick(true, true, false);
        Assertions.assertEquals("HELD", m.branch);
        Assertions.assertEquals(1, m.latch.reentries(), "one re-entry, not one per tick");
    }

    @Test
    void parentRestartDropsHeldEscapeButKeepsCount() {
        tick(true, false, false);
        m.stop();
        tick(false, true, false); // restarted: onStart
        Assertions.assertEquals("PATH", m.branch);
        tick(true, false, false);
        Assertions.assertEquals(1, m.latch.reentries(), "re-entry after the restart still counts");
    }
}
