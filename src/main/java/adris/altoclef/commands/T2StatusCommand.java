package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.SpeedrunClock;
import adris.altoclef.util.helpers.TungstenHelper;
import net.minecraft.item.Items;

/** `@t2stat` — print clock + kit without stopping the run. */
public class T2StatusCommand extends Command {

    public T2StatusCommand() {
        super("t2stat", "Print testrun2 clock and kit");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        var inv = mod.getItemStorage();
        Debug.logMessage(SpeedrunClock.dump());
        adris.altoclef.tasks.speedrun.testrun2.T2History.dump();
        Debug.logMessage(adris.altoclef.tasks.speedrun.testrun2.T2Codes.glossary());
        Debug.logMessage("TESRUN2 kit"
                + " pick=" + inv.getItemCount(Items.IRON_PICKAXE)
                + " bucket=" + (inv.getItemCount(Items.BUCKET) + inv.getItemCount(Items.WATER_BUCKET) + inv.getItemCount(Items.LAVA_BUCKET))
                + " fns=" + inv.getItemCount(Items.FLINT_AND_STEEL)
                + " rods=" + inv.getItemCount(Items.BLAZE_ROD)
                + " pearls=" + inv.getItemCount(Items.ENDER_PEARL)
                + " eyes=" + inv.getItemCount(Items.ENDER_EYE)
                + " tungsten=" + TungstenHelper.isTungstenLoaded()
                + " primary=" + TungstenHelper.isPrimary());
        finish();
    }
}
