package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.IntArg;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.fugue.FugueCoordinator;
import adris.altoclef.fugue.FugueNode;
import adris.altoclef.fugue.FuguePlan;
import adris.altoclef.fugue.FugueRecipeBook;
import adris.altoclef.fugue.FugueTask;

/**
 * Opt-in entry point for Fugue v1. {@code @get} is unchanged; Fugue only runs when asked for here.
 * <pre>
 * {@code @fugue iron_pickaxe}      acquire with dependency-aware scheduling
 * {@code @fugue status}            current / last objective, plan and metrics
 * </pre>
 */
public class FugueCommand extends Command {

    public FugueCommand() {
        super("fugue", "Fugue v1 (opt-in): dependency-aware acquisition of early tools. @fugue <item> [count] | status",
                new StringArg("item", "status"),
                new IntArg("count", 1));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String item = parser.get(String.class);
        int count = parser.get(Integer.class);
        item = item == null ? "status" : item.trim();
        if (item.isEmpty() || item.equalsIgnoreCase("status") || item.equals("?")) {
            status(mod);
            finish();
            return;
        }
        if (!FugueTask.supports(item)) {
            mod.log("Fugue v1 supports only: " + String.join(", ", FugueRecipeBook.ironTools().keys()) + ". Use @get for anything else.");
            finish();
            return;
        }
        // v1.1 stall recovery stays off unless the client is started with -Dtenorclef.fugue.recovery=true.
        mod.runUserTask(FugueTask.live(item, Math.max(1, count), Boolean.getBoolean("tenorclef.fugue.recovery")), this::finish);
    }

    private static void status(AltoClef mod) {
        FugueTask t = FugueTask.getLastStarted();
        if (t == null || t.getCoordinator() == null) {
            mod.log("Fugue: no objective started. Usage: @fugue <item> [count]");
            return;
        }
        FugueCoordinator c = t.getCoordinator();
        mod.log("Fugue " + t.getGoal() + " x" + t.getCount() + ": " + c.getStatus() + " task=" + t.getLastResult()
                + (c.getFailure() != null ? " (" + c.getFailure() + ")" : "")
                + (c.getCurrentKey() != null ? " running " + c.getCurrentKey() + "->" + c.getCurrentTarget() : ""));
        FuguePlan plan = c.getLastPlan();
        if (plan != null) {
            for (FugueNode n : plan.getNodes().values()) mod.log("  node " + n);
        }
        mod.log("  " + c.metrics() + " interrupts=" + t.getInterrupts());
    }
}
