package adris.altoclef.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.TungstenFollowTask;
import adris.altoclef.tasks.movement.TungstenGotoTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/**
 * Physics A* movement backend facade (Tungsten).
 *
 * Architecture (locked):
 * - Tungsten = parkour / chase-escape / ;goto-style travel when available
 * - Baritone = mining / block interaction / inventory (unchanged)
 *
 * Travel mover preference ({@link #setTravelMover}):
 * - AUTO: Tungsten if bound, else Baritone
 * - TUNGSTEN: prefer Tungsten when available (falls back to Baritone if missing)
 * - BARITONE (default): force Baritone travel tasks
 *
 * Preference only affects {@link #gotoBlock} / {@link #followEntity} travel helpers.
 * Mining and block-break pathing stay on Baritone regardless.
 *
 * Vendored source: vendor/tungsten (3ndetz/Tungsten @ altoclef-compat, commit 5cb12ad).
 * Runtime: jar under libs/ or vendor/tungsten/build/libs — see docs/TUNGSTEN_BACKEND.md.
 * Binding is via {@link TungstenBridge} reflection so AltoClef still compiles when the jar is absent.
 */
public final class TungstenMovement {

    /** Travel backend preference for goto/follow helpers (not mining). */
    public enum TravelMover {
        AUTO,
        TUNGSTEN,
        BARITONE
    }

    private static volatile TravelMover travelMover = TravelMover.BARITONE;

    private TungstenMovement() {}

    public static void setTravelMover(TravelMover mover) {
        travelMover = mover != null ? mover : TravelMover.AUTO;
    }

    public static TravelMover getTravelMover() {
        return travelMover;
    }

    public static TravelMover parseTravelMover(String raw) {
        if (raw == null || raw.isBlank()) return TravelMover.AUTO;
        return switch (raw.trim().toLowerCase()) {
            case "tungsten", "tung", "physics" -> TravelMover.TUNGSTEN;
            case "baritone", "bati", "bt" -> TravelMover.BARITONE;
            case "auto", "default" -> TravelMover.AUTO;
            default -> TravelMover.AUTO;
        };
    }

    public static boolean isAvailable() {
        return TungstenBridge.isPresent();
    }

    private static boolean preferTungstenTravel() {
        return switch (travelMover) {
            case BARITONE -> false;
            case TUNGSTEN, AUTO -> isAvailable();
        };
    }

    /**
     * Travel to a block. Respects {@link #getTravelMover()}; mining stays on Baritone elsewhere.
     */
    public static Task gotoBlock(BlockPos pos) {
        if (preferTungstenTravel()) {
            Debug.logMessage("TungstenMovement: physics A* goto " + pos.toShortString()
                    + " (mover=" + travelMover + ")");
            return new TungstenGotoTask(pos);
        }
        return new GetToBlockTask(pos);
    }

    /**
     * Follow/chase an entity (manhunt hunter path). Respects travel mover preference.
     */
    public static Task followEntity(Entity entity, double maintainDistance) {
        if (preferTungstenTravel()) {
            Debug.logMessage("TungstenMovement: followEntity " + safeName(entity)
                    + " (mover=" + travelMover + ")");
            return new TungstenFollowTask(entity, maintainDistance);
        }
        return new GetToEntityTask(entity, maintainDistance);
    }

    /** Start Tungsten path to block (used by TungstenGotoTask). */
    public static boolean requestPathTo(BlockPos pos) {
        return TungstenBridge.pathTo(pos);
    }

    /** Start Tungsten path to block along a given route of feet positions (e.g. from Baritone). */
    public static boolean requestPathVia(BlockPos pos, java.util.List<BlockPos> waypoints) {
        return TungstenBridge.pathToVia(pos, waypoints);
    }

    /** Start Tungsten entity follow (used by TungstenFollowTask). */
    public static boolean requestFollow(Entity entity, double maintainDistance) {
        return TungstenBridge.follow(entity, maintainDistance);
    }

    public static boolean isPathing() {
        return TungstenBridge.isPathing();
    }

    public static void cancel() {
        TungstenBridge.cancelAll();
    }

    /** Human-readable status for commands / debug. */
    public static String statusLine() {
        String pref = "mover=" + travelMover;
        if (isAvailable()) {
            return "Tungsten backend: AVAILABLE (" + TungstenBridge.detail() + "; " + pref + ")";
        }
        return "Tungsten backend: MISSING — Baritone fallback (" + TungstenBridge.detail()
                + "; " + pref + "; see docs/TUNGSTEN_BACKEND.md)";
    }

    /** Called by @tgoto when Tungsten is missing so the user gets a clear message. */
    public static void logMissingBackend(AltoClef mod) {
        Debug.logWarning(statusLine());
        Debug.logMessage("Build vendor/tungsten then place jar in libs/ (or use vendor build/libs). Do NOT full-merge UnionClef.");
    }

    private static String safeName(Entity e) {
        if (e == null) return "null";
        try {
            return e.getName().getString();
        } catch (Throwable t) {
            return "?";
        }
    }
}
