package adris.altoclef.movement;

import java.util.Locale;

/**
 * One structured record per GetToBlockTask run: what was asked, which mover actually drove it,
 * how long it took, and whether the player really ended up in the goal (checked against world
 * position, not engine status). Pure Java so it is unit-testable; logged as a single TRAVEL line.
 */
public final class TravelTrace {

    private final String goal;
    private final double sx, sy, sz;
    private final double tx, ty, tz;
    private final String requested;

    private String executed = "NONE";
    private boolean fallback;
    private int ticks;
    private int firstPathingTick = -1;
    private int stallTicks;
    private double traveled;
    private double lx, ly, lz;
    private boolean finished;
    private String line;

    public TravelTrace(String goal, double sx, double sy, double sz, double tx, double ty, double tz, String requested) {
        this.goal = goal;
        this.sx = sx; this.sy = sy; this.sz = sz;
        this.tx = tx; this.ty = ty; this.tz = tz;
        this.requested = requested;
        this.lx = sx; this.ly = sy; this.lz = sz;
    }

    /** Which mover took the latest dispatch; {@code fellBack} is sticky once any dispatch fell back. */
    public void dispatched(String backend, boolean fellBack) {
        if (backend != null) executed = backend;
        fallback |= fellBack;
    }

    /** Call once per task tick with the player position and whether a path is being planned/executed. */
    public void tick(double x, double y, double z, boolean pathing) {
        if (finished) return;
        ticks++;
        double d = Math.sqrt((x - lx) * (x - lx) + (y - ly) * (y - ly) + (z - lz) * (z - lz));
        // Ignore teleports (dimension change, respawn) so they don't count as walking.
        if (d < 10) traveled += d;
        if (pathing && firstPathingTick < 0) firstPathingTick = ticks;
        if (d < 0.01) stallTicks++;
        lx = x; ly = y; lz = z;
    }

    /**
     * Close the record. {@code arrived} must come from a world check (goal.isInGoal(playerPos)).
     * Returns the TRAVEL line; later calls return the same line.
     */
    public String finish(boolean arrived, String failureReason) {
        if (finished) return line;
        finished = true;
        double remaining = Math.sqrt((tx - lx) * (tx - lx) + (ty - ly) * (ty - ly) + (tz - lz) * (tz - lz));
        String outcome = arrived ? "ARRIVED" : failureReason != null ? "FAILED" : "STOPPED";
        line = String.format(Locale.ROOT,
                "TRAVEL goal=%s start=%.0f,%.0f,%.0f target=%.0f,%.0f,%.0f requested=%s executed=%s fallback=%b"
                        + " pathFoundTick=%d ticks=%d traveled=%.1f remaining=%.1f stallTicks=%d outcome=%s reason=%s verified=%b",
                goal, sx, sy, sz, tx, ty, tz, requested, executed, fallback,
                firstPathingTick, ticks, traveled, remaining, stallTicks, outcome,
                failureReason == null ? "-" : failureReason, arrived);
        return line;
    }

    public boolean isFinished() { return finished; }
    public int ticks() { return ticks; }
    public double traveled() { return traveled; }
    public String executed() { return executed; }
    public boolean fallback() { return fallback; }
    public int firstPathingTick() { return firstPathingTick; }
    public int stallTicks() { return stallTicks; }
}
