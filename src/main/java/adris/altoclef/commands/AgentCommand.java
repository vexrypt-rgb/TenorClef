package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.agent.AgentFiles;
import adris.altoclef.tasks.speedrun.testrun2.agent.AgentLoopTask;
import adris.altoclef.tasks.speedrun.testrun2.agent.AgentSnapshot;

/**
 * {@code @agent on|off|ask <cmd>|snap|help}
 */
public class AgentCommand extends Command {

    public AgentCommand() {
        super("agent", "File-based AI/API control loop (whitelist actions)",
                new StringArg("args", "help"));
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String raw = "help";
        try {
            raw = parser.get(String.class);
        } catch (Throwable ignored) {}
        if (raw == null || raw.isBlank()) raw = "help";
        String[] p = raw.trim().split("\\s+", 2);
        String verb = p[0].toLowerCase();
        String rest = p.length > 1 ? p[1] : "";

        switch (verb) {
            case "on", "start" -> {
                AgentFiles.ensure();
                Debug.logMessage("AGENT starting loop");
                mod.runUserTask(new AgentLoopTask(), this::finish);
            }
            case "off", "stop" -> {
                AgentFiles.pushInbox("stop");
                Debug.logMessage("AGENT queued stop");
                finish();
            }
            case "ask", "do" -> {
                if (rest.isBlank()) {
                    Debug.logWarning("AGENT ask needs a command, e.g. @agent ask xget bread");
                    finish();
                    return;
                }
                AgentFiles.pushInbox(rest);
                Debug.logMessage("AGENT queued: " + rest);
                finish();
            }
            case "goal" -> {
                adris.altoclef.tasks.speedrun.testrun2.util.AgentGoal.set(rest);
                Debug.logMessage("AGENT goal=" + rest);
                finish();
            }
            case "snap", "status" -> {
                AgentFiles.ensure();
                String json = AgentSnapshot.json(mod, "manual", null);
                AgentFiles.writeSnapshot(json);
                Debug.logMessage("AGENT " + json);
                Debug.logMessage("AGENT dir=" + AgentFiles.dir().toAbsolutePath());
                finish();
            }
            default -> {
                Debug.logMessage("AGENT file protocol. Does not change other commands.");
                Debug.logMessage("  @agent on          start loop (writes snapshot every 2s)");
                Debug.logMessage("  @agent off         queue stop");
                Debug.logMessage("  @agent ask xget bread");
                Debug.logMessage("  @agent goal stay hidden");
                Debug.logMessage("  @agent snap        print snapshot");
                Debug.logMessage("Files: <gameDir>/altoclef/agent/snapshot.json inbox.txt outbox.log");
                Debug.logMessage("Allowed: get xget food goto wait say idle stop testrun2 aa t2core equip");
                finish();
            }
        }
    }
}
