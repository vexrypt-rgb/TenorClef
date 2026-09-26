package adris.altoclef.movement;

import adris.altoclef.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reflection bridge to kaptainwutax.tungsten (3ndetz/Tungsten altoclef-compat).
 * Keeps AltoClef compilable when the Tungsten jar is absent; fails soft at runtime.
 *
 * Real API (vendor commit 5cb12ad):
 * - TungstenModDataContainer.PATHFINDER.find(WorldView, Vec3d, PlayerEntity)
 * - PATHFINDER.active / PATHFINDER.stop (AtomicBoolean)
 * - EXECUTOR.isRunning() / EXECUTOR.stop
 * - FollowEntityTask.start(Entity, double) / stop() / isActive()
 */
final class TungstenBridge {

    private static final String MOD = "kaptainwutax.tungsten.TungstenMod";
    private static final String DATA = "kaptainwutax.tungsten.TungstenModDataContainer";
    private static final String FOLLOW = "kaptainwutax.tungsten.task.FollowEntityTask";

    private static Boolean present;
    private static String presentDetail = "not probed";

    private static Class<?> dataClass;
    private static Object pathfinder;
    private static Field pathfinderActive;
    private static Field pathfinderStop;
    private static Method pathfinderFind;
    private static Field executorField;
    private static Method executorIsRunning;
    private static Field executorStop;

    private static Class<?> followClass;
    private static Method followStart;
    private static Method followStop;
    private static Method followIsActive;

    private TungstenBridge() {}

    static synchronized boolean isPresent() {
        if (present != null) return present;
        present = false;
        try {
            Class.forName(MOD, false, TungstenBridge.class.getClassLoader());
            dataClass = Class.forName(DATA, false, TungstenBridge.class.getClassLoader());
            followClass = Class.forName(FOLLOW, false, TungstenBridge.class.getClassLoader());

            Field pfField = dataClass.getField("PATHFINDER");
            pathfinder = pfField.get(null);
            if (pathfinder == null) {
                // Tungsten not initialised yet: do not cache false, or it stays "missing" all session.
                presentDetail = "PATHFINDER null (not initialised yet)";
                present = null;
                return false;
            }
            Class<?> pfClass = pathfinder.getClass();
            pathfinderActive = pfClass.getField("active");
            pathfinderStop = pfClass.getField("stop");
            pathfinderFind = pfClass.getMethod("find", WorldView.class, Vec3d.class, PlayerEntity.class);

            executorField = dataClass.getField("EXECUTOR");
            // resolve executor methods lazily (EXECUTOR set in TungstenMod.onInitializeClient)

            followStart = followClass.getMethod("start", Entity.class, double.class);
            followStop = followClass.getMethod("stop");
            followIsActive = followClass.getMethod("isActive");

            present = true;
            presentDetail = "kaptainwutax.tungsten bound";
            Debug.logMessage("TungstenBridge: " + presentDetail);
        } catch (Throwable t) {
            presentDetail = "missing: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            present = false;
        }
        return present;
    }

    static String detail() {
        isPresent();
        return presentDetail;
    }

    static boolean pathTo(BlockPos pos) {
        if (!isPresent()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return false;
        try {
            cancelPathingOnly();
            // PathFinder.find() silently returns while the previous search thread is still
            // winding down, so report "busy" instead of a success that started nothing.
            AtomicBoolean active = (AtomicBoolean) pathfinderActive.get(pathfinder);
            if (active != null && active.get()) return false;
            Vec3d target = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            Field targetField = Class.forName(MOD).getField("TARGET");
            targetField.set(null, target);
            try { dataClass.getField("world").set(null, mc.world); dataClass.getField("player").set(null, mc.player); } catch (Throwable ignored) {}
            pathfinderFind.invoke(pathfinder, mc.world, target, mc.player);
            return active == null || active.get();
        } catch (Throwable t) {
            Debug.logWarning("TungstenBridge.pathTo failed: " + t);
            return false;
        }
    }

    static boolean follow(Entity entity, double maintainDistance) {
        if (!isPresent() || entity == null) return false;
        try {
            cancelAll();
            followStart.invoke(null, entity, maintainDistance);
            return true;
        } catch (Throwable t) {
            Debug.logWarning("TungstenBridge.follow failed: " + t);
            return false;
        }
    }

    static boolean isPathing() {
        if (!isPresent()) return false;
        try {
            if (followIsActive != null && Boolean.TRUE.equals(followIsActive.invoke(null))) {
                return true;
            }
            AtomicBoolean active = (AtomicBoolean) pathfinderActive.get(pathfinder);
            if (active != null && active.get()) return true;
            Object exec = executorField.get(null);
            if (exec != null) {
                if (executorIsRunning == null) {
                    executorIsRunning = exec.getClass().getMethod("isRunning");
                }
                if (Boolean.TRUE.equals(executorIsRunning.invoke(exec))) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    static void cancelAll() {
        if (!isPresent()) return;
        try {
            if (followIsActive != null && Boolean.TRUE.equals(followIsActive.invoke(null))) {
                followStop.invoke(null);
            }
        } catch (Throwable ignored) {}
        cancelPathingOnly();
    }

    private static void cancelPathingOnly() {
        try {
            AtomicBoolean stop = (AtomicBoolean) pathfinderStop.get(pathfinder);
            if (stop != null) stop.set(true);
            Object exec = executorField.get(null);
            if (exec != null) {
                if (executorStop == null) {
                    executorStop = exec.getClass().getField("stop");
                }
                executorStop.setBoolean(exec, true);
            }
        } catch (Throwable ignored) {}
    }
}
