package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.tasksystem.TaskChain;
import adris.altoclef.tasksystem.TaskIntrospection;
import adris.altoclef.tasksystem.TaskIntrospection.ChainView;

import java.util.List;

/** Read-only @task / @tasktree / @why over live TaskRunner state. Never controls tasks. */
public class TaskIntrospectCommand extends Command {

    public enum Mode { TASK, TREE, WHY }

    private final Mode mode;

    public TaskIntrospectCommand(Mode mode) {
        super(mode == Mode.TASK ? "task" : mode == Mode.TREE ? "tasktree" : "why",
                mode == Mode.TASK ? "What is the bot doing (live chain, goal, leaf, last failure)"
                        : mode == Mode.TREE ? "Live task hierarchy of every active chain"
                        : "Why the user goal is or is not progressing");
        this.mode = mode;
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        long now = System.currentTimeMillis();
        TaskChain cur = mod.getTaskRunner().getCurrentTaskChain();
        ChainView running = cur != null ? TaskIntrospection.of(cur) : null;
        List<String> lines;
        switch (mode) {
            case TREE -> {
                lines = new java.util.ArrayList<>();
                for (TaskChain c : mod.getTaskRunner().getChains()) {
                    if (!c.isActive() && c != cur) continue;
                    lines.addAll(TaskIntrospection.tree(TaskIntrospection.of(c), now));
                }
                if (lines.isEmpty()) lines.add("No active chains.");
            }
            case WHY -> lines = TaskIntrospection.why(running, TaskIntrospection.of(mod.getUserTaskChain()), now);
            default -> lines = TaskIntrospection.summary(running, now);
        }
        for (String l : lines) mod.log(l);
        finish();
    }
}
