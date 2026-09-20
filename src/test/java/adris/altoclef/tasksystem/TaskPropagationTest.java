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
