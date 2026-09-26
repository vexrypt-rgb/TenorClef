package adris.altoclef.util;

/**
 * Sim time warp: runs the integrated server AND the client tick clock N times faster so a
 * singleplayer test run plays out in 1/N of the wall-clock time.
 *
 * Both clocks must move together. Scaling only the server leaves the bot (client-side
 * physics and input) at 20 TPS; scaling only the client makes block breaking and furnaces
 * lag behind the bot. 1.16.1 has no /tick command, so ServerTickWarpMixin and
 * ClientTimerWarpMixin do it by hand. On 1.20.3+ WarpCommand uses vanilla /tick rate.
 *
 * Singleplayer only. On a real server the client timer would read as speed hacking.
 * Wall-clock timers (TimerReal, keepalive, T2Deadman) are NOT scaled: they get more game
 * time per real second, so they become more lenient, never stricter.
 */
public final class WarpClock {

    public static final float MAX = 20f;

    // Read every server tick and every frame, so keep it volatile and cheap.
    private static volatile float factor = initialFactor();

    private WarpClock() {}

    public static float get() {
        return factor;
    }

    public static boolean active() {
        return factor != 1f;
    }

    public static void set(float f) {
        if (!(f >= 1f)) f = 1f; // also catches NaN
        factor = Math.min(f, MAX);
    }

    /** Server: vanilla 50ms tick budget becomes 50/N ms. */
    public static long scaleMillis(long ms) {
        float f = factor;
        return f == 1f ? ms : Math.max(1L, (long) (ms / f));
    }

    /** Client: vanilla ms-per-tick (50) becomes 50/N. */
    public static float scaleTickTime(float ms) {
        float f = factor;
        return f == 1f ? ms : ms / f;
    }

    // -Dtenorclef.warp=5 lets the sim loop start warped without typing a command.
    private static float initialFactor() {
        try {
            String p = System.getProperty("tenorclef.warp");
            if (p != null) {
                float f = Float.parseFloat(p.trim());
                if (f >= 1f) return Math.min(f, MAX);
            }
        } catch (Throwable ignored) {}
        return 1f;
    }
}
