package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.benchmark.FugueBench;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;

/**
 * {@code @fuguebench <stock|fugue|fuguer> [empty:3,partial:1,full:1,steal:1,cancel:1]} live A/B trials (1.16.1 only).
 */
public class FugueBenchCommand extends Command {

    public FugueBenchCommand() {
        super("fuguebench", "Fugue A/B benchmark: <stock|fugue|fuguer> [scenario:reps,...]",
                new StringArg("arm", "fugue"),
                new StringArg("plan", "-"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String msg = FugueBench.start(parser.get(String.class), parser.get(String.class));
        Debug.logHarness(msg);
        mod.log(msg);
        finish();
    }
}
