package adris.altoclef.tasksystem;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;

import java.util.ArrayList;

public class TaskRunner {

    private final ArrayList<TaskChain> chains = new ArrayList<>();
    private final AltoClef mod;
    private boolean active;

    private TaskChain cachedCurrentTaskChain = null;

    public String statusReport = " (no chain running) ";

    public TaskRunner(AltoClef mod) {
        this.mod = mod;
        active = false;
    }

    public void tick() {
        if (!active || !AltoClef.inGame()) {
            statusReport = " (no chain running) ";
            return;
        }

        // Get highest priority chain and run
        TaskChain maxChain = null;
        float maxPriority = Float.NEGATIVE_INFINITY;
        for (TaskChain chain : chains) {
            if (!chain.isActive()) continue;
            float priority = chain.getPriority();
            if (priority > maxPriority) {
                maxPriority = priority;
                maxChain = chain;
            }
        }
        if (cachedCurrentTaskChain != null && maxChain != cachedCurrentTaskChain) {
            cachedCurrentTaskChain.onInterrupt(maxChain);
        }
        cachedCurrentTaskChain = maxChain;
        if (maxChain != null) {
            maxChain.tick();
            String failBit = "";
            var tasks = maxChain.getTasks();
            if (!tasks.isEmpty()) {
                // Prefer deepest (leaf) failure / recovery if any
                TaskFailure leafFail = null;
                RecoveryDecision leafRecovery = null;
                for (int i = tasks.size() - 1; i >= 0; i--) {
                    Task t = tasks.get(i);
                    TaskFailure f = t.getLastFailure();
                    if (f != null) {
                        leafFail = f;
                        leafRecovery = t.getLastRecovery();
                        break;
                    }
                }
                if (leafFail != null) {
                    failBit = ", fail=" + leafFail.getReason();
                    if (leafRecovery != null) {
                        failBit += ", recovery=" + leafRecovery.getAction();
                    }
                }
            }
            statusReport = "Chain: " + maxChain.getName() + ", priority: " + maxPriority + failBit;
        } else {
            statusReport = " (no chain running) ";
        }
    }

    public void addTaskChain(TaskChain chain) {
        chains.add(chain);
    }

    public void enable() {
        if (!active) {
            mod.getBehaviour().push();
            mod.getBehaviour().setPauseOnLostFocus(false);
        }
        active = true;
    }

    public void disable() {
        if (active) {
            mod.getBehaviour().pop();
            Debug.logMessage("Stopped");
        }
        for (TaskChain chain : chains) {
            chain.stop();
        }
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public TaskChain getCurrentTaskChain() {
        return cachedCurrentTaskChain;
    }

    // Kinda jank ngl
    public AltoClef getMod() {
        return mod;
    }
}
