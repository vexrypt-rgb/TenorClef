package adris.altoclef.fugue;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.movement.GetOutOfWaterTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.BlockPos;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Fugue v1 runtime: acquires {@code count} of a supported catalogue item by running one stock catalogue task per
 * dependency node, in the order {@link FugueCoordinator} picks. It adds ordering, aggregation, verification and
 * bounded recovery on top of the existing task tree; movement, mining, crafting and smelting are the stock tasks'.
 *
 * <p>Opt-in only: started by {@code @fugue <item> [count]} (or FugueBench). Nothing else constructs it.
 *
 * <p>Survival chains interrupt it like any user task ({@link Task#interrupt}); on resume the coordinator keeps its
 * node state and the current attempt continues. {@link Task#stop} cancels it (CANCELLED, never SUCCESS) and stops
 * the running child.
 */
public class FugueTask extends Task {

    /** Builds the stock task that produces {@code key} until {@code target} are held. */
    public interface ChildFactory {
        Task create(String key, int target);
    }

    private static volatile FugueTask lastStarted;

    private final String goal;
    private final int count;
    private final FuguePlanner planner;
    private final FugueCoordinator.Config config;
    private final Supplier<FugueWorldView> viewSupplier;
    private final ChildFactory childFactory;
    private final Consumer<String> sink;

    private FugueCoordinator coordinator;
    private FugueWorldView view;
    private Task child;
    private String childKey;
    private int interrupts;
    /** How the child's mining target and water escape are read (tests substitute stand-ins). */
    private Function<Task, String> targetProbe = FugueTask::targetOf;
    private Predicate<Task> escapeProbe = FugueTask::escaping;
    /** Recovery overhead: time spent reading the child's target and escape state (recovery on only). */
    private long probeNanosTotal, probeNanosMax, probeCount;

    public FugueTask(String goal, int count, FuguePlanner planner, FugueCoordinator.Config config,
                     Supplier<FugueWorldView> viewSupplier, ChildFactory childFactory, Consumer<String> sink) {
        this.goal = goal;
        this.count = Math.max(1, count);
        this.planner = planner;
        this.config = config;
        this.viewSupplier = viewSupplier;
        this.childFactory = childFactory;
        this.sink = sink != null ? sink : s -> {};
    }

    /** The live wiring: catalogue tasks as children, the client as the world view, FUGUE lines in the log. */
    public static FugueTask live(String goal, int count) {
        return live(goal, count, false);
    }

    /** As {@link #live(String, int)}; {@code recovery} turns on v1.1 stall recovery (off by default). */
    public static FugueTask live(String goal, int count, boolean recovery) {
        FugueCoordinator.Config config = new FugueCoordinator.Config();
        config.recovery = recovery;
        return new FugueTask(goal, count, new FuguePlanner(FugueRecipeBook.ironTools()), config,
                () -> new LiveFugueView(AltoClef.getInstance()),
                TaskCatalogue::getItemTask,
                msg -> Debug.logMessage("FUGUE " + msg));
    }

    public static boolean supports(String key) {
        return FugueRecipeBook.ironTools().supports(key);
    }

    /** Most recently started Fugue objective (for {@code @fugue status}); may be finished or stopped. */
    public static FugueTask getLastStarted() {
        return lastStarted;
    }

    @Override
    protected void onStart() {
        if (coordinator == null) {
            view = viewSupplier.get();
            coordinator = new FugueCoordinator(goal, count, planner, config, sink);
            lastStarted = this;
            sink.accept("objective START " + goal + " x" + count);
        } else {
            interrupts++;
            sink.accept("objective RESUME after interrupt #" + interrupts + " (node " + coordinator.getCurrentKey() + ")");
        }
    }

    @Override
    protected Task onTick() {
        // Only report on the child that is actually installed: a forcing predecessor can delay the swap a tick.
        Task running = getSub();
        FugueCoordinator.ChildReport report;
        if (child != null && running == child) {
            if (config.recovery) {
                long p0 = System.nanoTime();
                String target = targetProbe.apply(child);
                boolean esc = escapeProbe.test(child);
                long dp = System.nanoTime() - p0;
                probeNanosTotal += dp;
                probeNanosMax = Math.max(probeNanosMax, dp);
                probeCount++;
                report = new FugueCoordinator.ChildReport(child.isFinished(), child.getLastResult(), child.getLastFailure(), target, esc);
            } else {
                report = new FugueCoordinator.ChildReport(child.isFinished(), child.getLastResult(), child.getLastFailure());
            }
        } else if (child == null) {
            // Recovery waits on this: the dropped child has been stopped and removed by Task.tick.
            report = FugueCoordinator.ChildReport.detached(running == null);
        } else {
            report = null;
        }
        FugueCoordinator.Decision d = coordinator.step(view, report);
        switch (d.type) {
            case SUCCEEDED:
                dropChild();
                succeed();
                setDebugState("done");
                return null;
            case FAILED:
                dropChild();
                if (coordinator.isBlocked()) blocked(d.failure.getReason(), d.failure.getMessage());
                else fail(d.failure);
                setDebugState("failed: " + d.failure.getMessage());
                return null;
            case RELEASE:
                dropChild();
                setDebugState("retrying " + d.key);
                return null;
            case RECOVER:
                // Run nothing until the old child (and its navigation) has let go; the coordinator decides when.
                dropChild();
                setDebugState("recovering " + d.key);
                return null;
            case RUN:
            default:
                if (d.fresh && child != null && d.key.equals(childKey)) {
                    // Same item again: let the old instance stop this tick so the retry is a genuinely new task.
                    dropChild();
                    return null;
                }
                if (d.fresh || child == null) {
                    child = childFactory.create(d.key, d.target);
                    childKey = d.key;
                }
                setDebugState(d.key + " -> " + d.target);
                return child;
        }
    }

    private void dropChild() {
        child = null;
        childKey = null;
    }

    @Override
    protected void onStop(Task interruptTask) {
        // Interrupts come back through onStart; a real stop leaves the Task layer's CANCELLED in place.
        // A clean stop (cancel) ends the objective: drop its temporary target exclusions.
        if (interruptTask == null && coordinator != null) coordinator.releaseExclusions(view);
    }

    /** Block the deepest {@link DestroyBlockTask} under {@code task} is breaking, as "x,y,z"; null if none. */
    static String targetOf(Task task) {
        BlockPos found = null;
        for (Task t = task; t != null; t = t.getSub()) {
            if (t instanceof DestroyBlockTask destroy) found = destroy.getPos();
        }
        return found == null ? null : found.getX() + "," + found.getY() + "," + found.getZ();
    }

    private static boolean escaping(Task task) {
        for (Task t = task; t != null; t = t.getSub()) {
            if (t instanceof GetOutOfWaterTask) return true;
        }
        return false;
    }

    @Override
    public boolean isFinished() {
        return coordinator != null && coordinator.isTerminal();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FugueTask f && f.goal.equals(goal) && f.count == count;
    }

    @Override
    protected String toDebugString() {
        return "Fugue " + goal + " x" + count + (coordinator != null && coordinator.getCurrentKey() != null
                ? " [" + coordinator.getCurrentKey() + "->" + coordinator.getCurrentTarget() + "]" : "");
    }

    FugueTask withProbes(Function<Task, String> target, Predicate<Task> escape) {
        targetProbe = target;
        escapeProbe = escape;
        return this;
    }

    public FugueCoordinator getCoordinator() { return coordinator; }
    public long getProbeNanosTotal() { return probeNanosTotal; }
    public long getProbeNanosMax() { return probeNanosMax; }
    public long getProbeCount() { return probeCount; }
    public String getGoal() { return goal; }
    public int getCount() { return count; }
    public int getInterrupts() { return interrupts; }
}
