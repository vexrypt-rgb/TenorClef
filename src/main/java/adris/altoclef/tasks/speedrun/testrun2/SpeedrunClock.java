package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.Debug;

import java.util.LinkedHashMap;
import java.util.Map;

/** Wall-clock splits so you can see which phase ate the run. */
public final class SpeedrunClock {

    private static long start;
    private static final Map<String, Long> splits = new LinkedHashMap<>();

    private SpeedrunClock() {}

    public static void reset() {
        start = System.currentTimeMillis();
        splits.clear();
        Debug.logMessage("TESRUN2 clock 0:00.0");
    }

    public static void split(String name) {
        if (start == 0) reset();
        long t = System.currentTimeMillis() - start;
        splits.put(name, t);
        Debug.logMessage("TESRUN2 split " + name + "  " + fmt(t));
    }

    public static String now() {
        if (start == 0) return "0:00.0";
        return fmt(System.currentTimeMillis() - start);
    }

    public static long millis() {
        return start == 0 ? 0 : System.currentTimeMillis() - start;
    }

    public static String dump() {
        StringBuilder sb = new StringBuilder("TESRUN2 splits");
        long prev = 0;
        for (var e : splits.entrySet()) {
            sb.append(" | ").append(e.getKey()).append(' ').append(fmt(e.getValue() - prev));
            prev = e.getValue();
        }
        sb.append(" | total ").append(now());
        return sb.toString();
    }

    private static String fmt(long ms) {
        long min = ms / 60_000;
        long sec = (ms / 1000) % 60;
        long tenths = (ms / 100) % 10;
        return String.format("%d:%02d.%d", min, sec, tenths);
    }
}
