package adris.altoclef.fugue;

import adris.altoclef.tasksystem.ITaskCanForce;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskChain;
import adris.altoclef.tasksystem.TaskResult;
import adris.altoclef.tasksystem.TaskRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** v1.1 recovery through FugueTask and the real {@link Task#tick}: ownership, cancellation, child replacement. */
public class FugueRecoveryTaskTest {

    private static final TaskChain CHAIN = new TaskChain(new TaskRunner(null)) {
        @Override protected void onStop() {}
        @Override public void onInterrupt(TaskChain other) {}
        @Override protected void onTick() {}
        @Override public float getPriority() { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "test"; }
    };

    /** A gather child stuck on {@link #target}; can refuse to be interrupted, like a mid-parkour task. */
    static class StuckChild extends Task implements ITaskCanForce {
        String target = "1,64,1";
        boolean escaping;
        boolean force;
        int stops;

        @Override protected void onStart() {}
        @Override protected Task onTick() { return null; }
        @Override protected void onStop(Task interruptTask) { stops++; }
        @Override protected boolean isEqual(Task other) { return other == this; }
        @Override protected String toDebugString() { return "stuck"; }
        @Override public boolean shouldForce(Task interruptingCandidate) { return force; }
    }

    private final List<StuckChild> children = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    private FugueTask task(FakeWorld w) {
        FugueCoordinator.Config cfg = new FugueCoordinator.Config();
        cfg.failureGraceTicks = 0;
        cfg.recovery = true;
        cfg.stallTicks = 50;
        cfg.releaseTimeoutTicks = 20;
        return new FugueTask("log", 3, new FuguePlanner(FugueRecipeBook.ironTools()), cfg, () -> w,
                (key, target) -> {
                    StuckChild c = new StuckChild();
                    children.add(c);
                    return c;
                }, log::add)
                .withProbes(t -> t instanceof StuckChild s ? s.target : null, t -> t instanceof StuckChild s && s.escaping);
    }

    private static void ticks(FugueTask t, int n) {
        for (int i = 0; i < n; i++) t.tick(CHAIN);
    }

    @Test
    void recoveryStopsTheOldChildBeforeExcludingAndStartsANewInstance() {
        FakeWorld w = new FakeWorld();
        FugueTask t = task(w);
        ticks(t, 60);
        Assertions.assertEquals(2, children.size(), String.join("\n", log));
        StuckChild first = children.get(0), second = children.get(1);
        Assertions.assertTrue(first.stopped(), "old child stopped through the Task API");
        Assertions.assertEquals(1, first.stops);
        Assertions.assertNotSame(first, second);
        Assertions.assertSame(second, t.getSub());
        Assertions.assertTrue(w.excluded.contains("1,64,1"));
        Assertions.assertFalse(t.isFinished());
    }

    // 8
    @Test
    void cancelBeforeRecoveryLeavesNoExclusion() {
        FakeWorld w = new FakeWorld();
        FugueTask t = task(w);
        ticks(t, 30);
        t.stop();
        Assertions.assertEquals(TaskResult.CANCELLED, t.getLastResult());
        Assertions.assertTrue(children.get(0).stopped());
        Assertions.assertTrue(w.excluded.isEmpty());
        Assertions.assertEquals(0, t.getCoordinator().getRecoveries());
    }

    // 9
    @Test
    void cancelDuringRecoveryIsCancelledAndAppliesNothing() {
        FakeWorld w = new FakeWorld();
        w.navIdle = false; // recovery waits on navigation
        FugueTask t = task(w);
        ticks(t, 55);
        Assertions.assertTrue(t.getCoordinator().isRecovering(), String.join("\n", log));
        t.stop();
        Assertions.assertEquals(TaskResult.CANCELLED, t.getLastResult());
        Assertions.assertNotEquals(TaskResult.SUCCESS, t.getLastResult());
        Assertions.assertTrue(w.excluded.isEmpty());
        Assertions.assertEquals(1, children.size(), "no replacement child after cancel");
    }

    @Test
    void cancelAfterAnExclusionForgetsIt() {
        FakeWorld w = new FakeWorld();
        FugueTask t = task(w);
        ticks(t, 60);
        Assertions.assertFalse(w.excluded.isEmpty());
        t.stop();
        Assertions.assertTrue(w.excluded.isEmpty());
        Assertions.assertEquals(List.of("1,64,1"), w.forgotten);
    }

    @Test
    void interruptKeepsExclusions() {
        // A survival chain interrupting Fugue is not the end of the objective.
        FakeWorld w = new FakeWorld();
        FugueTask t = task(w);
        ticks(t, 60);
        t.interrupt(new StuckChild());
        Assertions.assertTrue(w.excluded.contains("1,64,1"));
    }

    // 10
    @Test
    void childThatRefusesToStopBlocksRecoveryAndFailsExplicitly() {
        FakeWorld w = new FakeWorld();
        FugueTask t = new FugueTask("log", 3, new FuguePlanner(FugueRecipeBook.ironTools()), cfg(), () -> w,
                (key, target) -> {
                    StuckChild c = new StuckChild();
                    c.force = true;
                    children.add(c);
                    return c;
                }, log::add)
                .withProbes(x -> x instanceof StuckChild s ? s.target : null, x -> false);
        ticks(t, 100);
        Assertions.assertEquals(1, children.size(), "no competing child while the old one keeps control");
        Assertions.assertSame(children.get(0), t.getSub());
        Assertions.assertTrue(t.isFinished());
        Assertions.assertEquals(TaskResult.FAILURE, t.getLastResult());
        Assertions.assertTrue(t.getLastFailure().getMessage().contains("did not release control"), t.getLastFailure().getMessage());
        Assertions.assertTrue(w.excluded.isEmpty());
    }

    private static FugueCoordinator.Config cfg() {
        FugueCoordinator.Config cfg = new FugueCoordinator.Config();
        cfg.failureGraceTicks = 0;
        cfg.recovery = true;
        cfg.stallTicks = 50;
        cfg.releaseTimeoutTicks = 20;
        return cfg;
    }
}
