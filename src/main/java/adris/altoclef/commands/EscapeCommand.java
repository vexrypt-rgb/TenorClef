package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.escape.EscapeSpawnTask;
import adris.altoclef.tasks.speedrun.testrun2.escape.SecretBaseTask;

/**
 * {@code @escape} — off-axis walk toward a random ±30M point, then seal a hole.
 */
public class EscapeCommand extends Command {

    public EscapeCommand() {
        super("escape", "Leave spawn off-axis and seal a hidden hole (2b2t-style)",
                new StringArg("mode", "go"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String mode = "go";
        try {
            mode = parser.get(String.class);
        } catch (Throwable ignored) {}
        if (mode == null) mode = "go";
        mode = mode.toLowerCase();

        if (mode.equals("help") || mode.equals("?")) {
            Debug.logMessage("ESCAPE: random ±30M OW dest via nether 1x2, fill netherrack behind.");
            Debug.logMessage("  @escape        kit, portal, tunnel dest/8, portal out, shaft");
            Debug.logMessage("  @escape here   shaft at current feet");
            Debug.logMessage("Runs until arrival or @stop. Dest only in escape_dest.txt.");
            finish();
            return;
        }

        if (mode.equals("here") || mode.equals("base")) {
            Debug.logMessage("ESCAPE shaft here");
            mod.runUserTask(new SecretBaseTask(), this::finish);
            return;
        }

        Debug.logMessage("ESCAPE start. Nether cover-tunnel to dest. Don't paste F3 in chat.");
        mod.runUserTask(new EscapeSpawnTask(), this::finish);
    }
}
