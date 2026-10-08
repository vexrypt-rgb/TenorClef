package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.StringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.compose.CompositionCompiler;
import adris.altoclef.compose.CompositionRun;
import adris.altoclef.compose.CompositionService;

import java.io.IOException;

/**
 * {@code @comp <list|compile|run|stop|status|log|tree> [name]}: command-line twin of the Compositions tab.
 * Never performs anything unless {@code run} is typed.
 */
public class CompositionCommand extends Command {

    private String name = "";

    public CompositionCommand() {
        super("comp", "Compositions: @comp <list|compile|run|stop|status|log|tree> [name]", new StringArg("action"));
    }

    @Override
    public void run(AltoClef mod, String line, Runnable onFinish) throws CommandException {
        String[] p = line.trim().split("\\s+", 2);
        name = p.length > 1 ? p[1].trim() : "";
        super.run(mod, p[0], onFinish);
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String action = parser.get(String.class).toLowerCase();
        CompositionService svc = CompositionService.INSTANCE;
        svc.reconcile(mod);
        try {
            switch (action) {
                case "list" -> mod.log("Compositions: " + svc.store().list());
                case "compile" -> {
                    CompositionCompiler.Result r = svc.compile(name, svc.store().load(name));
                    if (r.ok()) mod.log("Compiled " + name + " OK");
                    else {
                        mod.log("COMPILE FAILURE " + name);
                        r.diagnostics.forEach(d -> mod.log("  " + d));
                        if (r.error != null) mod.log("  " + r.error);
                    }
                }
                case "run" -> {
                    String refused = svc.perform(mod, name, svc.store().load(name));
                    mod.log(refused != null ? "Refused: " + refused : "Performing " + name);
                }
                case "stop" -> {
                    svc.stop(mod);
                    mod.log("Stop requested");
                }
                case "log" -> {
                    CompositionRun r = svc.current();
                    mod.log(r == null ? "No run." : "\n" + r.formatLog());
                }
                case "tree" -> {
                    CompositionRun r = svc.current();
                    mod.log(r == null ? "No run." : "\n" + r.formatTree(System.currentTimeMillis()));
                }
                default -> {
                    CompositionRun r = svc.current();
                    mod.log(r == null ? "IDLE" : r.compositionName + ": " + r.state()
                            + (r.failureKind() != CompositionRun.FailureKind.NONE
                            ? " " + r.failureKind() + " " + r.failureText() : "")
                            + " (" + r.durationMs() + "ms)");
                }
            }
        } catch (IOException e) {
            Debug.logWarning("comp: cannot read '" + name + "': " + e.getMessage());
        }
        finish();
    }
}
