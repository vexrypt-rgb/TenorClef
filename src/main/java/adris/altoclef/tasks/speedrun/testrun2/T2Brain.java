package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

/**
 * Drop this in any personal Task.onTick:
 *
 * <pre>
 *   Task fix = T2Brain.help(mod, "MyTask", currentChild);
 *   if (fix != null) return fix;
 * </pre>
 *
 * Probe writes E-codes. Solve returns a replacement Task (S140 wander)
 * when the same XZ has produced no progress. Parent must return that
 * child — ModernSpeedrunTask already does: if (solved != null) return stick(solved).
 */
public final class T2Brain {

    private T2Brain() {}

    public static void reset() {
        T2History.reset();
        T2Probe.reset();
        T2Solve.reset();
        adris.altoclef.tasks.speedrun.testrun2.util.Splits.reset();
    }

    /**
     * @param label phase or goal id, shown in HIST
     * @param child the task you are about to return (may be null)
     * @return a replacement child if the solver has a better idea, else null
     */
    public static Task help(AltoClef mod, String label, Task child) {
        if (mod == null) return null;
        adris.altoclef.tasks.speedrun.testrun2.util.DeathWatch.tick(mod);
        adris.altoclef.tasks.speedrun.testrun2.util.QueueWatch.tick(mod);
        if (adris.altoclef.tasks.speedrun.testrun2.util.QueueWatch.blocked()) {
            return null;
        }
        if (mod.getPlayer() == null) return null;
        adris.altoclef.tasks.speedrun.testrun2.util.KitLock.tick(mod);
        adris.altoclef.tasks.speedrun.testrun2.util.PlayerSense.tick(mod);
        adris.altoclef.tasks.speedrun.testrun2.util.Waypoints.tick(mod);
        adris.altoclef.tasks.speedrun.testrun2.util.SurvivePlus.tick(mod);
        T2History.tick(mod, label, child);
        T2Probe.tick(mod, label, child);
        if (adris.altoclef.tasks.speedrun.testrun2.util.PlayerSense.frozen()) {
            return null;
        }
        adris.altoclef.tasks.speedrun.testrun2.core.T2Input.tick();
        Task fix = T2Solve.tick(mod, label, child);
        if (SpeedrunOpt.SURVIVE_TICK) SurviveTick.tick(mod);
        return fix;
    }
}
