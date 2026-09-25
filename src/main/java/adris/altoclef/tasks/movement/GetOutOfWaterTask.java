package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.pathing.goals.Goal;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

/**
 * Escape water to dry footing. Critical: {@link #isEqual} must treat all
 * instances as equal so parents that return {@code new GetOutOfWaterTask()}
 * every tick do not forceCancel pathing (S102 spd≈0 thrash / water-bob stall).
 */
public class GetOutOfWaterTask extends CustomBaritoneGoalTask {

    private boolean startedShimmying = false;
    private final TimerGame shimmyTaskTimer = new TimerGame(5);

    @Override
    protected void onStart() {
        // Intentionally do not call super.onStart() forceCancel here via a no-op
        // override of cancel — CustomBaritoneGoalTask.onStart cancels pathing.
        // With isEqual fixed, onStart runs once per escape attempt, which is correct.
        super.onStart();
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();

        // get on the surface first
        if (mod.getPlayer().getAir() < mod.getPlayer().getMaxAir() || mod.getPlayer().isSubmergedInWater()) {
            return super.onTick();
        }

        boolean hasBlockBelow = false;
        for (int i = 0; i < 3; i++) {
            if (mod.getWorld().getBlockState(mod.getPlayer().getSteppingPos().down(i)).getBlock() != Blocks.WATER) {
                hasBlockBelow = true;
            }
        }
        boolean hasAirAbove = mod.getWorld().getBlockState(mod.getPlayer().getBlockPos().up(2)).getBlock().equals(Blocks.AIR);

        if (hasAirAbove && hasBlockBelow && StorageHelper.getNumberOfThrowawayBlocks(mod) > 0) {
            mod.getInputControls().tryPress(Input.JUMP);
            if (mod.getPlayer().isOnGround()) {

                if (!startedShimmying) {
                    startedShimmying = true;
                    shimmyTaskTimer.reset();
                }
                return new SafeRandomShimmyTask();
            }

            mod.getSlotHandler().forceEquipItem(mod.getClientBaritoneSettings().acceptableThrowawayItems.value.toArray(new Item[0]));
            LookHelper.lookAt(mod, mod.getPlayer().getSteppingPos().down());
            mod.getInputControls().tryPress(Input.CLICK_RIGHT);
        }

        return super.onTick();
    }

    @Override
    protected void onStop(Task interruptTask) {
        super.onStop(interruptTask);
    }

    @Override
    protected Goal newGoal(AltoClef mod) {
        return new EscapeFromWaterGoal();
    }

    @Override
    protected boolean isEqual(Task other) {
        // MUST be true for any GetOutOfWaterTask. Returning false caused
        // CollectWaterBucket / T2Solve S102 to restart this task every tick,
        // force-cancelling Baritone so spd stayed ≈0 while bobbing.
        return other instanceof GetOutOfWaterTask;
    }

    @Override
    protected String toDebugString() {
        return "get out of water";
    }

    /**
     * S183 — the escape must not end while the bot is merely BOBBING.
     *
     * <p>The original test was {@code !isTouchingWater() && isOnGround()}, sampled once. A bot
     * bobbing in water clears the surface for a tick at a time, so that instantaneous sample
     * reports "escaped" on every bob — while the bot is still standing in the water. The
     * parent then resumes, the bot sinks, and S102 rebuilds the escape. Run AD/AE:
     * {@code S102 escape water spd=0.000} at one fixed position for 40+ seconds.
     *
     * <p>Require the dry state to HOLD. Measured by wall clock rather than a tick counter so
     * the check is idempotent — {@code isFinished()} is polled more than once per tick by the
     * task chain, and a counter would advance 2–3× faster than real time (the same trap
     * documented on {@code HolePillar.risenEnough}).
     */
    private static long drySinceMs;

    /** How long the bot must be continuously dry-and-grounded before the escape counts. */
    private static final long DRY_CONFIRM_MS = 750L;

    @Override
    public boolean isFinished() {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() == null) return true;
        boolean dry = !mod.getPlayer().isTouchingWater() && mod.getPlayer().isOnGround();
        if (!dry) {
            drySinceMs = 0;
            return false;
        }
        long now = System.currentTimeMillis();
        if (drySinceMs == 0) drySinceMs = now;
        long dryFor = now - drySinceMs;
        if (dryFor < DRY_CONFIRM_MS) return false;
        // Observable: a fix with no log line is an unvalidatable fix. This fires ONCE per
        // escape (the task is then finished), so it cannot flood.
        Debug.logMessage("TESRUN2 S183 water escape confirmed dry for " + dryFor
                + "ms at " + mod.getPlayer().getBlockX() + ","
                + mod.getPlayer().getBlockY() + "," + mod.getPlayer().getBlockZ());
        drySinceMs = 0;
        return true;
    }

    private static class EscapeFromWaterGoal implements Goal {

        private static boolean isWater(int x, int y, int z) {
            if (MinecraftClient.getInstance().world == null) return false;
            return MovementHelper.isWater(MinecraftClient.getInstance().world.getBlockState(new BlockPos(x, y, z)));
        }

        private static boolean isWaterAdjacent(int x, int y, int z) {
            return isWater(x + 1, y, z) || isWater(x - 1, y, z) || isWater(x, y, z + 1) || isWater(x, y, z - 1)
                    || isWater(x + 1, y, z - 1) || isWater(x + 1, y, z + 1) || isWater(x - 1, y, z - 1)
                    || isWater(x - 1, y, z + 1);
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            return !isWater(x, y, z) && !isWaterAdjacent(x, y, z);
        }

        @Override
        public double heuristic(int x, int y, int z) {
            // Prefer dry land; mild penalty for water-adjacent so we step inland.
            // (Previously flat water=1 gave almost no escape gradient.)
            if (isWater(x, y, z)) {
                return 8;
            } else if (isWaterAdjacent(x, y, z)) {
                return 2;
            }
            return 0;
        }
    }
}
