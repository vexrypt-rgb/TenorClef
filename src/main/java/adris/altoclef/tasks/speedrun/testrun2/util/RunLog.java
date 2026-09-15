package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.tasks.speedrun.testrun2.T2History;

public final class RunLog {

    public static final String FILE = "last_run.log";

    private RunLog() {}

    public static void line(String s) {
        GameFiles.append(FILE, System.currentTimeMillis() + " " + s);
    }

    public static void dumpHistory() {
        try {
            String hist = T2History.dump();
            GameFiles.append(FILE, "--- HIST ---\n" + hist);
        } catch (Throwable t) {
            GameFiles.append(FILE, "hist unavailable");
        }
    }
}
