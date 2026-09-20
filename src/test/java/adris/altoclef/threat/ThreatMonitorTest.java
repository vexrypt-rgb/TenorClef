package adris.altoclef.threat;

import adris.altoclef.planner.AcquireItemGoal;
import adris.altoclef.planner.GoalStatus;
import adris.altoclef.planner.InventoryView;
import adris.altoclef.planner.PlanExecutor;
import adris.altoclef.tasksystem.RecoveryAction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 8: ThreatMonitor pauses / fails PlanExecutor consistently with RecoveryManager.
 */
public class ThreatMonitorTest {

    private static final class FakeInv implements InventoryView {
        final Map<String, Integer> counts = new HashMap<>();

        @Override
        public int getCount(String catalogueKey) {
            return counts.getOrDefault(catalogueKey, 0);
        }
    }

    @Test
    void tickStoresLatest() {
        ThreatMonitor mon = new ThreatMonitor();
        ThreatAssessment a = mon.tick(ThreatSignals.builder().health(3f).build());
        Assertions.assertEquals(ThreatLevel.CRITICAL, a.getLevel());
        Assertions.assertEquals(ThreatLevel.CRITICAL, mon.getLevel());
        Assertions.assertEquals(1, mon.getTickCount());
    }

    @Test
    void highPausesGoal() {
        ThreatMonitor mon = new ThreatMonitor();
        PlanExecutor ex = new PlanExecutor();
        ex.start(new AcquireItemGoal("cobblestone", 8), new FakeInv());
        Assertions.assertEquals(GoalStatus.RUNNING, ex.getStatus());

        mon.tick(ThreatSignals.builder().health(7f).build());
        Assertions.assertTrue(mon.applyToExecutor(ex));
        Assertions.assertEquals(GoalStatus.PAUSED, ex.getStatus());
        Assertions.assertTrue(ex.getLastNote().contains("paused"));
    }

    @Test
    void criticalFailsWithDangerEscalate() {
        ThreatMonitor mon = new ThreatMonitor();
        PlanExecutor ex = new PlanExecutor();
        ex.start(new AcquireItemGoal("dirt", 4), new FakeInv());

        mon.tick(ThreatSignals.builder().inLava(true).build());
        Assertions.assertTrue(mon.applyToExecutor(ex));
        Assertions.assertEquals(GoalStatus.FAILED, ex.getStatus());
        Assertions.assertTrue(ex.getLastNote().startsWith("DANGER"));
        Assertions.assertEquals(RecoveryAction.ESCALATE, ex.getLastDecision().getAction());
    }

    @Test
    void resumeWhenThreatClears() {
        ThreatMonitor mon = new ThreatMonitor();
        PlanExecutor ex = new PlanExecutor();
        ex.start(new AcquireItemGoal("log", 2), new FakeInv());

        mon.tick(ThreatSignals.builder().health(7f).build());
        mon.applyToExecutor(ex);
        Assertions.assertEquals(GoalStatus.PAUSED, ex.getStatus());

        mon.tick(ThreatSignals.builder().health(20f).build());
        Assertions.assertTrue(mon.applyToExecutor(ex));
        Assertions.assertEquals(GoalStatus.RUNNING, ex.getStatus());
    }

    @Test
    void closeHostileInterrupts() {
        ThreatMonitor mon = new ThreatMonitor();
        PlanExecutor ex = new PlanExecutor();
        ex.start(new AcquireItemGoal("cobblestone", 1), new FakeInv());

        mon.tick(ThreatSignals.builder()
                .nearbyHostileCount(1)
                .closestHostileDistance(2.5)
                .build());
        mon.applyToExecutor(ex);
        Assertions.assertEquals(GoalStatus.FAILED, ex.getStatus());
    }
}
