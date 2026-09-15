package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.AdvancementCatalog;
import adris.altoclef.tasks.speedrun.testrun2.AllAdvancementsTask;

public class AaCommand extends Command {

    public AaCommand() {
        super("aa", "All Advancements 1.16.5: catalog of every story/nether/end/adventure/husbandry ID");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        Debug.logMessage("AA: " + AdvancementCatalog.all().length + " goals. Dragon route first, then leftovers.");
        Debug.logMessage("AA: HARD goals (HDWGH, Adventuring Time, Two by Two) time out and skip — check L.");
        mod.runUserTask(new AllAdvancementsTask(), () -> {
            Debug.logMessage("AA: user task returned");
            finish();
        });
    }
}
