package adris.altoclef.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered list of {@link PlanStep}s produced by a {@link Planner}.
 */
public final class Plan {

    private final List<PlanStep> steps;
    private int currentIndex;
    private PlanStatus status;

    public Plan(List<PlanStep> steps) {
        this.steps = new ArrayList<>();
        if (steps != null) {
            for (PlanStep s : steps) {
                if (s != null) {
                    this.steps.add(s);
                }
            }
        }
        this.currentIndex = 0;
        this.status = this.steps.isEmpty() ? PlanStatus.EMPTY : PlanStatus.READY;
    }

    public static Plan empty() {
        return new Plan(List.of());
    }

    public List<PlanStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public int size() {
        return steps.size();
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status != null ? status : PlanStatus.READY;
    }

    /** Current step, or null if plan empty / finished. */
    public PlanStep currentStep() {
        if (currentIndex < 0 || currentIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentIndex);
    }

    /** Mark current RUNNING and set plan RUNNING. */
    public PlanStep beginCurrent() {
        PlanStep s = currentStep();
        if (s == null) {
            return null;
        }
        s.setStatus(PlanStepStatus.RUNNING);
        status = PlanStatus.RUNNING;
        return s;
    }

    /**
     * Mark current SUCCESS and advance. Returns true if more steps remain.
     */
    public boolean completeCurrent() {
        PlanStep s = currentStep();
        if (s != null) {
            s.setStatus(PlanStepStatus.SUCCESS);
        }
        currentIndex++;
        if (currentIndex >= steps.size()) {
            status = PlanStatus.SUCCESS;
            return false;
        }
        return true;
    }

    /** Mark current FAILED and plan FAILED. */
    public void failCurrent() {
        PlanStep s = currentStep();
        if (s != null) {
            s.setStatus(PlanStepStatus.FAILED);
        }
        status = PlanStatus.FAILED;
    }

    @Override
    public String toString() {
        return "Plan{" + status + " @" + currentIndex + "/" + steps.size()
                + " steps=" + steps + "}";
    }
}
