package adris.altoclef.core;

import adris.altoclef.control.FakeMovementController;
import adris.altoclef.knowledge.FakeWorldKnowledge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Wiring tests for Phase 3 facades. Uses fakes; no Minecraft runtime required
 * beyond classpath types for interface signatures.
 */
public class CoreServicesTest {

    @Test
    void bundlesFacades() {
        FakeWorldKnowledge world = new FakeWorldKnowledge();
        FakeMovementController movement = new FakeMovementController();
        CoreServices services = new CoreServices(world, movement);
        Assertions.assertSame(world, services.world());
        Assertions.assertSame(movement, services.movement());
    }

    @Test
    void rejectsNullWorldKnowledge() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new CoreServices(null, new FakeMovementController()));
    }

    @Test
    void rejectsNullMovement() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new CoreServices(new FakeWorldKnowledge(), null));
    }

    @Test
    void fakeMovementRecordsCancel() {
        FakeMovementController movement = new FakeMovementController();
        movement.pathing = true;
        movement.cancel();
        Assertions.assertEquals(1, movement.cancelCount);
        Assertions.assertFalse(movement.pathing);
    }

    @Test
    void canQueryWorldContract() {
        Assertions.assertTrue(ServicePreconditions.canQueryWorld(true, true, true));
        Assertions.assertFalse(ServicePreconditions.canQueryWorld(true, false, true));
    }
}
