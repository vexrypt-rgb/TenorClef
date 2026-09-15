package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.ResetSignal;

/** `@t2reset` — leave this singleplayer world so Atum / glue can roll the next seed. */
public class T2ResetCommand extends Command {

    public T2ResetCommand() {
        super("t2reset", "Abort this world and signal a singleplayer reset");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        Debug.logWarning("TESRUN2 manual reset");
        ResetSignal.fire("manual @t2reset");
        finish();
    }
}
