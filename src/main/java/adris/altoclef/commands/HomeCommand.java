package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.util.GameFiles;
import adris.altoclef.tasks.speedrun.testrun2.util.GoHomeTask;
import adris.altoclef.tasks.speedrun.testrun2.util.HomeStore;

import java.util.List;

/** {@code @home} walk to saved point. {@code @sethome} save feet. */
public class HomeCommand extends Command {

    public HomeCommand() {
        super(List.of("home", "t2home"), "Walk to the point saved by @sethome");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        HomeStore h = HomeStore.load();
        if (h == null) {
            Debug.logWarning("No home set. Stand where you want it and type @sethome");
            finish();
            return;
        }
        Debug.logMessage("HOME go " + h.dim + " " + h.x + " " + h.y + " " + h.z);
        mod.runUserTask(new GoHomeTask(h), this::finish);
    }
}
