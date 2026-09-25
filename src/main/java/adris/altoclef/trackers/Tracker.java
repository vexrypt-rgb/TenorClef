package adris.altoclef.trackers;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;

public abstract class Tracker {

    protected AltoClef mod;
    // Needs to update. volatile: readers call isDirty()/read state without holding the
    // lock, so the flag must be published.
    private volatile boolean dirty = true;

    public Tracker(TrackerManager manager) {
        manager.addTracker(this);
    }

    public void setDirty() {
        dirty = true;
    }

    // Virtual
    protected boolean isDirty() {
        return dirty;
    }

    /**
     * SERIALIZED. Two threads calling this at once ran {@code updateState()} concurrently
     * and corrupted it — two {@code InventorySubTracker} scans ended up inserting into the
     * same {@code ArrayList}, which hard-crashed the client with
     * {@code ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0} (run O,
     * 16:33, killing a run that had reached PORTAL at 7:29). Nothing else in here was
     * thread-safe either, so the lock is the cheap, whole-class fix.
     */
    protected synchronized void ensureUpdated() {
        if (isDirty()) {
            try {
                updateState();
            } catch (Throwable t) {
                // A tracker must never take the game down with it. Report it and leave the
                // tracker dirty so the next tick rebuilds from scratch.
                Debug.logWarning("Tracker " + getClass().getSimpleName()
                        + " update failed: " + t);
                return;
            }
            dirty = false;
        }
    }

    protected abstract void updateState();

    protected abstract void reset();
}
