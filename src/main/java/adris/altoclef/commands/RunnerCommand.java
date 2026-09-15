package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.tasks.manhunt.RunnerManhuntTask;

/**
 * @runner – Play as the speedrunner in a manhunt.
 *
 * Runs a modern speedrun route while remaining combat-capable.
 * If a hunter approaches or attacks, the runner will fight back
 * (or kite) instead of purely fleeing.
 */
public class RunnerCommand extends Command {

    public RunnerCommand() {
        super("runner", "Manhunt runner: speedrun the game and fight back if hunted");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        mod.runUserTask(new RunnerManhuntTask(mod), this::finish);
    }
}
