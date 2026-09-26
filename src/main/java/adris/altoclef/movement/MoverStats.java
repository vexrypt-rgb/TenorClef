package adris.altoclef.movement;

import adris.altoclef.Debug;

/**
 * Live seconds-per-block for long legs, Tungsten (search + travel) vs Baritone, measured the
 * same way: from leg start until within 16 blocks of the target. GetToBlockTask asks
 * {@link #preferTungsten()} per long leg so Tungsten is used only while it is actually faster.
 */
public final class MoverStats {

    private static final double ALPHA = 0.3;
    private static final int EXPLORE_EVERY = 5;
    private static double tungSpb = -1, barSpb = -1;
    private static int legs;

    private MoverStats() {}

    /** Picks the mover for the next long leg. Untried movers go first; every 5th leg tries the slower one. */
    public static synchronized boolean preferTungsten() {
        legs++;
        if (tungSpb < 0) return true;
        if (barSpb < 0) return false;
        boolean tungFaster = tungSpb <= barSpb;
        return legs % EXPLORE_EVERY == 0 ? !tungFaster : tungFaster;
    }

    public static synchronized void record(boolean tungsten, double seconds, double blocks, boolean gaveUp) {
        if (blocks < 4) return;
        double spb = seconds / blocks;
        if (tungsten) tungSpb = tungSpb < 0 ? spb : tungSpb + ALPHA * (spb - tungSpb);
        else barSpb = barSpb < 0 ? spb : barSpb + ALPHA * (spb - barSpb);
        Debug.logMessage(String.format(java.util.Locale.ROOT,
                "MOVER leg %s %.1fs/%.0fb%s -> tung=%.3f bar=%.3f s/b", tungsten ? "tungsten" : "baritone",
                seconds, blocks, gaveUp ? " (gave up)" : "", tungSpb, barSpb));
    }
}
