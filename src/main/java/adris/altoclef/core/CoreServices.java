package adris.altoclef.core;

import adris.altoclef.control.MovementController;
import adris.altoclef.knowledge.WorldKnowledge;

/**
 * Bundles the Phase 3 extracted facades for injection-style access.
 * AltoClef still owns construction; this is a convenience holder so new code
 * can take {@code CoreServices} instead of the whole god object.
 */
public final class CoreServices {

    private final WorldKnowledge worldKnowledge;
    private final MovementController movement;

    public CoreServices(WorldKnowledge worldKnowledge, MovementController movement) {
        this.worldKnowledge = ServicePreconditions.requireService(worldKnowledge, "worldKnowledge");
        this.movement = ServicePreconditions.requireService(movement, "movement");
    }

    public WorldKnowledge world() {
        return worldKnowledge;
    }

    public MovementController movement() {
        return movement;
    }
}
