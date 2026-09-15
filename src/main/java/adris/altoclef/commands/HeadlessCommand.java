package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.util.HeadlessTune;

import java.util.List;

public class HeadlessCommand extends Command {

    public HeadlessCommand() {
        super(List.of("headless", "nogui"), "Cap FPS/view for butler VPS (not a true dedicated-server bot)");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        HeadlessTune.apply();
        Debug.logMessage("HEADLESS Linux VPS: xvfb-run -a ./gradlew :1.16.5:runClient");
        finish();
    }
}
