package adris.altoclef.tasks.speedrun.testrun2.agent;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.agent.AgentJson;
import adris.altoclef.agent.AgentProtocol;
import adris.altoclef.agent.AgentResponse;
import adris.altoclef.agent.AltoClefAgentRuntime;
import adris.altoclef.tasks.speedrun.testrun2.T2Brain;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Sticky;
import adris.altoclef.tasksystem.Task;

/**
 * While this user task runs, snapshot.json is updated and inbox.txt /
 * request.json are consumed (legacy verbs + Phase 9 JSON).
 */
public class AgentLoopTask extends Task {

    private final T2Sticky sticky = new T2Sticky();
    private int ticks;
    private boolean done;
    private AgentProtocol protocol;

    @Override
    protected void onStart() {
        T2Brain.reset();
        sticky.clear();
        done = false;
        ticks = 0;
        AgentFiles.ensure();
        AgentFiles.log("LOOP start");
        Debug.logMessage("AGENT on. dir=" + AgentFiles.dir().toAbsolutePath());
        Debug.logMessage("AGENT write snapshot.json, append commands to inbox.txt or drop request.json");
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        ticks++;

        if (protocol == null) {
            protocol = AltoClefAgentRuntime.protocolFor(mod);
        }

        Task live = sticky.peek();
        Task fix = T2Brain.help(mod, "AGENT", live);
        if (fix != null) return sticky.keep("brain", fix);

        if (ticks % 40 == 0) {
            AgentFiles.writeSnapshot(AgentSnapshot.json(mod, "AGENT", live));
        }

        if (live != null && live.isFinished()) {
            AgentFiles.log("CHILD done " + live.getClass().getSimpleName());
            sticky.clear();
            live = null;
        }

        // Phase 9: prefer request.json drop, then inbox line
        String jsonDrop = AgentFiles.takeRequestJson();
        if (jsonDrop != null) {
            AgentResponse resp = protocol.handleJson(jsonDrop);
            String out = AgentJson.toJson(resp);
            AgentFiles.writeResponse(out);
            Debug.logMessage("AGENT protocol (file) " + out);
        }

        String line = AgentFiles.takeInbox();
        if (line != null) {
            AgentResponse jsonResp = protocol.tryHandleLine(line);
            if (jsonResp != null) {
                String out = AgentJson.toJson(jsonResp);
                AgentFiles.writeResponse(out);
                Debug.logMessage("AGENT protocol (inbox) " + out);
                return live;
            }

            Task next = AgentActions.apply(mod, line);
            if (next instanceof AgentActions.StopSentinel) {
                done = true;
                Debug.logMessage("AGENT stop");
                return null;
            }
            if (live != null && next != null) {
                AgentFiles.pushInbox(line);
                AgentFiles.log("BUSY hold " + line);
            } else if (next != null) {
                return sticky.keep("act:" + line, next);
            }
        }

        return live;
    }

    @Override
    protected void onStop(Task interrupt) {
        AgentFiles.log("LOOP stop");
        sticky.clear();
        Debug.logMessage("AGENT off");
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof AgentLoopTask;
    }

    @Override
    protected String toDebugString() {
        return "agent-loop";
    }
}
