package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * S175 — deadman switch for a BLOCKED CLIENT THREAD.
 *
 * <p>Every stall guard in this project (E199, S156, S160, S164, the E98/E99 freeze
 * detector, the S152 onStop marker) is evaluated <em>from inside the tick</em>. They can
 * only ever observe a bot that is still ticking. None of them can observe the failure
 * that actually killed run X.
 *
 * <p>Run X did not die from a driver bug. {@code trace.log} — written by
 * {@link T2Trace} on the client thread, one line per tick — ends at
 * {@code t=8139 clk=7:06.7}. The tick immediately before it covers
 * <b>19.7 seconds of wall clock</b>:
 *
 * <pre>
 *   MOVE t=8128 clk=6:46.5 ph=PORTAL @-1.5,49.0,-77.6 ... sel=6 child=HolePillarTask
 *   MOVE t=8129 clk=7:06.2 ph=PORTAL @ 0.3,51.0,-79.1 ... sel=7 child=HolePillarTask
 *   MOVE t=8139 clk=7:06.7 ph=PORTAL @ 0.3,51.0,-79.1  (last line in the file)
 * </pre>
 *
 * One tick consumed ~20s (the bot moved 2 blocks and rose 2 y during it), then the
 * client thread stopped ticking entirely while the game log kept printing
 * "Refreshed inventory..." and "World save took 0ms" for another 22 minutes. The
 * harness {@code timeout 3300} was the only thing that ever ended the run.
 *
 * <p>A thread that is blocked cannot notice that it is blocked. This watchdog is
 * therefore driven by a daemon thread, and it measures the one quantity the blocked
 * thread cannot fake: <b>the passage of wall-clock time with no tick progress</b>.
 *
 * <p>It deliberately does NOT try to unstick the client — there is no safe way to
 * interrupt an unknown blocking call from outside. It does three things:
 * <ol>
 *   <li>logs the stall loudly and early, with the whole state that is needed to
 *       diagnose it (position, child, phase, inventory) — including the WORST single
 *       tick observed, which is the signature of this failure;</li>
 *   <li>records a LONG single tick as its own fault code (S176) even if the client
 *       recovers, because a 20-second tick is a bug whether or not it is terminal;</li>
 *   <li>after {@link #FATAL_MS}, exits the JVM with a distinctive code so the harness
 *       stops burning the remaining timeout budget, and the run is not mistaken for
 *       "the bot was slow".</li>
 * </ol>
 */
public final class T2Deadman {

    /** Ticks may not take longer than this without comment. Normal worst case is &lt; 1s. */
    private static final long SLOW_TICK_WARN_MS = 2_000L;
    /** A single tick this long is a bug in its own right — always reported as S176. */
    private static final long SLOW_TICK_FAULT_MS = 8_000L;
    /** No tick progress for this long: log a warning, keep waiting. */
    private static final long STALL_WARN_MS = 30_000L;
    /** No tick progress for this long: the run is dead. Log, then hard-exit. */
    private static final long FATAL_MS = 120_000L;
    /** Poll granularity. Cheap enough to run 8x a second. */
    private static final long POLL_MS = 125L;
    /** How often to repeat the "still stalled" line, so a long stall is visible in a tail. */
    private static final long REPEAT_MS = 30_000L;

    /** Exit code the harness can grep for. Distinct from a crash (1) and a clean exit (0). */
    public static final int EXIT_CODE = 87;

    /** Monotonic-ish tick counter. Incremented from the client thread only. */
    private static volatile long tickStamp = 0L;
    /** Wall clock of the last observed tick progress. */
    private static volatile long lastProgressAt = System.currentTimeMillis();
    /** Wall clock when the current (possibly still-running) tick began. */
    private static volatile long currentTickBeganAt = System.currentTimeMillis();
    /** Worst single tick observed this run, in ms. */
    private static volatile long worstTickMs = 0L;
    /** Descriptor of the worst single tick (for the post-mortem). */
    private static volatile String worstTickDesc = "-";

    private static volatile boolean started = false;
    private static volatile boolean armed = false;
    private static Thread thread;

    private T2Deadman() {}

    /**
     * Called once per client tick, at the very END of the mod's tick work.
     *
     * <p>Everything this needs is sampled here, on the client thread, so the watchdog
     * thread never touches the (possibly mid-mutation) game state.
     */
    public static void beat(String desc) {
        long now = System.currentTimeMillis();
        long took = now - currentTickBeganAt;
        currentTickBeganAt = now;
        lastDesc = desc;
        // S181: remember who we are, so the watchdog can read this thread's stack if it
        // later blocks. Cheap (one field write) and it is the only way to see inside a stall.
        clientThread = Thread.currentThread();
        if (took > worstTickMs) {
            worstTickMs = took;
            worstTickDesc = desc;
        }
        // S176: a single tick long enough to be a bug on its own, reported the moment it
        // happens rather than only when it kills the run. Run X's 19.7s stall was invisible
        // because nothing measured tick duration at all.
        if (took >= SLOW_TICK_FAULT_MS) {
            T2Log.force("S176", "one client tick took " + took + "ms (limit "
                    + SLOW_TICK_FAULT_MS + "ms) — " + desc);
        } else if (took >= SLOW_TICK_WARN_MS) {
            T2Log.warn("S175", "slow client tick " + took + "ms — " + desc);
        }
        tickStamp++;
        driverBeats++;
        lastProgressAt = now;
        if (!started) start();
    }

    /**
     * S176 — prove the DRIVER is being ticked, separately from the client thread.
     *
     * <p>{@link #beat} is called by the speedrun driver, so it proves two things at once:
     * that the client thread is turning AND that the driver is being reached. The second
     * of those can fail on its own. Runs S and X both showed a
     * {@code Task STOP: ... interrupted by null} with no matching restart, after which
     * {@link adris.altoclef.tasksystem.Task#tick} returns at
     * {@code if (stopped) return;} forever while the client keeps rendering. That failure
     * produces a log that GROWS but whose driver content has stopped — invisible to
     * every existing guard, and invisible to {@link #beat}, which is no longer called.
     *
     * <p>Called from the client mixin, so it always runs. It compares the driver's own
     * beat counter against the client's and reports a driver that has fallen behind while
     * the client is healthy.
     */
    public static void clientTickAlive() {
        clientTicks++;
        if (!started) {
            // Nothing to compare yet. The driver beats for the first time only once it is
            // running, and the client ticks from the very first frame.
            return;
        }
        long stalled = clientTicks - driverProbeAtLastClientTick;
        if (stalled != 0) {
            driverProbeAtLastClientTick = clientTicks;
            driverProbeBehind = 0;
            return;
        }
        // Driver has not advanced while the client has ticked 20*30 times (~30s).
        if (++driverProbeBehind < 20 * 30) return;
        if (driverProbeBehind % (20 * 30) != 0) return;
        driverProbeBehind = 20 * 30; // clamp so the modulo above keeps firing every 30s
        String msg = "DEADMAN: client is ticking but the speedrun driver has not been ticked"
                + " for " + (driverProbeBehind / 20) + "s+ — the driver is latched stopped"
                + " (look for 'Task STOP' with no matching restart). beating="
                + driverBeats + " clientTicks=" + clientTicks;
        System.out.println("TENORCLEF: " + msg);
        Debug.logHarness(msg);
        try { T2Log.force("S176", msg); } catch (Throwable ignored) {}
        // S186 — RECOVER, do not just report.
        //
        // This counter already detected the latch correctly, but only LOGGED it, so the run
        // still died: the watchdog halted at 120s with code 87. Run AH is the proof —
        // `S152 onStop interrupt=null dead=false ph=NETHER y=47.8` at 27:57.2, then the driver
        // was never ticked again, and at 29:57 the watchdog killed a run that had been
        // working perfectly for 28 minutes.
        //
        // A latched driver is RECOVERABLE. The S181 stack dump proves the client thread is
        // alive and rendering (`glfwWaitEventsTimeout -> RenderSystem.limitDisplayFPS ->
        // MinecraftClient.render`), so the world, the player and the inventory are all intact —
        // only the task chain's main task was dropped. Re-installing it resumes the run with
        // state preserved (S152 keeps sessionLive set, so onStart is swallowed by the E80
        // guard and the phase is NOT re-derived).
        //
        // Bounded to LATCH_RECOVERY_MAX attempts: if re-installing does not take, this is not
        // a latch and the watchdog's fatal exit should still fire.
        if (driverProbeBehind == 20 * 30) recoverDriver();
    }

    private static int recoverAttempts = 0;
    private static final int LATCH_RECOVERY_MAX = 3;

    private static void recoverDriver() {
        if (recoverAttempts >= LATCH_RECOVERY_MAX) return;
        recoverAttempts++;
        try {
            AltoClef mod = AltoClef.getInstance();
            if (mod == null) return;
            String msg = "S186 driver latched for " + (driverProbeBehind / 20)
                    + "s while the client ticked — re-installing ModernSpeedrunTask"
                    + " (attempt " + recoverAttempts + "/" + LATCH_RECOVERY_MAX + ")";
            System.out.println("TENORCLEF: " + msg);
            Debug.logHarness(msg);
            try { T2Log.force("S186", msg); } catch (Throwable ignored) {}
            mod.runUserTask(new adris.altoclef.tasks.speedrun.testrun2.ModernSpeedrunTask());
            // Treat this as progress so the stall watchdog does not also fire on the same gap.
            lastProgressAt = System.currentTimeMillis();
            currentTickBeganAt = lastProgressAt;
            driverProbeAtLastClientTick = clientTicks;
            driverProbeBehind = 0;
        } catch (Throwable t) {
            Debug.logHarness("S186 driver re-install failed: " + t);
        }
    }

    private static volatile long clientTicks = 0L;
    private static volatile long driverBeats = 0L;
    private static long driverProbeAtLastClientTick = 0L;
    private static int driverProbeBehind = 0;

    /** Where the watchdog writes when it has to declare the client dead. */
    private static volatile String runDirHint = null;

    /** Set once at session start so the fatal message can name the run directory. */
    public static void setRunDir(String dir) {
        runDirHint = dir;
    }

    /**
     * True once {@link #beat} has been called at least once. Everything that only runs
     * on the client thread should be gated on this, so a blocked client never causes a
     * falsy-positive "no ticks yet" reading.
     */
    public static boolean ticking() {
        return started;
    }

    /**
     * Arm the fatal exit. Off by default: a run under a debugger, or a run that is
     * deliberately paused, should not be killed. The speedrun driver arms this in
     * {@code onStart()}.
     */
    public static void arm() {
        armed = true;
    }

    public static void disarm() {
        armed = false;
    }

    private static synchronized void start() {
        if (started) return;
        started = true;
        thread = new Thread(T2Deadman::loop, "T2Deadman");
        thread.setDaemon(true); // must never keep the JVM alive on its own
        thread.start();
        Debug.logHarness("DEADMAN: watchdog started (warn " + (STALL_WARN_MS / 1000)
                + "s, fatal " + (FATAL_MS / 1000) + "s, worst-tick limit "
                + (SLOW_TICK_FAULT_MS / 1000) + "s)");
    }

    /**
     * S181: while a tick is stuck, read the client thread's stack from the watchdog thread.
     *
     * <p>This exists because a blocked thread cannot observe its own blockage, and every
     * other channel goes silent with it: the game log stops, and the tick's own trace line
     * never lands (Runtime.halt() skips buffer flushing, so the last buffered lines are lost
     * too). Runs have now ended on 30-40s ticks inside {@code MineAndCollectTask} with
     * literally no evidence of where the time went. {@code Thread.getStackTrace()} on another
     * thread is safe and cheap, and it names the exact frame.
     *
     * <p>Dumps once per tick (keyed on the tick's start instant), so a tick that stays stuck
     * for minutes produces one stack, not one per 125ms poll.
     */
    private static void maybeDumpSlowTick(long sinceProgress) {
        if (sinceProgress < SLOW_TICK_FAULT_MS) return;
        long began = currentTickBeganAt;
        if (dumpedForTickAt == began) return;
        dumpedForTickAt = began;
        Thread t = clientThread;
        if (t == null) return;
        StringBuilder sb = new StringBuilder("DEADMAN: client tick running "
                + (sinceProgress / 1000) + "s — client thread stack, innermost first:");
        int n = 0;
        try {
            for (StackTraceElement e : t.getStackTrace()) {
                sb.append("\n    at ").append(e);
                if (++n >= 30) break;
            }
        } catch (Throwable ignored) {}
        System.out.println("TENORCLEF: " + sb);
        Debug.logHarness(sb.toString());
        try {
            T2Log.force("S181", sb.toString());
        } catch (Throwable ignored) {}
    }

    private static void loop() {
        boolean warned = false;
        long nextRepeatMs = 0;
        while (true) {
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
            // A tick is IN PROGRESS from currentTickBeganAt until lastProgressAt advances.
            // `beat()` sets both to the same instant, so this measures "time since the last
            // completed tick" — which is exactly "how long the CURRENT tick has been running".
            long sinceProgress = System.currentTimeMillis()
                    - Math.max(lastProgressAt, currentTickBeganAt);
            // S181: the current tick is ALREADY over the fault threshold, i.e. it is stuck
            // right now. Dump the client thread's stack while it is stuck — this is the only
            // moment the information exists. Runs are ending on 30-40s ticks inside
            // MineAndCollectTask with NO other evidence: the game log is silent, and the
            // tick's own trace line was lost because Runtime.halt() skips buffer flushing.
            maybeDumpSlowTick(sinceProgress);
            if (sinceProgress < STALL_WARN_MS) {
                warned = false;
                nextRepeatMs = 0;
                continue;
            }
            if (!warned) {
                warned = true;
                // First warning: full state, written to the log AND to disk.
                nextRepeatMs = sinceProgress + REPEAT_MS;
                Debug.logHarness("DEADMAN: no client tick for " + (sinceProgress / 1000)
                        + "s — the client thread is blocked. worstTick=" + worstTickMs
                        + "ms (" + worstTickDesc + ")");
                snapshot("DEADMAN_STALL");
            } else if (sinceProgress >= nextRepeatMs) {
                // Repeat on a threshold comparison, not a modulo window: a modulo test can be
                // skipped outright by 125ms polling jitter, which would leave a ten-minute
                // stall looking like a single line in a tail.
                nextRepeatMs = sinceProgress + REPEAT_MS;
                Debug.logHarness("DEADMAN: still stalled at " + (sinceProgress / 1000)
                        + "s (armed=" + armed + ", worstTick=" + worstTickMs + "ms)");
            }
            if (armed && sinceProgress >= FATAL_MS) {
                fatal(sinceProgress);
            }
        }
    }

    /** Guards against a second fatal pass interleaving with the first. */
    private static volatile boolean fatallyDeclared = false;

    private static void fatal(long sinceProgress) {
        // Idempotent: the loop polls every 125ms, so without this the body could run several
        // times between the check and the halt.
        synchronized (T2Deadman.class) {
            if (fatallyDeclared) return;
            fatallyDeclared = true;
        }
        String msg = "DEADMAN: client thread blocked for " + (sinceProgress / 1000)
                + "s (fatal limit " + (FATAL_MS / 1000) + "s). The run cannot make progress;"
                + " exiting with code " + EXIT_CODE + " instead of burning the timeout."
                + " worstTick=" + worstTickMs + "ms (" + worstTickDesc + ")";
        // Print all three ways: harness stdout, game log, chat.
        System.out.println("TENORCLEF: " + msg);
        Debug.logHarness(msg);
        try {
            T2Log.force("S175", msg);
        } catch (Throwable ignored) {}
        snapshot("DEADMAN_FATAL");
        try { writeFlag(); } catch (Throwable ignored) {}
        System.out.flush();
        Runtime.getRuntime().halt(EXIT_CODE);
    }

    /** Last descriptor passed to {@link #beat}, kept for the fatal snapshot. */
    private static volatile String lastDesc = "-";

    /**
     * S181: the client thread itself, captured in {@link #beat} (which runs on it). The
     * watchdog thread uses this to read the blocked thread's stack — the one piece of
     * evidence a blocked thread can never produce about itself.
     */
    private static volatile Thread clientThread = null;
    /** Guards the stack dump to once per tick (keyed on the tick's start instant). */
    private static volatile long dumpedForTickAt = 0L;

    private static void snapshot(String tag) {
        String body = "# " + tag + " @ " + java.time.Instant.now()
                + "\n# worstTickMs=" + worstTickMs
                + "\n# worstTick=" + worstTickDesc
                + "\n# lastTickDesc=" + lastDesc
                + "\n# tickStamp=" + tickStamp
                + "\n";
        try {
            Path p = path();
            if (p == null) return;
            Files.createDirectories(p.getParent());
            Files.writeString(p, body, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (Throwable ignored) {}
    }

    private static void writeFlag() {
        try {
            Path p = path();
            if (p == null) return;
            Files.writeString(p.resolveSibling("deadman.flag"),
                    "client-thread-blocked worstTickMs=" + worstTickMs + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (Throwable ignored) {}
    }

    private static Path path() {
        String d = runDirHint;
        if (d == null) return null;
        try {
            return Path.of(d, "altoclef", "deadman.txt");
        } catch (Throwable t) {
            return null;
        }
    }
}
