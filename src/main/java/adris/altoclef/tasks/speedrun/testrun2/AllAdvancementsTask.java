package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * All Advancements for Java 1.16.5.
 *
 * One sticky child per catalog goal. Skip goals already done (AdvancementProbe)
 * or that time out. Never inject UnstickWalk as a sibling of the goal task.
 */
public class AllAdvancementsTask extends Task {

    private AdvancementCatalog.Goal[] goals;
    private int index;
    private Task inner;
    private int goalTicks;
    private int pulse;
    private boolean routeStarted;
    private int doneHint;

    @Override
    protected void onStart() {
        goals = AdvancementCatalog.all();
        index = 0;
        inner = null;
        goalTicks = 0;
        pulse = 0;
        routeStarted = false;
        doneHint = 0;
        T2Brain.reset();
        Debug.logMessage("AA: catalog=" + goals.length + " ver=1." + McCompat.gameMinor()
                + ".x — skipping IDs newer than this client");
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        if (goals == null) goals = AdvancementCatalog.all();

        while (index < goals.length && (AdvancementProbe.done(goals[index].id) || !goals[index].onThisVersion())) {
            if (!goals[index].onThisVersion()) {
                Debug.logMessage("AA skip 1." + goals[index].since + "+ " + goals[index].id);
            } else {
                Debug.logMessage("AA skip done " + goals[index].id);
            }
            index++;
            inner = null;
            goalTicks = 0;
        }
        if (index >= goals.length) {
            Debug.logMessage("AA catalog walked. Open L for leftovers (HARD goals were timed out, not failed silently).");
            return null;
        }

        AdvancementCatalog.Goal g = goals[index];
        if (!(inner instanceof ModernSpeedrunTask)) {
            Task fix = T2Brain.help(mod, "AA:" + g.id, inner);
            if (fix != null) {
                return fix;
            }
        }
        goalTicks++;
        pulse++;
        if (pulse % (20 * 20) == 0) {
            Debug.logMessage("AA " + (index + 1) + "/" + goals.length
                    + " " + g.id + " t=" + (goalTicks / 20) + "s/" + g.seconds
                    + " child=" + (inner == null ? "-" : inner.getClass().getSimpleName())
                    + " doneHint=" + doneHint);
        }

        if (g.kind == AdvancementCatalog.Kind.ROUTE) {
            if (!routeStarted) {
                inner = new ModernSpeedrunTask();
                routeStarted = true;
                Debug.logMessage("AA route → ModernSpeedrunTask for " + g.id);
            }
            boolean routeOver = dragonDead(mod)
                    || AdvancementProbe.done("end/kill_dragon")
                    || AdvancementProbe.done("story/enter_the_end");
            if (routeOver && !g.id.startsWith("end/") && !g.id.startsWith("story/enter")) {
                // still in catalog; skip remaining ROUTE ids that the run already produced
            }
            if (AdvancementProbe.done(g.id) || (routeOver && g.id.startsWith("story/"))) {
                next("route covered " + g.id);
                return inner;
            }
            if (goalTicks > g.seconds * 20 && index > 0) {
                next("timeout " + g.id);
                return inner;
            }
            return inner;
        }

        if (goalTicks > Math.max(20 * 30, g.seconds * 20)) {
            next("timeout " + g.id + " — " + g.how);
            return inner;
        }

        Task wanted = AaGrinders.forGoal(mod, g);
        if (wanted == null) {
            next("no task / already have items for " + g.id);
            return inner;
        }
        if (inner == null || inner.getClass() != wanted.getClass()) {
            inner = wanted;
            Debug.logMessage("AA goal " + g.id + " — " + g.title + " | " + g.how);
        }
        return inner;
    }

    private void next(String why) {
        Debug.logMessage("AA next (" + why + ")");
        index++;
        inner = null;
        goalTicks = 0;
        doneHint++;
    }

    private boolean dragonDead(AltoClef mod) {
        try {
            if (count(mod, Items.DRAGON_EGG) >= 1 || count(mod, Items.ELYTRA) >= 1) return true;
            var list = mod.getEntityTracker().getTrackedEntities(EnderDragonEntity.class);
            return list != null && list.isEmpty()
                    && AdvancementProbe.done("end/kill_dragon");
        } catch (Throwable t) {
            return AdvancementProbe.done("end/kill_dragon");
        }
    }

    private int count(AltoClef mod, Item item) {
        try {
            return mod.getItemStorage().getItemCount(item);
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    protected void onStop(Task interruptTask) {
        inner = null;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof AllAdvancementsTask;
    }

    @Override
    protected String toDebugString() {
        if (goals == null || index >= goals.length) return "AA done";
        return "AA " + (index + 1) + "/" + goals.length + " " + goals[index].id;
    }
}
