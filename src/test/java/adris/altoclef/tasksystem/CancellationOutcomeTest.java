package adris.altoclef.tasksystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The cancellation contract through the real {@link Task#tick} / {@link Task#stop} path: a failure that was already
 * the task's own terminal outcome survives a stop; a stop accepted while the task was still running reads CANCELLED,
 * even when a child failed on the same tick (Fugue v1.1 B05); success is never rewritten; the outcome is stable and
 * stop callbacks run once.
 */
public class CancellationOutcomeTest {

    private static final TaskChain CHAIN = new TaskChain(new TaskRunner(null)) {
        @Override protected void onStop() {}
        @Override public void onInterrupt(TaskChain other) {}
        @Override protected void onTick() {}
        @Override public float getPriority() { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "test"; }
    };

    static final class Child extends Task {
        boolean done;
        Runnable onTickAction = () -> {};
        int stops;
        void giveUp() { done = true; fail(FailureReason.TIMEOUT, "child gave up", false); }
        void softFail() { fail(FailureReason.NO_PATH, "child no path", false); }
        @Override protected void onStart() {}
        @Override protected Task onTick() { onTickAction.run(); return null; }
        @Override protected void onStop(Task interruptTask) { stops++; }
        @Override protected boolean isEqual(Task other) { return other == this; }
        @Override protected String toDebugString() { return "child"; }
        @Override public boolean isFinished() { return done; }
    }

    static class Parent extends Task {
        final Child child;
        boolean goal;
        int stops;
        Parent(Child child) { this.child = child; }
        void ownFailure() { fail(FailureReason.TIMEOUT, "parent gave up", false); }
        void ownSuccess() { goal = true; succeed(); }
        @Override protected void onStart() {}
        @Override protected Task onTick() { return goal ? null : child; }
        @Override protected void onStop(Task interruptTask) { stops++; }
        @Override protected boolean isEqual(Task other) { return other == this; }
        @Override protected String toDebugString() { return "parent"; }
        @Override public boolean isFinished() { return goal; }
    }

    @Test
    void cancelWhileChildActive() {
        Child c = new Child();
        Parent p = new Parent(c);
        p.tick(CHAIN);
        p.stop();
        Assertions.assertEquals(TaskResult.CANCELLED, p.getLastResult());
        Assertions.assertEquals(TaskResult.CANCELLED, c.getLastResult());
    }

    @Test
    void cancelRacingChildFailureIsCancelled() {
        Child c = new Child();
        c.onTickAction = c::softFail; // child fails on the tick before the cancel is accepted
        Parent p = new Parent(c);
        p.tick(CHAIN);
        Assertions.assertEquals(TaskResult.FAILURE, p.getLastResult(), "absorbed while still running");
        p.stop();
        Assertions.assertEquals(TaskResult.CANCELLED, p.getLastResult());
        Assertions.assertEquals(TaskResult.FAILURE, c.getLastResult(), "the child keeps its own failure");
    }

    @Test
    void ownFailureBeforeCancelIsPreserved() {
        Child c = new Child();
        Parent p = new Parent(c);
        p.tick(CHAIN);
        p.ownFailure();
        p.stop();
        Assertions.assertEquals(TaskResult.FAILURE, p.getLastResult());
    }

    @Test
    void absorbedFailureOnFinishedTaskIsPreserved() {
        Child c = new Child();
        c.onTickAction = c::giveUp;
        Parent p = new Parent(c) {
            @Override public boolean isFinished() { return c.isFinished(); }
        };
        p.tick(CHAIN);
        Assertions.assertTrue(p.isFinished());
        p.stop();
        Assertions.assertEquals(TaskResult.FAILURE, p.getLastResult(), "terminal before the stop arrived");
    }

    @Test
    void successBeforeCancelIsPreserved() {
        Child c = new Child();
        Parent p = new Parent(c);
        p.tick(CHAIN);
        p.ownSuccess();
        p.tick(CHAIN);
        p.stop();
        Assertions.assertEquals(TaskResult.SUCCESS, p.getLastResult());
    }

    @Test
    void outcomeIsStableAndCallbacksRunOnce() {
        Child c = new Child();
        c.onTickAction = c::softFail;
        Parent p = new Parent(c);
        p.tick(CHAIN);
        p.stop();
        p.stop();
        for (int i = 0; i < 3; i++) Assertions.assertEquals(TaskResult.CANCELLED, p.getLastResult());
        Assertions.assertEquals(1, p.stops);
        Assertions.assertEquals(1, c.stops);
    }
}
