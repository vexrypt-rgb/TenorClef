package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.mapart.MapArtConverter;
import adris.altoclef.tasks.speedrun.testrun2.mapart.MapArtFiles;
import adris.altoclef.tasks.speedrun.testrun2.mapart.MapArtPicker;
import adris.altoclef.tasks.speedrun.testrun2.mapart.MapArtTask;

import java.nio.file.Path;
import java.util.List;

/**
 * {@code @mapart} help
 * {@code @mapart add} file dialog
 * {@code @mapart convert} inbox folder
 * {@code @mapart print} last converted art
 */
public class MapArtCommand extends Command {

    public MapArtCommand() {
        super(List.of("mapart", "printart"),
                "Convert an image to map art and gather/place it",
                new StringArg("mode", "help"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String mode = "help";
        try {
            String a = parser.get(String.class);
            if (a != null && !a.isBlank()) mode = a.toLowerCase();
        } catch (Throwable ignored) {}
        MapArtFiles.ensure();
        switch (mode) {
            case "add", "open", "pick", "image" -> {
                Debug.logMessage("MAPART opening file dialog (or drop PNG in "
                        + MapArtFiles.inbox().toAbsolutePath() + ")");
                MapArtPicker.pickAndConvert();
                finish();
            }
            case "convert", "inbox" -> {
                Path out = MapArtConverter.convertInboxAll();
                Debug.logMessage(out == null
                        ? "MAPART inbox empty: " + MapArtFiles.inbox().toAbsolutePath()
                        : "MAPART converted " + out.getFileName());
                finish();
            }
            case "print", "build", "go" -> {
                Path dir = MapArtFiles.latestOut();
                if (dir == null) {
                    Debug.logWarning("MAPART nothing converted. @mapart add  or drop PNG in inbox");
                    finish();
                    return;
                }
                mod.runUserTask(new MapArtTask(dir), this::finish);
            }
            case "list" -> {
                Debug.logMessage("MAPART inbox " + MapArtFiles.inbox().toAbsolutePath());
                Debug.logMessage("MAPART out " + MapArtFiles.out().toAbsolutePath());
                Path last = MapArtFiles.latestOut();
                Debug.logMessage("MAPART last " + (last == null ? "none" : last.getFileName()));
                finish();
            }
            default -> {
                Debug.logMessage("MAPART @mapart add | convert | print | list");
                Debug.logMessage("MAPART drop PNG in " + MapArtFiles.inbox().toAbsolutePath());
                finish();
            }
        }
    }
}
