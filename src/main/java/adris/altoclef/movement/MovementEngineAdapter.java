package adris.altoclef.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

import baritone.api.movement.IMovementEngine;
import baritone.api.movement.MovementGoal;
import baritone.api.movement.PathResult;

/**
 * Thin adapter from TenorClef travel tasks onto Ostinato {@code IMovementEngine}.
 * <p>
 * Ostinato: uses the typed driver API ({@code IBaritone.getMovementEngine()}) on every line.
 * When the engine declines or throws, falls back to {@code getCustomGoalProcess()}.
 * <p>
 * Mining / builder call sites are intentionally not routed here.
 */
public final class MovementEngineAdapter {

    private static Boolean classesPresent;
    private static String engineDetail = "not probed";

    /** Last travel dispatch, read by TravelTrace: the mover that took it, and whether it was not the preferred one. */
    private static String lastBackend = "NONE";
    private static boolean lastFellBack;
    private static int dispatches;

    private MovementEngineAdapter() {}

    public static synchronized boolean isEngineAvailable() {
        return probeClasses();
    }

    public static String detail() {
        probeClasses();
        return engineDetail;
    }

    // Ostinato: every pinned Ostinato jar carries the typed driver API, so there is nothing to probe.
    private static synchronized boolean probeClasses() {
        if (classesPresent == null) {
            classesPresent = true;
            engineDetail = "Ostinato MovementEngine (typed API)";
        }
        return classesPresent;
    }

    /** Clear cached state (tests). */
    public static synchronized void reset() {
        classesPresent = null;
        engineDetail = "not probed";
    }

    private static IMovementEngine engineFor(IBaritone baritone) {
        return baritone == null ? null : baritone.getMovementEngine();
    }

    private static boolean accepted(PathResult result) {
        return result != null && result.isAccepted();
    }

    /**
     * Ensure a Baritone-style goal is pursued via MovementEngine when present,
     * else {@code CustomGoalProcess.setGoalAndPath}.
     */
    public static boolean ensureGoalAndPath(Goal goal) {
        if (goal == null) {
            return false;
        }
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getClientBaritone() == null) {
            return false;
        }
        IBaritone bari = mod.getClientBaritone();
        if (probeClasses()) {
            try {
                IMovementEngine eng = engineFor(bari);
                if (eng != null) {
                    PathResult result = eng.goTo(MovementGoal.custom(goal));
                    if (accepted(result)) {
                        dispatches++;
                        lastBackend = String.valueOf(result.getBackend());
                        lastFellBack = !lastBackend.equals(requestedBackend());
                        return true;
                    }
                    Debug.logMessage("MovementEngineAdapter: engine declined; CustomGoalProcess fallback");
                }
            } catch (Throwable t) {
                Debug.logMessage("MovementEngineAdapter: engine goTo failed: " + t + " — fallback");
            }
        }
        bari.getCustomGoalProcess().setGoalAndPath(goal);
        dispatches++;
        lastBackend = "CUSTOM_GOAL_PROCESS";
        lastFellBack = classesPresent;
        return true;
    }

    /** Backend the engine would pick first (the preference setting), or NONE without an engine. */
    public static String requestedBackend() {
        if (!probeClasses()) return "NONE";
        try {
            return String.valueOf(BaritoneAPI.getProvider().getPrimaryBaritone().getMovementBackendPreference()); // Ostinato
        } catch (Throwable t) {
            return "UNKNOWN";
        }
    }

    /** Increments on every ensureGoalAndPath dispatch, so a trace can tell its own dispatches from stale ones. */
    public static int dispatchCount() {
        return dispatches;
    }

    public static String lastBackend() {
        return lastBackend;
    }

    public static boolean lastFellBack() {
        return lastFellBack;
    }

    /** Like ensureGoalAndPath but no-ops when already pathing/active. */
    public static boolean ensureGoalAndPathIfInactive(Goal goal) {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getClientBaritone() == null || goal == null) {
            return false;
        }
        if (isPathingOrActive()) {
            return true;
        }
        return ensureGoalAndPath(goal);
    }

    public static boolean goToBlock(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getClientBaritone() == null) {
            return false;
        }
        if (probeClasses()) {
            try {
                IMovementEngine eng = engineFor(mod.getClientBaritone());
                if (eng != null && accepted(eng.goTo(pos))) {
                    return true;
                }
            } catch (Throwable t) {
                Debug.logMessage("MovementEngineAdapter: goToBlock failed: " + t);
            }
        }
        return false;
    }

    public static boolean followEntity(Entity entity, double maintainDistance) {
        if (entity == null) {
            return false;
        }
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getClientBaritone() == null) {
            return false;
        }
        if (probeClasses()) {
            try {
                IMovementEngine eng = engineFor(mod.getClientBaritone());
                if (eng != null && accepted(eng.follow(entity, maintainDistance))) {
                    return true;
                }
            } catch (Throwable t) {
                Debug.logMessage("MovementEngineAdapter: follow failed: " + t);
            }
        }
        return false;
    }

    public static boolean isPathingOrActive() {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getClientBaritone() == null) {
            return false;
        }
        IBaritone bari = mod.getClientBaritone();
        if (probeClasses()) {
            try {
                IMovementEngine eng = engineFor(bari);
                if (eng != null && eng.isPathing()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return bari.getCustomGoalProcess().isActive() || bari.getPathingBehavior().isPathing();
    }

    public static void cancel() {
        AltoClef mod = AltoClef.getInstance();
        if (mod != null && mod.getClientBaritone() != null && probeClasses()) {
            try {
                IMovementEngine eng = engineFor(mod.getClientBaritone());
                if (eng != null) {
                    eng.cancel();
                    return;
                }
            } catch (Throwable t) {
                Debug.logMessage("MovementEngineAdapter: cancel failed: " + t);
            }
        }
        if (mod != null && mod.getClientBaritone() != null) {
            mod.getClientBaritone().getPathingBehavior().forceCancel();
            mod.getClientBaritone().getCustomGoalProcess().onLostControl();
        }
    }

    public static String statusLine() {
        AltoClef mod = AltoClef.getInstance();
        if (mod != null && mod.getClientBaritone() != null && probeClasses()) {
            try {
                IMovementEngine eng = engineFor(mod.getClientBaritone());
                if (eng != null) {
                    return eng.statusLine();
                }
            } catch (Throwable ignored) {
            }
        }
        return "MovementEngine: FALLBACK (" + detail() + ")";
    }
}
