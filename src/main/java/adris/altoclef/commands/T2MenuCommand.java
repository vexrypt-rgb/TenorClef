package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.tasks.speedrun.testrun2.gui.T2MenuScreen;

/** Chat: @t2menu */
public class T2MenuCommand extends Command {

    public T2MenuCommand() {
        super("t2menu", "Open the TenorClef menu");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        T2MenuScreen.open();
        finish();
    }
}
