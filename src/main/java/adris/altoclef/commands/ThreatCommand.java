package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.planner.GoalManager;
import adris.altoclef.threat.ThreatAssessment;
import adris.altoclef.threat.ThreatMonitor;

/**
 * Debug hook for Phase 8 threat layer.
 * <pre>
 * {@code @threat}
 * {@code @threat status}
 * </pre>
 */
public class ThreatCommand extends Command {

    public ThreatCommand() {
        super("threat", "Phase 8 threat monitor status (combat/survival unify)");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        adris.altoclef.threat.OstinatoThreatBridge.View intelligence =
                adris.altoclef.threat.OstinatoThreatBridge.read(mod.getClientBaritone());
        mod.log("Ostinato intelligence: " + intelligence.status + " tick=" + intelligence.tick
                + " highest=" + intelligence.highestLevel);
        for (String source : intelligence.sources) mod.log("  " + source);
        ThreatMonitor monitor = mod.getThreatMonitor();
        if (monitor == null) {
            mod.log("ThreatMonitor not initialized");
            finish();
            return;
        }
        ThreatAssessment a = monitor.getLatest();
        mod.log("Threat: " + monitor.summarize());
        if (a != null && a.getTimeToDanger() != null) {
            mod.log("  timeToDanger≈" + a.getTimeToDanger() + "s");
        }
        if (a != null && a.getSuggestedAction() != null) {
            mod.log("  suggestedRecovery=" + a.getSuggestedAction());
        }
        GoalManager gm = GoalCommand.getActiveManager();
        if (gm != null) {
            mod.log("  activeGoal: " + gm.summarize());
        } else {
            mod.log("  activeGoal: (none)");
        }
        finish();
    }
}
