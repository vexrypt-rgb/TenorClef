package adris.altoclef.compose;

import adris.altoclef.AltoClef;
import baritone.api.event.events.PathEvent;
import baritone.api.event.listener.AbstractGameEventListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns the single current Composition run. Performing is just {@code mod.runUserTask}; this class adds the
 * lifecycle (IDLE/PERFORMING/SUCCEEDED/FAILED/CANCELLED), compile caching and movement-event logging.
 */
public final class CompositionService {
    public static final CompositionService INSTANCE = new CompositionService();

    private final CompositionStore store = CompositionStore.standard();
    private CompositionRun current;
    private CompositionCompiler.Result lastCompile;
    private String lastCompiledSource;
    private String lastCompiledName;
    private boolean listenerRegistered;

    // movement observation (from the movement controller, plus Baritone PathEvents when they are emitted)
    private boolean moveActive;
    private boolean moveTerminalLogged;

    private CompositionService() {}

    public CompositionStore store() {
        return store;
    }

    public synchronized CompositionRun current() {
        return current;
    }

    public synchronized CompositionCompiler.Result lastCompile() {
        return lastCompile;
    }

    public synchronized String lastCompiledName() {
        return lastCompiledName;
    }

    /** "Clear output": forget the compile diagnostics shown in the log. */
    public synchronized void clearCompile() {
        if (lastCompile != null && !lastCompile.ok()) lastCompile = null;
    }

    public synchronized boolean isPerforming() {
        return current != null && !current.terminal();
    }

    public synchronized CompositionCompiler.Result compile(String name, String source) {
        lastCompile = CompositionCompiler.compile(source);
        lastCompiledSource = source;
        lastCompiledName = name;
        return lastCompile;
    }

    /** Returns null on success, else the reason Perform was refused. */
    public synchronized String perform(AltoClef mod, String name, String source) {
        return perform(mod, name, source, false);
    }

    /** Runs the last successful compile again without recompiling (the editor text may have changed since). */
    public synchronized String reperform(AltoClef mod) {
        if (lastCompile == null || !lastCompile.ok()) return "Nothing compiled to re-perform. Use Perform.";
        return perform(mod, lastCompiledName, lastCompiledSource, true);
    }

    private String perform(AltoClef mod, String name, String source, boolean reuse) {
        reconcile(mod);
        if (isPerforming()) return "A Composition is already being performed (" + current.compositionName + "). Stop it first.";
        if (!AltoClef.inGame()) return "Not in a world.";
        // A finished Composition's task may linger in the chain for a tick; that is not "another task".
        if (mod.getUserTaskChain().isActive()
                && !(mod.getUserTaskChain().getCurrentTask() instanceof CompositionTask)) return "Another user task is running. Stop it first.";

        if (!reuse && (lastCompile == null || !source.equals(lastCompiledSource) || !name.equals(lastCompiledName))) {
            compile(name, source);
        }
        CompositionCompiler.Result c = lastCompile;
        if (!c.ok()) {
            CompositionRun failed = new CompositionRun(name);
            for (CompositionCompiler.Diagnostic d : c.diagnostics) {
                failed.log(CompositionRun.EventType.COMPILE_ERROR, d.toString(), null);
            }
            if (c.error != null) failed.log(CompositionRun.EventType.COMPILE_ERROR, c.error, null);
            failed.finish(CompositionRun.State.FAILED, CompositionRun.FailureKind.COMPILE_FAILURE,
                    c.diagnostics.size() + " error(s)" + (c.error != null ? ": " + c.error : ""), null);
            current = failed;
            writeLog(failed);
            return null;
        }
        Composition instance;
        try {
            instance = c.type.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            CompositionRun failed = new CompositionRun(name);
            failed.log(CompositionRun.EventType.TASK_EXCEPTION, t.toString(), CompositionRun.stackOf(t));
            failed.finish(CompositionRun.State.FAILED, CompositionRun.FailureKind.RUNTIME_EXCEPTION, t.toString(), t);
            current = failed;
            writeLog(failed);
            return null;
        }
        registerListener(mod);
        moveActive = false;
        moveTerminalLogged = false;
        CompositionRun run = new CompositionRun(name);
        current = run;
        CompositionTask root = new CompositionTask(mod, instance, run);
        mod.runUserTask(root, () -> onChainFinish(run, root));
        return null;
    }

    /** Explicit user cancel. */
    public synchronized void stop(AltoClef mod) {
        CompositionRun run = current;
        if (run == null || run.terminal()) return;
        run.finish(CompositionRun.State.CANCELLED, CompositionRun.FailureKind.NONE, "stopped by user", null);
        run.closeAll();
        mod.cancelUserTask();
        writeLog(run);
    }

    /** Back to IDLE: forget the finished run. Refuses while performing. */
    public synchronized boolean reset() {
        if (isPerforming()) return false;
        current = null;
        return true;
    }

    /** Called after the root decided FAILED: the failed root must stop driving the world. */
    void afterTerminal(AltoClef mod, CompositionRun run) {
        run.closeAll();
        if (mod != null) mod.cancelUserTask();
        writeLog(run);
    }

    private synchronized void onChainFinish(CompositionRun run, CompositionTask root) {
        if (!run.terminal()) {
            // Ended without us deciding: normal success is decided in tick, so this is an external stop.
            run.closeAll();
            run.finish(CompositionRun.State.CANCELLED, CompositionRun.FailureKind.NONE, "user task ended externally", null);
        }
        writeLog(run);
    }

    /**
     * A task replaced by another runUserTask never gets onFinish. If our root was stopped while we still think
     * we are performing, record CANCELLED. Cheap; called from the GUI, commands and Perform.
     */
    public synchronized void reconcile(AltoClef mod) {
        CompositionRun run = current;
        if (run == null || run.terminal() || mod == null) return;
        boolean ours = mod.getUserTaskChain().getCurrentTask() instanceof CompositionTask;
        if (!ours) {
            run.closeAll();
            run.finish(CompositionRun.State.CANCELLED, CompositionRun.FailureKind.NONE,
                    "no longer the active user task", null);
            writeLog(run);
        }
    }

    // ---- movement events ----

    private void registerListener(AltoClef mod) {
        if (listenerRegistered) return;
        try {
            mod.getClientBaritone().getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
                @Override
                public void onPathEvent(PathEvent event) {
                    onPath(event);
                }
            });
            listenerRegistered = true;
        } catch (Throwable ignored) {
            // movement events then come only from polling the movement controller
        }
    }

    private synchronized void onPath(PathEvent event) {
        CompositionRun run = current;
        if (run == null || run.terminal()) return;
        switch (event) {
            case CALC_FINISHED_NOW_EXECUTING -> {
                if (!moveActive) {
                    moveActive = true;
                    moveTerminalLogged = false;
                }
                run.log(CompositionRun.EventType.MOVEMENT_STARTED, "path found, executing (" + event + ")", null);
            }
            case AT_GOAL -> {
                run.log(CompositionRun.EventType.MOVEMENT_SUCCEEDED, "reached goal (" + event + ")", null);
                moveTerminalLogged = true;
                moveActive = false;
            }
            case CALC_FAILED, NEXT_CALC_FAILED -> {
                run.log(CompositionRun.EventType.MOVEMENT_FAILED, "no path (" + event + ")", null);
                moveTerminalLogged = true;
                moveActive = false;
            }
            case CANCELED -> {
                // The engine repeats CANCELED every tick while idle; only the first after a start is news.
                if (moveActive) {
                    run.log(CompositionRun.EventType.MOVEMENT_CANCELLED, "path cancelled (" + event + ")", null);
                    moveTerminalLogged = true;
                    moveActive = false;
                }
            }
            default -> { }
        }
    }

    /** Detects movement start/stop through the TenorClef movement controller (no Baritone access). */
    synchronized void pollMovement(AltoClef mod, CompositionRun run) {
        boolean active;
        try {
            active = mod.getMovement().isPathingOrActive();
        } catch (Throwable t) {
            return;
        }
        if (active && !moveActive) {
            moveActive = true;
            moveTerminalLogged = false;
            run.log(CompositionRun.EventType.MOVEMENT_STARTED, "movement active: " + safeStatus(mod), null);
        } else if (!active && moveActive) {
            moveActive = false;
            if (!moveTerminalLogged) {
                run.log(CompositionRun.EventType.MOVEMENT_CANCELLED,
                        "movement went idle; engine reported no result event", null);
            }
        }
    }

    public String movementStatus(AltoClef mod) {
        return safeStatus(mod);
    }

    private static String safeStatus(AltoClef mod) {
        try {
            return mod.getMovement().statusLine();
        } catch (Throwable t) {
            return "unavailable";
        }
    }

    // ---- persistence of the last run ----

    private void writeLog(CompositionRun run) {
        try {
            Path dir = store.dir();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("last-run.log"),
                    run.compositionName + " -> " + run.state() + "  " + run.failureKind() + " " + run.failureText()
                            + "\n\n" + run.formatLog() + "\n" + run.formatTree(System.currentTimeMillis())
                            + (run.stackTrace().isEmpty() ? "" : "\n" + run.stackTrace()),
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
