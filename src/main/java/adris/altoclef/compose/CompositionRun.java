package adris.altoclef.compose;

import adris.altoclef.tasksystem.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Observation record for one Perform. Only holds what was actually seen on the live task tree; nothing here
 * drives a task. Mutated on the game thread, read by the GUI on the same thread.
 */
public final class CompositionRun {

    public enum State { IDLE, PERFORMING, SUCCEEDED, FAILED, CANCELLED }

    /** Why a run ended in FAILED. */
    public enum FailureKind { NONE, COMPILE_FAILURE, RUNTIME_EXCEPTION, TASK_FAILURE }

    public enum EventType {
        COMPOSITION_STARTED, TASK_STARTED, TASK_CHILD_STARTED, TASK_SUCCEEDED, TASK_FAILED, TASK_CANCELLED,
        TASK_EXCEPTION, COMPILE_ERROR, TASK_RETRY, TASK_BLOCKED, TASK_RECOVERY,
        MOVEMENT_STARTED, MOVEMENT_SUCCEEDED, MOVEMENT_FAILED, MOVEMENT_CANCELLED, COMPOSITION_FINISHED
    }

    public static final class Event {
        public final long timeMs;
        public final EventType type;
        public final String detail;
        public final String stack;

        Event(long timeMs, EventType type, String detail, String stack) {
            this.timeMs = timeMs;
            this.type = type;
            this.detail = detail;
            this.stack = stack;
        }
    }

    /** One task seen in the tree. */
    public static final class Node {
        public final Task task;
        public final String name;
        public final String className;
        public final Node parent;
        public final int depth;
        public final long startMs;
        public long endMs;
        public long ticks;
        public TaskResult result = TaskResult.RUNNING;
        public TaskFailure failure;
        public RecoveryDecision recovery;
        public boolean ended;
        public final List<Node> children = new ArrayList<>();

        Node(Task task, Node parent, long now) {
            this.task = task;
            this.name = task.toString();
            this.className = task.getClass().getName();
            this.parent = parent;
            this.depth = parent == null ? 0 : parent.depth + 1;
            this.startMs = now;
        }

        public long durationMs(long now) {
            return (ended ? endMs : now) - startMs;
        }
    }

    public final String compositionName;
    public final long startMs = System.currentTimeMillis();
    private State state = State.PERFORMING;
    private FailureKind failureKind = FailureKind.NONE;
    private String failureText = "";
    private String stackTrace = "";
    private long endMs;
    final List<Event> events = new ArrayList<>();
    final List<Node> nodes = new ArrayList<>();
    final Map<Task, Node> byTask = new IdentityHashMap<>();
    Node root;

    public CompositionRun(String name) {
        this.compositionName = name;
        log(EventType.COMPOSITION_STARTED, name, null);
    }

    public synchronized State state() {
        return state;
    }

    public synchronized boolean terminal() {
        return state != State.PERFORMING && state != State.IDLE;
    }

    public synchronized FailureKind failureKind() {
        return failureKind;
    }

    public synchronized String failureText() {
        return failureText;
    }

    public synchronized String stackTrace() {
        return stackTrace;
    }

    public synchronized long durationMs() {
        return (terminal() ? endMs : System.currentTimeMillis()) - startMs;
    }

    public synchronized List<Event> events() {
        return new ArrayList<>(events);
    }

    public synchronized List<Node> nodes() {
        return new ArrayList<>(nodes);
    }

    public synchronized Node root() {
        return root;
    }

    /** "Clear output": drops the event list, keeps the lifecycle state and the observed tree. */
    public synchronized void clearEvents() {
        events.clear();
    }

    public synchronized void log(EventType type, String detail, String stack) {
        events.add(new Event(System.currentTimeMillis(), type, detail == null ? "" : detail, stack));
    }

    /** Ends the run once; later calls are ignored so the first real cause wins. */
    public synchronized boolean finish(State terminalState, FailureKind kind, String text, Throwable t) {
        if (terminal()) return false;
        state = terminalState;
        failureKind = kind;
        failureText = text == null ? "" : text;
        if (t != null) stackTrace = stackOf(t);
        endMs = System.currentTimeMillis();
        log(EventType.COMPOSITION_FINISHED, terminalState + (failureText.isEmpty() ? "" : ": " + failureText), null);
        return true;
    }

    public static String stackOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    // ---- observation of the live tree (called from CompositionTask every tick) ----

    synchronized void observe(Task rootTask) {
        long now = System.currentTimeMillis();
        List<Task> chain = new ArrayList<>();
        for (Task t = rootTask; t != null && chain.size() < 64; t = t.getSub()) chain.add(t);

        Node parent = null;
        List<Node> live = new ArrayList<>();
        for (int d = 0; d < chain.size(); d++) {
            Task t = chain.get(d);
            Node n = byTask.get(t);
            if (n == null) {
                n = new Node(t, parent, now);
                byTask.put(t, n);
                nodes.add(n);
                if (parent != null) parent.children.add(n);
                if (d == 0) root = n;
                else log(d == 1 ? EventType.TASK_STARTED : EventType.TASK_CHILD_STARTED,
                        t + " (parent " + parent.className.substring(parent.className.lastIndexOf('.') + 1) + ")", null);
            }
            n.ticks++;
            update(n, t);
            live.add(n);
            parent = n;
        }
        // Nodes that left the active path have ended: record how they ended, as the task itself reports it.
        for (Node n : nodes) {
            if (n.ended || live.contains(n)) continue;
            update(n, n.task);
            if (n.result == TaskResult.RUNNING) {
                n.result = TaskResult.CANCELLED;
                log(EventType.TASK_CANCELLED, n.name + " (left the active path)", null);
            }
            n.ended = true;
            n.endMs = now;
        }
    }

    private void update(Node n, Task t) {
        TaskResult r = t.getLastResult();
        TaskFailure f = t.getLastFailure();
        RecoveryDecision rec = t.getLastRecovery();
        if (f != null) n.failure = f;
        if (rec != null && !rec.equals(n.recovery)) {
            n.recovery = rec;
            log(EventType.TASK_RECOVERY, n.name + " -> " + rec, null);
        }
        if (r != n.result) {
            n.result = r;
            switch (r) {
                case SUCCESS -> log(EventType.TASK_SUCCEEDED, n.name, null);
                case FAILURE -> log(EventType.TASK_FAILED, n.name + (f != null ? " [" + f + "]" : ""), null);
                case CANCELLED -> log(EventType.TASK_CANCELLED, n.name, null);
                case RETRY -> log(EventType.TASK_RETRY, n.name + (f != null ? " [" + f + "]" : ""), null);
                case BLOCKED -> log(EventType.TASK_BLOCKED, n.name + (f != null ? " [" + f + "]" : ""), null);
                default -> { }
            }
        }
    }

    synchronized void closeAll() {
        long now = System.currentTimeMillis();
        for (Node n : nodes) {
            if (n.ended) continue;
            update(n, n.task);
            if (n.result == TaskResult.RUNNING) {
                // Still running when the run ended: the thrower failed, everything else was cut short.
                if (n == root && failureKind == FailureKind.RUNTIME_EXCEPTION) {
                    n.result = TaskResult.FAILURE;
                    log(EventType.TASK_EXCEPTION, n.name + " (run aborted by exception)", null);
                } else {
                    n.result = TaskResult.CANCELLED;
                    log(EventType.TASK_CANCELLED, n.name + " (run ended)", null);
                }
            }
            n.ended = true;
            n.endMs = now;
        }
    }

    // ---- text output ----

    public synchronized String formatLog() {
        StringBuilder sb = new StringBuilder();
        for (Event e : events) {
            sb.append(String.format("+%6dms  %-22s %s%n", e.timeMs - startMs, e.type, e.detail));
            if (e.stack != null) sb.append(e.stack).append('\n');
        }
        return sb.toString();
    }

    public synchronized String formatTree(long now) {
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            sb.append("  ".repeat(n.depth)).append(n.name.trim())
                    .append("  [").append(n.result).append(", ").append(n.durationMs(now)).append("ms, ")
                    .append(n.ticks).append(" ticks]").append('\n');
        }
        return sb.toString();
    }
}
