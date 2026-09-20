package adris.altoclef.core;

import java.util.Objects;

/**
 * Tiny pure helpers for Phase 3 facade wiring (unit-testable without Minecraft).
 */
public final class ServicePreconditions {

    private ServicePreconditions() {}

    public static <T> T requireService(T service, String name) {
        return Objects.requireNonNull(service, name);
    }

    /**
     * Whether a knowledge facade is ready for world queries.
     * Pure boolean contract so fakes / unit tests need no MC types.
     */
    public static boolean canQueryWorld(boolean inGame, boolean hasWorld, boolean hasPlayer) {
        return inGame && hasWorld && hasPlayer;
    }
}
