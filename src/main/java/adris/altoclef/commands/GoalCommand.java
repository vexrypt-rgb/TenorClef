package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.IntArg;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.planner.AcquireItemGoal;
import adris.altoclef.planner.CatalogueInventoryView;
import adris.altoclef.planner.GoalManager;
import adris.altoclef.planner.Plan;
import adris.altoclef.planner.PlanRunnerTask;
import adris.altoclef.planner.PlanStep;

/**
 * Debug / demo hook for Phase 7 planner. Does not replace {@code @get}.
 * <pre>
 * {@code @goal cobblestone 64}
 * {@code @goal status}
 * </pre>
 */
public class GoalCommand extends Command {

    private static GoalManager activeManager;

    public GoalCommand() {
        super("goal", "Phase 7 planner demo: acquire catalogue item via PlanExecutor",
                new StringArg("item", "status"),
                new IntArg("count", 1));
    }

    public static GoalManager getActiveManager() {
        return activeManager;
    }

    /** Used by Phase 9 AgentProtocol / AltoClefAgentRuntime. */
    public static void setActiveManager(GoalManager manager) {
        activeManager = manager;
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String item = parser.get(String.class);
        int count = parser.get(Integer.class);
        if (item == null || item.isBlank()) {
            item = "status";
        }
        item = item.trim();

        if (item.equalsIgnoreCase("status")
                || item.equals("?")
                || item.equalsIgnoreCase("help")) {
            dumpStatus(mod);
            finish();
            return;
        }

        // Optional verb prefix leftover: treat "acquire" alone as usage hint
        if (item.equalsIgnoreCase("acquire") || item.equalsIgnoreCase("get")) {
            mod.log("Usage: @goal <catalogueItem> [count]   e.g. @goal cobblestone 64");
            finish();
            return;
        }

        startAcquire(mod, item, count);
    }

    private void dumpStatus(AltoClef mod) {
        if (activeManager != null) {
            mod.log("Goal: " + activeManager.summarize());
            Plan plan = activeManager.getPlan();
            if (plan != null) {
                for (PlanStep s : plan.getSteps()) {
                    mod.log("  step: " + s);
                }
            }
        } else {
            mod.log("No active goal. Usage: @goal <catalogueItem> [count]");
        }
    }

    private void startAcquire(AltoClef mod, String item, int count) {
        if (!TaskCatalogue.taskExists(item)) {
            Debug.logWarning("Goal: catalogue has no resource '" + item + "'");
            finish();
            return;
        }
        if (count < 1) {
            count = 1;
        }

        AcquireItemGoal goal = new AcquireItemGoal(item, count);
        GoalManager manager = new GoalManager();
        CatalogueInventoryView inv = new CatalogueInventoryView(mod);
        manager.start(goal, inv);
        activeManager = manager;

        if (manager.getStatus().isTerminal()) {
            mod.log("Goal immediate " + manager.getStatus() + ": " + manager.getLastNote());
            finish();
            return;
        }

        Plan plan = manager.getPlan();
        mod.log("Goal started: " + goal.getDescription()
                + " planSteps=" + (plan != null ? plan.size() : 0));
        if (plan != null) {
            for (PlanStep s : plan.getSteps()) {
                mod.log("  → " + s.getLabel());
            }
        }

        mod.runUserTask(new PlanRunnerTask(manager), this::finish);
    }
}
