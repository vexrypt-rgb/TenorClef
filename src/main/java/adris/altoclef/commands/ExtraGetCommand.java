package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.catalog.ExtraCatalogue;
import adris.altoclef.tasks.speedrun.testrun2.catalog.ExtraGetTask;

/**
 * {@code @xget food} {@code @xget trial_key} {@code @xget list}
 * Does not replace {@code @get}.
 */
public class ExtraGetCommand extends Command {

    public ExtraGetCommand() {
        super("xget", "Get an item via extra catalogue (aliases + version skip)",
                new StringArg("item", "list"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String raw = "list";
        try {
            raw = parser.get(String.class);
        } catch (Throwable ignored) {}
        if (raw == null || raw.isBlank()) raw = "list";

        if (raw.equalsIgnoreCase("list") || raw.equals("?") || raw.equalsIgnoreCase("help")) {
            ExtraCatalogue.dump();
            finish();
            return;
        }

        int count = 1;
        String name = raw;
        int space = raw.lastIndexOf(' ');
        if (space > 0) {
            try {
                count = Integer.parseInt(raw.substring(space + 1).trim());
                name = raw.substring(0, space).trim();
            } catch (NumberFormatException ignored) {}
        }

        if (!ExtraCatalogue.exists(name)) {
            Debug.logWarning("XGET not on this client: " + name
                    + " resolved=" + ExtraCatalogue.resolve(name));
            finish();
            return;
        }

        mod.runUserTask(new ExtraGetTask(name, count), this::finish);
    }
}
