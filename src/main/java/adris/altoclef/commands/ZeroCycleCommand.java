package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.combat.AnyWeaponCombatTask;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;

import java.util.List;

/** {@code @zerocycle} — first-perch dragon damage (beds + bow/crossbow). */
public class ZeroCycleCommand extends Command {

    public ZeroCycleCommand() {
        super(List.of("zerocycle", "0cycle"), "End first-perch beds + bow stall");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        Debug.logMessage("ZEROCYCLE pearl the dragon pillar — @groundzero is the fountain");
        mod.runUserTask(new adris.altoclef.tasks.speedrun.testrun2.combat.ZeroCycleTask(), this::finish);
    }
}
