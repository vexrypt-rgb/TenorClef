package adris.altoclef.compose;

import adris.altoclef.tasksystem.Task;

import java.util.ArrayList;
import java.util.List;

/** Runs children in order. A failed child fails the sequence (via the normal child-outcome absorption). */
public class SequenceTask extends Task {
    private final String label;
    private final List<Task> steps;
    private int index;

    public SequenceTask(String label, List<Task> steps) {
        this.label = label;
        this.steps = new ArrayList<>(steps);
    }

    @Override
    protected void onStart() {
        index = 0;
        for (Task t : steps) t.reset();
    }

    @Override
    protected Task onTick() {
        if (index >= steps.size()) return null;
        Task cur = steps.get(index);
        if (cur.isFinished()) {
            index++;
            return index < steps.size() ? steps.get(index) : null;
        }
        return cur;
    }

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    public boolean isFinished() {
        return index >= steps.size() && !steps.isEmpty();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SequenceTask s && s.label.equals(label) && s.steps.equals(steps);
    }

    @Override
    protected String toDebugString() {
        return "Sequence " + label + " [" + Math.min(index + 1, steps.size()) + "/" + steps.size() + "]";
    }
}
