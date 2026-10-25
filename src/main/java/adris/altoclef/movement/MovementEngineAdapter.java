package adris.altoclef.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;

/**
 * Thin adapter from TenorClef travel tasks onto Ostinato {@code IMovementEngine}.
 * <p>
 * Uses reflection so TenorClef still compiles and runs against stock Baritone jars
 * that lack MovementEngine types (1.21.1 published artifact, 1.16.1 Baritone-only).
 * When the engine is unavailable, falls back to {@code getCustomGoalProcess()}.
 * <p>
 * Mining / builder call sites are intentionally not routed here.
 */
public final class MovementEngineAdapter {

    private static final String ENGINE_FACTORY = "baritone.movement.MovementBackends";
    private static final String GOAL_CLASS = "baritone.api.movement.MovementGoal";
    private static final String ENGINE_IFACE = "baritone.api.movement.IMovementEngine";
    private static final String PATH_RESULT = "baritone.api.movement.PathResult";

    private static Boolean classesPresent;
    private static String engineDetail = "not probed";

    private static Method factoryMethod;
    private static Method goToGoalMethod;
    private static Method goToBlockMethod;
    private static Method followMethod;
    private static Method isPathingMethod;
    private static Method cancelMethod;
    private static Method statusLineMethod;
    private static Method goalCustomMethod;
    private static Method pathResultAccepted;
    private static Method pathResultBackend;
    private static Method preferenceMethod;

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

    private static synchronized boolean probeClasses() {
        if (classesPresent != null) {
            return classesPresent;
        }
        classesPresent = false;
        //#if MC >= 260000
        //$$ // Ostinato: 26.3 jars carry the typed driver API (IBaritone.getMovementEngine), no reflection.
        //$$ classesPresent = true;
        //$$ engineDetail = "Ostinato MovementEngine (typed API)";
        //$$ if (classesPresent) return true;
        //#endif
        try {
            Class<?> backends = Class.forName(ENGINE_FACTORY);
            Class<?> goalCls = Class.forName(GOAL_CLASS);
            Class<?> engineCls = Class.forName(ENGINE_IFACE);
            Class<?> pathResult = Class.forName(PATH_RESULT);
            factoryMethod = backends.getMethod("engine", IBaritone.class);
            goToGoalMethod = engineCls.getMethod("goTo", goalCls);
            goToBlockMethod = engineCls.getMethod("goTo", BlockPos.class);
            followMethod = engineCls.getMethod("follow", Entity.class, double.class);
            isPathingMethod = engineCls.getMethod("isPathing");
            cancelMethod = engineCls.getMethod("cancel");
            statusLineMethod = engineCls.getMethod("statusLine");
            goalCustomMethod = goalCls.getMethod("custom", Goal.class);
            pathResultAccepted = pathResult.getMethod("isAccepted");
            pathResultBackend = pathResult.getMethod("getBackend");
            preferenceMethod = backends.getMethod("preference");
            classesPresent = true;
            engineDetail = "Ostinato MovementEngine classes present";
            Debug.logMessage("MovementEngineAdapter: " + engineDetail);
        } catch (Throwable t) {
            engineDetail = "unavailable: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            classesPresent = false;
        }
        return classesPresent;
    }

    /** Clear cached class probe (tests / classpath changes). */
    public static synchronized void reset() {
        classesPresent = null;
        engineDetail = "not probed";
        factoryMethod = null;
    }

    private static Object engineFor(IBaritone baritone) throws Exception {
        if (!probeClasses() || baritone == null) {
            return null;
        }
        //#if MC >= 260000
        //$$ return baritone.getMovementEngine(); // Ostinato: typed API
        //#else
        return factoryMethod.invoke(null, baritone);
        //#endif
    }

    private static boolean accepted(Object pathResult) throws Exception {
        //#if MC >= 260000
        //$$ return pathResult != null && ((baritone.api.movement.PathResult) pathResult).isAccepted();
        //#else
        return pathResult != null && Boolean.TRUE.equals(pathResultAccepted.invoke(pathResult));
        //#endif
    }

    // Ostinato: one call per engine operation. 26.3 uses the typed API; older jars keep reflection.
    private static Object engGoToGoal(Object eng, Goal goal) throws Exception {
        //#if MC >= 260000
        //$$ return ((baritone.api.movement.IMovementEngine) eng).goTo(baritone.api.movement.MovementGoal.custom(goal));
        //#else
        return goToGoalMethod.invoke(eng, goalCustomMethod.invoke(null, goal));
        //#endif
    }

    private static Object engGoToBlock(Object eng, BlockPos pos) throws Exception {
        //#if MC >= 260000
        //$$ return ((baritone.api.movement.IMovementEngine) eng).goTo(pos);
        //#else
        return goToBlockMethod.invoke(eng, pos);
        //#endif
    }

    private static Object engFollow(Object eng, Entity entity, double maintainDistance) throws Exception {
        //#if MC >= 260000
        //$$ return ((baritone.api.movement.IMovementEngine) eng).follow(entity, maintainDistance);
        //#else
        return followMethod.invoke(eng, entity, maintainDistance);
        //#endif
    }

    private static boolean engIsPathing(Object eng) throws Exception {
        //#if MC >= 260000
        //$$ return ((baritone.api.movement.IMovementEngine) eng).isPathing();
        //#else
        return Boolean.TRUE.equals(isPathingMethod.invoke(eng));
        //#endif
    }

    private static void engCancel(Object eng) throws Exception {
        //#if MC >= 260000
        //$$ ((baritone.api.movement.IMovementEngine) eng).cancel();
        //#else
        cancelMethod.invoke(eng);
        //#endif
    }

    private static String engStatusLine(Object eng) throws Exception {
        //#if MC >= 260000
        //$$ return ((baritone.api.movement.IMovementEngine) eng).statusLine();
        //#else
        return String.valueOf(statusLineMethod.invoke(eng));
        //#endif
    }

    private static String resultBackend(Object result) throws Exception {
        //#if MC >= 260000
        //$$ return String.valueOf(((baritone.api.movement.PathResult) result).getBackend());
        //#else
        return String.valueOf(pathResultBackend.invoke(result));
        //#endif
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
                Object eng = engineFor(bari);
                if (eng != null) {
                    Object result = engGoToGoal(eng, goal);
                    if (accepted(result)) {
                        dispatches++;
                        lastBackend = resultBackend(result);
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
            //#if MC >= 260000
            //$$ return String.valueOf(BaritoneAPI.getProvider().getPrimaryBaritone().getMovementBackendPreference()); // Ostinato
            //#else
            return String.valueOf(preferenceMethod.invoke(null));
            //#endif
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
                Object eng = engineFor(mod.getClientBaritone());
                if (eng != null && accepted(engGoToBlock(eng, pos))) {
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
                Object eng = engineFor(mod.getClientBaritone());
                if (eng != null && accepted(engFollow(eng, entity, maintainDistance))) {
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
                Object eng = engineFor(bari);
                if (eng != null && engIsPathing(eng)) {
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
                Object eng = engineFor(mod.getClientBaritone());
                if (eng != null) {
                    engCancel(eng);
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
                Object eng = engineFor(mod.getClientBaritone());
                if (eng != null) {
                    return engStatusLine(eng);
                }
            } catch (Throwable ignored) {
            }
        }
        return "MovementEngine: FALLBACK (" + detail() + ")";
    }
}
