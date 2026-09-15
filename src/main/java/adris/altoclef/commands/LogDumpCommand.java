package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.util.GameFiles;
import adris.altoclef.tasks.speedrun.testrun2.util.RunLog;

public class LogDumpCommand extends Command {

    public LogDumpCommand() {
        super("logdump", "Write T2 history to altoclef/last_run.log");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        RunLog.dumpHistory();
        Debug.logMessage("LOG " + GameFiles.dir().resolve(RunLog.FILE).toAbsolutePath());
        finish();
    }
}
