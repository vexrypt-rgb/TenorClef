package adris.altoclef.tasks.speedrun.testrun2.core;

import adris.altoclef.tasksystem.Task;

/**
 * Keep one live child until the goal key changes or that child finishes.
 * Passing {@code new FooTask()} every tick is what cancelled Baritone.
 */
public final class T2Sticky {

    private Task live;
    private String key = "";

    public Task keep(String nextKey, Task fresh) {
        if (nextKey == null || nextKey.isEmpty() || fresh == null) {
            live = null;
            key = "";
            return null;
        }
        if (live != null && !finished(live) && nextKey.startsWith("brain:")) {
            return live;
        }
        if (nextKey.equals(key) && live != null && !finished(live)) {
            return live;
        }
        key = nextKey;
        live = fresh;
        return live;
    }

    public Task peek() {
        return live;
    }

    public void clear() {
        live = null;
        key = "";
    }

    private static boolean finished(Task t) {
        try {
            return t.isFinished();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
