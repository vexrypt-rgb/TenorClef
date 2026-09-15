package adris.altoclef.tasks.speedrun.testrun2.util;

public final class AgentGoal {

    private static String goal = "";

    private AgentGoal() {}

    public static void set(String g) {
        goal = g == null ? "" : g.trim();
        GameFiles.write("agent_goal.txt", goal + "\n");
    }

    public static String get() {
        if (!goal.isEmpty()) return goal;
        String f = GameFiles.read("agent_goal.txt");
        return f == null ? "" : f.trim();
    }
}
