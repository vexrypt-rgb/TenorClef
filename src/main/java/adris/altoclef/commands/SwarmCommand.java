package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.swarm.SwarmAdapter;

/**
 * {@code @swarm} drives Ostinato's encrypted swarm link.
 * <pre>
 * @swarm                      status
 * @swarm on | off             turn the link on or off
 * @swarm ping [group]         ping members
 * @swarm reload               reread roster and keyring
 * @swarm build <group> <file> [x y z]   build a schematic across a group
 * @swarm stop                 stop the build
 * @swarm set <setting> <value>    e.g. roster swarm.txt, home C:/sigil, channel whisper
 * @swarm menu                 open the Swarm tab
 * </pre>
 */
public class SwarmCommand extends Command {

    public SwarmCommand() {
        super("swarm", "Drive the Ostinato swarm link (encrypted)", new StringArg("args", "status"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String raw = "status";
        try {
            String a = parser.get(String.class);
            if (a != null && !a.isBlank()) raw = a.trim();
        } catch (Throwable ignored) {}
        if (!SwarmAdapter.available()) {
            Debug.logWarning("SWARM " + SwarmAdapter.detail());
            finish();
            return;
        }
        String[] p = raw.split("\\s+");
        switch (p[0].toLowerCase()) {
            case "on" -> {
                SwarmAdapter.setEnabled(true);
                Debug.logMessage("SWARM on (roster=" + SwarmAdapter.getString("swarmRosterFile") + ")");
            }
            case "off" -> {
                SwarmAdapter.setEnabled(false);
                Debug.logMessage("SWARM off");
            }
            case "menu" -> {
                adris.altoclef.tasks.speedrun.testrun2.gui.T2MenuScreen.openTab(6);
            }
            case "set" -> {
                if (p.length < 3) {
                    Debug.logWarning("SWARM @swarm set <setting> <value>");
                } else {
                    String name = p[1].startsWith("swarm") ? p[1] : "swarm" + Character.toUpperCase(p[1].charAt(0)) + p[1].substring(1);
                    String err = SwarmAdapter.set(name, raw.substring(raw.indexOf(p[1]) + p[1].length()).trim());
                    Debug.logMessage(err == null ? "SWARM " + name + " = " + SwarmAdapter.getString(name) : "SWARM " + err);
                }
            }
            case "status", "ping", "reload", "build", "stop" -> {
                if (!SwarmAdapter.run(raw)) {
                    Debug.logWarning("SWARM '" + raw + "' was not accepted");
                }
            }
            default -> Debug.logWarning("SWARM status|on|off|ping|reload|build|stop|set|menu");
        }
        finish();
    }
}
