package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

/**
 * Get out of a cave. Preferred dest is AIR with sky light, not a block inside stone.
 * If no sky-lit air is reachable (buried / falling toward the void), S151 falls back to
 * any standable air pocket so the bot has somewhere to stand and mine out from.
 */
public class SurfaceBailTask extends Task {

    /**
     * S156: SurfaceBailTask can only ever finish by reaching sky light >= 13 at y >= 63.
     * A bot that respawns underground with no pickaxe and no blocks cannot dig out, so it
     * used to stay in this task forever. Run K burned 23 minutes here after one death.
     */
    private static final int MAX_TICKS = 20 * 120;

    /**
     * S168. Give up early when altitude has not changed at all for this long. Run T sat at
     * 29,31,-39 with an empty inventory for 4+ minutes while this task retargeted every 8s
     * and Baritone answered "Failed exploring" every 2s. The 120s ceiling was never reached
     * because S140 replaced the task every 40s (see holdFor), but even with that fixed, 60s
     * of literally zero vertical movement is enough evidence to stop.
     */
    private static final int STUCK_GIVEUP_TICKS = 20 * 60;

    /**
     * S161: S156 set {@code done = true} and logged "handing back to bootstrap", but it
     * never handed anything back. Task.tick() only swaps a child when
     * {@code !newSub.isEqual(sub)}, and isEqual below used to answer "any SurfaceBailTask",
     * so the FINISHED instance was kept and ticked forever — run Q emitted S156 102 times
     * over seven minutes with the counter climbing 120s -> 371s while the bot sat at y=19.
     *
     * Giving up therefore needs two things: isEqual must go false once done (so the dead
     * instance is really stopped), and a cooldown must latch (so the parent does not hand
     * the slot straight back and start another identical 120s bail).
     */
    private static final int GIVE_UP_COOL_TICKS = 20 * 60;
    private static int giveUpCool = 0;
    private static int giveUps = 0;

    private Task inner;
    private int ticks;
    private boolean done;
    private int lastY = Integer.MIN_VALUE;
    private int noClimb;
    /**
     * S168: total ticks with NO vertical movement at all, independent of noClimb.
     *
     * noClimb cannot serve both purposes: retarget() zeroes it every 8 seconds so it can
     * trigger a fresh destination, which means it can never reach STUCK_GIVEUP_TICKS. This
     * counter is reset only when y actually changes, so it measures the thing we care about.
     */
    private int deadTicks;

    public static boolean underground(AltoClef mod) {
        try {
            if (mod.getPlayer() == null || mod.getWorld() == null) return false;
            if (mod.getPlayer().isTouchingWater() || mod.getPlayer().isSubmergedInWater()) return false;
            BlockPos feet = mod.getPlayer().getBlockPos();
            int sky = mod.getWorld().getLightLevel(LightType.SKY, feet);
            int y = feet.getY();
            // sky 1 vs 2 flickers at the cave mouth. Only treat closed caves.
            return sky <= 0 || (y < 50 && sky <= 2);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean nearSpawner(AltoClef mod) {
        try {
            var found = mod.getBlockScanner().getNearestBlock(net.minecraft.block.Blocks.SPAWNER);
            if (found == null || found.isEmpty()) return false;
            BlockPos p = found.get();
            return mod.getPlayer().getPos().squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) < 12 * 12;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int sky(AltoClef mod) {
        try {
            return mod.getWorld().getLightLevel(LightType.SKY, mod.getPlayer().getBlockPos());
        } catch (Throwable t) {
            return 15;
        }
    }

    @Override
    protected void onStart() {
        ticks = 0;
        done = false;
        inner = null;
        noClimb = 0;
        deadTicks = 0;
        lastY = Integer.MIN_VALUE;
        giveUpCool = 0;
        giveUps = 0;
        retarget(AltoClef.getInstance());
    }

    /**
     * S161: driven from the speedrun driver every tick, not from onTick. The whole point of
     * the give-up is that this task STOPS running, so a counter decremented inside onTick
     * would freeze at its starting value and the cooldown would never expire.
     */
    public static void tickShared() {
        if (giveUpCool > 0) giveUpCool--;
    }

    /** S161: true while a recent give-up is still cooling — the parent must not re-issue. */
    public static boolean cooling() {
        return giveUpCool > 0;
    }

    /** S161: how many times this run has given up. Two means the bot is not getting out. */
    public static int giveUps() {
        return giveUps;
    }

    /**
     * S161: underground with no pickaxe and nothing placeable is not "stuck", it is dead.
     * Bare hands cannot break stone, so a bot in a stone shaft with an empty inventory has
     * no action that leads anywhere — run Q respawned into a 45-block pit at 119,19,202
     * after losing its kit and then logged S156 for seven minutes.
     */
    public static boolean unrecoverable(AltoClef mod) {
        try {
            if (mod == null || mod.getPlayer() == null) return false;
            // S169: "underground" is a proxy for "in a hole", and it fails in water — and in
            // the exact case it was written for. Run U: the bot fell to y=-3 at 174,-3,-24
            // with `sky=0`, `pick=0` and an empty inventory, and this check returned false
            // because being in water short-circuits the depth test. Depth is the property
            // that actually makes a hole a hole, so measure depth directly as well. y<=0 is
            // below every overworld surface, so this can never fire on the surface.
            int y = mod.getPlayer().getBlockY();
            if (!underground(mod) && !(y <= 0 && sky(mod) <= 2)) return false;
            var inv = mod.getItemStorage();
            if (inv == null) return false;
            for (net.minecraft.item.Item pick : new net.minecraft.item.Item[]{
                    net.minecraft.item.Items.WOODEN_PICKAXE,
                    net.minecraft.item.Items.STONE_PICKAXE,
                    net.minecraft.item.Items.IRON_PICKAXE,
                    net.minecraft.item.Items.GOLDEN_PICKAXE,
                    net.minecraft.item.Items.DIAMOND_PICKAXE,
                    net.minecraft.item.Items.NETHERITE_PICKAXE}) {
                if (inv.getItemCount(pick) > 0) return false;
            }
            return !hasPlaceable(mod);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Anything the bot could pillar with, or at least dig by hand and re-place. */
    private static boolean hasPlaceable(AltoClef mod) {
        var inv = mod.getItemStorage();
        for (net.minecraft.item.Item b : new net.minecraft.item.Item[]{
                net.minecraft.item.Items.DIRT,
                net.minecraft.item.Items.COARSE_DIRT,
                net.minecraft.item.Items.GRAVEL,
                net.minecraft.item.Items.SAND,
                net.minecraft.item.Items.COBBLESTONE,
                net.minecraft.item.Items.STONE,
                net.minecraft.item.Items.ANDESITE,
                net.minecraft.item.Items.GRANITE,
                net.minecraft.item.Items.DIORITE,
                net.minecraft.item.Items.NETHERRACK,
                net.minecraft.item.Items.OAK_PLANKS,
                net.minecraft.item.Items.SPRUCE_PLANKS,
                net.minecraft.item.Items.BIRCH_PLANKS,
                net.minecraft.item.Items.JUNGLE_PLANKS,
                net.minecraft.item.Items.ACACIA_PLANKS,
                net.minecraft.item.Items.DARK_OAK_PLANKS}) {
            if (inv.getItemCount(b) > 0) return true;
        }
        return false;
    }

    @Override
    protected Task onTick() {
        ticks++;
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) {
            done = true;
            return null;
        }
        int y = mod.getPlayer().getBlockY();
        if (y == lastY) {
            noClimb++;
            deadTicks++;
        } else {
            noClimb = 0;
            deadTicks = 0;
        }
        lastY = y;

        if (sky(mod) >= 13 && y >= 63 && ticks > 20) {
            done = true;
            return null;
        }
        // S168: a bail that has not gained a single block of altitude in STUCK_GIVEUP_TICKS is
        // not "still climbing", it is failing, and waiting the full MAX_TICKS (120s) to say so
        // is how run T burned five minutes. Baritone was logging "Failed exploring" every two
        // seconds the whole time; its own failure signal was there to be read.
        //
        // noClimb resets only when y CHANGES, in either direction, so this measures "nothing
        // is working" rather than "not going up" - a bot that wanders downhill looking for an
        // opening is still making progress and must not be given up on.
        if (ticks > MAX_TICKS || deadTicks > STUCK_GIVEUP_TICKS) {
            // S156: stop hogging the child slot. The parent resumes bootstrap, which can at
            // least try to collect wood/craft tools instead of walking in circles forever.
            //
            // S161: `done = true` alone did NOT do that — see GIVE_UP_COOL_TICKS. Latch the
            // cooldown here so the parent has to do something else for a while, and count
            // the failure so an unrecoverable bot can abandon the seed instead of looping.
            giveUpCool = GIVE_UP_COOL_TICKS;
            giveUps++;
            T2Log.warn("S156", "surface-bail gave up after " + (ticks / 20) + "s at y=" + y
                    + " sky=" + sky(mod) + " dead=" + (deadTicks / 20) + "s"
                    + " giveUp=" + giveUps
                    + (unrecoverable(mod) ? " unrecoverable" : "") + " - handing back to bootstrap");
            done = true;
            return null;
        }
        if (noClimb > 20 * 8 || ticks % (20 * 12) == 0) {
            retarget(mod);
            noClimb = 0;
        }
        if (inner == null) retarget(mod);
        return inner;
    }

    private void retarget(AltoClef mod) {
        BlockPos dest = findSky(mod);
        if (dest != null && dest.getY() != mod.getPlayer().getBlockY()) {
            inner = new GetToBlockTask(dest);
            Debug.logMessage("TESRUN2 surface-bail dest=" + dest);
        } else {
            inner = new TimeoutWanderTask();
            Debug.logMessage("TESRUN2 surface-bail wander (no air sky dest)");
        }
    }

    private BlockPos findSky(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return null;
        BlockPos from = mod.getPlayer().getBlockPos();
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        // First: search upward (normal case: inside a cave, sky is above).
        for (int r = 0; r <= 24; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = 0; dy <= 24; dy += 2) {
                        BlockPos p = new BlockPos(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
                        if (!openSky(mod, p)) continue;
                        int score = Math.abs(dx) + Math.abs(dz) + dy;
                        if (score < bestScore) {
                            bestScore = score;
                            best = p;
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        // S151 FALLBACK. Upward search found nothing with SKY light - the bot is deep
        // underground with no surface access, or (observed on run F / run J) respawned
        // inside a hole and fell toward the void.
        //
        // DO NOT reuse openSky() here: it demands sky light >= 13, and underground there is
        // no sky light anywhere, so a sky-light-only fallback can never fire. That is
        // exactly why run J logged 45x "no air sky dest" with S151 at zero while the bot
        // fell y=70 -> y=-4 and then sat stuck for four minutes.
        //
        // Search instead for ANY standable air pocket - a cave, tunnel or ledge. Reaching
        // one gives the bot somewhere to stand and mine out from, which is strictly better
        // than the TimeoutWanderTask that a null result produces.
        BlockPos pocket = findAirPocket(mod, from);
        if (pocket != null) {
            Debug.logMessage("TESRUN2 surface-bail S151 air-pocket dest=" + pocket + " from=" + from);
            return pocket;
        }
        return null;
    }

    /**
     * S151: any air block with something solid underneath, i.e. somewhere the bot could
     * actually stand. Deliberately does NOT require sky light - see findSky().
     */
    private BlockPos findAirPocket(AltoClef mod, BlockPos from) {
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int r = 0; r <= 16; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = 24; dy >= -32; dy -= 2) {
                        if (dx == 0 && dz == 0 && dy == 0) continue;   // our own feet
                        BlockPos p = new BlockPos(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
                        if (!standableAir(mod, p)) continue;
                        int score = Math.abs(dx) + Math.abs(dz) + Math.abs(dy);
                        if (score < bestScore) {
                            bestScore = score;
                            best = p;
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private boolean standableAir(AltoClef mod, BlockPos p) {
        try {
            if (!mod.getWorld().getBlockState(p).isAir()) return false;
            return !mod.getWorld().getBlockState(p.down()).isAir();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean openSky(AltoClef mod, BlockPos p) {
        try {
            BlockState st = mod.getWorld().getBlockState(p);
            if (!st.isAir()) return false;
            return mod.getWorld().getLightLevel(LightType.SKY, p) >= 13;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isFinished() {
        return done;
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        // S161: a finished bail must not compare equal to a fresh one. Task.tick() keeps
        // the running child whenever newSub.isEqual(sub), so the old "any SurfaceBailTask"
        // answer meant a dead instance was ticked forever and S156 could never hand back.
        if (done) return false;
        return other instanceof SurfaceBailTask && !((SurfaceBailTask) other).done;
    }

    @Override
    protected String toDebugString() {
        return "surface-bail";
    }
}
