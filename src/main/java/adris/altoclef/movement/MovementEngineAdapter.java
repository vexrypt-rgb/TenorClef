package adris.altoclef.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
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
        return factoryMethod.invoke(null, baritone);
    }

    private static boolean accepted(Object pathResult) throws Exception {
        return pathResult != null && Boolean.TRUE.equals(pathResultAccepted.invoke(pathResult));
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
                    Object mg = goalCustomMethod.invoke(null, goal);
                    Object result = goToGoalMethod.invoke(eng, mg);
                    if (accepted(result)) {
                        return true;
                    }
                    Debug.logMessage("MovementEngineAdapter: engine declined; CustomGoalProcess fallback");
                }
            } catch (Throwable t) {
                Debug.logMessage("MovementEngineAdapter: engine goTo failed: " + t + " — fallback");
            }
        }
        bari.getCustomGoalProcess().setGoalAndPath(goal);
        return true;
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
                if (eng != null && accepted(goToBlockMethod.invoke(eng, pos))) {
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
                if (eng != null && accepted(followMethod.invoke(eng, entity, maintainDistance))) {
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
                if (eng != null && Boolean.TRUE.equals(isPathingMethod.invoke(eng))) {
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
                    cancelMethod.invoke(eng);
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
        TungstenMovement.cancel();
    }

    public static String statusLine() {
        AltoClef mod = AltoClef.getInstance();
        if (mod != null && mod.getClientBaritone() != null && probeClasses()) {
            try {
                Object eng = engineFor(mod.getClientBaritone());
                if (eng != null) {
                    return String.valueOf(statusLineMethod.invoke(eng));
                }
            } catch (Throwable ignored) {
            }
        }
        return "MovementEngine: FALLBACK (" + detail() + ") | " + TungstenMovement.statusLine();
    }
}
