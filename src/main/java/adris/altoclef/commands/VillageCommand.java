package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.util.VillageFirstTask;

public class VillageCommand extends Command {

    public VillageCommand() {
        super("village", "Path to the nearest villager");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        mod.runUserTask(new VillageFirstTask(), this::finish);
    }
}
