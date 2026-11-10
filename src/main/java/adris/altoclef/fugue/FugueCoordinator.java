package adris.altoclef.fugue;

import adris.altoclef.tasksystem.FailureReason;
import adris.altoclef.tasksystem.TaskFailure;
import adris.altoclef.tasksystem.TaskResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fugue's decision core, ticked once per {@link FugueTask} tick. Pure: it sees the world only through a
 * {@link FugueWorldView} and the running child only through a {@link ChildReport}, so every rule here is unit-tested
 * without Minecraft.
 *
 * <p>Outcome contract. A node succeeds only when Fugue's own predicate holds: the player holds at least the absolute
 * count fixed when the attempt started. A child that finishes without that is a failed attempt, whatever it
 * reported. The objective succeeds only when the goal count is held. Cancellation is the Task layer's CANCELLED.
 *
 * <p>Replanning is event-driven: a fresh netted plan is built whenever no node is running (start, after a node
 * succeeds or fails), always from current inventory, so a stolen or consumed input re-opens its node.
 */
public final class FugueCoordinator {

    public enum Status { RUNNING, SUCCEEDED, FAILED }

    public static final class Config {
        /** Consecutive failed attempts without progress allowed per node when the failure looks transient. */
        public int maxFailures = 2;
        /** Failed attempts allowed per node in total while attempts still make progress. */
        public int maxFailuresWithProgress = 8;
        /**
         * Ticks a still-running child must keep reporting FAILURE/BLOCKED before Fugue treats it as terminal.
         * Parents copy a grandchild's failure for as long as it lasts ({@code Task.absorbChildOutcome}) and the
         * stock user chain only ends on {@code isFinished()}/stop, so a one-tick ABORT from a stale pickup target
         * is noise the child retargets past, not the end of the attempt.
         */
        public long failureGraceTicks = 40;
        /** Ticks a node may run without its item count rising before the attempt is abandoned. */
        public long noProgressTicks = 20L * 150;
        /** Total node starts per objective; a livelock guard. */
        public int maxNodeStarts = 60;

        // v1.1 stall recovery. Off by default; off is exactly v1.
        /** Classify stalls on the child's mining target and recover by excluding that target. */
        public boolean recovery = false;
        /** REPEATED_TARGET_FAILURE: one target pursued this long with no item gained. */
        public long stallTicks = 20L * 45;
        /** ENVIRONMENTAL_TRAP: at least this many water escapes on one target, and */
        public int trapEscapes = 2;
        /** this long on it with no item gained. */
        public long trapTicks = 20L * 20;
        /** Recoveries per objective, and per node. */
        public int maxRecoveries = 3;
        public int maxRecoveriesPerNode = 2;
        /** Ticks the stopped child may keep its subtask or navigation before recovery fails explicitly. */
        public long releaseTimeoutTicks = 100;
    }

    /** v1.1 stall classes. Each has one observable predicate ({@link #classify}). */
    public enum Stall {
        /** The child keeps escaping water while pursuing one target and gains nothing. */
        ENVIRONMENTAL_TRAP,
        /** The child pursues one target for {@link Config#stallTicks} and gains nothing. */
        REPEATED_TARGET_FAILURE
    }

    /** What the running child looked like after its last tick (null = no child yet). */
    public static final class ChildReport {
        final boolean finished;
        final TaskResult result;
        final TaskFailure failure;
        /** Block the child's deepest {@code DestroyBlockTask} is breaking ("x,y,z"), or null. */
        final String target;
        /** The child's subtask chain is escaping water ({@code GetOutOfWaterTask}). */
        final boolean escaping;
        /** Not a report on a child: Fugue holds none, and {@link #released} says whether the old one is gone. */
        final boolean detached;
        final boolean released;

        public ChildReport(boolean finished, TaskResult result, TaskFailure failure) {
            this(finished, result, failure, null, false);
        }

        public ChildReport(boolean finished, TaskResult result, TaskFailure failure, String target, boolean escaping) {
            this(finished, result, failure, target, escaping, false, false);
        }

        private ChildReport(boolean finished, TaskResult result, TaskFailure failure, String target, boolean escaping,
                            boolean detached, boolean released) {
            this.finished = finished;
            this.result = result;
            this.failure = failure;
            this.target = target;
            this.escaping = escaping;
            this.detached = detached;
            this.released = released;
        }

        /** No child is installed; {@code released}: the previous one has been stopped and removed. */
        public static ChildReport detached(boolean released) {
            return new ChildReport(false, null, null, null, false, true, released);
        }
    }

    public static final class Decision {
        public enum Type {
            /** Run (or keep running) the catalogue task for {@link #key} up to {@link #target}. */
            RUN,
            /** Drop the current child this tick (so the next attempt starts from a fresh task instance). */
            RELEASE,
            /** Drop the child and run nothing while a stall recovery waits for it to give up control. */
            RECOVER,
            SUCCEEDED,
            FAILED
        }

        public final Type type;
        public final String key;
        public final int target;
        /** RUN: a new attempt starts here (the previous child, if any, must not be reused). */
        public final boolean fresh;
        public final TaskFailure failure;

        private Decision(Type type, String key, int target, boolean fresh, TaskFailure failure) {
            this.type = type;
            this.key = key;
            this.target = target;
            this.fresh = fresh;
            this.failure = failure;
        }

        @Override
        public String toString() {
            return type + (key != null ? " " + key + "->" + target + (fresh ? " fresh" : "") : "") + (failure != null ? " " + failure : "");
        }
    }

    static final class NodeState {
        int starts;
        int successes;
        int failedAttempts;
        /** Failed attempts since this node last made progress. */
        int failuresWithoutProgress;
        TaskFailure lastFailure;
        boolean exhausted;
        int recoveries;
    }

    private static final class Attempt {
        final String key;
        final String station;
        final int target;
        final int startCount;
        final long startTick;
        int best;
        long lastProgressTick;
        /** Tick the child began reporting FAILURE/BLOCKED without finishing (-1 = not failing). */
        long failingSince = -1;
        // v1.1 stall observation
        String mineTarget;
        long mineTargetSince;
        int escapes;
        boolean wasEscaping;
        boolean stallLogged;
        /** Set when a recovery that excluded this target started the attempt (null = not a recovery attempt). */
        String recoveredFrom;
        Stall recoveredKind;

        Attempt(String key, String station, int target, int startCount, long tick) {
            this.key = key;
            this.station = station;
            this.target = target;
            this.startCount = startCount;
            this.startTick = tick;
            this.best = startCount;
            this.lastProgressTick = tick;
        }
    }

    private final String goal;
    private final int goalCount;
    private final FuguePlanner planner;
    private final FugueScheduler scheduler = new FugueScheduler();
    private final Config config;
    private final Consumer<String> log;

    private final Map<String, NodeState> states = new HashMap<>();
    private Status status = Status.RUNNING;
    private TaskFailure failure;
    private boolean blocked;
    /** The attempt being judged ended because the child reported BLOCKED. */
    private boolean attemptBlocked;
    private Attempt current;
    private String lastKey;
    private String lastStation;
    private FuguePlan lastPlan;
    private long tick;
    /** A stall recovery waiting for the old child to release control (null = none). */
    private Recovery recovering;
    /** Targets this objective excluded; forgotten when it ends. */
    private final List<String> exclusions = new ArrayList<>();
    private boolean exclusionsReleased;
    private FugueWorldView lastView;

    private static final class Recovery {
        final Stall kind;
        final String key;
        final String target;
        final long since;
        /** The child escaped water at least once on this target: also exclude the resources around the bot. */
        final boolean wet;

        Recovery(Stall kind, String key, String target, long since, boolean wet) {
            this.kind = kind;
            this.key = key;
            this.target = target;
            this.since = since;
            this.wet = wet;
        }
    }

    // Metrics (read by @fugue status and FugueBench).
    private int replans;
    private long planNanosTotal;
    private long planNanosMax;
    private int nodeStarts;
    private int nodeSuccesses;
    private int failedAttempts;
    private int retries;
    private int reopens;
    private int switches;
    private int stalls;
    private int recoveries;
    private int recoveriesResumed;
    private int recoveriesIneffective;
    private final List<String> sequence = new ArrayList<>();

    public FugueCoordinator(String goal, int goalCount, FuguePlanner planner, Config config, Consumer<String> log) {
        this.goal = goal;
        this.goalCount = Math.max(1, goalCount);
        this.planner = planner;
        this.config = config != null ? config : new Config();
        this.log = log != null ? log : s -> {};
    }

    public Decision step(FugueWorldView view, ChildReport report) {
        tick++;
        lastView = view;
        if (status == Status.SUCCEEDED) return new Decision(Decision.Type.SUCCEEDED, null, 0, false, null);
        if (status == Status.FAILED) return new Decision(Decision.Type.FAILED, null, 0, false, failure);

        if (view.count(goal) >= goalCount) {
            if (current != null) nodeSucceeded(current, view.count(current.key));
            recovering = null;
            status = Status.SUCCEEDED;
            log.accept("objective SUCCESS " + goal + " held=" + view.count(goal) + "/" + goalCount + " " + metrics());
            releaseExclusions(view);
            return new Decision(Decision.Type.SUCCEEDED, null, 0, false, null);
        }

        if (recovering != null) return continueRecovery(view, report);

        if (current != null) {
            int c = view.count(current.key);
            if (c > current.best) {
                if (current.recoveredFrom != null && current.best == current.startCount) {
                    recoveriesResumed++;
                    log.accept("recovery RESUMED " + current.key + " gained an item " + (tick - current.startTick)
                            + " ticks after excluding " + current.recoveredFrom + " (" + current.recoveredKind + ")");
                }
                current.best = c;
                current.lastProgressTick = tick;
            }
            if (c >= current.target) {
                nodeSucceeded(current, c);
                // fall through: pick the next node now
            } else {
                TaskFailure f = attemptFailure(report, c);
                if (f == null && tick - current.lastProgressTick > config.noProgressTicks) {
                    f = new TaskFailure(FailureReason.TIMEOUT, current.key + " made no progress for " + config.noProgressTicks + " ticks", true);
                }
                if (f == null && config.recovery && report != null && !report.detached) {
                    Decision r = observeStall(report);
                    if (r != null) return r;
                }
                if (f == null) return new Decision(Decision.Type.RUN, current.key, current.target, false, null);
                return attemptFailed(f);
            }
        }
        return startNext(view);
    }

    /** The child ended (or reported a terminal problem) without Fugue's postcondition holding. */
    private TaskFailure attemptFailure(ChildReport report, int held) {
        attemptBlocked = false;
        if (report == null || report.detached) return null;
        TaskResult r = report.result;
        boolean failing = r == TaskResult.BLOCKED
                || (r == TaskResult.FAILURE && (report.failure == null || !report.failure.isRecoverable()));
        if (!failing) {
            current.failingSince = -1;
        } else if (!report.finished) {
            if (current.failingSince < 0) current.failingSince = tick;
            if (tick - current.failingSince < config.failureGraceTicks) return null;
        }
        if (r == TaskResult.BLOCKED) {
            attemptBlocked = true;
            return report.failure != null ? report.failure : new TaskFailure(FailureReason.UNKNOWN, current.key + " blocked", true);
        }
        if (failing) {
            return report.failure != null ? report.failure : new TaskFailure(FailureReason.UNKNOWN, current.key + " failed", false);
        }
        if (r == TaskResult.CANCELLED) {
            // Something outside Fugue stopped our child; not success.
            return new TaskFailure(FailureReason.UNKNOWN, current.key + " child cancelled", true);
        }
        if (report.finished) {
            return new TaskFailure(FailureReason.UNKNOWN, current.key + " child finished holding " + held + "/" + current.target, false);
        }
        return null;
    }

    private void nodeSucceeded(Attempt a, int held) {
        NodeState st = state(a.key);
        st.successes++;
        nodeSuccesses++;
        log.accept("node SUCCESS " + a.key + " held=" + held + "/" + a.target + " ticks=" + (tick - a.startTick));
        lastStation = a.station;
        current = null;
    }

    private Decision attemptFailed(TaskFailure f) {
        Attempt a = current;
        current = null;
        NodeState st = state(a.key);
        st.failedAttempts++;
        st.lastFailure = f;
        failedAttempts++;
        boolean progress = a.best > a.startCount;
        st.failuresWithoutProgress = progress ? 0 : st.failuresWithoutProgress + 1;
        boolean canRetry = progress
                ? st.failedAttempts < config.maxFailuresWithProgress
                : isTransient(f.getReason()) && st.failuresWithoutProgress < config.maxFailures;
        log.accept("node FAILED-ATTEMPT " + a.key + " #" + st.failedAttempts + " " + f.getReason() + " '" + f.getMessage()
                + "' held=" + a.best + "/" + a.target + " progress=" + progress + " -> " + (canRetry ? "retry" : "give up"));
        if (canRetry) {
            retries++;
            return new Decision(Decision.Type.RELEASE, a.key, 0, false, f);
        }
        st.exhausted = true;
        return fail(new TaskFailure(f.getReason(), "node " + a.key + " exhausted: " + f.getMessage(), false), attemptBlocked);
    }

    private Decision startNext(FugueWorldView view) {
        long t0 = System.nanoTime();
        FuguePlan plan = planner.plan(goal, goalCount, view);
        long dt = System.nanoTime() - t0;
        replans++;
        planNanosTotal += dt;
        planNanosMax = Math.max(planNanosMax, dt);
        lastPlan = plan;
        if (plan.isError()) {
            return fail(new TaskFailure(plan.getError(), "plan: " + plan.getErrorMessage(), false), false);
        }
        if (plan.isEmpty()) {
            return fail(new TaskFailure(FailureReason.UNKNOWN, "nothing missing but " + goal + " not held", false), false);
        }
        Set<String> exhausted = new HashSet<>();
        for (Map.Entry<String, NodeState> e : states.entrySet()) if (e.getValue().exhausted) exhausted.add(e.getKey());
        FugueNode next = scheduler.select(plan.ready(), exhausted, view, lastStation);
        if (next == null) {
            return fail(new TaskFailure(FailureReason.PRECONDITION_FAILED, "no runnable node in " + plan.summary(), false), false);
        }
        if (nodeStarts >= config.maxNodeStarts) {
            return fail(new TaskFailure(FailureReason.TIMEOUT, "node start budget " + config.maxNodeStarts + " spent", false), false);
        }
        NodeState st = state(next.getKey());
        if (st.successes > 0) {
            reopens++;
            log.accept("node REOPENED " + next.getKey() + " (held count fell below need)");
        }
        if (lastKey != null && !lastKey.equals(next.getKey())) switches++;
        lastKey = next.getKey();
        st.starts++;
        nodeStarts++;
        sequence.add(next.getKey());
        int held = view.count(next.getKey());
        current = new Attempt(next.getKey(), next.getRecipe().getStation(), held + next.getNeed(), held, tick);
        log.accept("node START " + next.getKey() + " target=" + current.target + " (need " + next.getNeed() + ", held " + held
                + ") attempt=" + st.starts + " plan=[" + plan.summary() + "] planUs=" + dt / 1000);
        return new Decision(Decision.Type.RUN, current.key, current.target, true, null);
    }

    // v1.1 stall recovery

    /** Tracks the child's mining target; starts a recovery when a stall predicate holds and budget remains. */
    private Decision observeStall(ChildReport report) {
        Attempt a = current;
        String t = report.target;
        if (t == null ? a.mineTarget != null : !t.equals(a.mineTarget)) {
            a.mineTarget = t;
            a.mineTargetSince = tick;
            a.escapes = 0;
            a.stallLogged = false;
            if (t != null && a.recoveredFrom != null && exclusions.contains(t)) {
                // The exclusion did not change what the child does: no more recoveries for this node.
                recoveriesIneffective++;
                state(a.key).recoveries = config.maxRecoveriesPerNode;
                log.accept("recovery INEFFECTIVE " + a.key + " child returned to excluded target " + t);
            }
        }
        if (report.escaping && !a.wasEscaping) a.escapes++;
        a.wasEscaping = report.escaping;
        Stall kind = classify(a);
        if (kind == null) return null;
        NodeState st = state(a.key);
        boolean budget = recoveries < config.maxRecoveries && st.recoveries < config.maxRecoveriesPerNode
                && !exclusions.contains(a.mineTarget);
        if (!budget) {
            if (!a.stallLogged) {
                a.stallLogged = true;
                stalls++;
                log.accept("stall " + kind + " " + a.key + " target=" + a.mineTarget + " -> no recovery left (recoveries="
                        + recoveries + " node=" + st.recoveries + "); v1 limits apply");
            }
            return null;
        }
        stalls++;
        recoveries++;
        st.recoveries++;
        log.accept("stall " + kind + " " + a.key + " target=" + a.mineTarget + " idle=" + idleTicks(a) + " escapes=" + a.escapes
                + " held=" + a.best + "/" + a.target + " -> RECOVER #" + recoveries + " (stop child, exclude target, restart)");
        recovering = new Recovery(kind, a.key, a.mineTarget, tick, a.escapes > 0);
        current = null;
        return new Decision(Decision.Type.RECOVER, a.key, 0, false, null);
    }

    /** Ticks on the current target without an item gained: from the later of "target first seen" and "last gain". */
    private long idleTicks(Attempt a) {
        return tick - Math.max(a.mineTargetSince, a.lastProgressTick);
    }

    /** The stall predicates; null = not stalled (movement, mining, crafting and short pauses never match). */
    private Stall classify(Attempt a) {
        if (a.mineTarget == null) return null;
        long idle = idleTicks(a);
        if (a.escapes >= config.trapEscapes && idle >= config.trapTicks) return Stall.ENVIRONMENTAL_TRAP;
        if (idle >= config.stallTicks) return Stall.REPEATED_TARGET_FAILURE;
        return null;
    }

    /** Waits for the stopped child to release control, excludes its target, then starts a fresh attempt. */
    private Decision continueRecovery(FugueWorldView view, ChildReport report) {
        Recovery r = recovering;
        boolean released = report != null && report.detached && report.released && view.navigationIdle();
        if (!released) {
            if (tick - r.since > config.releaseTimeoutTicks) {
                recovering = null;
                return fail(new TaskFailure(FailureReason.UNKNOWN, "recovery: " + r.key + " child did not release control within "
                        + config.releaseTimeoutTicks + " ticks", false), false);
            }
            return new Decision(Decision.Type.RECOVER, r.key, 0, false, null);
        }
        recovering = null;
        List<String> added = view.exclude(r.target, r.wet);
        boolean applied = added.contains(r.target);
        exclusions.addAll(added);
        log.accept("recovery " + (applied ? "EXCLUDED " : "COULD-NOT-EXCLUDE ") + r.target
                + (added.size() > 1 ? " +" + (added.size() - 1) + " neighbour(s)" : "") + " for " + r.key + " after "
                + (tick - r.since) + " ticks; restarting");
        if (!applied) state(r.key).recoveries = config.maxRecoveriesPerNode; // no safe action left for this node
        Decision d = startNext(view);
        if (current != null && applied) {
            if (current.key.equals(r.key)) {
                current.recoveredFrom = r.target;
                current.recoveredKind = r.kind;
            } else {
                // The replan no longer needs the stalled node now; its progress is not evidence the exclusion worked.
                log.accept("recovery: replan moved on from " + r.key + " to " + current.key + " (not counted as resumed)");
            }
        }
        return d;
    }

    /** Forgets this objective's exclusions (objective ended or was stopped). Idempotent. */
    public void releaseExclusions(FugueWorldView view) {
        if (exclusionsReleased) return;
        exclusionsReleased = true;
        FugueWorldView v = view != null ? view : lastView;
        if (v != null) for (String t : exclusions) v.forget(t);
        if (!exclusions.isEmpty()) log.accept("recovery released " + exclusions.size() + " exclusion(s)");
    }

    private Decision fail(TaskFailure f, boolean blocked) {
        status = Status.FAILED;
        failure = f;
        this.blocked = blocked;
        log.accept("objective " + (blocked ? "BLOCKED " : "FAILURE ") + f.getReason() + " '" + f.getMessage() + "' " + metrics());
        releaseExclusions(lastView);
        return new Decision(Decision.Type.FAILED, null, 0, false, failure);
    }

    /** The objective ended because a node stayed blocked (e.g. inventory full), not because it failed outright. */
    public boolean isBlocked() {
        return status == Status.FAILED && blocked;
    }

    static boolean isTransient(FailureReason r) {
        if (r == null) return false;
        switch (r) {
            case NO_PATH:
            case TIMEOUT:
            case TARGET_UNAVAILABLE:
            case WORLD_CHANGED:
            case DANGER:
            case BACKEND_FAILURE:
                return true;
            default:
                return false;
        }
    }

    private NodeState state(String key) {
        return states.computeIfAbsent(key, k -> new NodeState());
    }

    public String metrics() {
        return "replans=" + replans + " planUsTotal=" + planNanosTotal / 1000 + " planUsMax=" + planNanosMax / 1000
                + " starts=" + nodeStarts + " ok=" + nodeSuccesses + " failedAttempts=" + failedAttempts + " retries=" + retries
                + " reopens=" + reopens + " switches=" + switches
                + (config.recovery ? " stalls=" + stalls + " recoveries=" + recoveries + " resumed=" + recoveriesResumed
                + " ineffective=" + recoveriesIneffective : "")
                + " seq=" + String.join(">", sequence);
    }

    public Status getStatus() { return status; }
    public boolean isTerminal() { return status != Status.RUNNING; }
    public TaskFailure getFailure() { return failure; }
    public String getGoal() { return goal; }
    public int getGoalCount() { return goalCount; }
    public String getCurrentKey() { return current != null ? current.key : null; }
    public int getCurrentTarget() { return current != null ? current.target : 0; }
    public FuguePlan getLastPlan() { return lastPlan; }
    public int getReplans() { return replans; }
    public long getPlanNanosTotal() { return planNanosTotal; }
    public long getPlanNanosMax() { return planNanosMax; }
    public int getNodeStarts() { return nodeStarts; }
    public int getNodeSuccesses() { return nodeSuccesses; }
    public int getFailedAttempts() { return failedAttempts; }
    public int getRetries() { return retries; }
    public int getReopens() { return reopens; }
    public int getSwitches() { return switches; }
    public List<String> getSequence() { return Collections.unmodifiableList(sequence); }
    public int getStalls() { return stalls; }
    public int getRecoveries() { return recoveries; }
    public int getRecoveriesResumed() { return recoveriesResumed; }
    public int getRecoveriesIneffective() { return recoveriesIneffective; }
    public boolean isRecovering() { return recovering != null; }
    public List<String> getExclusions() { return Collections.unmodifiableList(exclusions); }
    NodeState getState(String key) { return states.get(key); }
}
