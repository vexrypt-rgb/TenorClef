package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasks.speedrun.testrun2.SpeedrunOpt;
import adris.altoclef.tasks.speedrun.testrun2.catalog.ExtraCatalogue;
import adris.altoclef.tasks.speedrun.testrun2.util.DeathWatch;
import adris.altoclef.tasks.speedrun.testrun2.util.EyeGate;
import adris.altoclef.tasks.speedrun.testrun2.util.GameFiles;
import adris.altoclef.tasks.speedrun.testrun2.util.HomeStore;
import adris.altoclef.tasks.speedrun.testrun2.util.PlayerSense;
import net.minecraft.item.Items;

/**
 * One screen that answers "did the drop-in actually load."
 */
public class T2DoctorCommand extends Command {

    public T2DoctorCommand() {
        super("t2doctor", "Print testrun2 wiring, files, eyes, home, catalogue");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        Debug.logMessage("T2DOCTOR minor=" + McCompat.gameMinor()
                + " prefix=" + safePrefix(mod));
        Debug.logMessage("T2DOCTOR eyes cfg=" + SpeedrunOpt.EYES
                + " have=" + EyeGate.count(mod)
                + " surviveTick=" + SpeedrunOpt.SURVIVE_TICK
                + " sense=" + PlayerSense.frozen());
        boolean food = false;
        try { food = TaskCatalogue.taskExists("food"); } catch (Throwable ignored) {}
        boolean bread = false;
        try { bread = TaskCatalogue.taskExists("bread"); } catch (Throwable ignored) {}
        Debug.logMessage("T2DOCTOR catalogue food=" + food
                + " bread=" + bread
                + " xget(food)=" + ExtraCatalogue.exists("food"));
        HomeStore h = HomeStore.load();
        Debug.logMessage("T2DOCTOR home=" + (h == null ? "NONE" : h.dim + " " + h.x + " " + h.y + " " + h.z));
        Debug.logMessage("T2DOCTOR death=" + (DeathWatch.lastDeath() == null ? "NONE" : "set"));
        Debug.logMessage("T2DOCTOR dir=" + GameFiles.dir().toAbsolutePath());
        if (mod.getPlayer() != null) {
            int pick = 0, cobble = 0, water = 0;
            try {
                pick = mod.getItemStorage().getItemCount(Items.STONE_PICKAXE)
                        + mod.getItemStorage().getItemCount(Items.IRON_PICKAXE);
                cobble = mod.getItemStorage().getItemCount(Items.COBBLESTONE);
                water = mod.getItemStorage().getItemCount(Items.WATER_BUCKET);
            } catch (Throwable ignored) {}
            Debug.logMessage("T2DOCTOR inv pick=" + pick + " cobble=" + cobble + " water=" + water
                    + " hp=" + (int) mod.getPlayer().getHealth());
        }
        Debug.logMessage("T2DOCTOR faults " + adris.altoclef.tasks.speedrun.testrun2.T2Fault.recent());
        Debug.logMessage("T2DOCTOR cmds: @testrun2 @aa @t2core @xget @escape @sethome @home @back @village @logdump @agent @ado @t2panic @t2menu");
        finish();
    }

    private static String safePrefix(AltoClef mod) {
        try {
            return AltoClef.getCommandExecutor().getCommandPrefix();
        } catch (Throwable t) {
            return "@";
        }
    }
}
