package adris.altoclef.tasks.construction;

import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskResult;

/**
 * Who owns navigation while {@link DestroyBlockTask} is getting out of water.
 *
 * <p>Measured (wes-pilot1, {@code WATERTRACE}): with the target out of reach below a 1-deep pond, every Baritone step
 * lifted the bot off the floor, so the bob check fired. On every airborne tick after that the parent called
 * {@code cancelEverything()} again, cancelling its own escape child's path, and on the first tick it touched the pond
 * floor (grounded, still wet) it fell through to PATH, dropped the escape and pathed back toward the buried target. That
 * repeated every ~20 ticks for the whole trial and the escape never once reached dry ground.
 *
 * <p>The fix: once an escape is issued it owns navigation until its own completion predicate holds
 * ({@code GetOutOfWaterTask.isFinished()}: dry and grounded for 750 ms) or it reports a terminal result itself. The
 * parent cancels navigation once, when it issues the escape, and does not re-run its water or path branches while the
 * escape is held. Coming back into the water for the same target after an escape ended is counted; past
 * {@link #ALLOWED_REENTRIES} the target is reported unreachable through the existing block-scanner blacklist, which
 * stops a shore-to-pond cycle for a target that can only be reached by standing in water.
 *
 * <p>No Minecraft types, so it is tick-tested directly ({@code WaterEscapeLatchTest}).
 */
public final class WaterEscapeLatch {

    /** Ticks wet without footing before a bob escape (unchanged from the pre-latch rule). */
    public static final int WATER_BOB_ESCAPE_TICKS = 25;
    /** Water re-entries for one target that are tolerated; the next one reports the target unreachable. */
    public static final int ALLOWED_REENTRIES = 1;

    private Task escape;
    /** The escape released last, until the runner has stopped it. */
    private Task released;
    private int escapesEnded;
    private int reentries;

    /**
     * The pre-latch escape trigger, unchanged: wet without footing, and either bobbing for
     * {@link #WATER_BOB_ESCAPE_TICKS} or out of mining reach.
     */
    public static boolean bobEscapeDue(boolean wet, boolean grounded, boolean reach, int bobTicks) {
        return wet && !grounded && (bobTicks >= WATER_BOB_ESCAPE_TICKS || !reach);
    }

    /**
     * The escape to keep returning this tick, or {@code null} when none is held. Releasing happens here: an escape that
     * finished, reported a terminal result, or was stopped by someone else is dropped and counted as ended.
     */
    public Task held() {
        if (escape == null) return null;
        if (escape.isActive() && !ended(escape)) return escape;
        released = escape;
        escape = null;
        escapesEnded++;
        return null;
    }

    /**
     * True while the escape just released is still the parent's running child. The parent then returns {@code null}
     * for that tick so the runner stops it before anything new is issued: {@code GetOutOfWaterTask.isEqual} matches
     * every instance, so a new escape returned in its place would leave the old, ended one running while this latch
     * held a new one that never starts.
     */
    public boolean releasePending(Task currentSub) {
        if (released == null) return false;
        if (currentSub == released && released.isActive()) return true;
        released = null;
        return false;
    }

    /**
     * Records a newly issued escape. Returns {@code true} when this entry is a re-entry past
     * {@link #ALLOWED_REENTRIES} for this target, i.e. the caller should report the target unreachable.
     */
    public boolean issued(Task newEscape) {
        escape = newEscape;
        if (escapesEnded == 0) return false;
        reentries++;
        return reentries > ALLOWED_REENTRIES;
    }

    /** The parent restarted: whatever child it had is gone, but the re-entry count for the target is kept. */
    public void parentRestarted() {
        if (escape != null) {
            escape = null;
            escapesEnded++;
        }
    }

    public int reentries() {
        return reentries;
    }

    /**
     * Called at the top of the parent's next tick. The runner ticks the escape on the tick it is issued, so by then it
     * is active unless something stopped it (or never started it), and either way it is no longer held.
     */
    private static boolean ended(Task t) {
        if (t.isFinished()) return true;
        TaskResult r = t.getExplicitResult();
        return r == TaskResult.SUCCESS || r == TaskResult.FAILURE || r == TaskResult.BLOCKED || r == TaskResult.CANCELLED;
    }
}
