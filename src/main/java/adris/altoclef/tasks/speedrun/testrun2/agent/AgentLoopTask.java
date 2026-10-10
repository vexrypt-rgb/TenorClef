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
    /** The command files are read a few times a second, not every tick: each poll is disk I/O on the game thread. */
    private static final int POLL_TICKS = 5;
    /** Commands put back because a child was running, so each is reported once and not on every poll. */
    private final java.util.Set<String> held = new java.util.HashSet<>();

    @Override
    protected void onStart() {
        T2Brain.reset();
        sticky.clear();
        held.clear();
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
            held.clear();
            live = null;
        }
        if (ticks % POLL_TICKS != 0) return live;

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

            if (live != null && !isStop(line)) {
                // Busy: back of the queue, untouched. Running it here would log and announce it on every poll.
                AgentFiles.pushInbox(line);
                if (held.add(line)) AgentFiles.log("BUSY hold " + line);
                return live;
            }
            Task next = AgentActions.apply(mod, line);
            if (next instanceof AgentActions.StopSentinel) {
                done = true;
                Debug.logMessage("AGENT stop");
                return null;
            }
            if (next != null) {
                return sticky.keep("act:" + line, next);
            }
        }

        return live;
    }

    private static boolean isStop(String line) {
        String t = line.trim();
        if (t.startsWith("@")) t = t.substring(1).trim();
        return t.split("\\s+")[0].equalsIgnoreCase("stop");
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
