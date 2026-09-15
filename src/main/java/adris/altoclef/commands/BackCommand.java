package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.util.DeathWatch;
import adris.altoclef.tasks.speedrun.testrun2.util.GoHomeTask;
import adris.altoclef.tasks.speedrun.testrun2.util.HomeStore;

public class BackCommand extends Command {

    public BackCommand() {
        super("back", "Walk to last death pos (altoclef/last_death.txt)");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        HomeStore d = DeathWatch.lastDeath();
        if (d == null) {
            Debug.logWarning("No last_death.txt");
            finish();
            return;
        }
        Debug.logMessage("BACK to death point");
        mod.runUserTask(new GoHomeTask(d), this::finish);
    }
}
