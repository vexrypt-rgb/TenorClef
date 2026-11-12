package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.swarm.SwarmAdapter;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;

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
 * @swarm send <bot> <command...>   Phase 1, when swarmCommandProtocol is on
 * @swarm outcome <command-id>
 * @swarm cancel <command-id>
 * </pre>
 */
public class SwarmCommand extends Command {

    // Ostinato: @swarm spawn <name> [count] [server], despawn <name|all>, and bots forward to Ostinato.

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
            case "status", "ping", "reload", "build", "stop", "spawn", "despawn", "bots" -> { // Ostinato: unified OWNER command.
                if (!SwarmAdapter.run(raw)) {
                    Debug.logWarning("SWARM '" + raw + "' was not accepted");
                }
            }
            case "send" -> {
                if (p.length < 3) {
                    Debug.logWarning("SWARM @swarm send <bot> <command...>");
                } else {
                    String command = raw.substring(raw.indexOf(p[1]) + p[1].length()).trim();
                    Debug.logMessage("SWARM " + protocolCall("submitCommand", p[1], command));
                }
            }
            case "outcome" -> {
                if (p.length < 2) {
                    Debug.logWarning("SWARM @swarm outcome <command-id>");
                } else {
                    Debug.logMessage("SWARM " + protocolCall("commandOutcome", p[1]));
                }
            }
            case "cancel" -> {
                if (p.length < 2) {
                    Debug.logWarning("SWARM @swarm cancel <command-id>");
                } else {
                    Debug.logMessage("SWARM " + protocolCall("cancelCommand", p[1]));
                }
            }
            default -> Debug.logWarning("SWARM status|on|off|ping|reload|build|stop|set|menu|send|outcome|cancel|spawn <name> [count] [server]|despawn <name|all>|bots"); // Ostinato:
        }
        finish();
    }

    /**
     * Phase 1 methods are invoked by name so this source still compiles against a Baritone jar that
     * does not have them. When the flag is off the implementation returns without sending.
     */
    private static String protocolCall(String method, String... args) {
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null || baritone.getSwarm() == null) {
                return "swarm: no baritone";
            }
            Object swarm = baritone.getSwarm();
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                types[i] = String.class;
            }
            Object result = swarm.getClass().getMethod(method, types).invoke(swarm, (Object[]) args);
            return result == null ? "" : String.valueOf(result);
        } catch (Throwable t) {
            Throwable cause = t.getCause() == null ? t : t.getCause();
            String message = cause.getMessage();
            return "swarm: " + (message == null ? cause.getClass().getSimpleName() : message);
        }
    }
}
