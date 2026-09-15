package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.ModernSpeedrunTask;

/** {@code @testrun2} modern RSG. Keep this file in commands/ only. */
public class Testrun2Command extends Command {

    public Testrun2Command() {
        super("testrun2", "Modern RSG speedrun (TenorClef)");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        Debug.logMessage("TESRUN2 start — @stop to cancel");
        mod.runUserTask(new ModernSpeedrunTask(), this::finish);
    }
}
