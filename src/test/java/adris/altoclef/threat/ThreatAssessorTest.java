package adris.altoclef.threat;

import adris.altoclef.tasksystem.RecoveryAction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Phase 8: ThreatAssessor maps health / hostiles / survival flags → ThreatLevel.
 */
public class ThreatAssessorTest {

    private final ThreatAssessor assessor = ThreatAssessor.INSTANCE;

    @Test
    void healthyNone() {
        ThreatAssessment a = assessor.evaluate(ThreatSignals.builder().build());
        Assertions.assertEquals(ThreatLevel.NONE, a.getLevel());
    }

    @Test
    void criticalHealth() {
        ThreatAssessment a = assessor.evaluate(
                ThreatSignals.builder().health(3f).build());
        Assertions.assertEquals(ThreatLevel.CRITICAL, a.getLevel());
        Assertions.assertEquals(RecoveryAction.ESCALATE, a.getSuggestedAction());
    }

    @Test
    void highHealth() {
        ThreatAssessment a = assessor.evaluate(
                ThreatSignals.builder().health(7f).build());
        Assertions.assertEquals(ThreatLevel.HIGH, a.getLevel());
    }

    @Test
    void lavaIsCritical() {
        ThreatAssessment a = assessor.evaluate(
                ThreatSignals.builder().health(20f).inLava(true).build());
        Assertions.assertEquals(ThreatLevel.CRITICAL, a.getLevel());
        Assertions.assertTrue(a.getReason().contains("lava"));
    }

    @Test
    void fireIsHigh() {
        ThreatAssessment a = assessor.evaluate(
                ThreatSignals.builder().onFire(true).build());
        Assertions.assertEquals(ThreatLevel.HIGH, a.getLevel());
    }

    @Test
    void drowningIsCritical() {
        ThreatAssessment a = assessor.evaluate(
                ThreatSignals.builder().drowning(true).build());
        Assertions.assertEquals(ThreatLevel.CRITICAL, a.getLevel());
    }

    @Test
    void closeHostileCritical() {
        ThreatAssessment a = assessor.evaluate(
                ThreatSignals.builder()
                        .nearbyHostileCount(1)
                        .closestHostileDistance(2.0)
                        .build());
        Assertions.assertEquals(ThreatLevel.CRITICAL, a.getLevel());
        Assertions.assertNotNull(a.getTimeToDanger());
    }

    @Test
    void nearHostileHigh() {
        ThreatAssessment a = assessor.evaluate(
                ThreatSignals.builder()
                        .nearbyHostileCount(2)
                        .closestHostileDistance(6.0)
                        .build());
        Assertions.assertEquals(ThreatLevel.HIGH, a.getLevel());
    }

    @Test
    void mediumHostileAndFood() {
        ThreatAssessment a = assessor.evaluate(
                ThreatSignals.builder()
                        .nearbyHostileCount(1)
                        .closestHostileDistance(12.0)
                        .build());
        Assertions.assertEquals(ThreatLevel.MEDIUM, a.getLevel());

        ThreatAssessment hungry = assessor.evaluate(
                ThreatSignals.builder().foodLevel(5).build());
        Assertions.assertEquals(ThreatLevel.MEDIUM, hungry.getLevel());
    }

    @Test
    void distantHostileLow() {
        ThreatAssessment a = assessor.evaluate(
                ThreatSignals.builder()
                        .nearbyHostileCount(1)
                        .closestHostileDistance(25.0)
                        .build());
        Assertions.assertEquals(ThreatLevel.LOW, a.getLevel());
    }

    @Test
    void playerDeadCritical() {
        ThreatAssessment a = assessor.evaluate(
                ThreatSignals.builder().playerDead(true).build());
        Assertions.assertEquals(ThreatLevel.CRITICAL, a.getLevel());
    }

    @Test
    void defenseHintRaisesFloor() {
        ThreatAssessment a = assessor.evaluate(
                ThreatSignals.builder().defensePriorityHint(95f).build());
        Assertions.assertEquals(ThreatLevel.CRITICAL, a.getLevel());
    }

    @Test
    void nullSignalsNone() {
        Assertions.assertEquals(ThreatLevel.NONE, assessor.evaluate(null).getLevel());
    }
}
