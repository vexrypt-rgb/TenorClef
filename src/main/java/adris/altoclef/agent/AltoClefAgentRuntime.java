package adris.altoclef.agent;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commands.GoalCommand;
import adris.altoclef.knowledge.KnowledgeFact;
import adris.altoclef.knowledge.WorldKnowledge;
import adris.altoclef.planner.AcquireItemGoal;
import adris.altoclef.planner.CatalogueInventoryView;
import adris.altoclef.planner.GoalManager;
import adris.altoclef.planner.Plan;
import adris.altoclef.planner.PlanRunnerTask;
import adris.altoclef.planner.PlanStep;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.threat.ThreatMonitor;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Live {@link AgentRuntime} wiring Phase 7 GoalManager / TaskCatalogue,
 * WorldKnowledge snapshot, and safe cancel of the user task chain.
 */
public class AltoClefAgentRuntime implements AgentRuntime {

    private final AltoClef mod;

    public AltoClefAgentRuntime(AltoClef mod) {
        this.mod = mod;
    }

    @Override
    public AgentResponse acquire(String requestId, String item, int count) {
        if (mod == null) {
            return AgentResponse.failure(requestId, "no AltoClef instance");
        }
        if (!mod.inGame()) {
            return AgentResponse.blocked(requestId, "not in game");
        }
        String key = item != null ? item.trim().toLowerCase(Locale.ROOT) : "";
        if (key.isEmpty()) {
            return AgentResponse.failure(requestId, "item required");
        }
        if (!TaskCatalogue.taskExists(key)) {
            return AgentResponse.failure(requestId, "catalogue has no resource '" + key + "'");
        }
        int n = Math.max(1, count);

        AcquireItemGoal goal = new AcquireItemGoal(key, n);
        GoalManager manager = new GoalManager();
        CatalogueInventoryView inv = new CatalogueInventoryView(mod);
        manager.start(goal, inv);
        GoalCommand.setActiveManager(manager);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("item", key);
        result.put("count", Integer.toString(n));
        result.put("goalId", goal.getId());
        result.put("goalStatus", manager.getStatus().name());
        result.put("note", manager.getLastNote() != null ? manager.getLastNote() : "");

        if (manager.getStatus().isTerminal()) {
            result.put("mode", "goal");
            if (manager.getStatus() == adris.altoclef.planner.GoalStatus.SUCCESS) {
                return AgentResponse.success(requestId, result);
            }
            return AgentResponse.failure(requestId, "goal immediate "
                    + manager.getStatus() + ": " + manager.getLastNote());
        }

        Plan plan = manager.getPlan();
        result.put("planSteps", Integer.toString(plan != null ? plan.size() : 0));
        if (plan != null && !plan.getSteps().isEmpty()) {
            PlanStep step = plan.getSteps().get(0);
            result.put("firstStep", step.getLabel());
        }
        result.put("mode", "goal");

        mod.runUserTask(new PlanRunnerTask(manager), () -> {
            Debug.logMessage("AGENT protocol goal finished: " + manager.summarize());
        });
        Debug.logMessage("AGENT protocol accepted acquire " + key + " x" + n);
        return AgentResponse.accepted(requestId, result);
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        if (mod == null) {
            m.put("error", "no AltoClef");
            return m;
        }
        m.put("inGame", Boolean.toString(mod.inGame()));
        m.put("paused", Boolean.toString(mod.isPaused()));

        WorldKnowledge wk = null;
        try {
            wk = mod.getWorldKnowledge();
        } catch (Throwable ignored) {}

        if (wk != null) {
            KnowledgeFact<Vec3d> pos = wk.playerPositionFact();
            if (pos != null && pos.isKnown()) {
                Vec3d v = pos.getValue();
                if (v != null) {
                    m.put("x", Integer.toString((int) Math.floor(v.x)));
                    m.put("y", Integer.toString((int) Math.floor(v.y)));
                    m.put("z", Integer.toString((int) Math.floor(v.z)));
                }
                m.put("posConfidence", String.format(Locale.ROOT, "%.2f", pos.getConfidence()));
                m.put("posSource", String.valueOf(pos.getSource()));
            }
            KnowledgeFact<Float> hp = wk.playerHealthFact();
            if (hp != null && hp.isKnown() && hp.getValue() != null) {
                m.put("health", String.format(Locale.ROOT, "%.1f", hp.getValue()));
                m.put("healthConfidence", String.format(Locale.ROOT, "%.2f", hp.getConfidence()));
            }
        } else if (mod.getPlayer() != null) {
            m.put("x", Integer.toString(mod.getPlayer().getBlockX()));
            m.put("y", Integer.toString(mod.getPlayer().getBlockY()));
            m.put("z", Integer.toString(mod.getPlayer().getBlockZ()));
            m.put("health", String.format(Locale.ROOT, "%.1f", mod.getPlayer().getHealth()));
        }

        GoalManager gm = GoalCommand.getActiveManager();
        if (gm != null) {
            m.put("goal", gm.summarize());
            m.put("goalStatus", gm.getStatus().name());
            if (gm.getGoal() != null) {
                m.put("goalId", gm.getGoal().getId());
            }
            Plan plan = gm.getPlan();
            if (plan != null) {
                m.put("planSteps", Integer.toString(plan.size()));
                PlanStep cur = plan.currentStep();
                if (cur != null) {
                    m.put("currentStep", cur.getLabel());
                }
            }
        } else {
            m.put("goal", "none");
            m.put("goalStatus", "IDLE");
        }

        try {
            List<Task> tasks = mod.getUserTaskChain().getTasks();
            if (tasks == null || tasks.isEmpty()) {
                m.put("userTask", "none");
            } else {
                m.put("userTask", tasks.get(0).toString());
                m.put("userTaskCount", Integer.toString(tasks.size()));
            }
        } catch (Throwable t) {
            m.put("userTask", "unavailable");
        }

        try {
            ThreatMonitor tm = mod.getThreatMonitor();
            if (tm != null && tm.getLatest() != null) {
                m.put("threat", tm.getLatest().getLevel().name());
                m.put("threatReason", tm.getLatest().getReason() != null
                        ? tm.getLatest().getReason() : "");
            }
        } catch (Throwable ignored) {}

        return m;
    }

    @Override
    public AgentResponse cancel(String requestId) {
        if (mod == null) {
            return AgentResponse.failure(requestId, "no AltoClef instance");
        }
        Map<String, String> result = new LinkedHashMap<>();
        GoalManager gm = GoalCommand.getActiveManager();
        boolean hadGoal = gm != null && !gm.getStatus().isTerminal();
        if (gm != null && !gm.getStatus().isTerminal()) {
            gm.cancel();
            result.put("goalCancelled", "true");
            result.put("goalStatus", gm.getStatus().name());
        } else {
            result.put("goalCancelled", "false");
        }

        boolean hadUser = false;
        try {
            List<Task> tasks = mod.getUserTaskChain().getTasks();
            hadUser = tasks != null && !tasks.isEmpty();
        } catch (Throwable ignored) {}

        // Safe cancel: stop user task chain / TaskRunner user work
        try {
            mod.cancelUserTask();
            result.put("userTaskCancelled", "true");
        } catch (Throwable t) {
            result.put("userTaskCancelled", "false");
            return AgentResponse.blocked(requestId, "cancel failed: " + t.getMessage());
        }

        if (!hadGoal && !hadUser) {
            result.put("note", "nothing active");
            return AgentResponse.success(requestId, result);
        }
        Debug.logMessage("AGENT protocol cancel: " + result);
        return AgentResponse.cancelled(requestId, result);
    }

    @Override
    public boolean isBusy() {
        if (mod == null) return false;
        GoalManager gm = GoalCommand.getActiveManager();
        if (gm != null && !gm.getStatus().isTerminal()
                && gm.getStatus() != adris.altoclef.planner.GoalStatus.IDLE) {
            return true;
        }
        try {
            List<Task> tasks = mod.getUserTaskChain().getTasks();
            return tasks != null && !tasks.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Shared protocol instance bound to the live mod singleton. */
    public static AgentProtocol protocolFor(AltoClef mod) {
        return new AgentProtocol(new AltoClefAgentRuntime(mod));
    }
}
