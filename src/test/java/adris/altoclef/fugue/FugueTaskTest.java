package adris.altoclef.fugue;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskChain;
import adris.altoclef.tasksystem.TaskResult;
import adris.altoclef.tasksystem.TaskRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** FugueTask through the real {@link Task#tick} loop with scripted stand-ins for catalogue tasks. */
public class FugueTaskTest {

    private static final TaskChain CHAIN = new TaskChain(new TaskRunner(null)) {
        @Override protected void onStop() {}
        @Override public void onInterrupt(TaskChain other) {}
        @Override protected void onTick() {}
        @Override public float getPriority() { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "test"; }
    };

    private static final FugueRecipeBook BOOK = FugueRecipeBook.ironTools();

    /** Stand-in for a catalogue task: produces into the fake world when told to, like ResourceTask's predicate. */
    static class FakeChild extends Task {
        final FakeWorld world;
        final String key;
        final int target;
        boolean work = true;
        boolean giveUp;
        int ticks;

        FakeChild(FakeWorld world, String key, int target) {
            this.world = world;
            this.key = key;
            this.target = target;
        }

        @Override protected void onStart() {}
        @Override protected Task onTick() {
            ticks++;
            if (giveUp) fail(FailureReason.PRECONDITION_FAILED, "gave up", false);
            else if (work) world.produce(BOOK, key, target);
            return null;
        }
        @Override protected void onStop(Task interruptTask) {}
        @Override protected boolean isEqual(Task other) { return other == this; }
        @Override protected String toDebugString() { return "fake " + key; }
        @Override public boolean isFinished() { return giveUp || world.count(key) >= target; }
    }

    private final List<FakeChild> children = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    private FugueTask task(FakeWorld w, String goal, int n, boolean childrenWork) {
        return new FugueTask(goal, n, new FuguePlanner(BOOK), noGrace(), () -> w,
                (key, target) -> {
                    FakeChild c = new FakeChild(w, key, target);
                    c.work = childrenWork;
                    children.add(c);
                    return c;
                }, log::add);
    }

    @Test
    void runsWholeChainToVerifiedSuccess() {
        FakeWorld w = new FakeWorld();
        FugueTask t = task(w, "iron_pickaxe", 1, true);
        for (int i = 0; i < 100 && !t.isFinished(); i++) t.tick(CHAIN);
        Assertions.assertTrue(t.isFinished(), String.join("\n", log));
        Assertions.assertEquals(TaskResult.SUCCESS, t.getLastResult());
        Assertions.assertEquals(1, w.count("iron_pickaxe"));
        Assertions.assertEquals(12, children.size());
    }

    @Test
    void eachNodeOwnsAFreshChildAndOldOnesAreStopped() {
        FakeWorld w = new FakeWorld();
        FugueTask t = task(w, "iron_pickaxe", 1, true);
        for (int i = 0; i < 100 && !t.isFinished(); i++) t.tick(CHAIN);
        for (int i = 0; i < children.size() - 1; i++) {
            Assertions.assertTrue(children.get(i).stopped() || children.get(i).isFinished(), children.get(i).key);
        }
        Assertions.assertNull(t.getSub(), "finished objective releases its last child");
    }

    @Test
    void stopCancelsObjectiveAndRunningChild() {
        FakeWorld w = new FakeWorld();
        FugueTask t = task(w, "iron_pickaxe", 1, false);
        t.tick(CHAIN);
        t.tick(CHAIN);
        FakeChild running = children.get(children.size() - 1);
        Assertions.assertSame(running, t.getSub());
        t.stop();
        Assertions.assertEquals(TaskResult.CANCELLED, t.getLastResult());
        Assertions.assertTrue(running.stopped());
        Assertions.assertEquals(TaskResult.CANCELLED, running.getLastResult());
        Assertions.assertNotEquals(TaskResult.SUCCESS, t.getLastResult());
    }

    @Test
    void interruptResumesSameNodeWithoutReplanningFromScratch() {
        FakeWorld w = new FakeWorld();
        FugueTask t = task(w, "iron_pickaxe", 1, false);
        t.tick(CHAIN);
        String node = t.getCoordinator().getCurrentKey();
        int starts = t.getCoordinator().getNodeStarts();
        t.interrupt(null); // e.g. MobDefenseChain took over
        t.tick(CHAIN);
        Assertions.assertEquals(1, t.getInterrupts());
        Assertions.assertEquals(node, t.getCoordinator().getCurrentKey());
        Assertions.assertEquals(starts, t.getCoordinator().getNodeStarts());
        Assertions.assertFalse(t.stopped());
    }

    @Test
    void childGivingUpIsFailureNotSuccess() {
        FakeWorld w = new FakeWorld().with("iron_ingot", 3).with("stick", 2).with("crafting_table", 1);
        FugueTask t = new FugueTask("iron_pickaxe", 1, new FuguePlanner(BOOK), noGrace(), () -> w,
                (key, target) -> {
                    FakeChild c = new FakeChild(w, key, target);
                    c.giveUp = true;
                    children.add(c);
                    return c;
                }, log::add);
        for (int i = 0; i < 10 && !t.isFinished(); i++) t.tick(CHAIN);
        Assertions.assertTrue(t.isFinished());
        Assertions.assertEquals(TaskResult.FAILURE, t.getLastResult());
        Assertions.assertEquals(FailureReason.PRECONDITION_FAILED, t.getLastFailure().getReason());
        Assertions.assertEquals(0, w.count("iron_pickaxe"));
    }

    @Test
    void retryOfSameNodeGetsANewChildInstance() {
        FakeWorld w = new FakeWorld().with("stone_pickaxe", 1);
        int[] made = {0};
        FugueTask t = new FugueTask("cobblestone", 8, new FuguePlanner(BOOK), noGrace(), () -> w,
                (key, target) -> {
                    FakeChild c = new FakeChild(w, key, target) {
                        @Override public boolean isFinished() { return ticks > 0; } // "done" after one partial tick
                    };
                    c.work = false;
                    if (made[0]++ == 0) w.add("cobblestone", 5);
                    else c.work = true;
                    children.add(c);
                    return c;
                }, log::add);
        for (int i = 0; i < 20 && !t.isFinished(); i++) t.tick(CHAIN);
        Assertions.assertEquals(TaskResult.SUCCESS, t.getLastResult(), String.join("\n", log));
        Assertions.assertEquals(2, children.size());
        Assertions.assertNotSame(children.get(0), children.get(1));
        Assertions.assertTrue(children.get(0).stopped());
    }

    @Test
    void blockedChildMakesObjectiveBlocked() {
        FakeWorld w = new FakeWorld().with("stone_pickaxe", 1);
        FugueTask t = new FugueTask("raw_iron", 3, new FuguePlanner(BOOK), noGrace(), () -> w,
                (key, target) -> new Task() {
                    @Override protected void onStart() {}
                    @Override protected Task onTick() { blocked(FailureReason.INVENTORY_FULL, "full"); return null; }
                    @Override protected void onStop(Task interruptTask) {}
                    @Override protected boolean isEqual(Task other) { return other == this; }
                    @Override protected String toDebugString() { return "blocked"; }
                }, log::add);
        for (int i = 0; i < 10 && !t.isFinished(); i++) t.tick(CHAIN);
        Assertions.assertTrue(t.isFinished());
        Assertions.assertEquals(TaskResult.BLOCKED, t.getLastResult());
    }

    @Test
    void equalityIsByGoal() {
        FakeWorld w = new FakeWorld();
        Assertions.assertEquals(task(w, "iron_pickaxe", 1, true), task(w, "iron_pickaxe", 1, true));
        Assertions.assertNotEquals(task(w, "iron_pickaxe", 1, true), task(w, "stone_pickaxe", 1, true));
    }

    private static FugueCoordinator.Config noGrace() {
        FugueCoordinator.Config c = new FugueCoordinator.Config();
        c.failureGraceTicks = 0;
        return c;
    }
}
