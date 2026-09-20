package adris.altoclef.threat;

import adris.altoclef.tasksystem.RecoveryAction;

/**
 * Snapshot of current combat/survival risk (Phase 8).
 * Pure data — produced by {@link ThreatAssessor}.
 */
public final class ThreatAssessment {

    public static final ThreatAssessment NONE =
            new ThreatAssessment(ThreatLevel.NONE, "none", null, null);

    private final ThreatLevel level;
    private final String reason;
    private final Double timeToDanger;
    private final RecoveryAction suggestedAction;

    public ThreatAssessment(ThreatLevel level, String reason,
                            Double timeToDanger, RecoveryAction suggestedAction) {
        this.level = level != null ? level : ThreatLevel.NONE;
        this.reason = reason != null ? reason : "";
        this.timeToDanger = timeToDanger;
        this.suggestedAction = suggestedAction;
    }

    public static ThreatAssessment of(ThreatLevel level, String reason) {
        RecoveryAction action = null;
        if (level == ThreatLevel.CRITICAL || level == ThreatLevel.HIGH) {
            action = RecoveryAction.ESCALATE;
        } else if (level == ThreatLevel.MEDIUM) {
            action = RecoveryAction.WAIT;
        }
        return new ThreatAssessment(level, reason, null, action);
    }

    public ThreatLevel getLevel() {
        return level;
    }

    public String getReason() {
        return reason;
    }

    /** Optional estimated seconds-to-danger; null if unknown. */
    public Double getTimeToDanger() {
        return timeToDanger;
    }

    /** Suggested Phase 6 recovery action; null when none. */
    public RecoveryAction getSuggestedAction() {
        return suggestedAction;
    }

    public boolean shouldInterruptGoals() {
        return level.shouldInterruptGoals();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(level).append(": ").append(reason);
        if (timeToDanger != null) {
            sb.append(" ttd=").append(timeToDanger);
        }
        if (suggestedAction != null) {
            sb.append(" → ").append(suggestedAction);
        }
        return sb.toString();
    }
}
