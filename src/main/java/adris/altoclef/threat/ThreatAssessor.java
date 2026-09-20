package adris.altoclef.threat;

import adris.altoclef.tasksystem.RecoveryAction;

/**
 * Default threat policy wrapping WorldSurvival / MobDefense priority ideas
 * without duplicating their task selection (Phase 8).
 * <p>
 * Thresholds deliberately mirror chain priorities:
 * lava/fire/drown ≈ CRITICAL (chain priority ~100),
 * close hostiles / low HP ≈ HIGH,
 * nearby hostiles / hunger ≈ MEDIUM/LOW.
 */
public class ThreatAssessor implements ThreatEvaluator {

    public static final ThreatAssessor INSTANCE = new ThreatAssessor();

    /** Health at or below → CRITICAL. */
    public static final float CRITICAL_HEALTH = 4f;
    /** Health at or below → HIGH. */
    public static final float HIGH_HEALTH = 8f;
    /** Health at or below → MEDIUM. */
    public static final float MEDIUM_HEALTH = 12f;
    /** Health at or below → LOW. */
    public static final float LOW_HEALTH = 16f;

    public static final int CRITICAL_FOOD = 3;
    public static final int HIGH_FOOD = 6;
    public static final int MEDIUM_FOOD = 10;
    public static final int LOW_FOOD = 14;

    public static final double CRITICAL_HOSTILE_DIST = 3.0;
    public static final double HIGH_HOSTILE_DIST = 8.0;
    public static final double MEDIUM_HOSTILE_DIST = 16.0;
    public static final double LOW_HOSTILE_DIST = 30.0;

    /** MobDefenseChain priority hints (when provided). */
    public static final float DEFENSE_CRITICAL_HINT = 90f;
    public static final float DEFENSE_HIGH_HINT = 70f;
    public static final float DEFENSE_MEDIUM_HINT = 50f;

    @Override
    public ThreatAssessment evaluate(ThreatSignals signals) {
        if (signals == null) {
            return ThreatAssessment.NONE;
        }

        ThreatLevel best = ThreatLevel.NONE;
        String reason = "none";
        Double timeToDanger = null;

        if (signals.isPlayerDead()) {
            return new ThreatAssessment(ThreatLevel.CRITICAL, "player dead", 0.0,
                    RecoveryAction.ESCALATE);
        }
        if (signals.isInLava()) {
            best = ThreatLevel.CRITICAL;
            reason = "in lava";
            timeToDanger = 0.0;
        } else if (signals.isDrowning()) {
            best = ThreatLevel.CRITICAL;
            reason = "drowning";
            timeToDanger = 1.0;
        } else if (signals.getHealth() <= CRITICAL_HEALTH) {
            best = ThreatLevel.CRITICAL;
            reason = "critical health " + fmt(signals.getHealth());
            timeToDanger = 2.0;
        }

        if (!best.isAtLeast(ThreatLevel.CRITICAL)) {
            if (signals.hasHostiles()
                    && signals.getClosestHostileDistance() <= CRITICAL_HOSTILE_DIST) {
                best = ThreatLevel.CRITICAL;
                reason = "hostile within " + fmtDist(signals.getClosestHostileDistance())
                        + " (n=" + signals.getNearbyHostileCount() + ")";
                timeToDanger = signals.getClosestHostileDistance() / 4.0;
            }
        }

        if (!best.isAtLeast(ThreatLevel.CRITICAL)) {
            if (signals.isOnFire()) {
                best = ThreatLevel.HIGH;
                reason = "on fire";
                timeToDanger = 3.0;
            } else if (signals.getHealth() <= HIGH_HEALTH) {
                best = ThreatLevel.HIGH;
                reason = "low health " + fmt(signals.getHealth());
            } else if (signals.getFoodLevel() <= CRITICAL_FOOD) {
                best = ThreatLevel.HIGH;
                reason = "starving food=" + signals.getFoodLevel();
            } else if (signals.hasHostiles()
                    && signals.getClosestHostileDistance() <= HIGH_HOSTILE_DIST) {
                best = ThreatLevel.HIGH;
                reason = "hostile within " + fmtDist(signals.getClosestHostileDistance())
                        + " (n=" + signals.getNearbyHostileCount() + ")";
                timeToDanger = signals.getClosestHostileDistance() / 3.0;
            }
        }

        if (!best.isAtLeast(ThreatLevel.HIGH)) {
            if (signals.getHealth() <= MEDIUM_HEALTH) {
                best = ThreatLevel.MEDIUM;
                reason = "moderate health " + fmt(signals.getHealth());
            } else if (signals.getFoodLevel() <= HIGH_FOOD) {
                best = ThreatLevel.MEDIUM;
                reason = "hungry food=" + signals.getFoodLevel();
            } else if (signals.hasHostiles()
                    && signals.getClosestHostileDistance() <= MEDIUM_HOSTILE_DIST) {
                best = ThreatLevel.MEDIUM;
                reason = "hostile within " + fmtDist(signals.getClosestHostileDistance())
                        + " (n=" + signals.getNearbyHostileCount() + ")";
            }
        }

        if (!best.isAtLeast(ThreatLevel.MEDIUM)) {
            if (signals.getHealth() <= LOW_HEALTH) {
                best = ThreatLevel.LOW;
                reason = "slightly low health " + fmt(signals.getHealth());
            } else if (signals.getFoodLevel() <= MEDIUM_FOOD) {
                best = ThreatLevel.LOW;
                reason = "peckish food=" + signals.getFoodLevel();
            } else if (signals.getFoodLevel() <= LOW_FOOD) {
                best = ThreatLevel.LOW;
                reason = "food=" + signals.getFoodLevel();
            } else if (signals.hasHostiles()
                    && signals.getClosestHostileDistance() <= LOW_HOSTILE_DIST) {
                best = ThreatLevel.LOW;
                reason = "distant hostile "
                        + fmtDist(signals.getClosestHostileDistance())
                        + " (n=" + signals.getNearbyHostileCount() + ")";
            }
        }

        // Optional MobDefenseChain priority hint — raise floor, don't lower
        float hint = signals.getDefensePriorityHint();
        if (hint >= DEFENSE_CRITICAL_HINT && !best.isAtLeast(ThreatLevel.CRITICAL)) {
            best = ThreatLevel.CRITICAL;
            reason = "defense priority " + (int) hint + " (" + reason + ")";
        } else if (hint >= DEFENSE_HIGH_HINT && !best.isAtLeast(ThreatLevel.HIGH)) {
            best = ThreatLevel.HIGH;
            reason = "defense priority " + (int) hint + " (" + reason + ")";
        } else if (hint >= DEFENSE_MEDIUM_HINT && !best.isAtLeast(ThreatLevel.MEDIUM)) {
            best = ThreatLevel.MEDIUM;
            reason = "defense priority " + (int) hint + " (" + reason + ")";
        }

        RecoveryAction action = suggestedFor(best);
        return new ThreatAssessment(best, reason, timeToDanger, action);
    }

    private static RecoveryAction suggestedFor(ThreatLevel level) {
        return switch (level) {
            case CRITICAL, HIGH -> RecoveryAction.ESCALATE;
            case MEDIUM -> RecoveryAction.WAIT;
            case LOW, NONE -> null;
        };
    }

    private static String fmt(float h) {
        return String.format("%.1f", h);
    }

    private static String fmtDist(double d) {
        return String.format("%.1f", d);
    }
}
