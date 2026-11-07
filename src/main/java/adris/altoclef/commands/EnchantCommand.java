package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.IntArg;
import adris.altoclef.commandsystem.args.ItemTargetArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.container.EnchantItemTask;
import adris.altoclef.util.ItemTarget;

public class EnchantCommand extends Command {

    public EnchantCommand() throws CommandException {
        super("enchant", "Enchant an item at an enchanting table: @enchant <item> [count] [minLevel]",
                new ItemTargetArg("item"),
                new IntArg("minLevel", 1)
        );
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        ItemTarget item = parser.get(ItemTarget.class);
        int minLevel = parser.get(Integer.class);
        mod.runUserTask(new EnchantItemTask(item, minLevel), this::finish);
    }
}
