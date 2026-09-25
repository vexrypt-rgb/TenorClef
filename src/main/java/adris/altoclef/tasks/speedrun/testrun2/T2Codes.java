package adris.altoclef.tasks.speedrun.testrun2;

/**
 * Stable codes. Grep the log for {@code T2 [Exxx]} / {@code T2 [Ixx]}.
 *
 *  I0x  heartbeat / phase
 *  E1x  water
 *  E2x  combat
 *  E3x  stall
 *  E4x  closer / fallback
 *  E5x  death
 *  E6x  portal / nether entry
 *  E7x  inventory
 *  E8x  task lifecycle
 *  E9x  movement / craft table
 *  E10x sensor (jump, GUI, starve, wrong child)
 */
public final class T2Codes {

    public static final String I02_PULSE = "I02";
    public static final String I03_PHASE = "I03";
    public static final String I04_CHILD = "I04";

    public static final String E10_WATER = "E10";
    public static final String E102_WATER_STILL = "E102";

    public static final String E20_FIGHT = "E20";
    public static final String E110_PIGLIN_NAKED = "E110";

    public static final String E30_STALL = "E30";
    public static final String E118_CHILD_STALE = "E118";

    public static final String E40_CLOSER = "E40";

    public static final String E50_DEATH = "E50";

    public static final String E60_PORTAL = "E60";
    public static final String E105_LAVA_NO_WATER = "E105";
    public static final String E107_NO_FLINT = "E107";
    public static final String E119_PORTAL_IGNORED = "E119";

    public static final String E80_RESTART = "E80";
    public static final String E106_UNSTICK_CHILD = "E106";
    public static final String E111_NULL_CHILD = "E111";
    public static final String E112_PHASE_FLAP = "E112";

    public static final String E90_CAVE = "E90";
    public static final String E91_TABLE = "E91";
    public static final String E97_WOOD_JUMP = "E97";

    /**
     * S179 — THE TREE OSCILLATION: the anti-stall counter was keyed to a 4-block bucket, and
     * an oscillating bot sits on the bucket boundary.
     *
     * <p>User-observed symptom: "the bot keeps moving back and forth over a tree trying to
     * break it". Run AA reproduces it exactly. The bot walked to `43.7,-24.8`, froze there
     * for 152 ticks, then oscillated `x=43.9 <-> 44.2` for 20+ seconds — never moving more
     * than 1.1 blocks, never breaking the tree.
     *
     * <p>The old guard was {@code x >> 2} bucketing with a 160-tick dwell requirement. The
     * oscillation straddled the **x=44 cell boundary** and crossed it **160 times** between
     * t=200 and t=700, resetting the counter each time, so {@code woodStill} fired exactly
     * ONCE in the whole run (t=171, during the initial freeze) and never again.
     *
     * <p>Fix: track a running bounding box of recent positions and fire on the AREA
     * explored, which has no boundaries to straddle. A bot genuinely walking to a tree
     * sweeps tens of blocks; an oscillating one stays inside a ~2-block box however much it
     * jitters. Threshold: span &le; 3 blocks in both axes after 8 seconds.
     *
     * <p>Generalise: **never measure "has not moved" with a bucketed/discretised position.**
     * Any quantization introduces boundaries, and the pathological case (jitter) is exactly
     * the one that clusters at them. Use a continuous measure — bounding box, accumulated
     * path length, or max displacement from an anchor.
     */
    public static final String S179_WOOD_OSCILLATION = "S179";
    public static final String E98_XZ_FREEZE = "E98";
    public static final String E99_WALKOFF_FAIL = "E99";
    public static final String E100_JUMP_PLACE = "E100";
    public static final String E101_BOOT_FREEZE = "E101";
    public static final String E103_STARVE = "E103";
    public static final String E104_GUI = "E104";
    public static final String E108_TABLE_FEET = "E108";
    public static final String E109_NO_TOOL = "E109";
    public static final String E109_FIST_WITH_PICK = "E109b";
    public static final String E116_HOLE = "E116";
    public static final String E131_PILLAR_THRASH = "E131";
    public static final String S130_PILLAR = "S130";
    public static final String S131_PILLAR_FAIL = "S131";
    public static final String S132_PILLAR_CLEAR = "S132";
    public static final String S133_PILLAR_COOL_ARM = "S133";
    public static final String S134_PILLAR_COOL_END = "S134";
    public static final String S135_PILLAR_SUPPRESS = "S135";
    public static final String S136_PILLAR_END = "S136";
    public static final String S137_PILLAR_RECOOL = "S137";
    public static final String S138_PILLAR_BAN_CLEAR = "S138";

    /**
     * S178 — THE DESCENDING-SHAFT TRAP: the pillar ban was horizontal-only, so tunnelling
     * straight down never cleared it.
     *
     * <p>{@code HolePillar}'s ban-clear test measured only {@code |x-banX| + |z-banZ|}. But
     * the bot does not always leave a shaft sideways — while collecting iron it frequently
     * tunnels STRAIGHT DOWN. Run Z: S135 (S130 suppressed) fired **231** times with the bot
     * descending a self-dug shaft inside one column — `sky` falling 9→7→6→3, `place` climbing
     * 46→51 — and because manh stayed 0 the ban never cleared, so {@code busy()} stayed true
     * and the pillar escape was suppressed the entire way down. Meanwhile
     * {@code stillBannedBoxed()} kept re-arming and DOUBLING the cool ({@code lastCoolArmed*2},
     * capped 20×20), which is how a single column reached {@code cool=197 ban=197}.
     *
     * <p>The same run then died in the mirror-image loop: E116 fired 55 times as an 8-block
     * descending staircase ({@code 72→64→56→48→40→32→23}) instead of a climb, E100
     * (jump-in-place) fired 222 times, and the final tick blocked for 12.5s at
     * {@code HolePillarTask @144.7,45.0,125.5} with the bot at half health.
     *
     * <p>Fix: the ban now remembers the Y it was armed at ({@code banY}) and clears on either
     * a 2-block horizontal exit OR a 5-block vertical move. A 5-block depth change means the
     * surrounding rock, the light level and the escape geometry are all different, so the
     * pillar decision must be re-made rather than inherited.
     *
     * <p>Generalise: a "has the player left the area?" test must measure every axis the player
     * can actually leave by. A horizontal-only proximity test paired with a vertical escape
     * route is a livelock by construction.
     */
    public static final String S178_BAN_Y_BLIND = "S178";

    /**
     * S180 — THE DIG-DOWN / PILLAR-UP LIVELOCK: the escape test was unsatisfiable, so the
     * pillar always "failed" and the bot re-entered the shaft it had just left.
     *
     * <p>{@code evaluateRise} accepted a +4 rise outright, but for a marginal +2/+3 escape it
     * required {@code !boxed() && wallCount(feet-1) < 3 && headroom() && !columnUnderneath()}.
     * The last two are **structurally impossible** for a bot that has just pillared out of a
     * shaft: the shaft it climbed is always directly beneath it, and its walls are still there
     * one level down. So a shallow escape could NEVER be confirmed.
     *
     * <p>Run AB: the bot dug a 2-deep hole at the surface, S130 fired, it pillared 62 → 64,
     * and its own tick log showed {@code boxed=false walls=0/2/4 sky=11} — genuinely free,
     * standing on the rim. The strict test rejected it and logged
     * {@code stuck-low ... roseReal=false giveUp=true}, arming a 12s ban; CollectIron then
     * re-entered the same shaft. Measured: a clean 2-block oscillation, 4 pillar cycles in
     * 103 seconds, never getting the iron. S141 (the reject log) never fired either, because
     * {@code riseStable} was still 0 — the rejection was SILENT.
     *
     * <p>Fix: {@code clear = !boxed() && headroom()} — an escape is "the bot can move freely",
     * which is what {@code boxed()} already tests. Leniency is the safe direction: a false
     * accept costs one cool period (S130 re-fires), a false reject is a livelock.
     *
     * <p>Generalise: **a guard that is always true in the exact situation it guards is not a
     * guard — it is an unsatisfiable condition.** Ask of every conjunct "could this ever be
     * false here?"; if not, it silently disables the whole branch. And log the reject, not
     * just the accept.
     */
    public static final String S180_PILLAR_ESCAPE_UNSATISFIABLE = "S180";

    /**
     * S181 — THE STACK DUMP: making a blocked tick visible from outside.
     *
     * <p>Runs AA, Z and AC all ended the same way: a single **30–40 second** tick inside
     * {@code MineAndCollectTask} during BOOTSTRAP wood collection near spawn, followed by a
     * permanent client-thread block. Run AC's was **40136ms** — a new record, worse than run
     * X's 19.7s.
     *
     * <p>The reason it could never be diagnosed: **every channel goes silent with the thread.**
     * The game log stops entirely (verified — zero non-bot lines for the whole stall window),
     * and the tick's own trace line is lost as well, because {@code Runtime.halt()} skips
     * buffer flushing, so the last buffered lines never reach disk.
     *
     * <p>{@link T2Deadman} runs on its own daemon thread, so it can call
     * {@code clientThread.getStackTrace()} **while the client is still stuck** and name the
     * exact frame. {@code beat()} captures the thread reference; the watchdog dumps once per
     * stuck tick (keyed on the tick's start instant) so a minutes-long stall produces one
     * stack, not one per 125ms poll.
     *
     * <p>Generalise: when a thread can hang, capture its identity early and let a DIFFERENT
     * thread interrogate it. A hang that leaves no trace is not a mystery to reason about, it
     * is a measurement you have not taken yet.
     */
    public static final String S181_STUCK_TICK_STACK = "S181";

    /**
     * S182 — THE LOW-CEILING REJECT: the escape test demanded air 2 blocks above the head,
     * which is false in every normal 2-high space.
     *
     * <p>{@code headroom()} originally returned {@code !solid(feet + 2)} — "not a ceiling
     * lip". But a 2-high tunnel or an overhang has feet and feet+1 free and **feet+2 solid**,
     * which is a walkable ceiling, not a trap. So a bot that escaped into a normal low space
     * was still rejected.
     *
     * <p>Run AC's last {@code stuck-low} is the proof:
     * {@code y=70 startY=68 walls=0/0/4 sky=12} — no walls at feet OR head, open sky, an
     * unmistakably free bot, rejected purely because a block sat two above it. S180 (the
     * unsatisfiable-conjunct fix) had already accepted 7 other escapes in the same run; this
     * was the one case its remaining conjunct still blocked.
     *
     * <p>{@code !boxed()} already establishes there is an opening at feet level, which is what
     * "can move" means. {@code headroom()} only needs to confirm the bot is not suffocating,
     * so it now checks feet+1 (the bot's own head block).
     *
     * <p>Generalise: when a predicate means "is there room to stand", test the space the body
     * actually occupies. Testing one block beyond it silently excludes every tight-but-legal
     * space — tunnels, doorways, under overhangs — which in a mining game is most of them.
     */
    public static final String S182_HEADROOM_TOO_STRICT = "S182";

    /**
     * S183 — THE BOBBING BOT: the water bail ended on an INSTANTANEOUS wet test, so a bot
     * bobbing in water ended its own escape on every bob.
     *
     * <p>{@code isSubmergedInWater()} is true only while the player's **eyes** are under
     * water. A bobbing bot clears the surface for a tick at a time, so the first bob that
     * lifted its eyes above the surface read as "dry" and ended the bail — while the bot was
     * still standing in the water. The parent resumed, the bot sank back in, S102 fired
     * again, a fresh bail was built, and it ended on the next bob.
     *
     * <p>Run AD, user-reported as "the bot got stuck bobbing up and down in the water":
     * S102 every ~6s at {@code spd=0.000 @243,61,136} for 30+ seconds, position never changing.
     *
     * <p>Fix, two parts: (1) test ANY water contact from the WORLD (feet and head block fluid
     * state) rather than eye submersion — the world cannot lie on a bob; (2) require
     * {@code DRY_TICKS} consecutive dry ticks before the bail may finish. Plus an escalation:
     * after 5s with no horizontal progress, path to a
     * visible shore, because a 1-deep pool has no swimmable exit and GetOutOfWaterTask can
     * circle in it forever. {@code findShore()} already existed for this and had no caller.
     *
     * <p>Generalise: **an "am I still in the bad state?" test must not use a quantity that
     * oscillates with the very behaviour being escaped.** Bobbing alternates the eyes above and
     * below the surface, so an eye-based test is guaranteed to sample "escaped" on some tick.
     * Measure the thing that is actually stable (here: the water in the block), and require it
     * to hold for a sustained period rather than sampling it once.
     */
    public static final String S183_WATER_BOB = "S183";

    /**
     * S184 — A FURNACE IS NOT A CRAFTING TABLE: the "standing still means stuck" test was
     * applied to a GUI where standing still is REQUIRED.
     *
     * <p>{@code T2Solve.workGui()} lumps Furnace in with Craft/Inventory/Anvil/Chest, and the
     * stall test that follows — {@code flips >= 4 || sameXz > 4s || guiAge > 12s} — is right
     * for an instantaneous craft. Smelting one item takes **10 seconds**, and the bot must
     * stand still with the screen open for it to finish. So the test closed the screen
     * mid-smelt, the smelt aborted, the parent re-opened the furnace, and the cycle repeated.
     *
     * <p>User-reported as *"the bot spins in circles when it is in the furnace ui"*. Run AF:
     * {@code S120 craft-gui jump} and {@code E104 FurnaceScreen open 8s} alternating every
     * ~6s at {@code @69,66,18} for the final minute, with {@code E70 iron stall 6s} alongside —
     * the bot had the ore AND the furnace and still could not finish.
     *
     * <p>Fix: {@code slowGui()} (Furnace, Brewing Stand) gets a long bounded budget (30s) and
     * is **never** closed by a same-xz test, because motionlessness is correct there.
     *
     * <p>Generalise: a "no progress" test is only valid against a proxy that actually tracks
     * progress. Standing still tracks "stuck" for a craft (instantaneous) and tracks "working
     * correctly" for a furnace (time-based). Check whether the metric's sign flips between the
     * cases you are grouping together.
     */
    public static final String S184_FURNACE_NOT_TABLE = "S184";

    /**
     * S185 — DETECT THE CONDITION, NOT THE NAME: the smelting stall guard keyed on the child
     * task's NAME and therefore never applied.
     *
     * <p>{@code ModernSpeedrunTask.iron()} suppressed the E70 stall ladder while smelting via
     * {@code cn.contains("Smelt") || cn.contains("Furnace")}. But the child that actually drives
     * smelting is **{@code CollectIronIngotTask}** — a name containing neither string. So
     * {@code smelting} was FALSE exactly when smelting was happening, E70 was never suppressed,
     * and it tore down and rebuilt the iron task every 6 seconds while the furnace ran.
     *
     * <p>Run AG: {@code E104 FurnaceScreen open 8s} ×11 and {@code E70 iron stall 6s} ×9
     * interleaved at one spot, with {@code CollectIronIngotTask} logged as the child on every
     * S120/S184 line. The guard's INTENT ("a furnace holds position by design") was correct;
     * only its detection was wrong.
     *
     * <p>Fix: {@code slowScreenOpen()} reads the live screen, which is the thing that actually
     * means "holding position is correct here". Also stopped E104 counting a slow screen as a
     * stall — it fired 11 false alarms on correct smelting behaviour.
     *
     * <p>Generalise: **a guard keyed on a task's class NAME breaks silently the moment a
     * differently-named task performs the same work.** Names are a proxy for behaviour and
     * proxies rot. Detect the condition itself (state, inventory, open screen) whenever one is
     * available, and treat a name test as a last resort. This is the third instance in this
     * project of a guard testing a proxy instead of the thing (see also S173's "sky light
     * lies" and S183's eye submersion).
     */
    public static final String S185_NAME_NOT_CONDITION = "S185";

    /**
     * S186 — RECOVER FROM A LATCHED DRIVER instead of halting a healthy run.
     *
     * <p>{@code T2Deadman.clientTickAlive()} already detected the latch correctly — it counts
     * client ticks with no matching driver beat and logs S176 after 30s — but it only
     * **reported**. The watchdog then halted at 120s. Run AH is the proof:
     * {@code S152 onStop interrupt=null dead=false ph=NETHER y=47.8} at 27:57.2, the driver
     * never ticked again, and at 29:57 a run that had been working perfectly for 28 minutes
     * was killed with code 87.
     *
     * <p>**S181 proved the latch is recoverable**: its stack dump showed the client thread
     * alive and rendering — {@code glfwWaitEventsTimeout -> RenderSystem.limitDisplayFPS ->
     * MinecraftClient.render} — so world, player and inventory are all intact. Only the task
     * chain's main task was dropped. Re-installing it resumes the run, and because S152 keeps
     * {@code sessionLive} set, {@code onStart} is swallowed by the E80 guard and the phase is
     * NOT re-derived (no clock reset, no respawn into BOOTSTRAP).
     *
     * <p>Bounded to 3 attempts: if re-installing does not take, this is not a latch and the
     * fatal exit should still fire.
     *
     * <p>Generalise: **when a detector already knows exactly what went wrong, check whether the
     * condition is recoverable before wiring it to a kill.** A watchdog that can only terminate
     * converts a transient fault into a lost run. Reporting and recovering are different jobs.
     */
    public static final String S186_DRIVER_LATCH_RECOVERY = "S186";

    /**
     * S187 — THE BLIND WALK NUDGE WALKED OFF A LEDGE. {@code T2Input.walkTurn()} (used by S100,
     * ProjectileDodge, T2CoreTask) turned +70° and held forward for 2s with no look at the
     * ground. 2026-09-24 02:32: NETHER y=95, S100 fired at 02:32:24, falling at 02:32:25, dead
     * ("fell from a high place") at 02:32:26, 13:40 into the run. walkTurn now tries +70, -70,
     * ±140, 180 and only walks a heading with ground within 3 blocks and no lava for 4 blocks
     * ({@code core.SafeHeading}); S187 logs when no heading is safe and the nudge is skipped.
     */
    public static final String S187_WALK_NUDGE_NO_SAFE_HEADING = "S187";

    /**
     * S188 — THE REROLL HANG, root cause of S174. {@code ResetSignal.fire()} ran
     * {@code mc.execute(mc::disconnect)} from inside the client tick. execute() runs INLINE on
     * the client thread, and client.disconnect() loops {@code while (!server.isStopping())
     * render()} — but nothing had closed the connection ({@code world.disconnect()}, which the
     * vanilla Save-and-Quit button calls first), so the integrated server never stopped and the
     * loop never ended. Reproduced live (sim run base1): S159 reject at 0:27 → S181 DEADMAN stack
     * ResetSignal → disconnect → render → limitDisplayFPS, frozen 90s+. Fix: send() (always
     * queued), world.disconnect() first, then TitleScreen for AutoWorldCreateMixin.
     */
    public static final String S188_REROLL_DISCONNECT_HANG = "S188";

    /**
     * S189 — THE HARNESS WORLDS WERE HARDCORE. AutoWorldCreateMixin passed {@code true} as the
     * 3rd LevelInfo argument believing it was "generateStructures"; yarn 1.16.1 mislabels it —
     * vanilla CreateWorldScreen passes {@code hardcore} there. Every AutoRun world had
     * level.dat hardcore=1, so each death made the bot a SPECTATOR, which sinks through blocks:
     * the "respawn free-fall" (sky 15→0 inside rock, fixed XZ, runs U/V below bedrock) seen in
     * all 5 logged respawns, 3 seed resets via S165 fellIn, and 808s of surface-bail. S165,
     * S169 and S172 were all fighting this symptom. Fix: hardcore=false, allowCommands=false.
     */
    public static final String S189_HARNESS_WORLD_WAS_HARDCORE = "S189";

    /**
     * S190 — THE WATER-BAIL PING-PONG. T2Solve S102 returned WaterBailTask; next tick it
     * returned null ("already escaping") and taskFor(IRON) replaced the bail with
     * CollectIronIngotTask, so S102 fired again. Live run fix1: IRON @177,62,-142, child
     * flipped every tick for 60s+ at spd=0 while S164 kept re-pinning the collector. The driver
     * now keeps an unfinished WaterBailTask, like SurfaceBailTask; the bail ends itself.
     */
    public static final String S190_WATER_BAIL_KEPT = "S190";

    /**
     * S191 — CLIMB TARGET INSIDE ROCK. nether() "climb off lava before gold" returned
     * {@code GetToBlockTask(playerPos.up(12))}: an exact block that is usually netherrack, and a
     * goal that moved with every step. Historical logs: 302s at 27,47,26 → 27,59,26. Now a
     * {@code GetToYTask(52)} (any column at y=52), stable across ticks.
     */
    public static final String S191_CLIMB_TO_Y_LEVEL = "S191";

    /**
     * S192 — IDLE AFTER REROLL. Once S188 made rerolls work, the fresh world loaded but
     * {@code autoRunCommand} had already fired for this client launch, so nothing restarted
     * @testrun2; the old task ended at phase DONE and DEADMAN (still armed) exited 87 after
     * 120s. Live run fix3. AltoClef now re-arms the auto-run for each rerolled world, and
     * ResetSignal disarms the watchdog until the next onStart.
     */
    public static final String S192_AUTORUN_AFTER_REROLL = "S192";

    /**
     * S193 — CLIMB GATE WITHOUT HYSTERESIS (regression from S191). nether() started the
     * GetToYTask(52) climb below y=48 and dropped it as soon as y reached 48, so CollectGold
     * stepped back to 47 and the climb restarted. Live run fix4: GetToY <-> CollectGold every
     * few seconds at -25,47/48,-5 for 2 min. Now: start below 48, continue to 52; a climb with
     * no height gain for 30s is abandoned for 60s and the gold is mined where the bot stands.
     */
    public static final String S193_NETHER_CLIMB_HYSTERESIS = "S193";
    public static final String E132_SHAFT_STUCK = "E132";
    public static final String E133_PILLAR_PINGPONG = "E133";
    public static final String S139_PILLAR_RISE_HOLD = "S139";
    public static final String S141_PILLAR_HOP_END = "S141";
    /**
     * Re-cool ladder gave up and lifted the shaft ban so a real pillar attempt can
     * happen. Without this the ban could never clear inside a 1x1 shaft (there is no
     * horizontal room to satisfy manh >= 2), so the bot oscillated until the end of time.
     */
    public static final String S142_PILLAR_UNBAN = "S142";
    /**
     * The iron-pick-needs-sticks handoff to the wood task is being held so the child
     * survives the parent's class-equality reuse check. Without the latch, iron()
     * re-issued collectWood() every tick the log throttle let through, so a fresh
     * MineAndCollectTask replaced the previous one before it could fell a tree.
     */
    public static final String S143_WOOD_LATCH = "S143";
    /**
     * The wood latch expired without producing sticks. The tree is unreachable, not
     * merely far — blacklist it and re-pick nearest any-type log.
     */
    public static final String E134_WOOD_LATCH_EXPIRE = "E134";
    /**
     * S100's noJump/walkTurn/cancelPath nudge was undone by a collector child that
     * re-paths every tick, so T2Solve replaced the child with a wander instead.
     */
    public static final String S144_JUMPSTUCK_REPLACE = "S144";
    /**
     * Boxed in a 1x1 shaft with zero placeable blocks. Both S130 gate on
     * HolePillar.hasPlace(), so this state matched neither and the bot just spun.
     * S145 mines one shaft wall block to obtain cobblestone, after which S130 works.
     */
    public static final String S145_MINE_FOR_COBBLE = "S145";
    /**
     * The IRON target was locked for this phase. A per-tick recompute made it a moving
     * target (8 -> 20 -> 22 -> 24) and, because CollectIronIngotTask.isEqualResource
     * compares the count, every change tore down and restarted the collection.
     */
    public static final String S146_IRON_TARGET_LOCK = "S146";

    /**
     * Sticky per-column ban. Armed when E133 sees a ping-pong, and REFUSES to re-arm S130
     * at that column until the bot has genuinely left it (STICKY_CLEAR_MANH away for
     * STICKY_CLEAR_TICKS). The ordinary banTicks ban clears on a 2-block nudge, which a
     * pillar hop produces immediately, so it never held.
     */
    public static final String S147_STICKY_COLUMN_BAN = "S147";
    /** Sticky ban lifted - the bot really did leave the column. */
    public static final String S148_STICKY_BAN_LIFT = "S148";
    /**
     * Blaze-rod search latched. nether() rebuilds CollectBlazeRodsTask every tick, and
     * SearchChunksExploreTask swaps its own child to a TimeoutWanderTask while no
     * nether-bricks chunk is in range - so stick()'s class-equality branch fails and the
     * search restarts from scratch (wipe of alreadyExplored) every time.
     */
    public static final String S149_BLAZE_SEARCH_LATCH = "S149";
    /** Blaze-rod latch released (rods satisfied, or the phase changed). */
    public static final String S150_BLAZE_LATCH_RELEASE = "S150";
    /**
     * SurfaceBailTask downward fallback. When the bot respawns underground with no sky
     * above (observed after Nether death: Y=-3, solid rock all around), the ordinary
     * upward-only search finds nothing and the bot wanders until it dies. Search downward
     * for any open air column as a last-ditch escape.
     */
    public static final String S151_SURFACE_BAIL_DOWN = "S151";

    /**
     * Session survives an interruption. onStop() used to clear sessionLive on every
     * interruption, so any task swap re-ran onStart() and re-derived the phase from the
     * inventory. Measured on run G: 87 sessions, 57 of them back in BOOTSTRAP, one death.
     */
    public static final String S152_SESSION_PRESERVED = "S152";

    /**
     * hadKit preserved on resume. onStart() set hadKit=true in the PORTAL resume branch
     * and then unconditionally cleared it 18 lines later.
     */
    public static final String S153_HAD_KIT_RESUME = "S153";

    /**
     * MLG clutch chain stays inactive when there is nothing to clutch with (no water bucket,
     * no configured clutch item). Hijacking the user task for a fall it cannot survive only
     * re-initialises the run.
     */
    public static final String S154_MLG_NO_CLUTCH_ITEM = "S154";

    /**
     * Mob-defense engagement budget: "annoying hostile" chasing outranks the user task, so it
     * is capped in time and distance and then forced to yield for a cooldown.
     */
    public static final String S155_MOB_DEFENSE_DISENGAGE = "S155";

    /**
     * Surface-bail timeout: the task can only finish by reaching sky light, so a bot with
     * no pickaxe and no blocks underground would otherwise occupy the child slot forever.
     */
    public static final String S156_SURFACE_BAIL_TIMEOUT = "S156";

    /**
     * Child swap throttle: ConstructNetherPortalBucketTask and HolePillarTask were
     * alternating every tick, so neither ever ran.
     */
    public static final String S157_CHILD_SWAP_THROTTLE = "S157";

    /**
     * Shaft hopping: the bot pillared out of several DIFFERENT 1x1 shafts in a short
     * window. Every earlier guard was column-scoped, so a bot that digs a fresh shaft per
     * iron target never tripped them — E133 never fired and S147 was never armed. This
     * counts arms across columns and bans an area so the bot has to relocate.
     */
    public static final String S158_SHAFT_HOP_BAN = "S158";

    /**
     * Spawn gate: the seed spawned with no village and no ruined portal within
     * SpawnScout.SPAWN_LOOT_RADIUS, so the world is rerolled instead of played. The check
     * runs on a probe window rather than once, because an unseen village is not an absent
     * village. Bounded by SpawnScout.MAX_REROLLS.
     */
    public static final String S159_SPAWN_GATE = "S159";

    /**
     * Portal build timeout: one ConstructNetherPortalBucketTask instance owned the child
     * slot past its budget without finishing. stick() pins Construct so a half-built
     * portal is never abandoned, and that pin had no exit — a build waiting on a resource
     * it cannot obtain froze the run. Suspends the pin and sends the bot wandering.
     */
    public static final String S160_CONSTRUCT_TIMEOUT = "S160";

    /**
     * Unrecoverable underground (slow proof): the bot is below the surface with no pickaxe
     * of any tier and nothing placeable, and SurfaceBail has already given up and said so,
     * which is direct evidence that it cannot climb out. Abandon the seed instead of
     * holding the child slot until the harness timeout kills the run (run Q: 102x S156
     * across seven minutes at y=19).
     */
    public static final String S161_UNRECOVERABLE = "S161";

    /**
     * Combat override: a hostile closed on the bot while a pinned task (typically the
     * portal build) owned the child slot. Pins exist so work is not abandoned, but a pin
     * that combat cannot break means the bot keeps working while it is being killed.
     */
    public static final String S162_COMBAT_OVERRIDE = "S162";

    /**
     * Deep portal dig: the bucket-portal build is still sinking below y=30 in the dark,
     * which means it is chasing a deep lava lake down a 1x1 shaft it cannot climb back out
     * of. Clear the cached site and force a pillar out, then let the build pick a new one.
     */
    public static final String S163_DEEP_DIG_BAIL = "S163";

    /**
     * Pair-agnostic child ping-pong: two children of ANY kind alternated fast enough that
     * neither could run. S157 knew only about Construct <-> HolePillar; this counts the
     * class pair so a new pair is caught the first time it appears.
     */
    public static final String S164_CHILD_PINGPONG = "S164";

    /**
     * Unrecoverable underground (fast proof): same dead end as S161 but proven WITHOUT
     * waiting for a bail to time out — the bot either fell at least 24 blocks after going
     * underground, or has been underground for 30 straight seconds, and it still has no
     * pickaxe and nothing placeable. Run R respawned at y=63 and reached y=-1 in seventeen
     * seconds, so the 120-second bail timeout never got the chance to fire: the seed was
     * already dead and the run burned another fifty minutes proving it.
     */
    public static final String S165_UNRECOVERABLE_FAST = "S165";

    /**
     * Surface-first lava lake: the portal build needs lava, and a lake below y=40 forces a
     * long dark 1x1 shaft down to it that the bot cannot climb back out of. Pick the
     * shallowest lake at or above y=40 and only fall back to a deep one when there is no
     * alternative. Run Q sank 175,187 from y=71 to y=28 chasing a deep lake and died there.
     */
    public static final String S165_SURFACE_LAKE = "S165L";

    /**
     * Bled out in a shaft: the portal build had the bot in the dark and it lost four hearts
     * without any fight taking the child slot, which means ranged damage — closeHostile()
     * deliberately ignores skeletons and witches so the clock is not burned chasing them.
     * Arms the same pillar-out bail as S163. Run R: "Player29 was shot by Skeleton" at y=34,
     * above S163's depth floor, so depth alone was never going to catch it.
     */
    public static final String S166_SHAFT_BLEED = "S166";

    /**
     * Spawn submerged in water: the S159 gate checks for a village or ruined portal, which
     * says nothing about the block the bot is standing in. Runs N (38 min, 0 items), R
     * (river, 370 craft/water swaps) and S (DesertLakes, spd=0.000, survival chain took the
     * slot at t=1:15 and the driver never ticked again) all died this way. Being wet is
     * normal; submerged for 8 continuous seconds at spawn means the escape-water pathing is
     * not converging, so reroll rather than spend the run proving it.
     */
    public static final String S167_WATER_SPAWN = "S167";

    /**
     * Deadline starvation: a task whose own timeout is longer than the window another guard
     * gives it can never reach that timeout. Run T's SurfaceBailTask has MAX_TICKS = 120s but
     * fell through holdFor() to the 40s default, so S140 replaced it every 40s and the 120s
     * give-up (which the whole S156 / S161 chain depends on) was unreachable. S168 gives the
     * bail a 140s hold AND a 60s no-vertical-movement give-up, so it no longer has to reach
     * 120s at all to stop wasting the slot.
     */
    public static final String S168_DEADLINE_STARVED = "S168";

    /**
     * Treeless spawn accepted: the S159 gate accepted on structure alone, so a ruined portal
     * in a biome with no wood passed. Run P (SnowyTundraBiome, rp=true rpDist=57) mined 8
     * iron, could not craft the pickaxe for want of sticks, wandered 160 blocks and died;
     * run U (BeachBiome, rp=true rpDist=33) spent 24 minutes with `logs=0` in all 82
     * heartbeats and `woodpick=0`. The gate now honours SpawnScout's resetWorthy flag
     * instead of discarding it, and the tree census is 81x81 rather than 33x33 so a forest
     * one hill away is seen.
     */
    public static final String S169_TREELESS_SPAWN = "S169";

    /**
     * Barren seed backstop: the gate's verdict is a 20-second-old guess made with only the
     * spawn chunks loaded, and run U proved a single sighting is not worth trusting. If the
     * bot still has zero logs eight minutes after an accepted spawn it is in BOOTSTRAP with
     * no wood, which is unplayable — reroll instead of spending the other 47 minutes of the
     * harness budget confirming it.
     */
    public static final String S170_BARREN_SEED = "S170";

    /**
     * A hold must not be inherited from the phase. S168 fixed the bail's 40s hold by adding a
     * SurfaceBail branch to holdFor(), but placed it AFTER the phase checks — so a bail
     * running during PORTAL still inherited PORTAL's 90s hold, which is again shorter than
     * SurfaceBailTask.MAX_TICKS (120s). The S168 bug, relocated rather than fixed. S171
     * hoists the escape-task branch to the top: an escape task's budget comes from the
     * escape task, not from whatever phase happens to be running.
     */
    public static final String S171_HOLD_INHERITED_FROM_PHASE = "S171";

    /**
     * S172. The S165 unrecoverable check sat BELOW the E90 "bootstrap in dark, surface" bail,
     * and E90 ends in `return stick(new SurfaceBailTask())` on a condition that is true for
     * exactly the bot S165 exists to catch. Run V respawned at 57,-2,225 with `sky=0
     * pick=0 iron=0 buck=0` and an empty inventory — textbook unrecoverable — while E90 fired
     * 20 times and S165 fired zero. Same class as S168: a guard that another guard prevents
     * from being reached is not a guard. The block now sits above E90, and a fourth fast
     * proof (`buriedAlive`: y<=0, sky<=2, and no pick with nothing to place) removes the 30s
     * wait for the unmistakable case.
     */
    public static final String S172_GUARD_ORDER = "S172";

    /**
     * S173 — THE PILLARING BUG. `HolePillar.boxed()` was geometry-only by design ("sky light
     * lies"), which cannot distinguish "trapped in a pit" from "standing in the 1x1 ore shaft
     * I just dug at the surface". Run W is the proof: S130 fired **854** times and ALL 18 of
     * its START lines read `sky=15 boxed=true` at y=58-69 — the bot climbed 59 -> 63, walked
     * back down to 59 for the iron it came for, was told it was boxed, and climbed again. At
     * t=9:52 it still had `stonepick=0 iron=0`. This is precisely the oscillation the standing
     * directive names. A real pit has a roof and sky 0; a self-dug surface shaft has sky>=10
     * and an unobstructed column. Requiring BOTH keeps S130 armed for genuine pits.
     */
    public static final String S173_SURFACE_SHAFT_NOT_BOXED = "S173";

    /**
     * S174 — THE REROLL PATH NEVER WORKED. Every reset (S159 reject, S167 water spawn,
     * S165/S172 unrecoverable) fires {@code ResetSignal} → {@code AutoWorldState.rearm()} →
     * {@code mc.disconnect()}, and creation of the fresh world was driven by
     * {@code TitleScreen.tick()}. <b>disconnect() from a live integrated server does not land
     * the headless client on TitleScreen</b>, so the trigger never fired. Run W is the proof:
     * a correct S165 reset at 23:35:27 logged `re-armed for a fresh seed (reroll #1)` and then
     * the client sat in the old world for 13+ minutes with a `World save took 0ms` heartbeat
     * and no title screen, no create call — silent until the harness timeout.
     *
     * It looked healthy for so long because NO earlier run had ever needed a reroll (the gate
     * is 7-for-7, so the path was never exercised). The reroll is now driven from
     * {@code MinecraftClient.tick()}, which runs on every screen and in-world, with the title
     * screen kept as the preferred path and a 45s loud give-up instead of a silent hang.
     */
    public static final String S174_REROLL_DEAD = "S174";

    /**
     * S175 — THE CLIENT THREAD BLOCKED MID-TICK. Every stall guard in this project is
     * evaluated from inside the tick, so none of them can observe the one failure that
     * actually ended run X. {@code trace.log} is written by {@link T2Trace} on the client
     * thread once per tick, and it simply <b>stops mid-file</b> at {@code t=8139 clk=7:06.7}
     * — with the tick before it covering <b>19.7 seconds of wall clock</b>:
     *
     * <pre>
     *   t=8128 clk=6:46.5 @-1.5,49.0,-77.6 g=true  sel=6 child=HolePillarTask
     *   t=8129 clk=7:06.2 @ 0.3,51.0,-79.1 g=true  sel=7 child=HolePillarTask   +19.7s
     *   t=8139 clk=7:06.7 (last line in the file)
     * </pre>
     *
     * The game log kept printing "Refreshed inventory..." and "World save took 0ms" for a
     * further 22 minutes, so the client process was alive but not ticking the player, while
     * {@code timeout 3300} was the only thing that ever ended the run. Everything earlier in
     * this session that pointed at the driver — the {@code S152 onStop interrupt=null} line,
     * the {@code PORTAL->FIGHT} flapping — was a <em>symptom</em> of this same stall, not its
     * cause.
     *
     * A blocked thread cannot notice that it is blocked, so detection runs on a daemon
     * thread ({@link T2Deadman}) measuring wall-clock time against the driver's beat counter.
     */
    public static final String S175_CLIENT_THREAD_BLOCKED = "S175";

    /**
     * S176 — ONE TICK TOOK LONG ENOUGH TO BE A BUG. Reported immediately, whether or not the
     * run survives it, because nothing in the project measured tick DURATION before. Run X's
     * 19.7-second tick was invisible the moment it happened: the only trace of it is the gap
     * between two adjacent lines of {@code trace.log}. Also carries the second, independent
     * failure: the client ticking normally while the speedrun driver is never reached because
     * it was latched stopped by {@code Task.stop(null)}.
     */
    public static final String S176_TICK_TOO_LONG = "S176";

    /**
     * S177 — THE SEED WAS ACCEPTED FOR A RUINED PORTAL THE DRIVER NEVER USED.
     *
     * <p>The spawn gate accepts on {@code village || portal}, where {@code portal} means
     * "ruined-portal obsidian within {@code SPAWN_LOOT_RADIUS}" — SpawnScout scans OBSIDIAN
     * and CRYING_OBSIDIAN. Every recent run was accepted on exactly that, and then ignored it:
     *
     * <pre>
     *   run T  village=false rp=true rpDist=64  -> PORTAL 4:07
     *   run U  village=false rp=true rpDist=33  -> treeless, 24 min, 0 logs
     *   run X  village=false rp=true rpDist=63  -> PORTAL 7:07, then 10x S165L
     * </pre>
     *
     * <p>{@code portal()} only ever tested {@code anyFound(Blocks.NETHER_PORTAL)} — a
     * COMPLETED, LIT portal. A ruined frame's block id is OBSIDIAN, so it was invisible to
     * that check until the bot happened to walk past it. The driver therefore went digging
     * for a lava lake instead, which is precisely the descend-cast-ascend cycle that
     * produces the S165L deep-shaft fallback.
     *
     * <p>A ruined portal is a real frame with a few blocks missing. Filling the gaps costs a
     * little cobblestone and one flint-and-steel: no lava lake, no water bucket, no casting,
     * no vertical travel. When one is KNOWN and within 96 blocks it is strictly cheaper than
     * any bucket build, so it is now preferred. The frame ORIENTATION is discovered from the
     * surrounding obsidian rather than assumed, because ruined portals generate on either
     * horizontal axis.
     */
    public static final String S177_RUINED_PORTAL_IGNORED = "S177";

    /**
     * S177b — THE RE-ADOPT LOOP. Two constants describing the same trip disagreed.
     *
     * <p>{@link ModernSpeedrunTask#ruinedPortal} adopts a ruined portal within 96 blocks, and
     * its first version also revoked the adopted site when the player was more than 32 blocks
     * from it ("walked far away; re-evaluate"). 32 is smaller than 96, so a frame legitimately
     * adopted at 58 blocks was dropped on the very next tick, the block scanner found the same
     * obsidian again, and the driver re-adopted it — logging `adopting ruined portal
     * @135,10,153 dist=58` **22 times**, roughly 4s apart, for three straight minutes in run Z
     * (7:17 → 8:08), without the bot ever travelling toward it.
     *
     * <p>Stickiness must not be distance-revocable: walking TO a site necessarily means being
     * far from it. The site is now cleared only on a proven give-up, which is the one case
     * where re-scanning is the right answer.
     *
     * <p>Generalise: when one guard says "close enough to start" and another says "too far,
     * give up", the give-up threshold must be strictly LARGER, or the pair oscillates. Check
     * every pair of distance constants for this inversion.
     */
    public static final String S177B_RUINED_PORTAL_READOPT = "S177b";

    public static final String S109_EQUIP_PICK = "S109";

    private T2Codes() {}

    public static String glossary() {
        return String.join("\n",
                "T2 codes:",
                "  I02 pulse every 30s",
                "  E10  submerged â€” WaterBail once",
                "  E102 standing in water, velocity ~0",
                "  E20  combat overlay",
                "  E110 piglin in range, no gold helmet",
                "  E30  phase stall 2 min no inv change",
                "  E118 same child 90s no inv change",
                "  E40  closer / fallback path",
                "  E50  death recycle",
                "  E60  portal watchdog",
                "  E105 Construct running with lava and no water",
                "  E107 portal phase, no flint / steel",
                "  E119 nether portal exists, phase not PORTAL",
                "  E80  parent onStart ignored",
                "  E106 UnstickWalk is a child (should not happen)",
                "  E111 wanted child is null in a live phase",
                "  E112 phase flipped twice in <2s",
                "  E90  bootstrap underground",
                "  E91  crafting table under feet",
                "  E97  wood punch same XZ",
                "  E98  XZ freeze overlay (bootstrap only)",
                "  E99  walk-off failed 3x",
                "  E100 jumping in place (onGround flip, same XZ)",
                "  E101 bootstrap freeze",
                "  E103 hunger 0 and no food",
                "  E104 inventory/craft GUI open >8s",
                "  E108 table at feet + jumping",
                "  E109 mining with no pick",
                "  E109b pick in bag but fist/wrong tool equipped",
                "  E116 fell 8+ blocks in overworld",
                "  E131 HolePillar<->CollectIron thrash at same xz",
                "  E132 shaft stuck re-cool loop",
                "SOLVE S130 pillar-out of 1x1 shaft",
                "SOLVE S131 pillar fail / no rise / no blocks",
                "SOLVE S132 pillar clear (risen enough)",
                "SOLVE S133 pillar failCool armed",
                "SOLVE S134 pillar failCool expired",
                "SOLVE S135 S130 suppressed (cool/busy)",
                "SOLVE S136 HolePillarTask finished (reason)",
                "SOLVE S137 pillar re-cool still boxed at ban xz",
                "SOLVE S138 shaft ban cleared (left xz)",
                " E133 pillar ping-pong: repeated START/END at one column, never leaves",
                "SOLVE S139 pillar rise confirmed sustained (real escape, not a one-tick hop)",
                "SOLVE S141 pillar hop ended with only +2y — NOT counted as risen",
                "SOLVE S142 shaft ban lifted after 2 re-cools — allow a real pillar attempt",
                "SOLVE S143 wood task latched for sticks: iron() must NOT re-issue collectWood each tick",
                " E134  wood latch expired with no sticks — tree unreachable, blacklist and retarget",
                "SOLVE S144 S100 nudge failed twice — replace the re-pathing collector with a wander",
                "SOLVE S145 boxed with no place blocks — mine a shaft wall for cobble, then S130 works",
                "SOLVE S146 iron target locked for this IRON phase (a moving target restarts collection)",
                "SOLVE S147 sticky column ban: refuse S130 at a column that already ping-ponged",
                "SOLVE S148 sticky ban lifted after a real departure from the column",
                "SOLVE S149 blaze-rod search latched: stick() must not restart it every tick",
                "SOLVE S150 blaze-rod latch released (rods satisfied or phase changed)",
                "SOLVE S151 surface-bail downward fallback: no sky above, search for open air below",
                "RUN S152 session preserved across an interruption: onStop() no longer resets the run",
                "RUN S153 kit flag kept on resume: hadKit is no longer wiped after the PORTAL branch",
                "RUN S154 MLG chain inactive: falling with no clutch item, so it cannot hijack the run",
                "RUN S155 mob-defense disengage: hostile chasing exceeded its budget, priority handed back",
                "RUN S156 surface-bail timeout: could not reach sky, handed the child slot back to bootstrap",
                "RUN S157 child swap throttle: Construct/pillar alternation held so one task can run",
                "SOLVE S158 shaft-hop: pillared out of several different shafts, area banned to force relocation",
                "RUN S159 spawn gate: seed accepted / rejected / rerolled (village or ruined portal only)",
                "RUN S160 portal build timeout: pin suspended, bot sent to relocate",
                "RUN S161 unrecoverable underground (a bail already gave up): seed abandoned",
                "RUN S165 unrecoverable underground (fell in / 30s dark, no bail needed): abandoned",
                "RUN S165L portal build picked a lake below y=40 - expect a long dark shaft",
                "RUN S166 shaft bleed: lost 4 hearts in the dark with no fight, pillar out",
                "RUN S167 spawn submerged in water: rerolling the seed",
                "RUN S168 bail deadline starved (hold 40s < timeout 120s) - hold now 140s",
                "RUN S169 spawn treeless (rp in a beach/tundra): no sticks, no pickaxe, reroll",
                "RUN S170 barren seed: no wood 8 min after an accepted spawn - reroll",
                "RUN S171 holdFor() branch order: escape-task budget must come first",
                "RUN S172 S165 sat below the E90 early-return: unreachable in BOOTSTRAP",
                "RUN S173 boxed() geometry-only: surface ore shaft read as a pit, S130 x854",
                "RUN S174 reroll path never worked: disconnect() never reached TitleScreen",
                "RUN S175 client thread blocked mid-tick: 19.7s tick then zero ticks, run dead",
                "RUN S176 one client tick exceeded 8s (or the driver was latched stopped)",
                "RUN S162 combat override: a hostile broke a pin to take the child slot",
                "SOLVE S163 deep portal dig: still sinking below y=30, site cleared and pillar out",
                "SOLVE S164 child ping-pong: any pair alternating too fast, current child held",
                "SOLVE S109 force-equip a pick while mining with a block in hand",
                "  E132 shaft stuck: re-cool x3+ still boxed",
                "SOLVE S100 stop jump + walk",
                "SOLVE S102 swim forward",
                "SOLVE S103 get bread",
                "SOLVE S104 close GUI",
                "SOLVE S105 get water before lava cast",
                "SOLVE S108 step off table",
                "SOLVE S110 gold helmet vs piglin",
                "SOLVE S111 missing portal child"
        );
    }
}
