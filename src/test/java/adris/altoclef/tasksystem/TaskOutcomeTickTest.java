package adris.altoclef.tasksystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

/**
 * Outcomes through the real {@link Task#tick} loop (not just absorbChildOutcome):
 * "the task stopped" must stay distinguishable from "the task succeeded".
 */
public class TaskOutcomeTickTest {

    private static final TaskChain CHAIN = new TaskChain(new TaskRunner(null)) {
        @Override protected void onStop() {}
        @Override public void onInterrupt(TaskChain other) {}
        @Override protected void onTick() {}
        @Override public float getPriority() { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "test"; }
    };

    /** Child whose behaviour per tick is scripted; done flag drives isFinished like the bail tasks. */
    static final class Scripted extends Task {
        final String name;
        boolean done;
        Runnable onTickAction = () -> {};

        Scripted(String name) { this.name = name; }

        void giveUp() { done = true; fail(FailureReason.TIMEOUT, name + " gave up", false); }
        void reached() { done = true; succeed(); }
        void doneNoOutcome() { done = true; }
        void softFail() { fail(FailureReason.NO_PATH, "retry", true); }

        @Override protected void onStart() {}
        @Override protected Task onTick() { onTickAction.run(); return null; }
        @Override protected void onStop(Task interruptTask) {}
        @Override protected boolean isEqual(Task other) { return other == this; }
        @Override protected String toDebugString() { return name; }
        @Override public boolean isFinished() { return done; }
    }

    static final class Parent extends Task {
        Supplier<Task> child;
        boolean goal;
        Parent(Supplier<Task> child) { this.child = child; }
        @Override protected void onStart() {}
        @Override protected Task onTick() { return goal ? null : child.get(); }
        @Override protected void onStop(Task interruptTask) {}
        @Override protected boolean isEqual(Task other) { return other == this; }
        @Override protected String toDebugString() { return "parent"; }
        @Override public boolean isFinished() { return goal; }
    }

    @Test
    void successfulChildGivesSuccessWhenParentGoalMet() {
        Scripted c = new Scripted("c");
        c.onTickAction = c::reached;
        Parent p = new Parent(() -> c);
        p.tick(CHAIN);
        Assertions.assertEquals(TaskResult.SUCCESS, c.getLastResult());
        Assertions.assertNotEquals(TaskResult.FAILURE, p.getLastResult());
        p.goal = true;
        p.tick(CHAIN);
        Assertions.assertEquals(TaskResult.SUCCESS, p.getLastResult());
    }

    @Test
    void giveUpThroughTickIsFailureAndParentIsNotSuccess() {
        Scripted c = new Scripted("bail");
        c.onTickAction = c::giveUp;
        Parent p = new Parent(() -> c);
        p.tick(CHAIN);
        // Finished (stopped executing) but NOT successful: the finish shim must not overwrite FAILURE.
        Assertions.assertTrue(c.isFinished());
        Assertions.assertEquals(TaskResult.FAILURE, c.getLastResult());
        Assertions.assertEquals(TaskResult.FAILURE, p.getLastResult());
        Assertions.assertEquals(FailureReason.TIMEOUT, p.getLastFailure().getReason());
    }

    @Test
    void childFailureThenFreshChildClearsStaleParentFailure() {
        Scripted bad = new Scripted("first");
        bad.onTickAction = bad::giveUp;
        Scripted[] current = {bad};
        Parent p = new Parent(() -> current[0]);
        p.tick(CHAIN);
        Assertions.assertEquals(TaskResult.FAILURE, p.getLastResult());

        // Recovery: parent hands the slot to a fresh attempt that is running normally.
        current[0] = new Scripted("second");
        p.tick(CHAIN);
        Assertions.assertEquals(TaskResult.RUNNING, p.getLastResult());
        Assertions.assertNull(p.getLastFailure());

        Scripted second = (Scripted) current[0];
        second.onTickAction = second::reached;
        p.tick(CHAIN);
        Assertions.assertEquals(TaskResult.SUCCESS, second.getLastResult());
        Assertions.assertNotEquals(TaskResult.FAILURE, p.getLastResult());
    }

    @Test
    void legacyFinishWithoutOutcomeStillShimsToSuccess() {
        // Existing valid path: most legacy tasks' isFinished() IS their postcondition.
        Scripted c = new Scripted("legacy");
        c.onTickAction = c::doneNoOutcome;
        c.tick(CHAIN);
        Assertions.assertEquals(TaskResult.SUCCESS, c.getLastResult());
    }

    @Test
    void softFailureThenGoalReachedIsRecoveredSuccess() {
        // CustomBaritoneGoalTask pattern: RETRY mid-run, later the goal check passes.
        Scripted c = new Scripted("walk");
        c.onTickAction = c::softFail;
        c.tick(CHAIN);
        Assertions.assertEquals(TaskResult.RETRY, c.getLastResult());
        Assertions.assertFalse(c.isFinished());
        c.onTickAction = c::reached;
        c.tick(CHAIN);
        Assertions.assertEquals(TaskResult.SUCCESS, c.getLastResult());
        Assertions.assertNull(c.getLastFailure());
    }

    @Test
    void stoppedMidRunIsCancelledNotSuccess() {
        Scripted c = new Scripted("c");
        c.tick(CHAIN);
        c.stop();
        Assertions.assertEquals(TaskResult.CANCELLED, c.getLastResult());
    }

    @Test
    void blockedIsNotSuccessEvenIfFinished() {
        Scripted c = new Scripted("c");
        c.onTickAction = () -> { c.done = true; c.blocked(FailureReason.INVENTORY_FULL, "full"); };
        c.tick(CHAIN);
        Assertions.assertEquals(TaskResult.BLOCKED, c.getLastResult());
    }
}
