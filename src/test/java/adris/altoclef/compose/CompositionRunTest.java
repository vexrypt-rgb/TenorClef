package adris.altoclef.compose;

import adris.altoclef.compose.CompositionRun.EventType;
import adris.altoclef.compose.CompositionRun.FailureKind;
import adris.altoclef.compose.CompositionRun.State;
import adris.altoclef.tasksystem.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Lifecycle and observation through the real Task.tick loop, with no game running. */
class CompositionRunTest {

    private static final TaskChain CHAIN = new TaskChain(new TaskRunner(null)) {
        @Override protected void onStop() {}
        @Override public void onInterrupt(TaskChain other) {}
        @Override protected void onTick() {}
        @Override public float getPriority() { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "test"; }
    };

    private static CompositionTask root(CompositionRun run, Composition c) {
        return new CompositionTask(null, c, run);
    }

    private static boolean has(CompositionRun run, EventType t) {
        return run.events().stream().anyMatch(e -> e.type == t);
    }

    @Test
    void successfulSequenceSucceedsWithChildren() {
        CompositionRun run = new CompositionRun("ok");
        CompositionTask t = root(run, m -> Kit.seq("s", Kit.waitMs(0), Kit.waitMs(0)));
        for (int i = 0; i < 20 && !run.terminal(); i++) t.tick(CHAIN);
        assertEquals(State.SUCCEEDED, run.state());
        assertEquals(FailureKind.NONE, run.failureKind());
        assertTrue(has(run, EventType.TASK_STARTED));
        assertTrue(has(run, EventType.TASK_CHILD_STARTED));
        assertTrue(has(run, EventType.TASK_SUCCEEDED));
        assertEquals(State.SUCCEEDED, run.state());
        // root + sequence + 2 waits
        assertEquals(4, run.nodes().size());
    }

    @Test
    void runningTaskStaysPerforming() {
        CompositionRun run = new CompositionRun("run");
        CompositionTask t = root(run, m -> Kit.forever("hold"));
        for (int i = 0; i < 10; i++) t.tick(CHAIN);
        assertEquals(State.PERFORMING, run.state());
        assertFalse(run.terminal());
        assertEquals(10, run.root().ticks);
    }

    @Test
    void taskFailureIsFailedNotSucceeded() {
        CompositionRun run = new CompositionRun("fail");
        CompositionTask t = root(run, m -> Kit.seq("s", Kit.waitMs(0),
                Kit.failWith(FailureReason.RESOURCE_MISSING, "no iron")));
        for (int i = 0; i < 20 && !run.terminal(); i++) t.tick(CHAIN);
        assertEquals(State.FAILED, run.state());
        assertEquals(FailureKind.TASK_FAILURE, run.failureKind());
        assertTrue(run.failureText().contains("RESOURCE_MISSING"));
        assertTrue(has(run, EventType.TASK_FAILED));
    }

    @Test
    void exceptionInTickIsRuntimeException() {
        CompositionRun run = new CompositionRun("boom");
        Task bomb = new TimedTask("bomb", -1, null) {
            @Override protected Task onTick() { throw new IllegalStateException("kaboom"); }
        };
        CompositionTask t = root(run, m -> bomb);
        t.tick(CHAIN);
        assertEquals(State.FAILED, run.state());
        assertEquals(FailureKind.RUNTIME_EXCEPTION, run.failureKind());
        assertTrue(run.stackTrace().contains("kaboom"));
        assertTrue(has(run, EventType.TASK_EXCEPTION));
    }

    @Test
    void exceptionInBuildIsRuntimeException() {
        CompositionRun run = new CompositionRun("badbuild");
        CompositionTask t = root(run, m -> { throw new java.io.IOException("cannot build"); });
        t.tick(CHAIN);
        assertEquals(State.FAILED, run.state());
        assertEquals(FailureKind.RUNTIME_EXCEPTION, run.failureKind());
        assertTrue(run.stackTrace().contains("cannot build"));
    }

    @Test
    void firstTerminalCauseWins() {
        CompositionRun run = new CompositionRun("once");
        assertTrue(run.finish(State.CANCELLED, FailureKind.NONE, "stopped", null));
        assertFalse(run.finish(State.SUCCEEDED, FailureKind.NONE, "", null));
        assertEquals(State.CANCELLED, run.state());
    }

    @Test
    void replacedChildIsRecordedAsEnded() {
        CompositionRun run = new CompositionRun("tree");
        CompositionTask t = root(run, m -> Kit.seq("s", Kit.waitMs(0), Kit.forever("hold")));
        for (int i = 0; i < 6; i++) t.tick(CHAIN);
        assertEquals(State.PERFORMING, run.state());
        long ended = run.nodes().stream().filter(n -> n.ended).count();
        assertEquals(1, ended, "the finished first wait left the active path");
        assertTrue(run.formatTree(System.currentTimeMillis()).contains("hold"));
    }

    @Test
    void stoppedRootReportsCancelledTask() {
        CompositionRun run = new CompositionRun("cancel");
        CompositionTask t = root(run, m -> Kit.forever("hold"));
        t.tick(CHAIN);
        t.stop();
        run.closeAll();
        assertEquals(TaskResult.CANCELLED, t.getLastResult());
        assertEquals(TaskResult.CANCELLED, run.nodes().get(1).result);
    }
}
