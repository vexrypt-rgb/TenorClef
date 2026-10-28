package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.allblocks.AllBlocks;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;

/**
 * {@code @allblocks} (same as Ostinato's {@code #allblocks}): gather one of every obtainable item into labeled chests
 * and place one of every placeable block on a grid, in survival.
 * <pre>
 * @allblocks start [x y z] [retry]   start or resume; origin defaults to your feet (fixed after the first start)
 * @allblocks status                  obtained / placed / skipped / unsupported / stuck / remaining
 * @allblocks report [n]              stuck, unsupported and skipped entries with the reason
 * @allblocks stop                    stop, progress is kept
 * @allblocks reset                   forget this world's progress
 * @allblocks tour                    walk to the chest-room and grid views (for screenshots)
 * </pre>
 */
public class AllBlocksCommand extends Command {

    public AllBlocksCommand() {
        super("allblocks", "Collect one of every item into labeled chests and place every block (survival). start [x y z] [retry] | status | report [n] | stop | reset | tour",
                new StringArg("action", "status"), new StringArg("a", ""), new StringArg("b", ""), new StringArg("c", ""), new StringArg("d", ""));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String action = parser.get(String.class);
        StringBuilder rest = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            String s = parser.get(String.class);
            if (s != null && !s.isEmpty()) rest.append(rest.length() > 0 ? " " : "").append(s);
        }
        String out;
        switch (action == null ? "status" : action.toLowerCase()) {
            case "start", "resume" -> out = AllBlocks.start(rest.toString());
            case "stop" -> out = AllBlocks.stop();
            case "report" -> {
                int n = 20;
                try {
                    if (rest.length() > 0) n = Integer.parseInt(rest.toString().trim());
                } catch (NumberFormatException ignored) {
                }
                out = AllBlocks.report(n);
            }
            case "reset" -> out = AllBlocks.reset();
            case "tour" -> out = AllBlocks.tour();
            default -> out = AllBlocks.status();
        }
        for (String line : out.split("\n")) Debug.logMessage(line);
        finish();
    }
}
