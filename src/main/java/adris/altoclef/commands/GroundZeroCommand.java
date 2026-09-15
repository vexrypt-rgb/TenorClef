package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.combat.GroundZeroTask;

import java.util.List;

public class GroundZeroCommand extends Command {

    public GroundZeroCommand() {
        super(List.of("groundzero", "gz"), "Fountain zero-cycle: cage + crystal shot + beds");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        Debug.logMessage("GROUNDZERO — fountain cage, punch a crystal, then beds");
        mod.runUserTask(new GroundZeroTask(), this::finish);
    }
}
