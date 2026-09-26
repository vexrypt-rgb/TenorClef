package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.benchmark.PathBench;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.IntArg;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;

/**
 * {@code @pathbench search [- | setting=v1,v2] [reps]}  Baritone A* only, CSV + summary.
 * {@code @pathbench travel [baritone | tungsten] [reps]}  end-to-end movement trials.
 */
public class PathBenchCommand extends Command {

    public PathBenchCommand() throws CommandException {
        super("pathbench", "Pathfinding benchmark: search [-|setting=a,b] [reps] | travel [baritone|tungsten] [reps]",
                new StringArg("mode", "search"),
                new StringArg("opt", "-"),
                new IntArg("reps", 3));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String mode = parser.get(String.class);
        String opt = parser.get(String.class);
        int reps = parser.get(Integer.class);
        String msg = PathBench.start(mode, opt, reps);
        Debug.logHarness(msg);
        mod.log(msg);
        finish();
    }
}
