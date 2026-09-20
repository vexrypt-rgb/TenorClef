package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.agent.AgentJson;
import adris.altoclef.agent.AgentProtocol;
import adris.altoclef.agent.AgentResponse;
import adris.altoclef.agent.AltoClefAgentRuntime;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.tasks.speedrun.testrun2.agent.AgentFiles;
import adris.altoclef.tasks.speedrun.testrun2.agent.AgentLoopTask;
import adris.altoclef.tasks.speedrun.testrun2.agent.AgentSnapshot;

/**
 * {@code @agent on|off|ask <cmd>|snap|json <payload>|help}
 * <p>
 * Phase 9: {@code @agent json {...}} dispatches structured
 * {@link adris.altoclef.agent.AgentRequest}s. Legacy chat verbs unchanged.
 * JSON lines in {@code inbox.txt} or a {@code request.json} drop are also accepted
 * while the agent loop is running.
 */
public class AgentCommand extends Command {

    public AgentCommand() {
        super("agent", "File-based AI/API control loop (whitelist actions + JSON protocol)",
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
                // If the ask payload is JSON, dispatch immediately (no loop required)
                if (AgentJson.looksLikeJsonRequest(rest) || rest.trim().startsWith("{")) {
                    dispatchJson(mod, rest.trim());
                    finish();
                    return;
                }
                AgentFiles.pushInbox(rest);
                Debug.logMessage("AGENT queued: " + rest);
                finish();
            }
            case "json" -> {
                if (rest.isBlank()) {
                    Debug.logWarning("AGENT json needs a payload, e.g. @agent json {\"id\":\"1\",\"action\":\"status\"}");
                    finish();
                    return;
                }
                dispatchJson(mod, rest.trim());
                finish();
            }
            case "goal" -> {
                adris.altoclef.tasks.speedrun.testrun2.util.AgentGoal.set(rest);
                Debug.logMessage("AGENT goal=" + rest);
                finish();
            }
            case "snap", "status" -> {
                AgentFiles.ensure();
                // Legacy snapshot + Phase 9 structured response
                String json = AgentSnapshot.json(mod, "manual", null);
                AgentFiles.writeSnapshot(json);
                Debug.logMessage("AGENT " + json);
                AgentResponse proto = AltoClefAgentRuntime.protocolFor(mod)
                        .handleJson("{\"id\":\"snap\",\"action\":\"status\"}");
                String protoJson = AgentJson.toJson(proto);
                AgentFiles.writeResponse(protoJson);
                Debug.logMessage("AGENT protocol " + protoJson);
                Debug.logMessage("AGENT dir=" + AgentFiles.dir().toAbsolutePath());
                finish();
            }
            default -> {
                Debug.logMessage("AGENT file protocol. Does not change other commands.");
                Debug.logMessage("  @agent on          start loop (writes snapshot every 2s)");
                Debug.logMessage("  @agent off         queue stop");
                Debug.logMessage("  @agent ask xget bread");
                Debug.logMessage("  @agent json {\"id\":\"1\",\"action\":\"get\",\"parameters\":{\"item\":\"cobblestone\",\"count\":64}}");
                Debug.logMessage("  @agent goal stay hidden");
                Debug.logMessage("  @agent snap        print snapshot + protocol status");
                Debug.logMessage("Files: <gameDir>/altoclef/agent/ snapshot.json inbox.txt request.json response.json outbox.log");
                Debug.logMessage("Allowed verbs: get xget food goto wait say idle stop testrun2 aa t2core equip");
                Debug.logMessage("JSON actions: get acquire goal status snap cancel  (see docs/AGENT_PROTOCOL.md)");
                finish();
            }
        }
    }

    private static void dispatchJson(AltoClef mod, String payload) {
        AgentFiles.ensure();
        AgentProtocol protocol = AltoClefAgentRuntime.protocolFor(mod);
        AgentResponse resp = protocol.handleJson(payload);
        String out = AgentJson.toJson(resp);
        AgentFiles.writeResponse(out);
        Debug.logMessage("AGENT protocol " + out);
    }
}
