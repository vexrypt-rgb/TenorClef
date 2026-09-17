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
    private int woodStill;
    private int woodPause;
    private int wanderHold;
    private int woodX = Integer.MIN_VALUE;
    private int woodZ = Integer.MIN_VALUE;
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
        hadKit = false;
        darkPortalTicks = 0;
        lootGaveUp = false;
        statusTicks = 0;
        junkDropped = false;
        endTicks = 0;
        stillTicks = 0;
        portalAttempts = 0;
        forceSurface = false;
        forceSurfaceTicks = 0;
        waterCooldown = 0;
        wetStreak = 0;
        pickCraftLock = false;
        deathLock = 0;
        SpeedrunClock.reset();
        var spawn = SpawnScout.scan(AltoClef.getInstance());
        if (SpeedrunOpt.ABORT_BAD_SPAWN && spawn.resetWorthy()) {
            Debug.logWarning("TESRUN2 abort: ocean spawn, no village/RP/trees. Reset the seed.");
            phase = Phase.DONE;
            ResetSignal.fire("ocean spawn");
        }
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
        try {
            return onTickInner(mod);
        } catch (Throwable t) {
            T2Log.warn("E199", "onTick crash " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return active;
        }
    }

    private Task onTickInner(AltoClef mod) {
        statusTicks++;
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
                T2Log.warn("E94", "step-off frozen t=0 — PORTAL with current pick");
                skipIronPick = true;
                pickCraftLock = false;
                recraftPause = 0;
                stepOffAge = 0;
                active = null;
                setPhase(Phase.PORTAL);
                return stick(portal(mod));
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

        // IRON must mine ore. Only yank BOOTSTRAP out of a cave.
        // Exception: already have 3 iron and are crafting a pick in the dark —
        // that is the jump-on-buried-table lock.
        if (phase == Phase.BOOTSTRAP && SurfaceBailTask.underground(mod)) {
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
        if (count(mod, Items.IRON_PICKAXE) >= 1 || skipIronPick) {
            return Phase.PORTAL;
        }
        // Naked + a portal in range is a death respawn, not a speedrun route.
        boolean naked = count(mod, Items.IRON_PICKAXE) < 1
                && count(mod, Items.WOODEN_PICKAXE) < 1
                && count(mod, Items.STONE_PICKAXE) < 1
                && count(mod, Items.IRON_INGOT) + count(mod, Items.RAW_IRON) < 1;
        if (portalNearby(mod) && !naked) {
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
            int x = mod.getPlayer().getBlockX() >> 2;
            int z = mod.getPlayer().getBlockZ() >> 2;
            if (x == woodX && z == woodZ) woodStill++;
            else { woodStill = 0; woodX = x; woodZ = z; }
            if (woodStill > 20 * 8) {
                // Do NOT TimeoutWander for 8s — that skips nearby jungle/etc for a far oak.
                // Blacklist the stuck tree column and immediately re-pick nearest ANY log type.
                T2Log.warn("E97", "wood jump-lock — retarget nearest any-type log");
                woodStill = 0;
                woodPause = 20 * 2; // brief strafe only
                McCompat.closeScreen();
                McCompat.cancelPathing();
                blacklistNearbyWood(mod);
                active = null;
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
        int ironN = count(mod, Items.IRON_INGOT) + count(mod, Items.RAW_IRON);
        // Only block the first pick craft in a real hole. After a pick
        // exists, sword/buckets may craft under leaves.
        int sky = 15;
        try {
            sky = mod.getWorld().getLightLevel(net.minecraft.world.LightType.SKY, mod.getPlayer().getBlockPos());
        } catch (Throwable ignored) {}
        if (count(mod, Items.IRON_PICKAXE) >= 1 || skipIronPick) {
            pickCraftLock = false;
            T2History.note("WHY iron: pick done — skip sword/shield table, go PORTAL");
            return null;
        }
        int ore = count(mod, Items.IRON_ORE) + countOpt(mod, "DEEPSLATE_IRON_ORE");
        int want = Math.min(24, Math.max(8, ironN + ore));
        if (count(mod, Items.IRON_INGOT) < want && count(mod, Items.IRON_PICKAXE) < 1 && ironN + ore >= 8) {
            // already have 8 metal units — smelt the rest in this furnace, do not start a second trip
        }
        if (count(mod, Items.IRON_INGOT) < 8) {
            pickCraftLock = false;
            T2History.note("WHY iron: smelt " + want + " in one furnace");
            return TaskCatalogue.getItemTask(Items.IRON_INGOT, want);
        }
        // Iron pick is 3 ingots + 2 sticks. logs=0 with no sticks = GUI forever.
        if (stickFuel(mod) < 2) {
            T2Log.warn("E93", "iron pick missing sticks fuel=" + stickFuel(mod));
            T2History.note("WHY iron: 1 log for sticks before pick table");
            pickCraftLock = false;
            return collectWood(mod, 1);
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

    private Task portal(AltoClef mod) {
        if (WorldHelper.getCurrentDimension() == Dimension.NETHER) return null;
        if (active instanceof ConstructNetherPortalBucketTask && !active.isFinished()) {
            return active;
        }
        if (mod.getBlockScanner().anyFound(Blocks.NETHER_PORTAL)) {
            T2History.note("WHY portal: walk in existing");
            return new EnterNetherPortalTask(Dimension.NETHER);
        }
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
                if (mod.getPlayer().getBlockY() < 48) {
                    T2History.note("WHY nether: climb off lava before gold");
                    try {
                        return new GetToBlockTask(mod.getPlayer().getBlockPos().up(12));
                    } catch (Throwable ignored) {
                        return new TimeoutWanderTask();
                    }
                }
                return TaskCatalogue.getItemTask(Items.GOLD_INGOT, 5);
            }
            return TaskCatalogue.getItemTask(Items.GOLDEN_HELMET, 1);
        }

        if (pearls < SpeedrunOpt.PEARLS && gold >= 8 && tradeTicks < TRADE_MAX_TICKS) {
            tradeTicks++;
            return new TradeWithPiglinsTask(32, new ItemTarget(Items.ENDER_PEARL, SpeedrunOpt.PEARLS));
        }
        if (rods < SpeedrunOpt.BLAZE_RODS) {
            return new CollectBlazeRodsTask(SpeedrunOpt.BLAZE_RODS);
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
        if (active instanceof StepOffTableTask && !active.isFinished()) {
            return active;
        }
        if (active instanceof SurfaceBailTask && !active.isFinished()
                && !(wanted instanceof EnterNetherPortalTask)) {
            return active;
        }
        if (active instanceof ConstructNetherPortalBucketTask && !active.isFinished()
                && !(wanted instanceof EnterNetherPortalTask)) {
            return active;
        }
        if (wanderHold > 0 && active instanceof TimeoutWanderTask && !active.isFinished()
                && !(wanted instanceof EnterNetherPortalTask)) {
            return active;
        }
        if (active instanceof ConstructNetherPortalBucketTask && !active.isFinished()
                && (wanted instanceof UnstickWalkTask || wanted instanceof TimeoutWanderTask)) {
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
            HolePillar.reset();
            active = null;
            ironStill = 0;
            return stick(iron(mod));
        }
        if (ironStill == 20 * 12) {
            T2Log.warn("E70", "iron frozen 12s @" + x + "," + z + " — walk");
            McCompat.cancelPathing();
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
            clearBlockBlacklist(mod);
            HolePillar.reset();
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
            T2Log.warn("E91", "step off table @" + x + "," + z + " n=" + e91Count);
            craftStuck = 0;
            McCompat.closeScreen();
            forceSurface = false;
            active = null;
            if (e91Count >= 2 && count(mod, Items.WOODEN_PICKAXE) + count(mod, Items.STONE_PICKAXE) >= 1) {
                skipIronPick = true;
                pickCraftLock = false;
                T2Log.warn("E94", "abandon iron pick after " + e91Count + " table fails — PORTAL");
                T2History.note("WHY E94: wooden pick is enough for a bucket portal");
                setPhase(Phase.PORTAL);
                return stick(portal(mod));
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
        if (!dead) sessionLive = false;
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
