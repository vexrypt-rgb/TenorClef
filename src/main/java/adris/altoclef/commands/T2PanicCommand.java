package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasks.speedrun.testrun2.util.RunLog;

/** Stop keys, close GUI, dump history. Does not start another task. */
public class T2PanicCommand extends Command {

    public T2PanicCommand() {
        super("t2panic", "Release keys, close screen, write last_run.log");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        McCompat.closeScreen();
        McCompat.cancelPathing();
        McCompat.setMove(false, false);
        try {
            mod.getUserTaskChain().cancel(mod);
        } catch (Throwable ignored) {}
        RunLog.dumpHistory();
        Debug.logMessage("T2PANIC keys up, path cancelled, last_run.log written");
        finish();
    }
}
