package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.combat.FightNearbyTask;

/** `@t2fight` — smash the nearest hostile with whatever is in the bag. */
public class T2FightCommand extends Command {

    public T2FightCommand() {
        super("t2fight", "Fight nearest hostile with any tool/weapon");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        Debug.logMessage("TESRUN2 combat: any-weapon, cooldown + crit + creeper kite");
        mod.runUserTask(FightNearbyTask.hostiles(), this::finish);
    }
}
