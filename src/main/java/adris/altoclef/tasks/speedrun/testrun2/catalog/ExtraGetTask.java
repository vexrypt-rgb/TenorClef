package adris.altoclef.tasks.speedrun.testrun2.catalog;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.speedrun.testrun2.T2Brain;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Sticky;
import adris.altoclef.tasksystem.Task;

/** Sticky wrapper so @xget does not restart the catalogue child every tick. */
public class ExtraGetTask extends Task {

    private final String raw;
    private final int count;
    private final T2Sticky sticky = new T2Sticky();
    private boolean done;

    public ExtraGetTask(String raw, int count) {
        this.raw = raw;
        this.count = Math.max(1, count);
    }

    @Override
    protected void onStart() {
        T2Brain.reset();
        sticky.clear();
        done = false;
        Debug.logMessage("XGET " + raw + " x" + count + " -> " + ExtraCatalogue.resolve(raw));
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        Task inner = sticky.peek();
        Task fix = T2Brain.help(mod, "XGET:" + raw, inner);
        if (fix != null) return sticky.keep("brain", fix);

        if (inner != null && inner.isFinished()) {
            done = true;
            return null;
        }
        Task wanted = ExtraCatalogue.get(raw, count);
        if (wanted == null) {
            Debug.logWarning("XGET no task for " + raw);
            done = true;
            return null;
        }
        return sticky.keep("get:" + ExtraCatalogue.resolve(raw), wanted);
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
        return other instanceof ExtraGetTask o
                && o.raw.equals(raw) && o.count == count;
    }

    @Override
    protected String toDebugString() {
        return "xget " + raw + " x" + count;
    }
}
