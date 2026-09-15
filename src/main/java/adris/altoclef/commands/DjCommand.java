package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.dj.DjPlayer;
import adris.altoclef.tasks.speedrun.testrun2.util.GameFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** {@code @dj} / {@code @dj play} / {@code @dj stop} — note-block song, no world piano. */
public class DjCommand extends Command {

    public DjCommand() {
        super(List.of("dj", "nbs"), "Play Note Block Studio songs (client-side)",
                new StringArg("mode", "play"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String mode = "play";
        try {
            String a = parser.get(String.class);
            if (a != null && !a.isBlank()) mode = a.toLowerCase();
        } catch (Throwable ignored) {}
        Path dir = GameFiles.dir().resolve("dj");
        try { Files.createDirectories(dir); } catch (Throwable ignored) {}
        switch (mode) {
            case "stop", "off" -> {
                DjPlayer.stop();
                Debug.logMessage("DJ stop");
            }
            case "list" -> Debug.logMessage("DJ folder " + dir.toAbsolutePath() + " put .nbs here");
            default -> {
                DjPlayer.playFirstIn(dir);
                Debug.logMessage("DJ " + DjPlayer.title()
                        + " — drop OpenNBS .nbs files in " + dir.toAbsolutePath());
            }
        }
        finish();
    }
}
