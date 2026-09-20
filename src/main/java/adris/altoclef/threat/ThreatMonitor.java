package adris.altoclef.threat;

import adris.altoclef.planner.GoalManager;
import adris.altoclef.planner.PlanExecutor;

/**
 * Holds the latest {@link ThreatAssessment} and optionally interrupts the
 * Phase 7 {@link PlanExecutor} / {@link GoalManager} (Phase 8).
 * <p>
 * Does <strong>not</strong> replace {@code MobDefenseChain} /
 * {@code WorldSurvivalChain} — those keep running as fallback.
 */
public class ThreatMonitor {

    private final ThreatEvaluator evaluator;
    private ThreatAssessment latest = ThreatAssessment.NONE;
    private long tickCount;

    public ThreatMonitor() {
        this(ThreatAssessor.INSTANCE);
    }

    public ThreatMonitor(ThreatEvaluator evaluator) {
        this.evaluator = evaluator != null ? evaluator : ThreatAssessor.INSTANCE;
    }

    public ThreatAssessment getLatest() {
        return latest;
    }

    public ThreatLevel getLevel() {
        return latest != null ? latest.getLevel() : ThreatLevel.NONE;
    }

    public long getTickCount() {
        return tickCount;
    }

    public ThreatEvaluator getEvaluator() {
        return evaluator;
    }

    /**
     * Recompute assessment from signals and store it.
     *
     * @return the new assessment
     */
    public ThreatAssessment tick(ThreatSignals signals) {
        tickCount++;
        latest = evaluator.evaluate(signals);
        if (latest == null) {
            latest = ThreatAssessment.NONE;
        }
        return latest;
    }

    /** Force-set assessment (tests / debug). */
    public void setLatest(ThreatAssessment assessment) {
        latest = assessment != null ? assessment : ThreatAssessment.NONE;
    }

    /**
     * Apply latest assessment to a running plan executor:
     * HIGH → pause; CRITICAL → fail with {@code FailureReason.DANGER};
     * below HIGH → resume if paused.
     *
     * @return true if the executor status changed
     */
    public boolean applyToExecutor(PlanExecutor executor) {
        if (executor == null || latest == null) {
            return false;
        }
        return executor.applyThreat(latest);
    }

    /** Convenience for {@link GoalManager}. */
    public boolean applyToGoalManager(GoalManager manager) {
        return applyToExecutor(manager);
    }

    public String summarize() {
        return "threat=" + (latest != null ? latest.toString() : "null")
                + " ticks=" + tickCount;
    }
}
