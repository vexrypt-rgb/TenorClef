package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.pvp.PvpBenchTask;
import adris.altoclef.tasks.pvp.PvpTask;

/** `@pvp [player|players|mobs|bench N]` */
public class PvpCommand extends Command {

    public PvpCommand() {
        super("pvp", "Fight a player (or nearest player / hostiles). 'pvp bench N' runs the singleplayer bench",
                new StringArg("target", "players"), new StringArg("rounds", "8"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String t = parser.get(String.class);
        String n = parser.get(String.class);
        switch (t.toLowerCase()) {
            case "bench" -> mod.runUserTask(new PvpBenchTask(Integer.parseInt(n)), this::finish);
            case "mobs" -> mod.runUserTask(PvpTask.hostiles(), this::finish);
            case "players" -> mod.runUserTask(PvpTask.nearestPlayer(), this::finish);
            default -> mod.runUserTask(PvpTask.player(t), this::finish);
        }
    }
}
