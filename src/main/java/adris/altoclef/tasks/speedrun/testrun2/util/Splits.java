package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.tasks.speedrun.testrun2.SpeedrunClock;

import java.util.ArrayList;
import java.util.List;

public final class Splits {

    public static final String FILE = "splits.txt";

    private static final List<String> LINES = new ArrayList<>();

    private Splits() {}

    public static void reset() {
        LINES.clear();
    }

    public static void mark(String name) {
        String row = SpeedrunClock.now() + "  " + name;
        LINES.add(row);
        GameFiles.append(FILE, row);
        RunLog.line("split " + name);
    }

    public static List<String> hudLines() {
        List<String> out = new ArrayList<>();
        out.add("Segno  " + SpeedrunClock.now());
        int from = Math.max(0, LINES.size() - 8);
        for (int i = from; i < LINES.size(); i++) {
            out.add(LINES.get(i));
        }
        return out;
    }
}
