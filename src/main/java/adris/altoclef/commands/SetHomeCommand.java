package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.util.GameFiles;
import adris.altoclef.tasks.speedrun.testrun2.util.HomeStore;

import java.util.List;

/** {@code @sethome} — always saves the block under your feet. No extra words. */
public class SetHomeCommand extends Command {

    public SetHomeCommand() {
        super(List.of("sethome", "set_home"), "Save current feet as home (altoclef/home.txt)");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        if (mod.getPlayer() == null) {
            Debug.logWarning("HOME not saved — no player");
            finish();
            return;
        }
        HomeStore.saveHere(mod);
        HomeStore h = HomeStore.load();
        if (h == null) {
            Debug.logWarning("HOME write failed at " + GameFiles.dir().resolve(HomeStore.FILE).toAbsolutePath());
        } else {
            Debug.logMessage("HOME set " + h.dim + " " + h.x + " " + h.y + " " + h.z);
            Debug.logMessage("HOME file " + GameFiles.dir().resolve(HomeStore.FILE).toAbsolutePath());
        }
        finish();
    }
}
