package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.gui.T2MenuScreen;

import java.util.List;

/** {@code @t2menu} or {@code @menu} — button panel for personal commands. */
public class T2MenuCommand extends Command {

    public T2MenuCommand() {
        super(List.of("t2menu", "menu"), "Open the T2 command button screen");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        Debug.logMessage("T2MENU opening");
        try {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.execute(() -> {
                T2MenuScreen.open();
                Debug.logMessage("T2MENU screen set");
            });
        } catch (Throwable t) {
            T2MenuScreen.open();
        }
        finish();
    }
}
