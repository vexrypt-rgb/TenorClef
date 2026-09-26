package adris.altoclef.tasksystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Propagation via {@link Task#absorbChildOutcome} with lightweight fakes
 * (no Minecraft / tick loop).
 */
public class TaskPropagationTest {

    @Test
    void parentAbsorbsChildFailure() {
        FakeTask parent = new FakeTask("parent");
        FakeTask child = new FakeTask("child");
        child.fail(FailureReason.NO_PATH, "blocked", false);
        parent.absorbChildOutcome(child);
        Assertions.assertEquals(TaskResult.FAILURE, parent.getExplicitResult());
        Assertions.assertNotNull(parent.getLastFailure());
        Assertions.assertEquals(FailureReason.NO_PATH, parent.getLastFailure().getReason());
    }

    @Test
    void parentKeepsTerminalOverChildRetry() {
        FakeTask parent = new FakeTask("parent");
        parent.fail(FailureReason.TARGET_UNAVAILABLE, "gone", false);
        FakeTask child = new FakeTask("child");
        child.fail(FailureReason.TIMEOUT, "stall", true);
        parent.absorbChildOutcome(child);
        Assertions.assertEquals(TaskResult.FAILURE, parent.getExplicitResult());
        Assertions.assertEquals(FailureReason.TARGET_UNAVAILABLE, parent.getLastFailure().getReason());
    }

    @Test
    void succeedClearsFailure() {
        FakeTask t = new FakeTask("t");
        t.fail(FailureReason.TIMEOUT, "x", true);
        t.succeed();
        Assertions.assertEquals(TaskResult.SUCCESS, t.getExplicitResult());
        Assertions.assertNull(t.getLastFailure());
    }

    @Test
    void getLastResultShimsFinished() {
        FakeTask t = new FakeTask("t");
        t.finished = true;
        Assertions.assertEquals(TaskResult.SUCCESS, t.getLastResult());
    }

    // —— Truthful outcomes: isFinished() is a lifecycle signal, not proof of success ——

    @Test
    void childSuccessParentSuccess() {
        FakeTask parent = new FakeTask("parent");
        FakeTask child = new FakeTask("child");
        child.succeed();
        parent.absorbChildOutcome(child);
        // A child's success is not copied up (one child done != parent done)...
        Assertions.assertNotEquals(TaskResult.FAILURE, parent.getLastResult());
        Assertions.assertNull(parent.getLastFailure());
        // ...the parent succeeds when its own goal is met.
        parent.finished = true;
        Assertions.assertEquals(TaskResult.SUCCESS, parent.getLastResult());
    }

    @Test
    void childFailureParentFailure() {
        FakeTask parent = new FakeTask("parent");
        FakeTask child = new FakeTask("child");
        child.fail(FailureReason.PRECONDITION_FAILED, "nope", false);
        parent.absorbChildOutcome(child);
        Assertions.assertEquals(TaskResult.FAILURE, parent.getLastResult());
        Assertions.assertEquals(FailureReason.PRECONDITION_FAILED, parent.getLastFailure().getReason());
    }

    @Test
    void failureThenSuccessLeavesNoStaleParentFailure() {
        FakeTask parent = new FakeTask("parent");
        FakeTask child = new FakeTask("child");
        child.fail(FailureReason.PRECONDITION_FAILED, "first try", false);
        parent.absorbChildOutcome(child);
        Assertions.assertEquals(TaskResult.FAILURE, parent.getExplicitResult());

        child.succeed();
        parent.absorbChildOutcome(child);
        Assertions.assertEquals(TaskResult.RUNNING, parent.getExplicitResult());
        Assertions.assertNull(parent.getLastFailure());
        parent.finished = true;
        Assertions.assertEquals(TaskResult.SUCCESS, parent.getLastResult());
    }

    @Test
    void newerChildFailureReplacesAbsorbedOne() {
        FakeTask parent = new FakeTask("parent");
        FakeTask a = new FakeTask("a");
        a.fail(FailureReason.PRECONDITION_FAILED, "a", false);
        parent.absorbChildOutcome(a);
        FakeTask b = new FakeTask("b");
        b.fail(FailureReason.DANGER, "b", false);
        parent.absorbChildOutcome(b);
        Assertions.assertEquals(FailureReason.DANGER, parent.getLastFailure().getReason());
    }

    @Test
    void parentOwnFailureNotClearedByChildSuccess() {
        FakeTask parent = new FakeTask("parent");
        parent.fail(FailureReason.TARGET_UNAVAILABLE, "own", false);
        FakeTask child = new FakeTask("child");
        child.succeed();
        parent.absorbChildOutcome(child);
        Assertions.assertEquals(TaskResult.FAILURE, parent.getExplicitResult());
    }

    @Test
    void giveUpIsNotSuccess() {
        // Pattern used by HolePillarTask/WaterBailTask/SurfaceBailTask: finished AND failed.
        FakeTask t = new FakeTask("bail");
        t.fail(FailureReason.TIMEOUT, "gave up", false);
        t.finished = true;
        Assertions.assertEquals(TaskResult.FAILURE, t.getLastResult());
        Assertions.assertEquals(FailureReason.TIMEOUT, t.getLastFailure().getReason());
    }

    @Test
    void genuinelyCompletedStillSucceeds() {
        FakeTask t = new FakeTask("done");
        t.finished = true;
        Assertions.assertEquals(TaskResult.SUCCESS, t.getLastResult());
        Assertions.assertNull(t.getLastFailure());
    }

    /** Minimal Task subclass for outcome API tests. */
    static final class FakeTask extends Task {
        final String name;
        boolean finished;

        FakeTask(String name) {
            this.name = name;
        }

        @Override
        protected void onStart() {
        }

        @Override
        protected Task onTick() {
            return null;
        }

        @Override
        protected void onStop(Task interruptTask) {
        }

        @Override
        protected boolean isEqual(Task other) {
            return other == this;
        }

        @Override
        protected String toDebugString() {
            return name;
        }

        @Override
        public boolean isFinished() {
            return finished;
        }
    }
}
