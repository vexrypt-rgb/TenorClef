package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.core.T2CoreTask;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Reserve;

/**
 * {@code @t2core [demo|tools|food|cobble|help]}
 * Isolated. Does not change other commands.
 */
public class T2CoreCommand extends Command {

    public T2CoreCommand() {
        super("t2core", "Test sticky-child + reserve + progress stall + input arbiter",
                new StringArg("mode", "demo"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String raw = "demo";
        try {
            raw = parser.get(String.class);
        } catch (Throwable ignored) {}
        if (raw == null || raw.isBlank()) raw = "demo";
        String mode = raw.trim().toLowerCase();

        if (mode.equals("help") || mode.equals("?")) {
            Debug.logMessage("T2CORE isolated. @testrun2 @aa @schem @get unchanged.");
            Debug.logMessage("  @t2core        food -> pick -> table -> cobble-to-48 -> hold");
            Debug.logMessage("  @t2core demo   same");
            Debug.logMessage("  @t2core tools  pick only");
            Debug.logMessage("  @t2core food   bread if hungry");
            Debug.logMessage("  @t2core cobble skip food, pick + cobble");
            Debug.logMessage("floor cobble=" + T2Reserve.COBBLE_MIN + " fill=" + T2Reserve.COBBLE_FILL);
            Debug.logMessage("C210 hole drop without spending reserve  C211 cobble stale -> wander");
            finish();
            return;
        }

        T2CoreTask.Mode m = switch (mode) {
            case "tools", "pick" -> T2CoreTask.Mode.TOOLS;
            case "food", "bread" -> T2CoreTask.Mode.FOOD;
            case "cobble", "stone" -> T2CoreTask.Mode.COBBLE;
            default -> T2CoreTask.Mode.DEMO;
        };

        Debug.logMessage("T2CORE mode=" + m + " — @stop to end.");
        mod.runUserTask(new T2CoreTask(m), this::finish);
    }
}
