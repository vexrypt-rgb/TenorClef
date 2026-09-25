package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.container.LootContainerTask;
import adris.altoclef.tasks.movement.EnterNetherPortalTask;
import adris.altoclef.tasks.movement.GoToStrongholdPortalTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.resources.CollectBlazeRodsTask;
import adris.altoclef.tasks.resources.MineAndCollectTask;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.tasks.resources.TradeWithPiglinsTask;
import adris.altoclef.tasks.construction.compound.ConstructNetherPortalBucketTask;
import adris.altoclef.tasks.speedrun.KillEnderDragonWithBedsTask;
import adris.altoclef.tasks.speedrun.testrun2.combat.BlazePeekTask;
import adris.altoclef.tasks.speedrun.testrun2.combat.FightNearbyTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.TungstenHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Modern RSG driver with stall guards.
 *
 * Stuck-between-tasks bugs this revision closes:
 *   1. New child task instance every tick (active was unused) — AltoClef restarts
 *      the child if isEqual is picky. Children are now sticky.
 *   2. LOOT wander never times out — after LOOT_MAX_TICKS we force iron mining.
 *   3. Same chest re-looted forever — looted positions are blacklisted.
 *   4. `|| true` forced a 4-bed sheep grind before the stronghold.
 *   5. Having 10 obsidian called get(obsidian) instead of building a portal.
 *   6. wanted==null immediately spawned BeatMinecraftTask, which then fought
 *      this phase machine next tick.
 *   7. Piglin trade could run forever before a single blaze rod.
 *   8. isFinished() never flipped DONE, so @testrun2's timer never stopped.
 *   9. Phase could flip PORTAL while still missing a bucket if flint dropped
 *      from an RP chest — portal task then stalls. Bucket is required first.
 *  10. Water + tungsten lock with no child swap — WaterBailTask is injected.
 */
public class ModernSpeedrunTask extends Task {

    private enum Phase {
        BOOTSTRAP, LOOT, IRON, PORTAL, NETHER, EYES, STRONGHOLD, END, DONE
    }

    private static final int LOOT_MAX_TICKS = 20 * 90;          // 90s of loot/wander
    private static final int PHASE_STALL_TICKS = 20 * 120;      // 2 min same phase, no inventory change
    private static final int TRADE_MAX_TICKS = 20 * 75;
    private static final int CHEST_RANGE_SQ = 36;

    private Phase phase = Phase.BOOTSTRAP;
    private Task active;
    private Task closer;
    private int lootTicks;
    private int phaseTicks;
    private int tradeTicks;
    /**
     * Ticks spent in the "get a gold helmet before the fortress" branch without ever
     * becoming protected. E96 used to be a pure detector: with >=5 gold it returned
     * getItemTask(GOLDEN_HELMET, 1) unconditionally, so if that craft could not be
     * satisfied (no reachable crafting table) the branch returned the same task forever
     * and the bot walked into a fortress bare-headed — E110 at 9:51 in the last run.
     */
    private int goldHelmTicks;
    /** S193 nether climb hysteresis: stall counter, best Y reached, and give-up cooldown. */
    private int netherClimbStallTicks;
    private int netherClimbBestY = Integer.MIN_VALUE;
    private int netherClimbCooldown;
    private int lastInvHash;
    private final Set<BlockPos> looted = new HashSet<>();
    private boolean usedCloser;
    private int lastHp;
    private boolean recycleArmed;
    private boolean hadKit;
    private int darkPortalTicks;
    private boolean lootGaveUp;
    private int statusTicks;
    private boolean junkDropped;
    private int endTicks;
    private boolean sessionLive;
    private int stillTicks;
    private int lastStillX = Integer.MIN_VALUE;
    private int lastStillY;
    private int lastStillZ;
    private int ironStill;
    /**
     * Last observed iron count (ingots + ore). Used by ironWatch() to distinguish a
     * genuine stall from merely standing still while a furnace runs — see the long
     * comment in ironWatch().
     */
    private int lastIronN = -1;
    private int lastIronX = Integer.MIN_VALUE;
    private int lastIronZ;
    private int goldSkip;
    private int portalAttempts;
    private int craftStuck;
    private int craftX = Integer.MIN_VALUE;
    private int craftZ = Integer.MIN_VALUE;
    private boolean forceSurface;
    private int forceSurfaceTicks;
    private int waterCooldown;
    private int wetStreak;
    /** Once 3 iron exist, stay on pick craft. Do not bounce to SurfaceBail. */
    private boolean pickCraftLock;
    private boolean skipIronPick;
    private int e91Count;
    private int recraftPause;
    private int stepOffAge;
    /**
     * S179: ticks spent on the current wood hunt, plus the bounding box of positions seen
     * during it. The box (not a bucketed cell) is what detects an oscillating bot — see the
     * S179 comment in bootstrap(). A discrete bucket is defeated by motion that straddles
     * the bucket boundary, which is exactly what an oscillation does.
     */
    private int woodStill;
    private int woodBoxX0 = Integer.MIN_VALUE;
    private int woodBoxX1 = Integer.MIN_VALUE;
    private int woodBoxZ0 = Integer.MIN_VALUE;
    private int woodBoxZ1 = Integer.MIN_VALUE;
    private int woodPause;
    /**
     * Consecutive E97 local-retargets that produced no wood. Reset the moment woodUnits
     * rises. At 3, BOOTSTRAP stops retargeting locally and walks away from the cluster —
     * see the E97 SOLVER comment in bootstrap(). A jungle's nearest logs are often
     * canopy logs that cannot be pathed to, so local blacklisting never converges there.
     */
    private int woodChurn;
    /**
     * The iron target for the CURRENT IRON phase, computed once when the phase is
     * entered and held until the phase changes. See the "IRON TARGET MUST BE STABLE"
     * comment in iron(). A value recomputed per tick made the target a moving one
     * (8 -> 20 -> 22 -> 24), and because `CollectIronIngotTask.isEqualResource`
     * compares the count, every change restarted the whole resource collection.
     * 0 means "not yet chosen for this phase".
     */
    private int ironWant;
    /**
     * Latch for the E93 "iron pick needs sticks" branch.
     *
     * iron() checked stickFuel(mod) < 2 on EVERY tick and returned collectWood(mod, 1)
     * whenever it was short. collectWood returns a fresh MineAndCollectTask each call
     * (or a TimeoutWanderTask when the scanner has no log), so the parent task-tree
     * diff — which only reuses a child when `active.getClass() == wanted.getClass()` —
     * saw a NEW task object with a NEW target every time, discarded the old one, and
     * the wood task never accumulated enough ticks to actually reach and fell a tree.
     *
     * Observed run C: E93 at 5:49 / 5:54 / 5:59 / 6:04 / 6:10 / 6:15 — one re-issue per
     * 5s (the T2Log.once throttle interval), i.e. every single tick that the throttle
     * let through. The bot churned at -242,190 for ~1 minute needing exactly ONE log,
     * and only advanced when a tree happened to be inside punching range already.
     *
     * Fix: once we hand off to the wood task, stop issuing it. While the latch is armed
     * iron() re-returns the live `active` child untouched (so stick()'s class-equality
     * branch keeps the identical object) and only releases the latch when wood appears
     * or the window expires.
     */
    private int woodForSticksTicks;
    /** The task collectWood() produced when the latch was armed. Only used to detect
     *  "was a latch ever armed" for the S143 release log and for reset(). */
    private Task woodForSticks;
    private int wanderHold;

    /**
     * S149. Ticks remaining in which a live blaze-rod search must NOT be restarted.
     *
     * WHY THIS EXISTS
     * nether() returns `new CollectBlazeRodsTask(...)` EVERY TICK. CollectBlazeRodsTask
     * extends ResourceTask and delegates to SearchChunksExploreTask, which replaces its own
     * child with a TimeoutWanderTask while no nether-bricks chunk is in range:
     *
     *   SearchChunksExploreTask.onTick():
     *       if (searcher == null) { return getWanderTask(); }   // TimeoutWanderTask(true)
     *
     * So `active` becomes a TimeoutWanderTask. stick()'s class-equality branch
     * (`active.getClass() == wanted.getClass()`) then compares TimeoutWanderTask against a
     * freshly built CollectBlazeRodsTask, fails, and does `active = wanted`. That tears the
     * whole search down: CollectBlazeRodsTask.onStart() -> resetSearch() clears
     * `alreadyExplored` and nulls the searcher, and the fortress/blob search restarts from
     * scratch.
     *
     * Measured on run F, Nether phase:
     *   New searcher: [-7, 10]   <- SAME CHUNK, four separate times
     *   New searcher: [-7, 10]
     *   New searcher: [-7, 10]
     *   New searcher: [-7, 10]
     *   "Search finished" / "Target object search failed": 0  <- it never completed once
     * Child flips: CollectBlazeRodsTask -> TimeoutWanderTask -> CollectBlazeRodsTask, and
     * the bot orbited spawn for ~80s then swept west at 0.44 blocks/sec for 100s.
     *
     * This is the exact same failure shape as S146 (a per-tick recompute tearing down a
     * running ResourceTask) and S143 (a per-tick re-issue resetting a latched task). The
     * fix is the same: while a blaze search is LIVE, do not restart it.
     *
     * Unlike wanderHold, this is set whenever nether() asks for blaze rods, so it survives
     * the internal CollectBlazeRodsTask -> TimeoutWanderTask substitution that the driver
     * cannot otherwise see.
     */
    private int blazeSearchTicks;
    /** True while the live child is (or belongs to) a blaze-rod search. S149. */
    private boolean blazeSearchLive;

    /**
     * S157: minimum ticks the child must stay put after a Construct <-> HolePillar swap.
     * Without it the two tasks alternate every tick and neither ever runs.
     */
    private static final int SWAP_COOL_TICKS = 20 * 3;
    private int swapCoolTicks;
    /** S157 evidence: suppressed swaps since the last log line, and the log rate limit. */
    private static final int SWAP_LOG_COOL_TICKS = 20 * 30;
    private int swapHoldCount;
    private int swapLogCooldown;

    /**
     * S164: a pair-agnostic child ping-pong throttle. S157 only knows about
     * Construct <-> HolePillar, but any two children that alternate fast enough are equally
     * dead: run R swapped CraftInTableTask <-> GetOutOfWaterTask 370 times in 16 seconds
     * while standing in a river trying to craft at a table, and no guard existed for it
     * because it was not the pair anyone had seen before.
     *
     * Escape hatches are exempt on purpose — suppressing an escape turns oscillation into a
     * stall, which is strictly worse.
     */
    private static final int SWAP_WINDOW_TICKS = 20;
    private static final int SWAP_MAX = 6;
    private static final int SWAP_HOLD_TICKS = 20 * 3;
    private String swapPairName = "";
    private String lastActiveName = "";
    private int swapCount;
    private int swapWindowTicks;
    private int swapHoldTicks;

    /**
     * Spawn gate (S159). The bot only plays seeds that spawn near a village or a ruined
     * portal. Deliberately NOT evaluated at tick 1: the block scanner has not seen the
     * spawn area yet at onStart, so an early gate would reject every seed and reroll
     * forever. 20s gives it time to scan the surroundings.
     */
    private static final int SPAWN_GATE_TICKS = 20 * 20;

    /**
     * Latest moment the gate may still be waiting for a structure to come into view. A
     * village in a chunk that has not loaded is "unseen", not "absent" — rejecting on the
     * first probe would throw away good seeds. 60s is long enough for the spawn chunks and
     * a bit of walking around, short enough that a reroll still costs under a minute.
     */
    private static final int SPAWN_GATE_DEADLINE = 20 * 60;
    private boolean spawnGateChecked;
    private int gateProbes;

    /**
     * S170. The gate runs ONCE, on a 20-second-old world with only the spawn chunks loaded.
     * Run U showed what that is worth: `spawn ACCEPTED biome=BeachBiome ... rpDist=33` and
     * then 24 minutes with `logs=0` and `woodpick=0`, because "a ruined portal within 160
     * blocks" says nothing about whether the bot can ever get sticks. Run P was the same
     * shape and died at 14:38 for the same reason.
     *
     * A single sighting cannot be trusted, so this is the backstop that measures the thing
     * that actually matters — the passage of time without wood. If the wood count is still
     * zero this long after an accepted spawn, the seed is a dud and no amount of further
     * wandering is going to fix it; the whole 55-minute harness budget would be spent
     * proving it. Reroll and try again.
     *
     * 8 minutes is chosen from the runs: run T reached PORTAL at 4:07 and had logs well
     * before that, and even a slow BOOTSTRAP that has to cross a river has sticks by ~5
     * minutes. Eight is past every successful run this session and well inside the budget,
     * so a reroll costs at most an eighth of the harness instead of all of it.
     */
    private static final int BARREN_DEADLINE_TICKS = 20 * 60 * 8;
    private int gateAcceptTicks = -1;
    private boolean barrenChecked;

    /**
     * S167. "Near a village or ruined portal" says nothing about the ground the bot is
     * actually standing on, and a spawn IN water has now cost three separate runs:
     *   - run N: water spawn, 38 minutes, 0 items collected,
     *   - run R: RiverBiome, `CraftInTable <-> GetOutOfWater` swapped 370 times in 16s,
     *   - run S: DesertLakesBiome, submerged at -129,61,42 with `spd=0.000`, a survival
     *     chain took the child slot at t=1:15 and the driver never ticked again.
     *
     * Being wet for a moment is not the problem - the bot swims across rivers constantly.
     * Being SUBMERGED continuously for this long at spawn is: it means the escape-water
     * pathing is not converging. Reroll instead of spending 55 minutes proving it.
     */
    private static final int WATER_LOCK_TICKS = 20 * 8;
    private int waterLockTicks;

    /**
     * S160. A portal build is pinned in {@link #stick} so it is never abandoned mid-way,
     * which is correct — but the pin must have an exit. After this long the pin is
     * suspended for {@link #CONSTRUCT_COOL_TICKS} so the driver can do something else.
     */
    // 120s, not 180: a legitimate bucket portal build is well under a minute (dig the
    // frame, place water, collect lava), so anything past two minutes is not "still
    // building" — it is stuck. Run O burned nine minutes of a 55-minute budget here.
    private static final int CONSTRUCT_MAX_TICKS = 20 * 120;
    private static final int CONSTRUCT_COOL_TICKS = 20 * 30;
    private Task constructLive;
    private int constructLiveTicks;
    private int constructCoolTicks;

    /**
     * S163. The bucket-portal build digs toward the nearest lava lake the block scanner has
     * seen, and a lake deep underground means a 1x1 shaft straight down. Run Q dug
     * 175,187 from y=71 to y=28 that way, took six fall-damage hits on the way, met a
     * zombie at the bottom and died at 9:34 with the whole kit on it — the first run all
     * session to reach PORTAL on a good seed.
     *
     * Sinking in the dark below {@link #DEEP_Y} is therefore treated as a failed dig: the
     * build state is reset (so it cannot walk back down the same shaft) and the bot is
     * forced to pillar out before anything else resumes.
     *
     * S166 retuned the two floors. DEEP_Y was 30 and DEEP_SINK 8, which together only fired
     * below y=22 — and run R died at y=34, ABOVE that floor, shot by a skeleton in a shaft
     * the guard was written to catch. Overworld surface is ~y=62-70 everywhere, so y=38 is
     * already 25 blocks underground; SAFE_LAKE_Y (40) deliberately sits just above it so a
     * legitimate surface lake never trips this.
     */
    private static final int DEEP_Y = 38;
    private static final int DEEP_TICKS = 20 * 15;
    private static final int DEEP_SINK = 4;
    private static final int DEEP_BAIL_TICKS = 20 * 30;
    private int deepDarkTicks;
    private int deepEntryY = Integer.MIN_VALUE;
    private int deepBailTicks;

    /**
     * S166. Depth is not the only thing that makes a shaft fatal. closeHostile() filters out
     * skeletons and witches on purpose - chasing ranged mobs burns the clock - so a bot
     * standing in a hole being shot has no combat task to take the slot and simply bleeds
     * out. That is exactly how run R ended: `Player29 was shot by Skeleton` at y=34, with
     * S163 silent because 34 > DEEP_Y.
     *
     * So losing SHAFT_HURT_HP while the portal build has the bot in the dark also arms the
     * same bail. Eight HP is four hearts: one stray arrow is survivable and not worth
     * abandoning a build for, four is a fight the bot is losing.
     */
    private static final int SHAFT_HURT_HP = 8;
    private int shaftHpRef = -1;

    /**
     * S165. "Underground with no tools" has to be detected FAST, not after two 120-second
     * bails. Run R respawned at -136,63,-115 and fell to y=-1 in seventeen seconds — it was
     * dead in the void before SurfaceBailTask ever reached its S156 timeout, so giveUps()
     * stayed at 0 and the give-up-based check never fired. Same shape as run Q, which did
     * reach the timeout and then burned seven minutes proving what the inventory already
     * said. Three independent proofs, any one of which is enough:
     *   - a bail already gave up (unclimbable hole confirmed twice over),
     *   - the bot has been underground for 30s AND is below y=45 with nothing in its hands,
     *   - it has dropped UNDERGROUND_FALL_BLOCKS within 10s of going underground.
     *
     * Both fast proofs are deliberately narrow. BOOTSTRAP legitimately spends time under a
     * roof while it looks for iron, and a cave mouth walked down slowly can lose altitude
     * without anything being wrong — so the timeout proof also requires real depth, and the
     * fall proof also requires the drop to be FAST. Neither fires on a run that is still
     * making progress.
     *
     * S168 retuned the fall threshold from 24 to 16 blocks. Run T respawned at 29,51,-39 with
     * an empty inventory, dropped to y=36 and then y=31 — a 20-block fall that missed a
     * 24-block threshold by four — and then sat at 29,31,-39 with `sky=0`, `pick=0`, logging
     * "Failed exploring" every two seconds while SurfaceBailTask was replaced 14 times over
     * five minutes. The timeout proof never fired either, because SurfaceBailTask.MAX_TICKS
     * (120s) is longer than the 40s S140 replace window, so every replacement reset the
     * clock. Sixteen blocks is still well above a surface dip: a respawn into a shallow
     * hollow is survivable in a way that a 16-block drop into a cave is not.
     */
    private static final int UNDERGROUND_HOPELESS_TICKS = 20 * 30;
    private static final int UNDERGROUND_HOPE_Y = 45;
    private static final int UNDERGROUND_FALL_BLOCKS = 16;
    private static final int UNDERGROUND_FALL_TICKS = 20 * 10;
    private int undergroundTicks;
    private int undergroundEnterY = Integer.MIN_VALUE;

    /** After mob-defense / blacklist stall, force CollectIron to re-pick. */
    private boolean ironNeedsKick;
    private int lastCombatPulse;
    private int deathLock;
    private Dimension deathDim;
    private int unstickHold;
    private int freezeStill;
    private int freezeCool;
    private int freezeFails;
    private int freezeX = Integer.MIN_VALUE;
    private int freezeY = Integer.MIN_VALUE;
    private int freezeZ = Integer.MIN_VALUE;
    private int lastProgressHash;

    @Override
    protected void onStart() {
        if (sessionLive && phase != Phase.DONE) {
            T2Log.warn("E80", "parent onStart ignored, still ph=" + phase + " t=" + SpeedrunClock.now());
            T2History.note("onStart swallowed — keep " + phase);
            return;
        }
        // S175. Arm the deadman for a real attempt. It is deliberately armed HERE and not
        // at class-load: the harness spends minutes in world creation and screen setup
        // where a long gap between ticks is expected, and a watchdog that fires during
        // start-up would be worse than no watchdog at all.
        try {
            T2Deadman.setRunDir(
                    adris.altoclef.tasks.speedrun.testrun2.util.GameFiles.dir().toString());
            T2Deadman.arm();
        } catch (Throwable ignored) {}
        sessionLive = true;
        T2Brain.reset();
        AltoClef boot = AltoClef.getInstance();
        if (boot != null && boot.getPlayer() != null && count(boot, Items.IRON_PICKAXE) >= 1) {
            phase = Phase.PORTAL;
            hadKit = true;
            T2History.note("onStart resume PORTAL — iron pick in inv");
        } else if (boot != null && boot.getPlayer() != null
                && (count(boot, Items.STONE_PICKAXE) + count(boot, Items.WOODEN_PICKAXE) >= 1)) {
            phase = Phase.IRON;
            T2History.note("onStart resume IRON — pick in inv");
        } else {
            phase = Phase.BOOTSTRAP;
        }
        active = null;
        closer = null;
        lootTicks = 0;
        phaseTicks = 0;
        tradeTicks = 0;
        usedCloser = false;
        looted.clear();
        lastHp = 20;
        recycleArmed = false;
        // S153. This used to run unconditionally and wiped the `hadKit = true` set by the
        // PORTAL-resume branch above, so a run that resumed with a full kit immediately
        // forgot it had one. Only clear it when we are genuinely starting from scratch.
        if (phase == Phase.BOOTSTRAP) hadKit = false;
        darkPortalTicks = 0;
        lootGaveUp = false;
        statusTicks = 0;
        junkDropped = false;
        endTicks = 0;
        stillTicks = 0;
        portalAttempts = 0;
        goldHelmTicks = 0;
        lastIronN = -1;
        woodForSticksTicks = 0;
        woodForSticks = null;
        woodChurn = 0;
        ironWant = 0;
        blazeSearchTicks = 0;
        blazeSearchLive = false;
        forceSurface = false;
        forceSurfaceTicks = 0;
        waterCooldown = 0;
        wetStreak = 0;
        pickCraftLock = false;
        deathLock = 0;
        SpeedrunClock.reset();
        T2Trace.reset();   // fresh trace.log for this session
        spawnGateChecked = false;
        gateProbes = 0;
        // S170: both must reset with the gate, or a rerolled run inherits the previous
        // seed's accept time and fires the barren check immediately.
        gateAcceptTicks = -1;
        barrenChecked = false;
        constructLive = null;
        constructLiveTicks = 0;
        constructCoolTicks = 0;
        deepDarkTicks = 0;
        deepEntryY = Integer.MIN_VALUE;
        deepBailTicks = 0;
        shaftHpRef = -1;
        waterLockTicks = 0;
        swapPairName = "";
        lastActiveName = "";
        swapCount = 0;
        swapWindowTicks = 0;
        swapHoldTicks = 0;
        // NOTE: no spawn check here any more. At onStart the block scanner has not seen the
        // spawn area, so village/portal are both false and any gate would reject every
        // seed and reroll forever. The S159 gate runs once the scanner has had time — see
        // spawnGate() and the SPAWN_GATE_TICKS field comment.
        // Mining always Baritone; travel mover may be tungsten when jar present (1.21.x only).
        String moverLine;
        try {
            moverLine = adris.altoclef.movement.TungstenMovement.statusLine();
        } catch (Throwable t) {
            moverLine = "mover=baritone (status unavailable)";
        }
        Debug.logMessage("TESRUN2 start " + moverLine + " eyes=" + SpeedrunOpt.EYES
                + " rods=" + SpeedrunOpt.BLAZE_RODS
                + " skipDiamond=" + SpeedrunOpt.SKIP_DIAMOND_ARMOR);
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return null;
        Task r;
        try {
            r = onTickInner(mod);
        } catch (Throwable t) {
            T2Log.warn("E199", "onTick crash " + t.getClass().getSimpleName() + ": " + t.getMessage());
            r = active;
        }
        // High-frequency movement/decision/inventory trace for post-mortem. `active` is
        // the live child the chain is keeping; `r` is what the driver decided this tick.
        T2Trace.tick(mod, phase.name(), active, r);

        // S175. Beat LAST, once per real client tick. This is the only measurement in the
        // project that runs on the client thread and can therefore observe a tick that
        // never finished: run X's trace.log simply stops mid-file, because the client
        // thread blocked inside a single tick that had already taken 19.7s. See T2Deadman.
        try {
            T2Deadman.beat(phase + " @" + mod.getPlayer().getBlockX() + ","
                    + mod.getPlayer().getBlockY() + "," + mod.getPlayer().getBlockZ()
                    + " child=" + (active == null ? "-" : active.getClass().getSimpleName()));
        } catch (Throwable ignored) {}
        return r;
    }

    /**
     * S159 — the run only plays seeds that spawn near a village or a ruined portal.
     * Anything else is a dead run: run N spawned in open water in a Forest, collected
     * 0 items in 38 minutes, and the old "ocean && trees < 3" test classified it as
     * playable because the biome name was not "ocean".
     *
     * Two failure modes this has to avoid:
     *   - rejecting a good seed because the block scanner had not seen the village yet
     *     (hence the probe window up to SPAWN_GATE_DEADLINE, not a single check);
     *   - rerolling forever if the check is wrong (hence SpawnScout.MAX_REROLLS — once the
     *     budget is gone the bot plays what it got rather than burning the whole harness
     *     timeout on world creation).
     */
    private void spawnGate(AltoClef mod) {
        var s = SpawnScout.scan(mod);
        int x = mod.getPlayer().getBlockX();
        int y = mod.getPlayer().getBlockY();
        int z = mod.getPlayer().getBlockZ();
        // S167: a village or ruined portal next door does not help if the bot cannot leave
        // the water it spawned in. Checked BEFORE the accept so a good structure does not
        // excuse a water-locked spawn.
        if (waterLockTicks >= WATER_LOCK_TICKS) {
            spawnGateChecked = true;
            T2Log.force("S167", "spawn REJECTED biome=" + s.biome()
                    + " submerged " + (waterLockTicks / 20) + "s @" + x + "," + y + "," + z
                    + " village=" + s.village() + " rp=" + s.portal() + " " + s.dist()
                    + " - reroll #" + SpawnScout.rerolls() + "/" + SpawnScout.MAX_REROLLS);
            phase = Phase.DONE;
            ResetSignal.fire("spawn submerged in water for "
                    + (waterLockTicks / 20) + "s (reroll #" + SpawnScout.rerolls() + ")");
            return;
        }
        if (s.village() || s.portal()) {
            // S169: a structure in range is necessary but not sufficient. The old accept
            // branch returned unconditionally, so `resetWorthy` — the flag SpawnScout
            // computes for exactly this case (ruined portal in a biome with no wood) — was
            // computed and then thrown away. Run U is what that cost: BeachBiome, rp=true
            // at 33 blocks, accepted, `logs=0` in all 82 heartbeat samples across 24
            // minutes. Honour the flag: reject unless the reroll budget is gone.
            if (s.resetWorthy() && SpawnScout.rerolls() < SpawnScout.MAX_REROLLS) {
                spawnGateChecked = true;
                SpawnScout.noteReroll();
                T2Log.force("S169", "spawn REJECTED (treeless) biome=" + s.biome()
                        + " trees=" + s.trees() + " village=" + s.village()
                        + " rp=" + s.portal() + " " + s.dist() + " @" + x + "," + y + "," + z
                        + " - no wood for sticks, a portal alone is not playable"
                        + " - reroll #" + SpawnScout.rerolls() + "/" + SpawnScout.MAX_REROLLS);
                phase = Phase.DONE;
                ResetSignal.fire("spawn treeless: rp=" + s.portal() + " trees=" + s.trees()
                        + " in " + s.biome() + " (reroll #" + SpawnScout.rerolls() + ")");
                return;
            }
            spawnGateChecked = true;
            T2Log.force("S159", "spawn ACCEPTED biome=" + s.biome()
                    + " village=" + s.village() + " rp=" + s.portal()
                    + " " + s.dist()
                    + " @" + x + "," + y + "," + z
                    + " probes=" + gateProbes + " t=" + SpeedrunClock.now()
                    + (s.resetWorthy() ? " [FORCED past treeless: reroll budget gone]" : ""));
            gateAcceptTicks = statusTicks;
            return;
        }
        // Not seen yet is not the same as absent. Keep probing until the deadline.
        if (statusTicks < SPAWN_GATE_DEADLINE) {
            gateProbes++;
            return;
        }
        spawnGateChecked = true;
        if (SpawnScout.rerolls() >= SpawnScout.MAX_REROLLS) {
            T2Log.force("S159", "spawn FORCED-ACCEPT, reroll budget gone ("
                    + SpawnScout.rerolls() + ") biome=" + s.biome() + " trees=" + s.trees()
                    + " lava=" + s.lava() + " " + s.dist() + " @" + x + "," + y + "," + z);
            return;
        }
        SpawnScout.noteReroll();
        // Say WHY, not just "rejected". A bare "no village/RP" and a "ruined portal in a
        // treeless biome" need completely different follow-ups, and run P showed the
        // second one is a real, distinct failure mode that costs a whole run.
        String why = !s.village() && !s.portal() ? "no village/RP"
                : ("ruined portal but treeless (trees=" + s.trees() + ")");
        T2Log.force("S159", "spawn REJECTED biome=" + s.biome() + " trees=" + s.trees()
                + " lava=" + s.lava() + " village=" + s.village() + " rp=" + s.portal()
                + " " + s.dist() + " @" + x + "," + y + "," + z
                + " probes=" + gateProbes + " reason=" + why
                + " - reroll #" + SpawnScout.rerolls()
                + "/" + SpawnScout.MAX_REROLLS);
        phase = Phase.DONE;
        ResetSignal.fire("spawn: no village/ruined portal within "
                + SpawnScout.SPAWN_LOOT_RADIUS + " blocks (reroll #" + SpawnScout.rerolls() + ")");
    }

    private Task onTickInner(AltoClef mod) {
        statusTicks++;
        // S161: shared bail cooldown. It has to be driven from here because the whole point
        // of a give-up is that SurfaceBailTask stops being ticked.
        SurfaceBailTask.tickShared();
        // S167 — see the WATER_LOCK_TICKS field comment. Counted here, not in spawnGate(),
        // because the gate only runs once every 5s and would sample the player state
        // instead of measuring how long it has been stuck.
        try {
            if (mod.getPlayer().isSubmergedInWater()) waterLockTicks++;
            else waterLockTicks = 0;
        } catch (Throwable ignored) {
            waterLockTicks = 0;
        }
        if (statusTicks % 20 == 0) {
            String childDbg = active == null ? "-" : String.valueOf(active);
            String eq = "-";
            try {
                eq = String.valueOf(StorageHelper.getItemStackInSlot(
                        adris.altoclef.util.slots.PlayerSlot.getEquipSlot()).getItem());
            } catch (Throwable ignored) {}
            Debug.logMessage("T2 [NOW] t=" + SpeedrunClock.now() + " ph=" + phase
                    + " @" + mod.getPlayer().getBlockX() + "," + mod.getPlayer().getBlockY()
                    + "," + mod.getPlayer().getBlockZ()
                    + " do=" + childDbg
                    + " eq=" + eq
                    + " woodpick=" + count(mod, Items.WOODEN_PICKAXE)
                    + " stonepick=" + count(mod, Items.STONE_PICKAXE)
                    + " pick=" + count(mod, Items.IRON_PICKAXE)
                    + " iron=" + count(mod, Items.IRON_INGOT)
                    + " buck=" + (count(mod, Items.WATER_BUCKET) + count(mod, Items.LAVA_BUCKET)));
        }
        // Spawn gate (S159). Deliberately NOT at tick 1 — see the SPAWN_GATE_TICKS comment.
        // Probed every 5s, not every tick: SpawnScout.scan() reads ~3000 blocks and running
        // it 20x/second for 40 seconds would be 1.8M lookups for no extra information.
        if (!spawnGateChecked && phase != Phase.DONE
                && statusTicks >= SPAWN_GATE_TICKS && statusTicks % (20 * 5) == 0) {
            spawnGate(mod);
        }
        // S170 — see the BARREN_DEADLINE_TICKS comment. The gate's verdict is a 20-second-old
        // guess; this is the measurement. Only fires on a run that has been accepted and has
        // collected no wood at all, so a slow-but-working BOOTSTRAP is never touched.
        if (spawnGateChecked && !barrenChecked && phase != Phase.DONE
                && gateAcceptTicks > 0
                && statusTicks - gateAcceptTicks >= BARREN_DEADLINE_TICKS) {
            int logs = totalLogs(mod);
            if (logs == 0 && phase == Phase.BOOTSTRAP) {
                barrenChecked = true;
                T2Log.force("S170", "barren seed: " + ((statusTicks - gateAcceptTicks) / 20 / 60)
                        + "min since the spawn gate accepted, logs=0, phase=" + phase
                        + " @" + mod.getPlayer().getBlockX() + "," + mod.getPlayer().getBlockY()
                        + "," + mod.getPlayer().getBlockZ()
                        + " - reroll #" + (SpawnScout.rerolls() + 1) + "/" + SpawnScout.MAX_REROLLS
                        + " (a seed with no wood cannot be played)");
                SpawnScout.noteReroll();
                phase = Phase.DONE;
                ResetSignal.fire("barren seed: no wood after "
                        + ((statusTicks - gateAcceptTicks) / 20 / 60) + "min");
                return null;
            }
            barrenChecked = true;
        }
        ensureMiningPick(mod);
        phaseTicks++;
        if (phaseTicks % (20 * 10) == 0) {
            Debug.logMessage("T2 [HB] t=" + SpeedrunClock.now() + " ph=" + phase
                    + " y=" + mod.getPlayer().getBlockY()
                    + " child=" + (active == null ? "-" : active.getClass().getSimpleName())
                    + " logs=" + totalLogs(mod)
                    + " pick=" + count(mod, Items.IRON_PICKAXE)
                    + " woodpick=" + count(mod, Items.WOODEN_PICKAXE));
        }
        if (active instanceof StepOffTableTask) {
            stepOffAge++;
            if (stepOffAge >= 40) {
                stepOffAge = 0;
                active = null;
                McCompat.closeScreen();
                // Illegal phase skip: never leave BOOTSTRAP for PORTAL with pick=0.
                if (hasMiningPick(mod)) {
                    T2Log.warn("E94", "step-off frozen — PORTAL with pick present");
                    skipIronPick = true;
                    pickCraftLock = false;
                    recraftPause = 0;
                    setPhase(Phase.PORTAL);
                    return stick(portal(mod));
                }
                T2Log.warn("E94", "step-off frozen pick=0 — stay BOOTSTRAP, retry wooden pick");
                T2History.note("WHY E94 blocked: need wooden_pickaxe+ before PORTAL");
                skipIronPick = false;
                pickCraftLock = false;
                recraftPause = 20 * 2;
                setPhase(Phase.BOOTSTRAP);
                return stick(TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1));
            }
            return active;
        }
        stepOffAge = 0;
        Task solved = T2Brain.help(mod, phase.name(), active);
        if (adris.altoclef.tasks.speedrun.testrun2.util.QueueWatch.blocked()) return null;
        if (solved != null) {
            wanderHold = 20 * 8;
            return stick(solved);
        }
        // S190: keep a running water bail. T2Solve's S102 returns WaterBailTask, then on the
        // next tick returns null ("already escaping, leave it") — and the phase task below
        // replaced the bail anyway. Live run fix1 (IRON @177,62,-142): the child flipped
        // CollectIronIngotTask<->WaterBailTask every tick for 60s+ at spd=0, S164 kept
        // pinning the collector, and WaterBailTask's 5s shore escalation never got to run.
        // WaterBailTask ends itself (1s dry, or MAX_TICKS), same contract as SurfaceBailTask.
        if (active instanceof WaterBailTask && !active.isFinished()) {
            return active;
        }
        if (wanderHold > 0 && active instanceof TimeoutWanderTask && !active.isFinished()) {
            wanderHold--;
            return active;
        }
        if (wanderHold <= 0 && active instanceof TimeoutWanderTask) {
            active = null;
        }

        int fx = mod.getPlayer().getBlockX();
        int fy = mod.getPlayer().getBlockY();
        int fz = mod.getPlayer().getBlockZ();
        boolean craftingNow = pickCraftLock
                || (active != null && active.getClass().getSimpleName().contains("Craft"));
        if (freezeCool > 0) freezeCool--;
        if (craftingNow && unstickHold > 0 && !(active instanceof StepOffTableTask)) {
            unstickHold = 0;
            McCompat.setMove(false, false);
        }
        if (unstickHold > 0 && !craftingNow && phase != Phase.PORTAL) {
            unstickHold--;
            if (unstickHold % 20 == 0) McCompat.setYaw(McCompat.playerYaw() + 90f);
            McCompat.setMove(true, unstickHold % 10 < 6);
            if (unstickHold % 20 == 0) T2History.note("E98 overlay t=" + unstickHold + " @" + fx + "," + fz);
            if (unstickHold <= 0) {
                McCompat.setMove(false, false);
                freezeStill = 0;
                freezeCool = 20 * 15;
            }
            // keep the same child — overlay only, do not return null
        }
        int progress = totalLogs(mod) + totalPlanks(mod) + count(mod, Items.IRON_INGOT) * 3
                + count(mod, Items.WOODEN_PICKAXE) * 11 + count(mod, Items.STONE_PICKAXE) * 13
                + count(mod, Items.IRON_PICKAXE) * 17 + count(mod, Items.FLINT) * 19
                + count(mod, Items.WATER_BUCKET) * 23 + count(mod, Items.LAVA_BUCKET) * 29
                + count(mod, Items.FLINT_AND_STEEL) * 31;
        if (progress != lastProgressHash) {
            lastProgressHash = progress;
            freezeStill = 0;
        }
        if (fx == freezeX && fz == freezeZ && Math.abs(fy - freezeY) <= 1) freezeStill++;
        else {
            freezeStill = 0;
            freezeFails = 0;
            freezeX = fx;
            freezeY = fy;
            freezeZ = fz;
        }
        boolean mining = active != null && (active.getClass().getSimpleName().contains("Mine")
                || active.getClass().getSimpleName().contains("Collect"));
        int freezeLimit = mining ? 20 * 40 : 20 * 20;
        if (!craftingNow && phase == Phase.BOOTSTRAP && !mining && freezeCool <= 0
                && freezeStill > freezeLimit) {
            freezeFails++;
            T2Log.warn("E98", "XZ frozen " + (freezeStill / 20) + "s @" + fx + "," + fz
                    + " fail=" + freezeFails + " child="
                    + (active == null ? "-" : active.getClass().getSimpleName()));
            freezeStill = 0;
            McCompat.closeScreen();
            McCompat.cancelPathing();
            if (freezeFails >= 3) {
                freezeCool = 20 * 30;
                freezeFails = 0;
                T2Log.warn("E99", "walk-off failed 3x — skip this tile");
                return stick(offsetWalk(mod));
            }
            unstickHold = 20 * 4;
            McCompat.setYaw(McCompat.playerYaw() + 90f * freezeFails);
            McCompat.setMove(true, false);
            return null;
        }

        if (dragonDead(mod)) {
            setPhase(Phase.DONE);
            try { TungstenHelper.stop(); } catch (Throwable ignored) {}
            return null;
        }
        if (usedCloser && WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            usedCloser = false;
            closer = null;
            T2History.note("closer dropped — in nether");
        }

        int hp = (int) mod.getPlayer().getHealth();
        if (lastHp > 0 && hp <= 0) {
            recycleArmed = true;
            deathDim = WorldHelper.getCurrentDimension();
        }
        lastHp = hp;
        // Do NOT treat portal inventory flicker as death. Only HP hitting 0.
        if (count(mod, Items.IRON_PICKAXE) >= 1) hadKit = true;
        if (recycleArmed && hp > 0 && phase != Phase.END) {
            recycleArmed = false;
            deathLock = 20 * 25;
            lootGaveUp = true;
            T2Log.warn("E50", "respawn recycle dim=" + deathDim);
        }
        if (deathLock > 0) {
            deathLock--;
            Dimension now = WorldHelper.getCurrentDimension();
            if (deathDim == Dimension.NETHER && now == Dimension.OVERWORLD && portalNearby(mod)) {
                T2History.note("WHY death: re-enter portal for grave");
                return stick(new EnterNetherPortalTask(Dimension.NETHER));
            }
            if (deathLock == 0) recraftPause = 20 * 3;
            return stick(new DeathRecycleTask());
        }

        if (WorldHelper.getCurrentDimension() == Dimension.END) {
            endTicks++;
            setPhase(Phase.END);
        } else {
            endTicks = 0;
            Phase next = decide(mod);
            if (next != phase && phaseTicks >= 40) {
                setPhase(next);
            }
        }

        if (phase == Phase.DONE) return null;

        // S163 — see the DEEP_Y field comment. A portal dig that is still sinking in the
        // dark below y=30 is not "still building", it is the shaft that killed run Q.
        if (deepBailTicks > 0) deepBailTicks--;
        if (phase == Phase.PORTAL && active instanceof ConstructNetherPortalBucketTask
                && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD) {
            int dy = mod.getPlayer().getBlockY();
            if (dy <= DEEP_Y && SurfaceBailTask.sky(mod) <= 0) {
                if (deepEntryY == Integer.MIN_VALUE) deepEntryY = dy;
                deepDarkTicks++;
                if (deepDarkTicks >= DEEP_TICKS && dy <= deepEntryY - DEEP_SINK) {
                    deepBailTicks = DEEP_BAIL_TICKS;
                    deepDarkTicks = 0;
                    // Clear the cached portal site too. ConstructNetherPortalBucketTask
                    // keeps its chosen lava lake across ticks, and isEqual keeps the same
                    // instance alive, so without this the bot would pillar out and then dig
                    // straight back down the identical shaft.
                    if (active instanceof ConstructNetherPortalBucketTask) {
                        ((ConstructNetherPortalBucketTask) active).hardResetBuildState();
                    }
                    T2Log.force("S163", "portal dig sank y=" + deepEntryY + " -> y=" + dy
                            + " in the dark @" + mod.getPlayer().getBlockX() + "," + dy
                            + "," + mod.getPlayer().getBlockZ()
                            + " - site cleared, pillar out and rebuild elsewhere");
                }
            } else {
                deepDarkTicks = 0;
                deepEntryY = Integer.MIN_VALUE;
            }
        } else {
            deepDarkTicks = 0;
            deepEntryY = Integer.MIN_VALUE;
        }

        // S166 — see the SHAFT_HURT_HP field comment. Depth alone missed run R, which died
        // at y=34 to skeleton arrows. Any dark spot at or below y=55 counts here (a build
        // under a roof is still a build in a hole), and the reference resets whenever the
        // bot is back in the light so a slow drip of damage does not accumulate across the
        // whole run.
        boolean buildDark = phase == Phase.PORTAL
                && active instanceof ConstructNetherPortalBucketTask
                && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD
                && mod.getPlayer().getBlockY() <= 55
                && SurfaceBailTask.sky(mod) <= 0;
        if (buildDark && hp > 0) {
            if (shaftHpRef < 0) shaftHpRef = hp;
            else if (hp <= shaftHpRef - SHAFT_HURT_HP && deepBailTicks <= 0) {
                deepBailTicks = DEEP_BAIL_TICKS;
                deepDarkTicks = 0;
                deepEntryY = Integer.MIN_VALUE;
                if (active instanceof ConstructNetherPortalBucketTask) {
                    ((ConstructNetherPortalBucketTask) active).hardResetBuildState();
                }
                T2Log.force("S166", "portal build bled " + (shaftHpRef - hp) + " HP ("
                        + shaftHpRef + " -> " + hp + ") in the dark at y="
                        + mod.getPlayer().getBlockY()
                        + " @" + mod.getPlayer().getBlockX() + "," + mod.getPlayer().getBlockY()
                        + "," + mod.getPlayer().getBlockZ()
                        + " - ranged damage with no fight to take the slot, pillar out");
            }
        } else if (!buildDark) {
            shaftHpRef = -1;
        }

        if (deepBailTicks > 0) {
            // HolePillarTask is already exempt from the Construct pin in stick(), so this is
            // the one task that can actually take the slot and climb.
            if (active instanceof HolePillarTask && !active.isFinished()) return active;
            return stick(new HolePillarTask());
        }

        // Naked walk-in is a ruined-portal suicide after death. Need a pick.
        if (WorldHelper.getCurrentDimension() == Dimension.OVERWORLD
                && portalNearby(mod)
                && count(mod, Items.IRON_PICKAXE) >= 1) {
            usedCloser = false;
            closer = null;
            if (phase != Phase.PORTAL && phaseTicks >= 40) setPhase(Phase.PORTAL);
            T2History.note("WHY walk-in: portal + iron pick");
            return stick(new EnterNetherPortalTask(Dimension.NETHER));
        }

        // S161: an underground bot with no pickaxe and nothing to place cannot dig out of
        // stone with its fists, so bailing is theatre. Run Q respawned into a 45-block pit
        // at 119,19,202 with an empty inventory after losing the kit at 9:34 and then held
        // the child slot until the harness killed it seven minutes later.
        //
        // S165: `giveUps() >= 2` alone is far too slow (see the UNDERGROUND_HOPELESS_TICKS
        // field) — run R was in the void before the first bail could time out. Any of the
        // three proofs below is enough.
        //
        // S172: THIS BLOCK MUST STAY ABOVE THE E90 BAIL BELOW. It used to sit underneath it,
        // and the E90 block ends in `return stick(new SurfaceBailTask())` on a condition
        // (`phase == BOOTSTRAP && underground && !cooling`) that is TRUE for exactly the bot
        // this check exists to catch. Run V respawned at 57,-2,225 with
        // `sky=0 pick=0 iron=0 buck=0` and an empty inventory — the textbook unrecoverable
        // case — and then sat there while E90 fired 20 times and S165 fired ZERO times,
        // because the block was structurally unreachable in BOOTSTRAP. Same failure class as
        // the S168 deadline starvation: a guard another guard prevents you from reaching is
        // not a guard. Ordering here is load-bearing, not stylistic.
        int uy = mod.getPlayer().getBlockY();
        if (SurfaceBailTask.underground(mod)) {
            if (undergroundTicks == 0) undergroundEnterY = uy;
            undergroundTicks++;
        } else {
            undergroundTicks = 0;
            undergroundEnterY = Integer.MIN_VALUE;
        }
        boolean fellIn = undergroundEnterY != Integer.MIN_VALUE
                && uy <= undergroundEnterY - UNDERGROUND_FALL_BLOCKS
                && undergroundTicks <= UNDERGROUND_FALL_TICKS;
        boolean deepDark = undergroundTicks >= UNDERGROUND_HOPELESS_TICKS
                && uy <= UNDERGROUND_HOPE_Y;
        // S172: the S165 proofs above all require undergroundTicks to accumulate, and one of
        // the ways a bot becomes unrecoverable is a respawn straight into a deep dark spot
        // (run V: y=-2, sky=0). Counting is enough there, but a THIRD fast proof removes the
        // 30s wait entirely for the clear-cut case: below y=0 in the overworld with an empty
        // inventory is not a situation any amount of walking fixes.
        boolean buriedAlive = uy <= 0 && SurfaceBailTask.sky(mod) <= 2
                && SurfaceBailTask.unrecoverable(mod);
        if (phase != Phase.DONE && SurfaceBailTask.unrecoverable(mod)
                && (SurfaceBailTask.giveUps() >= 1 || deepDark || fellIn || buriedAlive)) {
            // Which proof fired is the whole point of the log line: S161 means a bail
            // already burned two minutes proving it, S165 means we caught it before that.
            String proof = SurfaceBailTask.giveUps() >= 1 ? "bailGaveUp"
                    : (fellIn ? "fellIn" : (buriedAlive ? "buriedAlive" : "deepDark"));
            String code = SurfaceBailTask.giveUps() >= 1
                    ? T2Codes.S161_UNRECOVERABLE : T2Codes.S165_UNRECOVERABLE_FAST;
            T2Log.force(code, "unrecoverable: y=" + mod.getPlayer().getBlockY()
                    + " no pick, nothing to place, proof=" + proof
                    + " underground=" + (undergroundTicks / 20) + "s"
                    + " entryY=" + (undergroundEnterY == Integer.MIN_VALUE ? "?" : "" + undergroundEnterY)
                    + " bails=" + SurfaceBailTask.giveUps()
                    + " @" + mod.getPlayer().getBlockX() + ","
                    + mod.getPlayer().getBlockY() + "," + mod.getPlayer().getBlockZ()
                    + " - abandoning the seed");
            phase = Phase.DONE;
            ResetSignal.fire("underground with no tools (proof=" + proof + ", "
                    + SurfaceBailTask.giveUps() + " failed surface bails)");
            return null;
        }

        // IRON must mine ore. Only yank BOOTSTRAP out of a cave.
        // Exception: already have 3 iron and are crafting a pick in the dark —
        // that is the jump-on-buried-table lock.
        //
        // S161: skip the bail while a previous give-up is still cooling. Re-issuing it
        // immediately is what made run Q emit S156 102 times — the new bail inherits the
        // same unclimbable hole and burns another two minutes to reach the same log line.
        if (phase == Phase.BOOTSTRAP && SurfaceBailTask.underground(mod)
                && !SurfaceBailTask.cooling()) {
            T2Log.warn("E90", "bootstrap in dark — surface");
            return stick(new SurfaceBailTask());
        }
        int skyNow = 15;
        try {
            skyNow = mod.getWorld().getLightLevel(net.minecraft.world.LightType.SKY, mod.getPlayer().getBlockPos());
        } catch (Throwable ignored) {}
        if (skyNow >= 10) {
            forceSurface = false;
            forceSurfaceTicks = 0;
        } else if (forceSurfaceTicks > 20 * 40) {
            // Bail pathing failed. Still refuse pick-craft until sky>=10.
            forceSurfaceTicks = 0;
        }
        if (pickCraftLock) forceSurface = false;
        if (forceSurface && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD) {
            forceSurfaceTicks++;
            if (active instanceof SurfaceBailTask && !active.isFinished()) {
                return active;
            }
            return stick(new SurfaceBailTask());
        }
        if (active instanceof SurfaceBailTask && !active.isFinished()) {
            return active;
        }
        Task unstick = unstickCraft(mod);
        if (unstick != null) return unstick;

        if (waterCooldown > 0) waterCooldown--;
        if (SpeedrunOpt.AVOID_DEEP_WATER && inWater(mod)
                && waterCooldown <= 0
                && phase != Phase.END && phase != Phase.NETHER) {
            waterCooldown = 20 * 15;
            T2Log.warn("E10", "submerged — bail once");
            return stick(new WaterBailTask());
        }

        boolean netherish = phase == Phase.NETHER || phase == Phase.EYES
                || WorldHelper.getCurrentDimension() == Dimension.NETHER;
        // BlazePeek stole CollectBlazeRods for 20+ min whenever a blaze was within 4 blocks.
        // Melee hostiles in face (creeper / zombie / baby zombie villager) — own the fight so
        // stick() can drop back into CollectIron instead of leaving a silent noop after KillAura.
        // Still no ranged chase (skeletons/witches filtered in closeHostile).
        if (!netherish && (creeperInFace(mod) || closeHostile(mod))) {
            lastCombatPulse = phaseTicks;
            ironNeedsKick = (phase == Phase.IRON);
            T2History.note("WHY fight: melee hostile in face");
            return stick(FightNearbyTask.hostiles());
        }
        // Combat just ended while IRON — clear ore blacklist and force CollectIron to tick.
        if (phase == Phase.IRON && lastCombatPulse > 0 && phaseTicks - lastCombatPulse < 20 * 3
                && !(active instanceof adris.altoclef.tasks.speedrun.testrun2.combat.AnyWeaponCombatTask)) {
            if (ironNeedsKick || (active != null && active.isFinished())) {
                T2History.note("WHY iron: post-combat resume CollectIron");
                clearBlockBlacklist(mod);
                ironNeedsKick = false;
                lastCombatPulse = 0;
                active = null;
            }
        }

        Task frozen = portalWatch(mod);
        if (frozen != null) return frozen;
        Task ironStuck = ironWatch(mod);
        if (ironStuck != null) return ironStuck;

        if (usedCloser && closer != null && phase == Phase.PORTAL) {
            T2History.tick(mod, phase.name(), closer);
            return closer;
        }
        usedCloser = false;
        if (stalled(mod) && phase == Phase.PORTAL) {
            T2Log.warn("E30", "stall ph=" + phase + " after " + (phaseTicks / 20) + "s");
            return startCloser(mod);
        }

        Task wanted = taskFor(mod, phase);
        if (wanted == null) {
            // Don't bounce into closer if decide() is about to advance next tick.
            Phase again = decide(mod);
            if (again != phase) {
                setPhase(again);
                wanted = taskFor(mod, phase);
            }
        }
        if (wanted == null) {
            if (phase == Phase.PORTAL) {
                return stick(new ConstructNetherPortalBucketTask());
            }
            if (phase == Phase.DONE || phase == Phase.NETHER || phase == Phase.END || phase == Phase.EYES) {
                return null;
            }
            T2History.note("WHY null child in " + phase + " → decide again");
            Phase again2 = decide(mod);
            if (again2 != phase) {
                setPhase(again2);
                wanted = taskFor(mod, phase);
            }
        }
        if (wanted == null) return null;
        return stick(wanted);
    }

    private Phase decide(AltoClef mod) {
        if (phase == Phase.END || phase == Phase.DONE) return phase;

        Dimension dim = WorldHelper.getCurrentDimension();

        if (count(mod, Items.ENDER_EYE) >= SpeedrunOpt.EYES && dim == Dimension.OVERWORLD) {
            return Phase.STRONGHOLD;
        }
        if (dim == Dimension.NETHER) {
            if (count(mod, Items.BLAZE_ROD) >= SpeedrunOpt.BLAZE_RODS
                    && count(mod, Items.ENDER_PEARL) >= SpeedrunOpt.PEARLS) {
                return Phase.EYES;
            }
            return Phase.NETHER;
        }
        // Bucket first: flint-only PORTAL is how RP-loot runs walk into lava with no water.
        // Pick is enough to leave IRON. Sword/shield table crafts walk
        // back into the hole we just climbed out of.
        // skipIronPick only skips the iron pick craft — still need wooden+ before PORTAL
        if (count(mod, Items.IRON_PICKAXE) >= 1 || (skipIronPick && hasMiningPick(mod))) {
            return Phase.PORTAL;
        }
        // Naked + a portal in range is a death respawn, not a speedrun route.
        // E94b: "naked" used to mean "no pick AND no iron". That let a bot holding a
        // stray iron ingot (but ZERO pickaxes) take this branch and enter PORTAL with
        // pick=0 — the same illegal phase skip the E94 step-off gate blocks. A nearby
        // portal is only actionable with an actual mining pick in inventory.
        boolean naked = !hasMiningPick(mod)
                && count(mod, Items.IRON_INGOT) + count(mod, Items.RAW_IRON) < 1;
        if (portalNearby(mod) && !naked && hasMiningPick(mod)) {
            return Phase.PORTAL;
        }
        boolean empty = count(mod, Items.IRON_PICKAXE) < 1
                && count(mod, Items.WOODEN_PICKAXE) < 1
                && count(mod, Items.STONE_PICKAXE) < 1
                && count(mod, Items.IRON_INGOT) + count(mod, Items.RAW_IRON) < 1;
        if (empty) {
            return Phase.BOOTSTRAP;
        }
        if (lootGaveUp
                || count(mod, Items.IRON_PICKAXE) >= 1
                || count(mod, Items.IRON_INGOT) + count(mod, Items.RAW_IRON) >= SpeedrunOpt.IRON) {
            return Phase.IRON;
        }
        Phase next;
        if (count(mod, Items.WOODEN_PICKAXE) >= 1 || count(mod, Items.STONE_PICKAXE) >= 1) {
            next = Phase.IRON;
        } else {
            next = Phase.BOOTSTRAP;
        }
        if (phase.ordinal() > next.ordinal() && phase.ordinal() < Phase.NETHER.ordinal() && !empty) {
            return phase;
        }
        return next;
    }

    private Task taskFor(AltoClef mod, Phase p) {
        return switch (p) {
            case BOOTSTRAP -> bootstrap(mod);
            case LOOT -> loot(mod);
            case IRON -> iron(mod);
            case PORTAL -> portal(mod);
            case NETHER -> nether(mod);
            case EYES -> eyes(mod);
            case STRONGHOLD -> stronghold(mod);
            case END -> end(mod);
            case DONE -> null;
        };
    }

    private Task bootstrap(AltoClef mod) {
        if (recraftPause > 0) recraftPause--;
        // Wood obtained -> the E97 churn counter has done its job; reset it so a later
        // tree-poor patch starts its own count instead of inheriting an old one.
        if (woodUnits(mod) >= 1) {
            woodChurn = 0;
            // S179: clear the box so a later tree-poor patch starts its own measurement
            // rather than inheriting the previous hunt's span.
            woodStill = 0;
            woodBoxX0 = woodBoxX1 = woodBoxZ0 = woodBoxZ1 = Integer.MIN_VALUE;
        }
        // Do not use TaskCatalogue "log" — on 1.16 that list pads with AIR
        // and Baritone Random-Orientation-spins punching nothing.
        if (woodPause > 0) {
            woodPause--;
            McCompat.setMove(true, woodPause % 8 < 4);
            if (woodPause <= 0) McCompat.setMove(false, false);
        }
        boolean hasTable = count(mod, Items.CRAFTING_TABLE) >= 1;
        try { hasTable = hasTable || mod.getBlockScanner().anyFound(Blocks.CRAFTING_TABLE); } catch (Throwable ignored) {}
        if (hasTable && count(mod, Items.WOODEN_PICKAXE) < 1 && count(mod, Items.STONE_PICKAXE) < 1) {
            T2History.note("WHY bootstrap: table exists — wooden pick, not more logs");
            return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
        }
        if (woodUnits(mod) < 1) {
            // S179 — THE TREE OSCILLATION, and why the old counter could not see it.
            //
            // This used to bucket the bot's position into 4-block cells (`x >> 2`) and
            // count ticks in the same cell. The problem: an oscillating bot sits ON or
            // NEAR a cell boundary, so it flips cells forever and the counter resets every
            // few ticks. User-observed: the bot walks back and forth over a tree instead of
            // breaking it. Run AA: the bot reached 43.7,-24.8, froze 152 ticks, then
            // oscillated x=43.9 <-> 44.2 for 20+ seconds. It never moved more than 1.1
            // blocks, but it crossed the x=44 boundary **160 times** between t=200 and
            // t=700, so `woodStill` fired exactly ONCE in the entire run (t=171, during the
            // initial freeze) and never again. A counter keyed to a discrete bucket cannot
            // measure "has not moved" when the idle motion straddles the bucket edge.
            //
            // Fix: track a running bounding box of recent positions and fire on the AREA
            // the bot has explored, which is boundary-free. A bot genuinely walking to a
            // tree sweeps tens of blocks; an oscillating one stays inside a ~2-block box
            // no matter how much it jitters. Local retargeting still happens first (below)
            // so a single unlucky log does not immediately trigger the walk-away.
            int px = mod.getPlayer().getBlockX();
            int pz = mod.getPlayer().getBlockZ();
            if (woodStill == 0) { woodBoxX0 = woodBoxX1 = px; woodBoxZ0 = woodBoxZ1 = pz; }
            if (px < woodBoxX0) woodBoxX0 = px;
            if (px > woodBoxX1) woodBoxX1 = px;
            if (pz < woodBoxZ0) woodBoxZ0 = pz;
            if (pz > woodBoxZ1) woodBoxZ1 = pz;
            woodStill++;
            int spanX = woodBoxX1 - woodBoxX0;
            int spanZ = woodBoxZ1 - woodBoxZ0;
            // The counter must measure time spent PINNED, not time since the hunt began.
            // A bot that walks 20 blocks to a tree has a large span the whole way, so if the
            // span is merely tested (and the counter left running) the guard fires the instant
            // the bot arrives and stops — punishing a normal approach. Re-anchor the box
            // whenever the span grows past the threshold, so woodStill means "consecutive
            // ticks inside a 3-block box" and nothing else.
            if (spanX > 3 || spanZ > 3) {
                woodBoxX0 = woodBoxX1 = px;
                woodBoxZ0 = woodBoxZ1 = pz;
                woodStill = 1;
            }
            // No separate `pinned` flag: the re-anchor above already guarantees that
            // reaching here with woodStill large means the span stayed <= 3 the whole time.
            // (A condition that an earlier branch makes tautological is dead code — the same
            // shape as the S172/S174 unreachable-guard traps.)
            if (woodStill > 20 * 8) {
                // Do NOT TimeoutWander for 8s — that skips nearby jungle/etc for a far oak.
                // Blacklist the stuck tree column and immediately re-pick nearest ANY log type.
                //
                // E97 SOLVER. Local retargeting alone does not converge in a JUNGLE.
                // Modelled 2026-09-20 (run D): spawn in jungle, woodUnits=0 for 4+ min.
                // blacklistNearbyWood() only covers the 7x10x7 volume around the player,
                // but a jungle's nearest logs are canopy logs the bot cannot path to at
                // all — so the scanner happily hands back another unreachable log and the
                // bot churns E97 -> S140 -> E97 forever at one spot (observed -284,113
                // with three E97+S140 pairs and woodpick still 0).
                //
                // After a few failed local retargets, give up on this patch of world and
                // take a real walk. woodChurn is reset whenever wood is actually obtained,
                // so a single unlucky tree never triggers the escape.
                woodChurn++;
                boolean bail = woodChurn >= 3;
                T2Log.warn("S179", "wood oscillation — pinned in a " + (spanX + 1) + "x"
                        + (spanZ + 1) + " box for " + (woodStill / 20) + "s @"
                        + px + "," + pz + " box[" + woodBoxX0 + ".." + woodBoxX1 + ","
                        + woodBoxZ0 + ".." + woodBoxZ1 + "] — retarget nearest log"
                        + (bail ? " [churn=" + woodChurn + " -> WANDER]" : " churn=" + woodChurn));
                woodStill = 0;
                McCompat.closeScreen();
                McCompat.cancelPathing();
                blacklistNearbyWood(mod);
                active = null;
                if (bail) {
                    woodChurn = 0;
                    woodPause = 0;
                    T2History.note("WHY E97: churn>=3 — walk away from this tree cluster");
                    // A plain wander picks a random direction and keeps the scanner fresh.
                    // clearBlockBlacklist so a real tree found on the walk is eligible.
                    clearBlockBlacklist(mod);
                    return new TimeoutWanderTask(16.0f, true);
                }
                woodPause = 20 * 2; // brief strafe only
                Task again = collectWood(mod, 2);
                if (again != null) return again;
                return new TimeoutWanderTask();
            }
            if (woodPause > 0) {
                // Strafing, but still accept any reachable log the scanner already sees.
                Task again = collectWood(mod, 2);
                if (again != null) return again;
                return new TimeoutWanderTask();
            }
            return collectWood(mod, 2);
        }
        if (count(mod, Items.CRAFTING_TABLE) < 1
                && !mod.getBlockScanner().anyFound(Blocks.CRAFTING_TABLE)) {
            return TaskCatalogue.getItemTask(Items.CRAFTING_TABLE, 1);
        }
        if (count(mod, Items.WOODEN_PICKAXE) < 1 && count(mod, Items.STONE_PICKAXE) < 1) {
            return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
        }
        if (count(mod, Items.STONE_PICKAXE) < 1) {
            return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
        }
        return null;
    }

    /** 1.16 logs only. Never AIR / mangrove / cherry. */
    private Task collectWood(AltoClef mod, int n) {
        if (totalLogs(mod) >= n) return null;
        Block[] blocks = new Block[]{
                Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG,
                Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG,
                Blocks.OAK_WOOD, Blocks.BIRCH_WOOD, Blocks.SPRUCE_WOOD,
                Blocks.JUNGLE_WOOD, Blocks.ACACIA_WOOD, Blocks.DARK_OAK_WOOD
        };
        Item[] items = new Item[]{
                Items.OAK_LOG, Items.BIRCH_LOG, Items.SPRUCE_LOG,
                Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG
        };
        boolean seen = false;
        try {
            seen = mod.getBlockScanner().anyFound(blocks);
        } catch (Throwable ignored) {}
        if (!seen) {
            if (phaseTicks % 200 == 1) T2Log.warn("E95", "no 1.16 log in scanner — wander");
            T2History.note("WHY E95: trees~=0, walk until a log exists");
            return new TimeoutWanderTask();
        }
        T2History.note("WHY wood: mine 1.16 logs only (no air)");
        try {
            return new MineAndCollectTask(new ItemTarget(items, n), blocks, MiningRequirement.HAND);
        } catch (Throwable t) {
            T2Log.warn("E95", "MineAndCollect ctor failed: " + t.getClass().getSimpleName());
            return new TimeoutWanderTask();
        }
    }

    private Task loot(AltoClef mod) {
        lootTicks++;
        Optional<BlockPos> chest = closestLootChest(mod);
        if (chest.isPresent() && looted.size() < SpeedrunOpt.LOOT_MAX_CHESTS) {
            BlockPos pos = chest.get();
            Vec3d c = Vec3d.ofCenter(pos);
            if (mod.getPlayer().getPos().squaredDistanceTo(c) > CHEST_RANGE_SQ) {
                return new TungstenMoveTask(pos, 3.0);
            }
            looted.add(pos);
            return new LootContainerTask(pos, lootWanted());
        }
        if (lootTicks < 20 * 20) {
            try {
                var bell = mod.getBlockScanner().getNearestBlock(Blocks.BELL);
                if (bell != null && bell.isPresent()) {
                    T2History.note("WHY loot: village bell");
                    return new TungstenMoveTask(bell.get(), 6.0);
                }
            } catch (Throwable ignored) {}
            return null;
        }
        lootGaveUp = true;
        T2History.note("WHY loot done — no chest after 20s, go IRON");
        setPhase(Phase.IRON);
        return iron(mod);
    }

    private Task iron(AltoClef mod) {
        // `ironN` = total metal stock: ingots PLUS unmelted ore (RAW_IRON preprocesses to
        // IRON_ORE on 1.16.1). This is deliberately NOT the same thing as the `iron=` field
        // in the logs, which is ingots only — see trap 9 in the project memory.
        int ironN = count(mod, Items.IRON_INGOT) + count(mod, Items.RAW_IRON);
        // Only block the first pick craft in a real hole. After a pick
        // exists, sword/buckets may craft under leaves.
        int sky = 15;
        try {
            sky = mod.getWorld().getLightLevel(net.minecraft.world.LightType.SKY, mod.getPlayer().getBlockPos());
        } catch (Throwable ignored) {}
        if (count(mod, Items.IRON_PICKAXE) >= 1 || (skipIronPick && hasMiningPick(mod))) {
            pickCraftLock = false;
            T2History.note("WHY iron: pick done — skip sword/shield table, go PORTAL");
            return null;
        }
        int ore = count(mod, Items.IRON_ORE) + countOpt(mod, "DEEPSLATE_IRON_ORE");
        // IRON TARGET MUST BE STABLE.
        //
        // This used to be `want = min(24, max(8, ironN + ore))` recomputed every tick.
        // Two problems, both observed in run E:
        //
        //  1. It double-counts: ironN is ALREADY IRON_INGOT + ore, so `ironN + ore`
        //     counts every piece of ore twice.
        //  2. Worse, it is a moving target. As ore is mined and smelted the value
        //     wanders 8 -> 20 -> 22 -> 24, and each distinct value builds a DIFFERENT
        //     CollectIronIngotTask. `ResourceTask.isEqualResource` compares
        //     `same.count == count`, so a changed count is treated as brand-new work:
        //     the running collection is torn down and restarted from scratch.
        //     Run E log: `Collecting 8 iron` x106, `20` x27, `22` x51, `24` x36 interleaved.
        //
        // Compute `want` ONCE per IRON phase (at the moment we enter it) and reuse it
        // until the phase changes. ironWant == 0 is the sentinel for "not yet chosen
        // for this phase"; setPhase() clears it when IRON is (re-)entered.
        if (ironWant <= 0) {
            // What the run actually needs out of IRON:
            //   iron pick  = 3 ingots
            //   bucket x2  = 6 ingots   (lava + water for the cast)
            //   ------------------------------
            //                9 total; 8 is the practical floor because the 8th also
            //                covers a lost bucket, and collect tasks round up anyway.
            //
            // Ask for more ONLY when the bot is already carrying a surplus, which means
            // it is topping up rather than starting from nothing. Asking for 24 from an
            // empty inventory is what made the old moving target sit at 20-24 and turned
            // a 9-ingot trip into a 24-ingot one for no reason.
            ironWant = (ironN >= SpeedrunOpt.IRON) ? 24 : SpeedrunOpt.IRON;
            T2Log.force("S146", "iron target locked want=" + ironWant
                    + " at ingot=" + count(mod, Items.IRON_INGOT)
                    + " ore=" + ore + " metal=" + ironN);
        }
        int want = ironWant;
        // Gate on the LOCKED target, but never below the 8 that a pick + 2 buckets needs.
        //
        // With want=24 the old hardcoded `count < 8` let the phase advance after only 8
        // ingots, quietly ignoring the locked target. Requiring the full `want` outright
        // would be worse in the other direction: a seed with little iron would never
        // reach the pick branch and would stall the whole run inside IRON. So: aim for
        // `want`, but once 8 ingots exist the pick craft may proceed — 8 is the useful
        // floor, 24 is only a preference.
        int need = SpeedrunOpt.IRON;
        if (count(mod, Items.IRON_INGOT) < need) {
            pickCraftLock = false;
            T2History.note("WHY iron: smelt " + want + " in one furnace");
            return TaskCatalogue.getItemTask(Items.IRON_INGOT, want);
        }
        // Iron pick is 3 ingots + 2 sticks. logs=0 with no sticks = GUI forever.
        //
        // E93 SOLVER. Do NOT re-issue the wood task every tick (see the field comment
        // on woodForSticksTicks). Issue it ONCE, then hold — the live child keeps
        // running intact until wood arrives or the latch expires.
        if (stickFuel(mod) < 2) {
            if (woodForSticksTicks > 0) {
                woodForSticksTicks--;
                // HOLD. Do not call collectWood() again while the latch is armed.
                //
                // The bug was never "the child is wrong" — it was "the child is replaced
                // every tick". collectWood() builds a fresh MineAndCollectTask (or a
                // TimeoutWanderTask when the scanner sees no log), and stick() only
                // reuses a live child when the classes match; even then it returns the
                // OLD instance. So the freshly built task object was thrown away before
                // it ever accumulated pathing progress, and the bot never left the spot.
                //
                // Hand back the live `active` instance. iron()'s return passes through
                // stick(), whose class-equality branch then returns that very same object,
                // so nothing is rebuilt and the child keeps its path and scan state.
                if (woodForSticksTicks == 0) {
                    T2Log.warn("E134", "wood latch expired 60s no sticks"
                            + " logs=" + totalLogs(mod) + " @" + mod.getPlayer().getBlockX()
                            + "," + mod.getPlayer().getBlockZ());
                    T2History.note("WHY E134: latch expired — blacklist tree, retarget");
                    blacklistNearbyWood(mod);
                }
                if (active != null && !active.isFinished()) {
                    return active;
                }
                return null;
            }
            T2Log.warn("E93", "iron pick missing sticks fuel=" + stickFuel(mod)
                    + " latch=" + woodForSticksTicks);
            T2History.note("WHY iron: 1 log for sticks before pick table");
            pickCraftLock = false;
            Task wood = collectWood(mod, 1);
            if (wood == null) {
                // totalLogs already >= 1 but stickFuel is still < 2: impossible unless the
                // log stayed in a broken slot. Fall through rather than spin.
                woodForSticks = null;
                woodForSticksTicks = 0;
            } else {
                woodForSticks = wood;
                // 60s is generous for walking to a tree and felling it, and short enough
                // that a genuinely unreachable tree does not wedge the run forever.
                woodForSticksTicks = 20 * 60;
                T2Log.force("S143", "wood latched " + wood.getClass().getSimpleName()
                        + " for 60s logs=" + totalLogs(mod) + " fp=" + totalPlanks(mod));
                return wood;
            }
        } else if (woodForSticks != null || woodForSticksTicks > 0) {
            T2Log.force("S143", "wood latch released — sticks available fuel=" + stickFuel(mod));
            woodForSticks = null;
            woodForSticksTicks = 0;
        }
        if (recraftPause > 0) recraftPause--;
        if (active instanceof SurfaceBailTask && !active.isFinished()) {
            return active;
        }
        if (SurfaceBailTask.underground(mod) && !(active instanceof SurfaceBailTask)) {
            T2History.note("WHY iron: surface before pick craft");
            pickCraftLock = false;
            return new SurfaceBailTask();
        }
        if (!pickCraftLock) T2History.note("WHY iron: lock pick craft, ignore y flicker");
        pickCraftLock = true;
        return TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
    }

    private Task maybeBoat(AltoClef mod) {
        if (!SpeedrunOpt.WANT_BOAT) return null;
        if (boatCount(mod) >= 1) return null;
        if (totalLogs(mod) < 5) return null; // already have wood, don't start a tree task
        return TaskCatalogue.getItemTask(Items.OAK_BOAT, 1);
    }

    private Task maybeGoldHat(AltoClef mod) {
        if (!SpeedrunOpt.GOLD_PIGLIN_HEAD) return null;
        if (count(mod, Items.GOLDEN_HELMET) + count(mod, Items.GOLDEN_CHESTPLATE)
                + count(mod, Items.GOLDEN_LEGGINGS) + count(mod, Items.GOLDEN_BOOTS) >= 1) {
            return null;
        }
        int gold = count(mod, Items.GOLD_INGOT) + count(mod, Items.GOLD_BLOCK) * 9;
        // Helmet is 5 gold. Keep 8 for piglin trades unless pearls are already done.
        if (gold < 5) return null;
        return TaskCatalogue.getItemTask(Items.GOLDEN_HELMET, 1);
    }

    private int boatCount(AltoClef mod) {
        int n = count(mod, Items.OAK_BOAT) + count(mod, Items.BIRCH_BOAT) + count(mod, Items.SPRUCE_BOAT)
                + count(mod, Items.JUNGLE_BOAT) + count(mod, Items.ACACIA_BOAT) + count(mod, Items.DARK_OAK_BOAT);
        n += countOpt(mod, "MANGROVE_BOAT") + countOpt(mod, "CHERRY_BOAT") + countOpt(mod, "OAK_CHEST_BOAT");
        return n;
    }

    /** Shield as soon as 1 iron exists. Does not wait for a full iron kit. */
    private Task maybeShield(AltoClef mod) {
        if (!SpeedrunOpt.GET_SHIELD_EARLY) return null;
        if (count(mod, Items.SHIELD) >= 1) return null;
        int iron = count(mod, Items.IRON_INGOT) + count(mod, Items.RAW_IRON)
                + count(mod, Items.IRON_NUGGET) / 9;
        if (iron < 1 && count(mod, Items.IRON_PICKAXE) < 1) return null;
        return TaskCatalogue.getItemTask(Items.SHIELD, 1);
    }

    private boolean tableAtFeet(AltoClef mod) {
        try {
            BlockPos feet = mod.getPlayer().getBlockPos();
            return mod.getWorld().getBlockState(feet).isOf(Blocks.CRAFTING_TABLE)
                    || mod.getWorld().getBlockState(feet.down()).isOf(Blocks.CRAFTING_TABLE);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean portalNearby(AltoClef mod) {
        try {
            return mod.getBlockScanner().anyFound(Blocks.NETHER_PORTAL);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * S177 — USE THE RUINED PORTAL THE SEED WAS ACCEPTED FOR.
     *
     * <p>The spawn gate accepts a seed on `village || portal`, where `portal` means "a
     * ruined portal's obsidian is within {@code SPAWN_LOOT_RADIUS}" (SpawnScout scans
     * OBSIDIAN + CRYING_OBSIDIAN). Every recent run was accepted that way and then never
     * touched it:
     *
     * <pre>
     *   run T  village=false rp=true rpDist=64   -> PORTAL 4:07
     *   run U  village=false rp=true rpDist=33   -> treeless, 24 min, 0 logs
     *   run X  village=false rp=true rpDist=63   -> PORTAL 7:07, then 10x S165L
     * </pre>
     *
     * <p>But {@code portal()} only ever looked for {@code Blocks.NETHER_PORTAL} — a
     * COMPLETED, lit portal. A ruined portal is a partially-built obsidian frame with
     * gaps; its block id is OBSIDIAN, not NETHER_PORTAL, so it is invisible to that check
     * until the bot accidentally walks past it. So the driver ignored the structure the
     * seed was chosen for and went digging for a lava lake instead, which is exactly the
     * descend-cast-ascend cycle that produces the S165L deep-shaft fallback and its tax.
     *
     * <p>Filling a ruined portal's gaps costs a few cobblestone and a flint-and-steel. It
     * needs no lava lake, no water bucket, no casting, and no vertical travel. When a
     * ruined portal is KNOWN and reasonably close, it is strictly the cheapest route to
     * the Nether and should be preferred over any bucket build.
     *
     * <p>Bounded deliberately: only obsidian the block scanner has actually SEEN counts
     * (we cannot path to a structure we have not observed), and only within
     * {@link #RUINED_PORTAL_MAX_DIST} — beyond that the walk costs more than the build.
     */
    private static final double RUINED_PORTAL_MAX_DIST = 96.0;
    /**
     * Once a ruined portal is adopted, stay with it until it is proven unusable. Do NOT
     * revoke on distance — see the S177b note in {@link #ruinedPortal(AltoClef)}: walking
     * to the site necessarily means being far from it, and a distance-revocable site
     * re-adopts the same block forever.
     */
    private BlockPos ruinedPortalSite = null;
    /** Set when the adopted site has been proven unusable, so it is never re-adopted. */
    private int ruinedPortalGivenUp = 0;

    /**
     * S177. Return a task that finishes the nearest KNOWN ruined portal, or null to fall
     * through to the ordinary bucket build.
     *
     * <p>Deliberately conservative: this must never make the portal leg worse. It only
     * engages when the block scanner has actually SEEN obsidian within
     * {@link #RUINED_PORTAL_MAX_DIST} (we cannot path to a structure we have not observed),
     * and it abandons after {@link #RUINED_PORTAL_GIVEUP_LIMIT} failed attempts so a
     * mis-detected cluster cannot eat the phase.
     */
    private Task ruinedPortal(AltoClef mod) {
        if (ruinedPortalGivenUp >= RUINED_PORTAL_GIVEUP_LIMIT) return null;
        // A site already adopted: keep using it, do not recompute.
        //
        // S177b — THE RE-ADOPT LOOP. The first version revoked the site when the player
        // was more than 32 blocks from it ("walked far away; re-evaluate"). That threshold
        // is SMALLER than the 96-block adoption radius, so a frame legitimately adopted at
        // 58 blocks was dropped on the very next tick, the scanner then found the SAME
        // obsidian again, and the driver re-adopted it — 22 times in run Z, ~4s apart, for
        // three straight minutes (`adopting ruined portal @135,10,153 dist=58` x22) while
        // the bot never went anywhere. Two constants describing the same trip disagreed.
        //
        // Stickiness must not be distance-revocable: walking TO the site necessarily means
        // being far from it. The site is cleared only on a proven give-up (below), which is
        // the one case where re-scanning is the right answer.
        if (ruinedPortalSite != null) {
            if (active instanceof RuinedPortalFinishTask rp && rp.gaveUp()) {
                ruinedPortalGivenUp++;
                T2Log.warn("S177", "ruined portal at " + shortPos(ruinedPortalSite)
                        + " unusable (attempt " + ruinedPortalGivenUp + "/"
                        + RUINED_PORTAL_GIVEUP_LIMIT + ") — bucket build instead");
                ruinedPortalSite = null;
                return null;
            }
            if (active instanceof RuinedPortalFinishTask && !active.isFinished()) {
                return active;
            }
            return new RuinedPortalFinishTask(ruinedPortalSite);
        }

        BlockPos found = null;
        try {
            var near = mod.getBlockScanner().getNearestBlock(
                    Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN);
            if (near.isPresent()) found = near.get();
        } catch (Throwable ignored) {}
        if (found == null) return null;
        double d = 0;
        try {
            d = mod.getPlayer().getBlockPos().getSquaredDistance(found);
        } catch (Throwable ignored) {}
        if (d > RUINED_PORTAL_MAX_DIST * RUINED_PORTAL_MAX_DIST) {
            return null; // too far to be worth the walk
        }
        ruinedPortalSite = found.toImmutable();
        // force() echoes to CHAT+STDOUT+HIST, so say this once per adoption, not per tick.
        T2Log.force("S177", "adopting ruined portal @"
                + found.getX() + "," + found.getY() + "," + found.getZ()
                + " dist=" + String.format(java.util.Locale.ROOT, "%.0f", Math.sqrt(d))
                + " (the seed was accepted on this structure)");
        return new RuinedPortalFinishTask(ruinedPortalSite);
    }

    /** Two failures is enough: a mis-detected obsidian cluster must not eat the phase. */
    private static final int RUINED_PORTAL_GIVEUP_LIMIT = 2;

    private static String shortPos(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    /**
     * S185: is a TIME-BASED container screen open (furnace / brewing stand)?
     *
     * <p>While one is open the bot legitimately holds position — smelting takes 10s per item —
     * so any stall ladder must stand down. Detected from the live screen rather than the child
     * task's name, because the smelting child is {@code CollectIronIngotTask} and matches
     * neither "Smelt" nor "Furnace".
     */
    private static boolean slowScreenOpen() {
        try {
            var mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null || mc.currentScreen == null) return false;
            String n = mc.currentScreen.getClass().getSimpleName();
            return n.contains("Furnace") || n.contains("Brew");
        } catch (Throwable t) {
            return false;
        }
    }

    private Task portal(AltoClef mod) {
        if (WorldHelper.getCurrentDimension() == Dimension.NETHER) return null;
        if (active instanceof ConstructNetherPortalBucketTask && !active.isFinished()) {
            return active;
        }
        if (mod.getBlockScanner().anyFound(Blocks.NETHER_PORTAL)) {
            T2History.note("WHY portal: walk in existing");
            return new EnterNetherPortalTask(Dimension.NETHER);
        }
        // S177: a ruined portal is a usable frame, not decoration. Prefer it over digging.
        Task ruined = ruinedPortal(mod);
        if (ruined != null) return ruined;
        int water = count(mod, Items.WATER_BUCKET);
        int lava = count(mod, Items.LAVA_BUCKET);
        int empty = count(mod, Items.BUCKET);
        int food = count(mod, Items.BREAD) + count(mod, Items.COOKED_BEEF) + count(mod, Items.COOKED_PORKCHOP)
                + count(mod, Items.COOKED_CHICKEN) + count(mod, Items.APPLE);
        int hunger = 20;
        try { hunger = mod.getPlayer().getHungerManager().getFoodLevel(); } catch (Throwable ignored) {}
        if (hunger <= 6 && food < 1) {
            T2History.note("WHY portal: starve — grab bread");
            return TaskCatalogue.getItemTask(Items.BREAD, 4);
        }
        if (water < 1 && lava < 1 && empty < 1) {
            T2History.note("WHY portal: water bucket before lava cast");
            return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
        }
        if (lava >= 1 && water < 1) {
            T2History.note("WHY portal: have lava — get water to cast");
            return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
        }
        int place = count(mod, Items.COBBLESTONE) + count(mod, Items.DIRT)
                + count(mod, Items.NETHERRACK) + count(mod, Items.STONE);
        if (place < 8) {
            T2History.note("WHY portal: need 8 place blocks have=" + place);
            return TaskCatalogue.getItemTask(Items.COBBLESTONE, 16);
        }
        if (count(mod, Items.FLINT_AND_STEEL) < 1) {
            if (count(mod, Items.FLINT) >= 1) {
                T2History.note("WHY portal: craft flint and steel");
                return TaskCatalogue.getItemTask(Items.FLINT_AND_STEEL, 1);
            }
            T2History.note("WHY portal: 1 flint before construct");
            return TaskCatalogue.getItemTask(Items.FLINT, 1);
        }
        if (LavaBucketPortal.ready(mod)) {
            Task bucket = LavaBucketPortal.start(mod);
            if (bucket != null) return bucket;
        }
        return new ConstructNetherPortalBucketTask();
    }

    private Task nether(AltoClef mod) {
        int rods = count(mod, Items.BLAZE_ROD);
        int pearls = count(mod, Items.ENDER_PEARL);
        int gold = count(mod, Items.GOLD_INGOT) + count(mod, Items.GOLD_BLOCK) * 9
                + count(mod, Items.GOLD_NUGGET) / 9;
        // Never time out the helm. 40s skip was sending us to a fortress
        // unarmored, then mining gold over lava.
        if (!wearingGold(mod)) {
            goldHelmTicks++;
            T2History.note("WHY nether: gold helm on head before fortress");
            if (count(mod, Items.GOLDEN_HELMET) >= 1) {
                try {
                    return new adris.altoclef.tasks.misc.EquipArmorTask(Items.GOLDEN_HELMET);
                } catch (Throwable t) {
                    T2Log.warn("E96", "equip ctor failed");
                }
            }
            if (gold < 5) {
                T2Log.warn("E96", "need 5 gold for helm");
                // Stay high. CollectGoldIngot loves lava-lake ore.
                // S191: a Y level, not the exact block 12 overhead. That block is usually
                // netherrack, so GoalBlock was unreachable — one run sat at 27,47,26 for 302s
                // trying to reach 27,59,26 — and the goal moved with every step.
                // S193: with hysteresis. Start below 48, keep climbing until 52. Live run
                // fix4 flipped GetToY(52) <-> CollectGold every few seconds at y=47/48 for
                // 2 min because the climb was dropped the moment y reached 48. A climb that
                // makes no height for 30s is abandoned for 60s so the gold can still be mined.
                int ny = mod.getPlayer().getBlockY();
                if (netherClimbCooldown > 0) netherClimbCooldown--;
                boolean climbing = active instanceof adris.altoclef.tasks.movement.GetToYTask
                        && !active.isFinished();
                if (netherClimbCooldown <= 0 && (ny < 48 || (climbing && ny < 52))) {
                    if (ny > netherClimbBestY) {
                        netherClimbBestY = ny;
                        netherClimbStallTicks = 0;
                    } else if (++netherClimbStallTicks > 20 * 30) {
                        T2Log.warn("S193", "nether climb stalled at y=" + ny
                                + " for 30s - mining gold here for 60s");
                        netherClimbCooldown = 20 * 60;
                        netherClimbStallTicks = 0;
                        netherClimbBestY = Integer.MIN_VALUE;
                        return TaskCatalogue.getItemTask(Items.GOLD_INGOT, 5);
                    }
                    T2History.note("WHY nether: climb off lava before gold");
                    try {
                        return new adris.altoclef.tasks.movement.GetToYTask(52);
                    } catch (Throwable ignored) {
                        return new TimeoutWanderTask();
                    }
                }
                netherClimbStallTicks = 0;
                netherClimbBestY = Integer.MIN_VALUE;
                return TaskCatalogue.getItemTask(Items.GOLD_INGOT, 5);
            }
            // E96 SOLVER. We have the gold but the helmet craft is not completing —
            // typically no reachable crafting table, or the recipe search keeps
            // re-picking a table it cannot path to. Before this the branch returned the
            // identical task forever, so the bot eventually walked into the fortress
            // with 5 gold in its pocket and no helmet (E110 x4 at 9:51).
            if (goldHelmTicks > 20 * 45) {
                T2Log.warn("E96", "helm craft stalled " + (goldHelmTicks / 20)
                        + "s — craft in place / bail to bastion chest");
                T2History.note("WHY E96: helm craft stalled, try local craft");
                goldHelmTicks = 0;
                // Ask for the table itself so TaskCatalogue places one where we stand
                // instead of pathing to a remembered-but-unreachable one.
                int tables = count(mod, Items.CRAFTING_TABLE);
                if (tables >= 1) {
                    // We already carry a table: getting the helmet re-runs the craft
                    // with a placeable table now in inventory, which usually unblocks it.
                    active = null;
                    return TaskCatalogue.getItemTask(Items.GOLDEN_HELMET, 1);
                }
                // No table in inventory. Before giving up on the craft, try to make a
                // table here — planks are almost always on hand in the Nether, and a
                // table is what the recipe search is missing.
                int planks = count(mod, Items.OAK_PLANKS) + count(mod, Items.BIRCH_PLANKS)
                        + count(mod, Items.SPRUCE_PLANKS) + count(mod, Items.JUNGLE_PLANKS)
                        + count(mod, Items.ACACIA_PLANKS) + count(mod, Items.DARK_OAK_PLANKS)
                        + count(mod, Items.CRIMSON_PLANKS) + count(mod, Items.WARPED_PLANKS);
                boolean anyTable = false;
                try { anyTable = mod.getBlockScanner().anyFound(Blocks.CRAFTING_TABLE); } catch (Throwable ignored) {}
                if (planks >= 4 && !anyTable) {
                    T2History.note("WHY E96: no table — craft one from planks");
                    active = null;
                    return TaskCatalogue.getItemTask(Items.CRAFTING_TABLE, 1);
                }
                // Nothing to craft with. Bastions and fortress chests carry golden
                // helmets; a treasure hunt beats standing still bare-headed.
                T2History.note("WHY E96: no table, no planks — hunt chest for helmet");
                return new TimeoutWanderTask();
            }
            return TaskCatalogue.getItemTask(Items.GOLDEN_HELMET, 1);
        }
        goldHelmTicks = 0;

        if (pearls < SpeedrunOpt.PEARLS && gold >= 8 && tradeTicks < TRADE_MAX_TICKS) {
            tradeTicks++;
            return new TradeWithPiglinsTask(32, new ItemTarget(Items.ENDER_PEARL, SpeedrunOpt.PEARLS));
        }
        if (rods < SpeedrunOpt.BLAZE_RODS) {
            // S149: latch the search so stick() does not restart it every tick.
            if (!blazeSearchLive || blazeSearchTicks <= 0) {
                blazeSearchLive = true;
                blazeSearchTicks = 20 * 120;
                T2Log.force("S149", "blaze search latched for " + (blazeSearchTicks / 20)
                        + "s rods=" + rods + "/" + SpeedrunOpt.BLAZE_RODS
                        + " child=" + (active == null ? "-" : active.getClass().getSimpleName()));
            }
            return new CollectBlazeRodsTask(SpeedrunOpt.BLAZE_RODS);
        }
        // Rods are satisfied - release the latch.
        if (blazeSearchLive) {
            blazeSearchLive = false;
            blazeSearchTicks = 0;
            T2Log.force("S150", "blaze search latch released rods=" + rods + "/"
                    + SpeedrunOpt.BLAZE_RODS);
        }
        if (pearls < SpeedrunOpt.PEARLS) {
            tradeTicks++;
            return new TradeWithPiglinsTask(32, new ItemTarget(Items.ENDER_PEARL, SpeedrunOpt.PEARLS));
        }
        return null;
    }

    private Task eyes(AltoClef mod) {
        if (count(mod, Items.ENDER_EYE) < SpeedrunOpt.EYES) {
            return TaskCatalogue.getItemTask(Items.ENDER_EYE, SpeedrunOpt.EYES);
        }
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            return new EnterNetherPortalTask(Dimension.OVERWORLD);
        }
        return null;
    }

    private Task stronghold(AltoClef mod) {
        if (count(mod, Items.ENDER_EYE) < SpeedrunOpt.EYES) {
            return TaskCatalogue.getItemTask(Items.ENDER_EYE, SpeedrunOpt.EYES);
        }
        // Only craft beds if wool is already in the bag. Never start a sheep hunt here.
        if (SpeedrunOpt.BEDS > 0
                && bedCount(mod) < SpeedrunOpt.BEDS
                && woolCount(mod) >= SpeedrunOpt.BEDS * 3) {
            return TaskCatalogue.getItemTask(Items.WHITE_BED, SpeedrunOpt.BEDS);
        }
        return new GoToStrongholdPortalTask(SpeedrunOpt.EYES);
    }

    private Task end(AltoClef mod) {
        if (bedCount(mod) >= 1) {
            try {
                return new KillEnderDragonWithBedsTask();
            } catch (Throwable t) {
                Debug.logWarning("TESRUN2: no bed dragon task, using closer");
            }
        }
        return startCloser(mod);
    }

    private Task offsetWalk(AltoClef mod) {
        BlockPos here = mod.getPlayer().getBlockPos();
        BlockPos dest = here.add(8, 0, 6);
        T2History.note("WHY E99: GetToBlock " + dest.getX() + "," + dest.getZ());
        try {
            return new GetToBlockTask(dest);
        } catch (Throwable t) {
            return new TimeoutWanderTask();
        }
    }

    /** Reuse the live child unless the replacement is a different kind of work. */
    private Task stick(Task wanted) {
        if (wanted == null) return null;

        // S162. A hostile in the bot's face outranks every pin below.
        //
        // Run Q: ConstructNetherPortalBucketTask owned the child slot while the bot dug
        // from y=71 to y=28, the top-of-tick combat branch asked for AnyWeaponCombatTask
        // the moment a zombie closed to 3.5 blocks, and every pin here answered with
        // `active` instead — so the bot kept digging while it was being hit and died at
        // 9:34 with 8 iron, a pickaxe and two buckets on it. A pin that cannot be broken
        // by combat is a pin that kills the run.
        //
        // Bounded by where the driver asks for it: only `closeHostile(mod)` (3.5 blocks)
        // or `creeperInFace(mod)` (2.5 blocks) produce this task.
        if (wanted instanceof adris.altoclef.tasks.speedrun.testrun2.combat.AnyWeaponCombatTask
                && !(active instanceof adris.altoclef.tasks.speedrun.testrun2.combat.AnyWeaponCombatTask)) {
            active = wanted;
            T2History.tick(AltoClef.getInstance(), phase.name(), active);
            return active;
        }

        // S160. Track how long the SAME portal-build instance has owned the child slot.
        // The Construct pins below exist so a half-built portal is not abandoned, but they
        // had no exit at all: run O sat at @29,66,-84 with spd=0.000 from 7:29 to 16:33
        // waiting on iron for a second bucket it could never obtain. The trace shows zero
        // swaps and one child for 300 consecutive ticks — the "child that can never
        // finish" shape, invisible to every ping-pong guard because nothing ever swapped.
        if (active instanceof ConstructNetherPortalBucketTask) {
            if (constructLive != active) {
                constructLive = active;
                constructLiveTicks = 0;
            } else if (constructLiveTicks < Integer.MAX_VALUE - 1) {
                constructLiveTicks++;
            }
        } else {
            constructLive = null;
            constructLiveTicks = 0;
        }
        if (constructCoolTicks > 0) {
            constructCoolTicks--;
        } else if (active instanceof ConstructNetherPortalBucketTask
                && constructLiveTicks >= CONSTRUCT_MAX_TICKS) {
            constructCoolTicks = CONSTRUCT_COOL_TICKS;
            AltoClef m = AltoClef.getInstance();
            String at = (m != null && m.getPlayer() != null)
                    ? (m.getPlayer().getBlockX() + "," + m.getPlayer().getBlockY()
                       + "," + m.getPlayer().getBlockZ())
                    : "?";
            T2Log.force("S160", "portal build held the slot " + (CONSTRUCT_MAX_TICKS / 20)
                    + "s without finishing @" + at
                    + " - pin suspended for " + (CONSTRUCT_COOL_TICKS / 20)
                    + "s so the driver can relocate");
        }
        // While the pin is suspended, re-issuing Construct would just start the same
        // unwinnable build for another whole window. Send the bot wandering instead so it
        // actually moves somewhere iron might be.
        //
        // HOLD the wander once issued. Returning `new TimeoutWanderTask()` every tick is
        // the per-tick-replace trap: the class-equality branch in stick() only reuses a
        // LIVE child, and a brand-new object each tick means the previous one's pathing is
        // discarded before it goes anywhere — the bot would stay just as frozen.
        // S163: while a deep-shaft bail is running the driver is deliberately asking for
        // HolePillarTask, not a wander. Let that through.
        if (constructCoolTicks > 0 && deepBailTicks <= 0
                && (wanted instanceof ConstructNetherPortalBucketTask
                || active instanceof ConstructNetherPortalBucketTask)) {
            if (active instanceof TimeoutWanderTask && !active.isFinished()) {
                return active;
            }
            return new TimeoutWanderTask();
        }

        // S164 — pair-agnostic ping-pong throttle (see the SWAP_WINDOW_TICKS field comment).
        // Counted on the CLASS PAIR, because `wanted` is a brand-new object every tick for
        // most children: comparing instances would never see the alternation at all.
        if (swapWindowTicks > 0) swapWindowTicks--;
        else {
            swapCount = 0;
            swapPairName = "";
        }
        if (active != null && wanted != null && deepBailTicks <= 0
                && !(wanted instanceof EnterNetherPortalTask)
                && !(wanted instanceof HolePillarTask)
                && !(wanted instanceof adris.altoclef.tasks.speedrun.testrun2.combat.AnyWeaponCombatTask)) {
            // Canonicalise the pair, or the counter never accumulates: when A and B
            // alternate, the string flips between "A<->B" and "B<->A" on every swap and
            // swapCount resets to 1 each time. Run R's CraftInTable<->GetOutOfWater thrash
            // (370 swaps) would have been invisible. Sort so both directions agree.
            String an = active.getClass().getSimpleName();
            String wn = wanted.getClass().getSimpleName();
            String pair = (an.compareTo(wn) <= 0) ? (an + "<->" + wn) : (wn + "<->" + an);
            // Count CHANGES OF THE ACTIVE CHILD, not ticks that merely show the same pair.
            // A long-lived pin (active held while the driver keeps asking for something
            // else) produces an unchanging pair and, if counted per tick, would look like
            // thrash every second and eat a 3-second hold it does not need.
            boolean activeChanged = !an.equals(lastActiveName);
            lastActiveName = an;
            if (!activeChanged) {
                // nothing to count
            } else if (pair.equals(swapPairName)) {
                swapCount++;
            } else {
                swapPairName = pair;
                swapCount = 1;
                swapWindowTicks = SWAP_WINDOW_TICKS;
            }
            if (swapCount >= SWAP_MAX && swapHoldTicks <= 0) {
                swapHoldTicks = SWAP_HOLD_TICKS;
                swapCount = 0;
                swapWindowTicks = 0;
                AltoClef m2 = AltoClef.getInstance();
                String at2 = (m2 != null && m2.getPlayer() != null)
                        ? (m2.getPlayer().getBlockX() + "," + m2.getPlayer().getBlockY()
                           + "," + m2.getPlayer().getBlockZ())
                        : "?";
                T2Log.warn("S164", "child ping-pong " + pair + " " + SWAP_MAX + "x in "
                        + (SWAP_WINDOW_TICKS / 20) + "s @" + at2 + " - holding "
                        + active.getClass().getSimpleName() + " for "
                        + (SWAP_HOLD_TICKS / 20) + "s");
            }
        }
        if (swapHoldTicks > 0) {
            swapHoldTicks--;
            if (active != null && !active.isFinished()
                    && !(wanted instanceof EnterNetherPortalTask)) {
                return active;
            }
        }

        if (active instanceof StepOffTableTask && !active.isFinished()) {
            return active;
        }
        // S149. A live blaze search must survive the CollectBlazeRodsTask ->
        // TimeoutWanderTask substitution that SearchChunksExploreTask performs internally.
        // Without this, `wanted` is a brand-new CollectBlazeRodsTask each tick, the
        // class-equality branch below fails against the wander, and active = wanted
        // restarts the fortress search (and wipes alreadyExplored) over and over.
        // Only a genuinely different request may break the latch.
        if (blazeSearchLive && blazeSearchTicks > 0
                && wanted instanceof CollectBlazeRodsTask
                && !(active instanceof EnterNetherPortalTask)
                && !(active instanceof HolePillarTask)) {
            blazeSearchTicks--;
            if (active != null) {
                T2History.tick(AltoClef.getInstance(), phase.name(), active);
                return active;
            }
        }
        if (active instanceof SurfaceBailTask && !active.isFinished()
                && !(wanted instanceof EnterNetherPortalTask)) {
            return active;
        }
        // S157: throttle the Construct <-> HolePillar swap. Exempting HolePillarTask from the
        // Construct guard below lets it take over, but nothing stopped Construct reclaiming the
        // slot on the very next tick, so neither task ever ran long enough to do anything.
        // Run L swapped these two 4739 times and then sat completely idle for 19.7 minutes
        // (only "Refreshed inventory..." every 30s) until a skeleton shot the bot.
        boolean swapPair = active != null && deepBailTicks <= 0
                && ((active instanceof ConstructNetherPortalBucketTask && wanted instanceof HolePillarTask)
                    || (active instanceof HolePillarTask && wanted instanceof ConstructNetherPortalBucketTask));
        if (swapPair && swapCoolTicks > 0) {
            swapCoolTicks--;
            swapHoldCount++;
            // Emit S157 at most once per 30s. Without this the code would fire silently
            // (like S147/S149) and there would be no way to tell the throttle worked.
            if (swapLogCooldown > 0) {
                swapLogCooldown--;
            } else {
                swapLogCooldown = SWAP_LOG_COOL_TICKS;
                T2Log.warn("S157", "held " + active.getClass().getSimpleName() + " through "
                        + swapHoldCount + " swap attempts - Construct/pillar ping-pong, wanted="
                        + wanted.getClass().getSimpleName());
                swapHoldCount = 0;
            }
            return active;      // hold the current child so it can actually run
        }
        if (swapPair) {
            swapCoolTicks = SWAP_COOL_TICKS;
        }
        if (active instanceof ConstructNetherPortalBucketTask && !active.isFinished()
                && !(wanted instanceof EnterNetherPortalTask)
                && !(wanted instanceof HolePillarTask)) {
            return active;
        }
        if (wanderHold > 0 && active instanceof TimeoutWanderTask && !active.isFinished()
                && !(wanted instanceof EnterNetherPortalTask)) {
            return active;
        }
        if (active instanceof ConstructNetherPortalBucketTask && !active.isFinished()
                && (wanted instanceof UnstickWalkTask || wanted instanceof TimeoutWanderTask)) {
            // NOTE: keep this guard. A bare wander during construction is how the portal
            // gets abandoned mid-build. T2Solve only issues a wander here via S144, which
            // is gated on repeated jump-stuck nudges, so the risk is bounded — but the
            // HolePillarException above is the real escape hatch for a trapped bot.
            return active;
        }
        if (active instanceof UnstickWalkTask && !active.isFinished()) {
            return active;
        }
        if (active instanceof HolePillarTask && (HolePillar.holding() || !active.isFinished())) {
            return active;
        }
        if (active instanceof HolePillarTask && wanted != null && !(wanted instanceof HolePillarTask)) {
            AltoClef m = AltoClef.getInstance();
            T2Log.force("S136", "stick drop pillar -> " + wanted.getClass().getSimpleName()
                    + " finished=" + active.isFinished()
                    + " hold=" + HolePillar.holding()
                    + " cool=" + HolePillar.failCoolLeft()
                    + " end=" + HolePillar.lastEndReason()
                    + " " + HolePillar.snap(m));
        }
        if (phase == Phase.IRON && active instanceof GetToBlockTask && !active.isFinished()
                && !(wanted instanceof EnterNetherPortalTask)
                && !(wanted instanceof StepOffTableTask)) {
            return active;
        }
        if (wanted instanceof EnterNetherPortalTask) {
            active = wanted;
            return active;
        }
        if (active instanceof EnterNetherPortalTask && !active.isFinished()
                && !(wanted instanceof EnterNetherPortalTask)) {
            return active;
        }
        if (active != null && wanted != null
                && active.getClass() == wanted.getClass()
                && !active.isFinished()) {
            T2History.tick(AltoClef.getInstance(), phase.name(), active);
            return active;
        }
        active = wanted;
        T2History.tick(AltoClef.getInstance(), phase.name(), active);
        return active;
    }

    /** Bucket pours change inv hash so stall() never fires. Watch feet instead. */
    private Task portalWatch(AltoClef mod) {
        if (phase != Phase.PORTAL) {
            stillTicks = 0;
            return null;
        }
        if (WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            stillTicks = 0;
            portalAttempts = 0;
            usedCloser = false;
            closer = null;
            return null;
        }
        int x = mod.getPlayer().getBlockX();
        int y = mod.getPlayer().getBlockY();
        int z = mod.getPlayer().getBlockZ();
        // Jump-in-place changes Y. XZ frozen is the real stall.
        if (x == lastStillX && z == lastStillZ) {
            stillTicks++;
        } else {
            stillTicks = 0;
            lastStillX = x;
            lastStillY = y;
            lastStillZ = z;
        }
        // Never use skylight. A ruined portal in a cave is sky=0 and still valid.
        if (portalNearby(mod) && stillTicks == 20 * 12) {
            T2Log.warn("E62", "portal wait @" + x + "," + y + "," + z);
            return stick(new EnterNetherPortalTask(Dimension.NETHER));
        }
        if (stillTicks == 20 * 12) {
            T2Log.warn("E62", "portal jump-place @" + x + "," + y + "," + z);
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
            McCompat.cancelPathing();
        }
        // Time-based: walking toward a far water source never trips stillTicks.
        if (phaseTicks > 0 && phaseTicks % (20 * 180) == 0) {
            T2Log.warn("E60", "portal still building t=" + (phaseTicks / 20) + "s");
        }
        if (stillTicks == 20 * 45) {
            portalAttempts++;
            T2Log.warn("E60", "portal frozen " + stillTicks / 20 + "s @" + x + "," + y + "," + z
                    + " attempt=" + portalAttempts);
            T2History.note("portal rebuild attempt=" + portalAttempts);
            active = null;
            closer = null;
            usedCloser = false;
            stillTicks = 0;
            if (count(mod, Items.WATER_BUCKET) < 1 && count(mod, Items.LAVA_BUCKET) >= 1) {
                T2History.note("WHY E60: have lava, fetch water");
                return stick(TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1));
            }
            return stick(offsetWalk(mod));
        }
        if (portalAttempts >= 3 || stillTicks > 20 * 180) {
            T2Log.warn("E60", "rebuilds=" + portalAttempts + " — walk to a new lava, do not abort");
            T2History.note("WHY E60: wander then construct again");
            portalAttempts = 0;
            stillTicks = 0;
            phaseTicks = 1;
            active = null;
            closer = null;
            usedCloser = false;
            return stick(new TimeoutWanderTask());
        }
        // E107 SOLVER. The detector fires when PORTAL has run 20s with no flint AND no
        // flint-and-steel. portal() then routes to CollectWaterBucketTask, which can hunt
        // for a water source indefinitely while the real blocker is that we can never
        // ignite the cast portal. Previously nothing consumed E107 — it was pure noise
        // (16 occurrences in the 04:55 run).
        //
        // Escalate in order of cheapness:
        //   1. have iron but no flint -> grab gravel and craft flint and steel
        //   2. no iron ingot at all  -> we cannot make steel; fall back to LavaBucketPortal
        //      which casts a portal from lava+water and needs no ignition item
        //   3. otherwise -> force a wander so we re-scan for a ruined portal
        if (count(mod, Items.FLINT_AND_STEEL) < 1 && count(mod, Items.FLINT) < 1
                && phaseTicks > 20 * 20 && phaseTicks % (20 * 20) == 0) {
            boolean haveIron = count(mod, Items.IRON_INGOT) >= 1;
            String cn = active == null ? "" : active.getClass().getSimpleName();
            T2Log.warn("E107", "solver: no flint t=" + (phaseTicks / 20) + "s"
                    + " iron=" + count(mod, Items.IRON_INGOT)
                    + " water=" + count(mod, Items.WATER_BUCKET)
                    + " lava=" + count(mod, Items.LAVA_BUCKET)
                    + " child=" + cn);
            // Only intervene when we are actually wedged on the water/gravel hunt;
            // leaving a healthy child alone avoids thrashing the task tree.
            boolean wedged = cn.contains("Water") || cn.contains("Flint") || cn.contains("Gravel")
                    || cn.contains("Collect") || stillTicks > 20 * 15;
            if (wedged) {
                // NOTE: stick() deliberately swallows non-portal requests while a
                // ConstructNetherPortalBucketTask is running (line ~934). That is exactly
                // the state E107 fires in, so every branch below clears `active` FIRST —
                // otherwise the solver would be discarded and the stall would simply
                // continue. Do not remove these clears.
                if (haveIron) {
                    T2History.note("WHY E107: iron present — gravel for flint");
                    active = null;
                    closer = null;
                    usedCloser = false;
                    return stick(TaskCatalogue.getItemTask(Items.FLINT, 1));
                }
                if (count(mod, Items.LAVA_BUCKET) >= 1 || count(mod, Items.BUCKET) >= 1) {
                    T2History.note("WHY E107: no iron — lava cast needs no ignition");
                    active = null;
                    closer = null;
                    usedCloser = false;
                    Task bucket = LavaBucketPortal.start(mod);
                    if (bucket != null) return stick(bucket);
                }
                T2History.note("WHY E107: wander to re-scan for ruined portal");
                active = null;
                closer = null;
                usedCloser = false;
                stillTicks = 0;
                return stick(new TimeoutWanderTask());
            }
        }
        return null;
    }

    /** CollectIron jump-mines one column for minutes. Walk after 20s frozen. */
    private Task ironWatch(AltoClef mod) {
        if (phase != Phase.IRON) {
            ironStill = 0;
            return null;
        }
        String cn = active == null ? "" : active.getClass().getSimpleName();
        if (cn.contains("Craft") || cn.contains("StepOff")) {
            return null;
        }
        // Pillar owns this XZ — do not E70-walkaway / reset mid-escape.
        if (cn.contains("HolePillar") || HolePillar.busy()) {
            ironStill = 0;
            return null;
        }
        int x = mod.getPlayer().getBlockX();
        int z = mod.getPlayer().getBlockZ();
        int gx = x >> 2;
        int gz = z >> 2;
        if (gx == lastIronX && gz == lastIronZ) ironStill++;
        else {
            ironStill = 0;
            lastIronX = gx;
            lastIronZ = gz;
        }
        int ironN = count(mod, Items.IRON_INGOT) + count(mod, Items.IRON_ORE);
        boolean cheapPick = count(mod, Items.WOODEN_PICKAXE) + count(mod, Items.STONE_PICKAXE) >= 1;
        // Standing still is NOT the same as being stuck.
        //
        // Smelting happens on the spot at a furnace, and the bot legitimately holds
        // position while the furnace runs. Keying the stall ladder purely on positional
        // stillness made E70 fire every 6s during smelting and rebuild the whole iron
        // task each time (observed run B: E70 x4 all while iron=25 >= the 24 target).
        // Only treat stillness as a stall once the iron count has actually stopped
        // moving; otherwise just reset the window and leave the child alone.
        // S185: keyed on the CHILD NAME, which is the wrong signal. The child that drives
        // smelting is `CollectIronIngotTask` — a name containing neither "Smelt" nor
        // "Furnace" — so `smelting` was FALSE exactly when smelting was happening, E70 was
        // never suppressed, and it rebuilt the iron task every 6s while the furnace ran.
        //
        // Run AG: `E104 FurnaceScreen open 8s` x11 and `E70 iron stall 6s` x9 interleaved at
        // one spot, with the child logged as `CollectIronIngotTask` on every S120/S184 line.
        // The guard's INTENT ("a furnace holds position by design") was right; only its
        // detection was wrong. Detect the condition, not the name: an open furnace/brewing
        // screen is the thing that actually means "holding position is correct here".
        boolean smelting = cn.contains("Smelt") || cn.contains("Furnace") || slowScreenOpen();
        if (ironN != lastIronN) {
            // Real progress — restart the stall window regardless of position.
            lastIronN = ironN;
            ironStill = 0;
        } else if (smelting) {
            // Furnace work holds position by design; never escalate on that.
            ironStill = 0;
            return null;
        }
        // Soft kick: blacklist/unreachable ore after combat often leaves CollectIron noop.
        if (ironNeedsKick && ironStill >= 20) {
            T2Log.warn("E70", "iron post-combat kick @" + x + "," + z + " — clear blacklist + re-pick");
            clearBlockBlacklist(mod);
            ironNeedsKick = false;
            HolePillar.reset();
            active = null;
            ironStill = 0;
            return stick(iron(mod));
        }
        if (ironStill == 20 * 6) {
            T2Log.warn("E70", "iron stall 6s @" + x + "," + z + " — clear blacklist + re-pick ore");
            clearBlockBlacklist(mod);
            ironStill = 0;
            return stick(iron(mod));
        }
        if (ironStill == 20 * 12) {
            T2Log.warn("E70", "iron frozen 12s @" + x + "," + z + " — walk");
            McCompat.cancelPathing();
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
            clearBlockBlacklist(mod);
            active = null;
            return stick(offsetWalk(mod));
        }
        if (ironStill >= 20 * 35 && cheapPick && ironN < 3) {
            T2Log.warn("E94", "iron frozen 35s iron=" + ironN + " — skip pick, PORTAL");
            skipIronPick = true;
            pickCraftLock = false;
            ironStill = 0;
            HolePillar.reset();
            setPhase(Phase.PORTAL);
            active = null;
            return stick(portal(mod));
        }
        if (ironStill > 20 * 50) {
            T2Log.warn("E70", "iron frozen 50s — wander");
            ironStill = 0;
            active = null;
            return stick(new TimeoutWanderTask());
        }
        return null;
    }

    /** Jump-in-place on a buried crafting table. XZ frozen, Y bouncing. */
    private Task unstickCraft(AltoClef mod) {
        if (phase != Phase.IRON && phase != Phase.BOOTSTRAP) {
            craftStuck = 0;
            return null;
        }
        String cn = active == null ? "" : active.getClass().getSimpleName();
        boolean crafting = cn.contains("Craft");
        int x = mod.getPlayer().getBlockX();
        int z = mod.getPlayer().getBlockZ();
        if (crafting && x == craftX && z == craftZ) {
            craftStuck++;
        } else {
            craftStuck = 0;
            craftX = x;
            craftZ = z;
        }
        if (!crafting) return null;
        boolean jumping = false;
        try { jumping = !mod.getPlayer().isOnGround(); } catch (Throwable ignored) {}
        // Jump-click next to the table never finishes the recipe. Do not wait
        // for tableAtFeet — SNAP stays at one XZ for minutes.
        boolean wetNow = false;
        try { wetNow = mod.getPlayer().isTouchingWater(); } catch (Throwable ignored) {}
        if (tableAtFeet(mod) && jumping && !wetNow && craftStuck >= 20 * 2) {
            T2Log.warn("E91", "craft jump thrash — step off xz=" + x + "," + z + " stuck=" + craftStuck);
            craftStuck = 0;
            McCompat.closeScreen();
            recraftPause = 20 * 3;
            return stick(new StepOffTableTask());
        }
        boolean dark = SurfaceBailTask.underground(mod);
        int ironN = count(mod, Items.IRON_INGOT) + count(mod, Items.RAW_IRON);
        if (phase == Phase.IRON && !dark && ironN < 3 && count(mod, Items.IRON_PICKAXE) < 1 && craftStuck >= 20 * 6) {
            T2Log.warn("E92", "craft table with 0 iron — mine first");
            T2History.note("WHY E92: close table, collect iron");
            craftStuck = 0;
            forceSurface = false;
            pickCraftLock = false;
            active = null;
            return stick(TaskCatalogue.getItemTask(Items.IRON_INGOT, 3));
        }
        if (craftStuck >= 20 * 12) {
            e91Count++;
            T2Log.warn("E91", "craft stall 12s @" + x + "," + z + " n=" + e91Count + " ph=" + phase);
            craftStuck = 0;
            McCompat.closeScreen();
            forceSurface = false;
            active = null;
            if (e91Count >= 2 && hasMiningPick(mod)
                    && count(mod, Items.WOODEN_PICKAXE) + count(mod, Items.STONE_PICKAXE) >= 1) {
                skipIronPick = true;
                pickCraftLock = false;
                T2Log.warn("E94", "abandon iron pick after " + e91Count + " table fails — PORTAL");
                T2History.note("WHY E94: wooden pick is enough for a bucket portal");
                setPhase(Phase.PORTAL);
                return stick(portal(mod));
            }
            // BOOTSTRAP with pick=0: prefer close+retry wooden pick over StepOff→E94 abandon.
            if (phase == Phase.BOOTSTRAP && !hasMiningPick(mod)) {
                T2Log.warn("E91", "BOOTSTRAP craft stall pick=0 — close+retry wooden pick");
                T2History.note("WHY E91: finish wooden pick before any PORTAL");
                pickCraftLock = false;
                recraftPause = 20;
                return stick(TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1));
            }
            recraftPause = 20 * 3;
            unstickHold = 20 * 4;
            // Prefer step-off over UnstickWalk: UnstickWalk used to jump, which
            // re-triggered E100 jump thrash and E106 (UnstickWalk-as-child).
            T2Log.warn("E91", "craft stall 12s — StepOffTable (not UnstickWalk) xz=" + x + "," + z);
            return stick(new StepOffTableTask());
        }
        return null;
    }

    private Task startCloser(AltoClef mod) {
        if (closer == null) {
            T2Log.warn("E40", "closer=ConstructNetherPortalBucketTask");
            closer = new ConstructNetherPortalBucketTask();
            usedCloser = true;
        }
        return closer;
    }

    private boolean stalled(AltoClef mod) {
        int hash = invHash(mod);
        if (hash != lastInvHash) {
            lastInvHash = hash;
            phaseTicks = 0;
            return false;
        }
        phaseTicks++;
        return phaseTicks > PHASE_STALL_TICKS && phase != Phase.END;
    }

    private int invHash(AltoClef mod) {
        return count(mod, Items.IRON_PICKAXE) * 3
                + count(mod, Items.IRON_INGOT) * 5
                + count(mod, Items.ENDER_EYE) * 7
                + count(mod, Items.BLAZE_ROD) * 11
                + count(mod, Items.ENDER_PEARL) * 13
                + count(mod, Items.FLINT_AND_STEEL) * 17
                + (WorldHelper.getCurrentDimension() == null ? 0 : WorldHelper.getCurrentDimension().ordinal() * 19);
    }

    private List<Item> lootWanted() {
        return Arrays.asList(
                Items.IRON_INGOT, Items.IRON_NUGGET, Items.GOLD_INGOT, Items.GOLD_NUGGET,
                Items.DIAMOND, Items.OBSIDIAN, Items.FLINT, Items.FLINT_AND_STEEL,
                Items.FIRE_CHARGE, Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_HELMET,
                Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.SHIELD,
                Items.BREAD, Items.GOLDEN_CARROT, Items.GOLDEN_APPLE, Items.APPLE,
                Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.ENDER_PEARL, Items.ENDER_EYE,
                Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.OAK_BOAT
        );
    }

    private Optional<BlockPos> closestLootChest(AltoClef mod) {
        BlockPos chest;
        try {
            var found = mod.getBlockScanner().getNearestBlock(
                    Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL);
            if (found == null || found.isEmpty()) return Optional.empty();
            chest = found.get();
        } catch (Throwable t) {
            return Optional.empty();
        }
        if (looted.contains(chest)) return Optional.empty();
        if (chest.getY() < 55) return Optional.empty();
        Vec3d p = mod.getPlayer().getPos();
        if (p.squaredDistanceTo(Vec3d.ofCenter(chest)) > (double) SpeedrunOpt.LOOT_SCAN_RANGE * SpeedrunOpt.LOOT_SCAN_RANGE) {
            return Optional.empty();
        }
        try {
            var world = mod.getWorld();
            if (world != null && !world.getBlockState(chest).getFluidState().isEmpty()) {
                return Optional.empty();
            }
            if (world != null && !world.getBlockState(chest.up()).getFluidState().isEmpty()) {
                return Optional.empty();
            }
        } catch (Throwable ignored) {}
        return Optional.of(chest);
    }

    private boolean hasPortalKit(AltoClef mod) {
        return count(mod, Items.FLINT_AND_STEEL) >= 1
                || count(mod, Items.FIRE_CHARGE) >= 1
                || mod.getBlockScanner().anyFound(Blocks.NETHER_PORTAL)
                || count(mod, Items.OBSIDIAN) >= 10;
    }

    private boolean blazeInFace(AltoClef mod) {
        try {
            var list = mod.getEntityTracker().getTrackedEntities(net.minecraft.entity.mob.BlazeEntity.class);
            if (list == null || mod.getPlayer() == null) return false;
            var me = mod.getPlayer().getPos();
            for (var e : list) {
                if (e.isAlive() && me.distanceTo(e.getPos()) < 4.0) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean creeperInFace(AltoClef mod) {
        try {
            var list = mod.getEntityTracker().getTrackedEntities(net.minecraft.entity.mob.CreeperEntity.class);
            if (list == null || mod.getPlayer() == null) return false;
            var me = mod.getPlayer().getPos();
            for (var e : list) {
                if (e.isAlive() && me.distanceTo(e.getPos()) < 2.5) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean closeHostile(AltoClef mod) {
        try {
            var list = mod.getEntityTracker().getTrackedEntities(net.minecraft.entity.mob.HostileEntity.class);
            if (list == null) return false;
            var me = mod.getPlayer().getPos();
            for (var e : list) {
                if (e instanceof net.minecraft.entity.mob.PiglinEntity) continue;
                if (e instanceof net.minecraft.entity.mob.SkeletonEntity) continue;
                if (e instanceof net.minecraft.entity.mob.WitchEntity) continue;
                if (e.isAlive() && me.distanceTo(e.getPos()) < 3.5) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean inWater(AltoClef mod) {
        try {
            boolean head = mod.getPlayer().isSubmergedInWater();
            if (head) wetStreak++;
            else wetStreak = 0;
            return wetStreak >= 20;
        } catch (Throwable t) {
            wetStreak = 0;
            return false;
        }
    }

    private boolean dragonDead(AltoClef mod) {
        try {
            if (WorldHelper.getCurrentDimension() != Dimension.END) return false;
            // Dragon entity is missing for a few ticks on entry. Don't finish the run.
            if (endTicks < 20 * 8) return false;
            var dragons = mod.getEntityTracker().getTrackedEntities(EnderDragonEntity.class);
            return dragons != null && dragons.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private int count(AltoClef mod, Item item) {
        return mod.getItemStorage().getItemCount(item);
    }

    /** Piglins stay neutral only if gold is ON the body, not in the bag. */
    private boolean wearingGold(AltoClef mod) {
        try {
            var p = mod.getPlayer();
            for (var stack : p.getArmorItems()) {
                if (stack == null || stack.isEmpty()) continue;
                Item it = stack.getItem();
                if (it == Items.GOLDEN_HELMET || it == Items.GOLDEN_CHESTPLATE
                        || it == Items.GOLDEN_LEGGINGS || it == Items.GOLDEN_BOOTS) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private int totalPlanks(AltoClef mod) {
        return count(mod, Items.OAK_PLANKS) + count(mod, Items.BIRCH_PLANKS)
                + count(mod, Items.SPRUCE_PLANKS) + count(mod, Items.JUNGLE_PLANKS)
                + count(mod, Items.ACACIA_PLANKS) + count(mod, Items.DARK_OAK_PLANKS);
    }

    private int woodUnits(AltoClef mod) {
        return totalLogs(mod) + totalPlanks(mod) / 4;
    }

    /** Sticks available if we convert planks/logs. Iron pick needs 2. */
    private int stickFuel(AltoClef mod) {
        int sticks = count(mod, Items.STICK);
        int planks = count(mod, Items.OAK_PLANKS) + count(mod, Items.BIRCH_PLANKS)
                + count(mod, Items.SPRUCE_PLANKS) + count(mod, Items.JUNGLE_PLANKS)
                + count(mod, Items.ACACIA_PLANKS) + count(mod, Items.DARK_OAK_PLANKS);
        return sticks + planks * 2 + totalLogs(mod) * 8;
    }

    private int totalLogs(AltoClef mod) {
        int n = mod.getItemStorage().getItemCount(
                Items.OAK_LOG, Items.BIRCH_LOG, Items.SPRUCE_LOG, Items.JUNGLE_LOG,
                Items.ACACIA_LOG, Items.DARK_OAK_LOG);
        n += countOpt(mod, "MANGROVE_LOG") + countOpt(mod, "CHERRY_LOG");
        return n;
    }

    private int countOpt(AltoClef mod, String itemName) {
        Item it = McCompat.item(itemName);
        return it == null ? 0 : count(mod, it);
    }

    private int food(AltoClef mod) {
        try {
            return StorageHelper.calculateInventoryFoodScore();
        } catch (Throwable t) {
            return 20;
        }
    }

    private int bedCount(AltoClef mod) {
        return mod.getItemStorage().getItemCount(
                Items.WHITE_BED, Items.ORANGE_BED, Items.MAGENTA_BED, Items.LIGHT_BLUE_BED,
                Items.YELLOW_BED, Items.LIME_BED, Items.PINK_BED, Items.GRAY_BED,
                Items.LIGHT_GRAY_BED, Items.CYAN_BED, Items.PURPLE_BED, Items.BLUE_BED,
                Items.BROWN_BED, Items.GREEN_BED, Items.RED_BED, Items.BLACK_BED);
    }

    private int woolCount(AltoClef mod) {
        return mod.getItemStorage().getItemCount(
                Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL,
                Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
                Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
                Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL);
    }

    private void setPhase(Phase p) {
        if (p == phase) return;
        T2Log.info("I01", phase + " -> " + p + " t=" + SpeedrunClock.now());
        SpeedrunClock.split(p.name());
        adris.altoclef.tasks.speedrun.testrun2.util.Splits.mark(p.name());
        phase = p;
        active = null;
        phaseTicks = 0;
        if (p == Phase.NETHER) tradeTicks = 0;
        // Re-lock the iron target for the new phase. Entering IRON (or re-entering it
        // after a NETHER excursion) must recompute it once, not carry a stale value.
        if (p == Phase.IRON) ironWant = 0;
        // S149: a phase change genuinely invalidates a blaze search.
        if (p != Phase.NETHER) {
            if (blazeSearchLive) {
                T2Log.force("S150", "blaze search latch released on phase change to " + p);
            }
            blazeSearchLive = false;
            blazeSearchTicks = 0;
        }
        if (p == Phase.DONE) {
            sessionLive = false;
            Debug.logMessage(SpeedrunClock.dump());
        }
    }

    @Override
    protected void onStop(Task interruptTask) {
        try { TungstenHelper.stop(); } catch (Throwable ignored) {}
        adris.altoclef.tasks.speedrun.testrun2.core.T2Input.releaseAll();
        try { McCompat.cancelPathing(); } catch (Throwable ignored) {}
        boolean dead = false;
        try {
            var p = AltoClef.getInstance().getPlayer();
            dead = p != null && p.getHealth() <= 0;
        } catch (Throwable ignored) {}

        // S152. sessionLive used to be cleared here on EVERY interruption, so any task
        // swap re-ran onStart(): SpeedrunClock.reset() + SpawnScout.scan() + a full
        // re-derivation of the phase from the inventory.
        //
        // Measured on run G: the bot fell from y=67 to y=6 at 223,168. MLGBucketTask
        // (AltoClef's MLG water-bucket clutch) takes priority on every long fall, which
        // interrupts the speedrun task, which re-entered onStart() roughly once per
        // second for the whole descent. 87 sessions in one run, 57 of them landing back
        // in BOOTSTRAP, E80 (the "swallowed" guard) firing only twice. The run never
        // recovered even though the bot only actually died once.
        //
        // Keeping sessionLive set means onStart() is swallowed by the E80 guard and the
        // run simply resumes wherever it was. Only Phase.DONE (setPhase) or an explicit
        // reset ends a session now.
        AltoClef snapMod = AltoClef.getInstance();
        double yNow = -999;
        try {
            if (snapMod != null && snapMod.getPlayer() != null) {
                yNow = snapMod.getPlayer().getY();
            }
        } catch (Throwable ignored) {}
        T2Log.warn("S152", "onStop interrupt="
                + (interruptTask == null ? "null" : interruptTask.getClass().getSimpleName())
                + " dead=" + dead + " ph=" + phase + " y=" + String.format("%.1f", yNow)
                + " -> session preserved");
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ModernSpeedrunTask;
    }

    @Override
    protected String toDebugString() {
        return "testrun2/" + phase + (active != null ? ":" + active : "");
    }


    /** Drop the stuck tree so collectWood picks the nearest other log of any type. */
    private void blacklistNearbyWood(AltoClef mod) {
        Block[] logs = new Block[]{
                Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG,
                Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG,
                Blocks.OAK_WOOD, Blocks.BIRCH_WOOD, Blocks.SPRUCE_WOOD,
                Blocks.JUNGLE_WOOD, Blocks.ACACIA_WOOD, Blocks.DARK_OAK_WOOD
        };
        try {
            BlockPos me = mod.getPlayer().getBlockPos();
            for (BlockPos pos : BlockPos.iterate(me.add(-3, -3, -3), me.add(3, 6, 3))) {
                Block b = mod.getWorld().getBlockState(pos).getBlock();
                for (Block log : logs) {
                    if (b == log) {
                        // 0 allowed failures => immediately unreachable this tree column
                        mod.getBlockScanner().requestBlockUnreachable(pos.toImmutable(), 0);
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void clearBlockBlacklist(AltoClef mod) {
        try {
            mod.getBlockScanner().clearBlacklist();
        } catch (Throwable ignored) {}
    }


    /** Wooden pickaxe or better — required before BOOTSTRAP may advance to PORTAL/IRON. */
    private boolean hasMiningPick(AltoClef mod) {
        return count(mod, Items.WOODEN_PICKAXE) >= 1
                || count(mod, Items.STONE_PICKAXE) >= 1
                || count(mod, Items.IRON_PICKAXE) >= 1
                || count(mod, Items.GOLDEN_PICKAXE) >= 1
                || count(mod, Items.DIAMOND_PICKAXE) >= 1
                || count(mod, Items.NETHERITE_PICKAXE) >= 1;
    }

    /**
     * E109b: PlaceBlocks/HolePillar leave dirt in hand; while mining/collecting in
     * BOOTSTRAP/IRON, keep the best pick equipped so dirt does not stick across mine ticks.
     */
    private void ensureMiningPick(AltoClef mod) {
        if (phase != Phase.IRON && phase != Phase.BOOTSTRAP) return;
        if (HolePillar.busy() || HolePillar.holding()) return;
        String cn = active == null ? "" : active.getClass().getSimpleName();
        boolean mining = cn.contains("Mine") || cn.contains("Collect") || cn.contains("Smelt");
        if (!mining) return;
        // S195: Baritone is pillaring/placing with a block in hand — do not swap it away.
        if (McCompat.baritonePlacing(mod)) return;
        try {
            Item eq = StorageHelper.getItemStackInSlot(
                    adris.altoclef.util.slots.PlayerSlot.getEquipSlot()).getItem();
            boolean eqPick = eq == Items.WOODEN_PICKAXE || eq == Items.STONE_PICKAXE
                    || eq == Items.IRON_PICKAXE || eq == Items.GOLDEN_PICKAXE
                    || eq == Items.DIAMOND_PICKAXE || eq == Items.NETHERITE_PICKAXE;
            if (eqPick) return;
            Item[] picks = new Item[]{
                    Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE,
                    Items.STONE_PICKAXE, Items.GOLDEN_PICKAXE, Items.WOODEN_PICKAXE
            };
            for (Item pick : picks) {
                if (count(mod, pick) >= 1) {
                    try {
                        mod.getSlotHandler().forceEquipItem(pick);
                    } catch (Throwable ignored) {}
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isFinished() {
        return phase == Phase.DONE;
    }
}
