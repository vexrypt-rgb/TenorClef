package adris.altoclef.tasks.speedrun.testrun2.agent;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.speedrun.testrun2.AllAdvancementsTask;
import adris.altoclef.tasks.speedrun.testrun2.ModernSpeedrunTask;
import adris.altoclef.tasks.speedrun.testrun2.catalog.ExtraCatalogue;
import adris.altoclef.tasks.speedrun.testrun2.core.T2CoreTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.Set;

/**
 * Whitelist only. The model cannot invent Java or Baritone # commands.
 */
public final class AgentActions {

    private static final Set<String> ALLOW = Set.of(
            "get", "xget", "food", "goto", "wait", "say", "idle", "stop",
            "testrun2", "aa", "t2core", "equip"
    );

    private AgentActions() {}

    public static boolean allowed(String verb) {
        return ALLOW.contains(verb.toLowerCase(Locale.ROOT));
    }

    /**
     * @return a child task, or null if the action is instant / stop.
     * {@code STOP} sentinel: caller should finish the loop.
     */
    public static Task apply(AltoClef mod, String line) {
        if (line == null || line.isBlank()) return null;
        String raw = line.trim();
        if (raw.startsWith("@")) raw = raw.substring(1).trim();
        String[] parts = raw.split("\\s+");
        String verb = parts[0].toLowerCase(Locale.ROOT);
        if (!allowed(verb)) {
            Debug.logWarning("AGENT deny: " + verb);
            AgentFiles.log("DENY " + verb);
            return null;
        }
        AgentFiles.log("DO " + raw);
        Debug.logMessage("AGENT " + raw);

        switch (verb) {
            case "stop" -> {
                return StopSentinel.INSTANCE;
            }
            case "say" -> {
                String msg = raw.length() > 4 ? raw.substring(4) : "";
                Debug.logMessage("AGENT say: " + msg);
                return null;
            }
            case "idle", "wait" -> {
                return null;
            }
            case "food" -> {
                return ExtraCatalogue.get("food", 8);
            }
            case "get", "xget" -> {
                String item = parts.length > 1 ? parts[1] : "bread";
                int n = 1;
                if (parts.length > 2) {
                    try { n = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {}
                }
                Task t = ExtraCatalogue.get(item, n);
                if (t != null) return t;
                try { return TaskCatalogue.getItemTask(item, n); } catch (Throwable ignored) {}
                return null;
            }
            case "goto" -> {
                if (parts.length < 4) return null;
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    return new GetToBlockTask(new BlockPos(x, y, z));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case "testrun2" -> {
                return new ModernSpeedrunTask();
            }
            case "aa" -> {
                return new AllAdvancementsTask();
            }
            case "t2core" -> {
                return new T2CoreTask();
            }
            case "equip" -> {
                return ExtraCatalogue.get(parts.length > 1 ? parts[1] : "iron_chestplate", 1);
            }
            default -> {
                return null;
            }
        }
    }

    public static final class StopSentinel extends Task {
        static final StopSentinel INSTANCE = new StopSentinel();
        @Override protected void onStart() {}
        @Override protected Task onTick() { return null; }
        @Override protected void onStop(Task i) {}
        @Override protected boolean isEqual(Task o) { return o instanceof StopSentinel; }
        @Override protected String toDebugString() { return "agent-stop"; }
        @Override public boolean isFinished() { return true; }
    }
}
