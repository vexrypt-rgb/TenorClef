package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.benchmark.BenchmarkFiles;
import adris.altoclef.benchmark.BenchmarkResult;
import adris.altoclef.benchmark.LiveBenchmarkSession;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.planner.AcquireItemGoal;
import adris.altoclef.planner.CatalogueInventoryView;
import adris.altoclef.planner.GoalManager;
import adris.altoclef.planner.GoalStatus;
import adris.altoclef.planner.Plan;
import adris.altoclef.planner.PlanRunnerTask;
import adris.altoclef.planner.PlanStep;

/**
 * Live benchmark session control (post-phase-10).
 * <pre>
 * {@code @bench start <name>}
 * {@code @bench stop}
 * {@code @bench status}
 * {@code @bench goal <item> [count]}
 * </pre>
 * Inactive session is a null singleton — hooks are no-ops.
 */
public class BenchCommand extends Command {

    public BenchCommand() {
        super("bench", "Live benchmark session: start/stop/status/goal",
                new StringArg("args", "status"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String raw = "status";
        try {
            raw = parser.get(String.class);
        } catch (Throwable ignored) {
        }
        if (raw == null || raw.isBlank()) {
            raw = "status";
        }
        String[] parts = raw.trim().split("\\s+");
        String verb = parts[0].toLowerCase();
        switch (verb) {
            case "start" -> doStart(mod, parts);
            case "stop" -> doStop(mod);
            case "status" -> doStatus(mod);
            case "help", "?" -> {
                mod.log("BENCH: @bench start <name> | stop | status | goal <item> [count]");
                finish();
            }
            case "goal" -> doGoal(mod, parts);
            default -> {
                mod.log("BENCH unknown verb '" + verb + "'. Use start|stop|status|goal");
                finish();
            }
        }
    }

    private void doStart(AltoClef mod, String[] parts) {
        if (parts.length < 2 || parts[1].isBlank()) {
            mod.log("Usage: @bench start <name>");
            finish();
            return;
        }
        LiveBenchmarkSession s = LiveBenchmarkSession.start(parts[1]);
        mod.log("BENCH started: " + parts[1] + " outDir=" + BenchmarkFiles.dir().toAbsolutePath());
        mod.log("  " + s.statusLine());
        finish();
    }

    private void doStop(AltoClef mod) {
        LiveBenchmarkSession s = LiveBenchmarkSession.stop();
        if (s == null) {
            mod.log("BENCH: no active session");
        } else {
            mod.log("BENCH stopped: " + s.statusLine());
            if (s.getLastExportPath() != null) {
                mod.log("  wrote " + s.getLastExportPath().toAbsolutePath());
            }
        }
        finish();
    }

    private void doStatus(AltoClef mod) {
        LiveBenchmarkSession s = LiveBenchmarkSession.current();
        if (s == null) {
            mod.log("BENCH: inactive (null session — gameplay unaffected)");
            mod.log("  outDir=" + BenchmarkFiles.dir().toAbsolutePath());
        } else {
            mod.log("BENCH: " + s.statusLine());
        }
        finish();
    }

    private void doGoal(AltoClef mod, String[] parts) {
        if (parts.length < 2 || parts[1].isBlank()) {
            mod.log("Usage: @bench goal <catalogueItem> [count]");
            finish();
            return;
        }
        String item = parts[1];
        int count = 1;
        if (parts.length >= 3) {
            try {
                count = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                mod.log("BENCH: bad count '" + parts[2] + "'");
                finish();
                return;
            }
        }
        if (!TaskCatalogue.taskExists(item)) {
            Debug.logWarning("BENCH goal: catalogue has no resource '" + item + "'");
            finish();
            return;
        }
        if (count < 1) {
            count = 1;
        }

        String runName = "goal-" + item + "x" + count;
        LiveBenchmarkSession.start(runName);
        mod.log("BENCH goal session=" + runName);

        AcquireItemGoal goal = new AcquireItemGoal(item, count);
        GoalManager manager = new GoalManager();
        CatalogueInventoryView inv = new CatalogueInventoryView(mod);
        manager.start(goal, inv);
        GoalCommand.setActiveManager(manager);

        if (manager.getStatus().isTerminal()) {
            boolean ok = manager.getStatus() == GoalStatus.SUCCESS;
            LiveBenchmarkSession.stop(ok, "immediate " + manager.getStatus() + ": " + manager.getLastNote());
            mod.log("BENCH goal immediate " + manager.getStatus() + ": " + manager.getLastNote());
            finish();
            return;
        }

        Plan plan = manager.getPlan();
        mod.log("BENCH goal started: " + goal.getDescription()
                + " planSteps=" + (plan != null ? plan.size() : 0));
        if (plan != null) {
            for (PlanStep s : plan.getSteps()) {
                mod.log("  → " + s.getLabel());
            }
        }

        mod.runUserTask(new PlanRunnerTask(manager), () -> {
            GoalStatus st = manager.getStatus();
            boolean ok = st == GoalStatus.SUCCESS;
            LiveBenchmarkSession stopped = LiveBenchmarkSession.stop(
                    ok, "goal " + st + ": " + manager.getLastNote());
            if (stopped != null) {
                BenchmarkResult r = stopped.toResult();
                mod.log("BENCH goal finished success=" + r.isSuccess()
                        + " durationMs=" + r.getDurationMs()
                        + (stopped.getLastExportPath() != null
                        ? " out=" + stopped.getLastExportPath() : ""));
            }
            finish();
        });
    }
}
