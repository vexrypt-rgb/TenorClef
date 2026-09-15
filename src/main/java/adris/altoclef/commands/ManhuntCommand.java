package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.tasks.manhunt.HunterManhuntTask;

/**
 * @manhunt [playerName] – Play as the hunter in a manhunt.
 *
 * If a player name is given, hunt that player.
 * Otherwise hunt the nearest non-friend player.
 *
 * Goal: kill the runner before they kill the dragon.
 */
public class ManhuntCommand extends Command {

    public ManhuntCommand() {
        super("manhunt", "Manhunt hunter: track and kill the runner",
                new StringArg("player", null, true)); // optional player name
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        String targetName = null;
        try {
            targetName = parser.get(String.class);
        } catch (Exception ignored) {
            // no name provided – will use nearest player
        }
        mod.runUserTask(new HunterManhuntTask(mod, targetName), this::finish);
    }
}
