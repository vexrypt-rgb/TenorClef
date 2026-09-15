package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.entity.DoToClosestEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.speedrun.testrun2.T2Brain;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Sticky;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Walk to the nearest villager. Loot stays on official @gamer / catalogue.
 */
public class VillageFirstTask extends Task {

    private final T2Sticky sticky = new T2Sticky();
    private boolean done;
    private int ticks;

    @Override
    protected void onStart() {
        sticky.clear();
        done = false;
        ticks = 0;
        T2Brain.reset();
        Debug.logMessage("VILLAGE seek villager");
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        ticks++;
        Task live = sticky.peek();
        Task fix = T2Brain.help(mod, "VILLAGE", live);
        if (fix != null) return sticky.keep("brain", fix);

        try {
            boolean near = false;
            for (var e : mod.getEntityTracker().getTrackedEntities(VillagerEntity.class)) {
                if (e.squaredDistanceTo(mod.getPlayer()) < 16) {
                    near = true;
                    break;
                }
            }
            if (near) {
                done = true;
                Debug.logMessage("VILLAGE at doors");
                Splits.mark("village");
                return null;
            }
        } catch (Throwable ignored) {}

        if (ticks > 20 * 180) {
            done = true;
            Debug.logMessage("VILLAGE timeout 3m");
            return null;
        }

        try {
            return sticky.keep("villager", new DoToClosestEntityTask(
                    (entity) -> new adris.altoclef.tasks.movement.GetToEntityTask(entity),
                    VillagerEntity.class));
        } catch (Throwable t) {
            return sticky.keep("wander", new TimeoutWanderTask());
        }
    }

    @Override
    protected void onStop(Task interrupt) {
        sticky.clear();
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof VillageFirstTask;
    }

    @Override
    protected String toDebugString() {
        return "village-first";
    }
}
