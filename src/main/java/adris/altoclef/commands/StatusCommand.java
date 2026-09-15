package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.tasks.speedrun.SpeedrunBeatMinecraftTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;

import java.util.List;

public class StatusCommand extends Command {
    public StatusCommand() {
        super("status", "Get status of currently executing command / task chain");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        List<Task> tasks = mod.getUserTaskChain().getTasks();
        if (tasks.isEmpty()) {
            mod.log("No tasks currently running.");
        } else {
            mod.log("CURRENT TASK: " + tasks.get(0).toString());
            if (tasks.size() > 1) {
                for (int i = 1; i < tasks.size(); i++) {
                    mod.log("  chain[" + i + "]: " + tasks.get(i));
                }
            }
            if (mod.isPaused()) {
                mod.log("Bot is PAUSED (@unpause to resume)");
            }
            Task current = mod.getUserTaskChain().getCurrentTask();
            if (current instanceof SpeedrunBeatMinecraftTask speedrun) {
                mod.log("@testrun phase=" + speedrun.getCurrentPhase()
                        + " dim=" + WorldHelper.getCurrentDimension());
                String summary = speedrun.getDebugSummary();
                if (summary != null && !summary.isBlank()) {
                    mod.log(summary);
                }
            }
        }
        finish();
    }
}
