package adris.altoclef.tasks.speedrun.testrun2.agent;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.speedrun.testrun2.T2Brain;
import adris.altoclef.tasks.speedrun.testrun2.core.T2Sticky;
import adris.altoclef.tasksystem.Task;

/**
 * While this user task runs, snapshot.json is updated and inbox.txt is consumed.
 */
public class AgentLoopTask extends Task {

    private final T2Sticky sticky = new T2Sticky();
    private int ticks;
    private boolean done;

    @Override
    protected void onStart() {
        T2Brain.reset();
        sticky.clear();
        done = false;
        ticks = 0;
        AgentFiles.ensure();
        AgentFiles.log("LOOP start");
        Debug.logMessage("AGENT on. dir=" + AgentFiles.dir().toAbsolutePath());
        Debug.logMessage("AGENT write snapshot.json, append commands to inbox.txt");
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        ticks++;

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

        String line = AgentFiles.takeInbox();
        if (line != null) {
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
