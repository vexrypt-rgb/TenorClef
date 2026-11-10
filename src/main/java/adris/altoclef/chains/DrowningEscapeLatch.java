package adris.altoclef.chains;

/**
 * Ostinato: S309. Whether {@link WorldSurvivalChain} keeps owning the drowning escape.
 *
 * <p>The chain used to return priority 100 only while the eyes were underwater and air was below two thirds.
 * The moment a bob lifted the eyes, priority dropped, {@code GetOutOfWaterTask} was stopped, and
 * {@code DestroyBlockTask} pathed back into the same pond. testrun-20261103-094642 did that from 09:49 until
 * the run was killed, with the same ~150 block path recalculated from a fixed position and no dry confirm
 * after the first pond. Once triggered, the escape stays owned until the bot is dry and grounded, or until
 * it makes no horizontal progress.
 *
 * <p>No Minecraft types. Tick-tested in {@code DrowningEscapeLatchTest}.
 */
public final class DrowningEscapeLatch {

    /** Same dry-and-grounded hold as {@code GetOutOfWaterTask}. */
    public static final long DRY_CONFIRM_MS = 750L;
    /** No 3-block progress for this long: the swim is not working. */
    public static final long STUCK_MS = 12_000L;
    /** Hard cap so a crawl across open water cannot own the chain forever. */
    public static final long TIMEOUT_MS = 45_000L;
    /** After a give-up, ignore a fresh trigger so the user task can pick another block. */
    public static final long COOLDOWN_MS = 8_000L;
    /** Squared block distance that counts as progress (3 blocks). */
    public static final int PROGRESS_DIST_SQ = 9;

    public enum Decision {
        /** Not escaping. User tasks may run. */
        IDLE,
        /** Keep {@code GetOutOfWaterTask} at survival priority. */
        HOLD,
        /** Dry and grounded long enough. Hand control back. */
        DONE_DRY,
        /** No progress or timed out. Hand control back and cool down. */
        GIVE_UP
    }

    private boolean latched;
    private long sinceMs;
    private long drySinceMs;
    private long lastProgressMs;
    private long cooldownUntilMs;
    private int anchorX;
    private int anchorY;
    private int anchorZ;

    /**
     * @param trigger      submerged with air low enough that the old chain would have interrupted
     * @param dryGrounded  not touching water and on the ground
     * @param nowMs        wall clock, so extra polls in one tick do not speed the timer up
     */
    public Decision tick(boolean trigger, boolean dryGrounded, int x, int y, int z, long nowMs) {
        if (!latched) {
            if (nowMs < cooldownUntilMs || !trigger) return Decision.IDLE;
            latched = true;
            sinceMs = nowMs;
            drySinceMs = 0;
            lastProgressMs = nowMs;
            anchorX = x;
            anchorY = y;
            anchorZ = z;
            return Decision.HOLD;
        }
        if (dryGrounded) {
            if (drySinceMs == 0) drySinceMs = nowMs;
            if (nowMs - drySinceMs >= DRY_CONFIRM_MS) {
                release();
                return Decision.DONE_DRY;
            }
        } else {
            drySinceMs = 0;
        }
        int dx = x - anchorX;
        int dy = y - anchorY;
        int dz = z - anchorZ;
        if (dx * dx + dy * dy + dz * dz >= PROGRESS_DIST_SQ) {
            anchorX = x;
            anchorY = y;
            anchorZ = z;
            lastProgressMs = nowMs;
        }
        if (nowMs - lastProgressMs >= STUCK_MS || nowMs - sinceMs >= TIMEOUT_MS) {
            release();
            cooldownUntilMs = nowMs + COOLDOWN_MS;
            return Decision.GIVE_UP;
        }
        return Decision.HOLD;
    }

    public boolean isLatched() {
        return latched;
    }

    private void release() {
        latched = false;
        drySinceMs = 0;
    }
}
