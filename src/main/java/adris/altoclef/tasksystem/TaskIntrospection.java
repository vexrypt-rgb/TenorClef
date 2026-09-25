package adris.altoclef.tasksystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only formatting of live task-chain state for @task / @tasktree / @why.
 * <p>
 * Pure (no Minecraft access) so it is unit-testable; commands feed it
 * {@link ChainView}s built from {@link TaskRunner}. It never mutates tasks.
 */
public final class TaskIntrospection {

    public static final String USER_CHAIN = "User Tasks";

    private TaskIntrospection() {
    }

    /** Snapshot of one chain. {@code tasks} is root-first (index i+1 is the sub of i). */
    public static final class ChainView {
        public final String name;
        public final float priority;
        public final boolean active;
        public final List<Task> tasks;

        public ChainView(String name, float priority, boolean active, List<Task> tasks) {
            this.name = name;
            this.priority = priority;
            this.active = active;
            this.tasks = tasks != null ? tasks : List.of();
        }
    }

    public static ChainView of(TaskChain chain) {
        return new ChainView(chain.getName(), chain.getPriority(), chain.isActive(), new ArrayList<>(chain.getTasks()));
    }

    static String elapsed(Task t, long now) {
        if (t.getStartMillis() <= 0) return "?";
        return String.format(Locale.ROOT, "%.1fs", (now - t.getStartMillis()) / 1000.0);
    }

    static String line(Task t, long now) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getSimpleName()).append(" [").append(t.getLastResult()).append("] ").append(elapsed(t, now));
        String dbg = t.getDebugState();
        if (dbg != null && !dbg.isBlank()) sb.append(" \"").append(dbg).append('"');
        TaskFailure f = t.getLastFailure();
        if (f != null) {
            sb.append(" fail=").append(f.getReason());
            RecoveryDecision r = t.getLastRecovery();
            if (r != null) sb.append(" recovery=").append(r.getAction());
        }
        return sb.toString();
    }

    /** Live task hierarchy of one chain, root first. */
    public static List<String> tree(ChainView chain, long now) {
        List<String> out = new ArrayList<>();
        out.add(chain.name + " (pri " + chain.priority + ")");
        for (int i = 0; i < chain.tasks.size(); i++) {
            out.add("  ".repeat(i) + "└─ " + line(chain.tasks.get(i), now));
        }
        if (chain.tasks.isEmpty()) out.add("  (no tasks)");
        return out;
    }

    /** Short "what are you doing" view: root, leaf, depth, result, deepest failure. */
    public static List<String> summary(ChainView running, long now) {
        List<String> out = new ArrayList<>();
        if (running == null) {
            out.add("No chain running.");
            return out;
        }
        out.add("Chain: " + running.name + " (pri " + running.priority + ")");
        if (running.tasks.isEmpty()) {
            out.add("No tasks.");
            return out;
        }
        Task root = running.tasks.get(0);
        Task leaf = running.tasks.get(running.tasks.size() - 1);
        out.add("Goal: " + line(root, now));
        if (leaf != root) out.add("Current: " + line(leaf, now) + " (depth " + (running.tasks.size() - 1) + ")");
        Task failed = deepestFailure(running.tasks);
        if (failed != null) {
            out.add("Last failure: " + failed.getClass().getSimpleName() + " " + failed.getLastFailure());
            if (failed.getLastRecovery() != null) out.add("Recovery: " + failed.getLastRecovery());
        }
        return out;
    }

    static Task deepestFailure(List<Task> tasks) {
        for (int i = tasks.size() - 1; i >= 0; i--) {
            if (tasks.get(i).getLastFailure() != null) return tasks.get(i);
        }
        return null;
    }

    /**
     * Causal explanation: which chain holds control and why the user goal
     * is (or is not) progressing. {@code user} may be null.
     */
    public static List<String> why(ChainView running, ChainView user, long now) {
        List<String> out = new ArrayList<>();
        if (running == null) {
            out.add("Nothing is running (runner inactive or no chain active).");
            return out;
        }
        boolean preempted = user != null && !user.tasks.isEmpty() && !USER_CHAIN.equals(running.name);
        if (preempted) {
            out.add("Goal: " + user.tasks.get(0).getClass().getSimpleName() + " is PAUSED.");
            out.add("Because: chain '" + running.name + "' (pri " + running.priority
                    + ") outranks User Tasks (pri " + user.priority + ").");
            Task userLeaf = user.tasks.get(user.tasks.size() - 1);
            out.add("User goal was last at: " + line(userLeaf, now));
        } else if (user == null || user.tasks.isEmpty()) {
            out.add("No user goal; running chain '" + running.name + "'.");
        } else {
            out.add("Goal: " + user.tasks.get(0).getClass().getSimpleName() + " is in control.");
        }
        if (running.tasks.isEmpty()) {
            out.add("Running chain has no task (acting directly).");
            return out;
        }
        Task leaf = running.tasks.get(running.tasks.size() - 1);
        out.add("Doing: " + line(leaf, now));
        Task failed = deepestFailure(running.tasks);
        if (failed != null) {
            TaskFailure f = failed.getLastFailure();
            out.add("Blocked by: " + f.getReason() + " in " + failed.getClass().getSimpleName()
                    + (f.getMessage() != null ? " — " + f.getMessage() : ""));
            RecoveryDecision r = failed.getLastRecovery();
            out.add("Recovery: " + (r != null ? r.toString() : "none selected"));
        } else {
            out.add("No recorded failure (most tasks do not report structured results yet).");
        }
        return out;
    }
}
