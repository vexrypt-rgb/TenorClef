package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.agent.AgentFiles;

/**
 * {@code @ado xget bread} queues one whitelist line into the agent inbox.
 */
public class AgentDoCommand extends Command {

    public AgentDoCommand() {
        super("ado", "Queue one agent action (get/xget/food/goto/stop/...)",
                new StringArg("a", "say"),
                new StringArg("b", ""),
                new StringArg("c", ""),
                new StringArg("d", ""));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String a = get(parser, "say");
        String b = get(parser, "");
        String c = get(parser, "");
        String d = get(parser, "");
        String line = (a + " " + b + " " + c + " " + d).trim();
        AgentFiles.pushInbox(line);
        Debug.logMessage("AGENT queued via @ado: " + line);
        finish();
    }

    private static String get(ArgParser parser, String fallback) {
        try {
            String v = parser.get(String.class);
            return v == null ? fallback : v;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
