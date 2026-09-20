package adris.altoclef.tasksystem;

/**
 * Why a task failed or needs retry (Phase 4). Subset is intentional —
 * planners / recovery (Phases 6–7) consume these later.
 */
public enum FailureReason {
    NO_PATH,
    RESOURCE_MISSING,
    TARGET_UNAVAILABLE,
    INVENTORY_FULL,
    DANGER,
    PLAYER_DEAD,
    WORLD_CHANGED,
    TIMEOUT,
    BACKEND_FAILURE,
    PRECONDITION_FAILED,
    UNKNOWN
}
