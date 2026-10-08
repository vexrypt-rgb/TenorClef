package adris.altoclef.compose;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.*;

/**
 * Root of a performed Composition. It is an ordinary {@link Task} handed to {@code mod.runUserTask}; it adds
 * only observation (tree, outcomes, exceptions) around the task the Composition built.
 */
public class CompositionTask extends Task {
    private final AltoClef mod;
    private final Composition composition;
    private final CompositionRun run;
    private Task built;

    public CompositionTask(AltoClef mod, Composition composition, CompositionRun run) {
        this.mod = mod;
        this.composition = composition;
        this.run = run;
    }

    @Override
    public void tick(TaskChain parentChain) {
        if (run.terminal()) {
            // A terminal run must stop driving the world; the chain is cancelled once, then we do nothing.
            return;
        }
        try {
            super.tick(parentChain);
        } catch (Throwable t) {
            run.observe(this);
            run.log(CompositionRun.EventType.TASK_EXCEPTION, t.toString(), CompositionRun.stackOf(t));
            run.finish(CompositionRun.State.FAILED, CompositionRun.FailureKind.RUNTIME_EXCEPTION, t.toString(), t);
            CompositionService.INSTANCE.afterTerminal(mod, run);
            return;
        }
        run.observe(this);
        CompositionService.INSTANCE.pollMovement(mod, run);

        Task sub = getSub();
        if (sub != null && sub.isFinished() && sub.getLastResult() == TaskResult.SUCCESS) {
            run.closeAll();
            run.finish(CompositionRun.State.SUCCEEDED, CompositionRun.FailureKind.NONE, "", null);
            return; // the chain sees isFinished() next tick and ends the user task
        }
        if (getExplicitResult() == TaskResult.FAILURE) {
            TaskFailure f = getLastFailure();
            run.finish(CompositionRun.State.FAILED, CompositionRun.FailureKind.TASK_FAILURE,
                    f != null ? f.toString() : "task failed", null);
            CompositionService.INSTANCE.afterTerminal(mod, run);
        }
    }

    @Override
    protected void onStart() {
        try {
            built = composition.build(mod);
        } catch (Exception e) {
            throw new CompositionBuildException(e);
        }
        if (built == null) throw new IllegalStateException("Composition.build returned null");
    }

    @Override
    protected Task onTick() {
        return built;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    public boolean isFinished() {
        return built != null && built.isFinished();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other == this;
    }

    @Override
    protected String toDebugString() {
        return "Composition " + run.compositionName;
    }

    /** Wraps an exception thrown from the user's build(); unwrapped for display. */
    static final class CompositionBuildException extends RuntimeException {
        CompositionBuildException(Throwable cause) {
            super("build() threw " + cause, cause);
        }
    }
}
