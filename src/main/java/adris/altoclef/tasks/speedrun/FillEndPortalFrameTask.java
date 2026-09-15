package adris.altoclef.tasks.speedrun;

import net.minecraft.client.MinecraftClient;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Place one eye of ender into a single empty end portal frame.
 */
public class FillEndPortalFrameTask extends Task {

    private final AltoClef mod;
    private final BlockPos framePos;
    private boolean placed = false;

    public FillEndPortalFrameTask(AltoClef mod, BlockPos framePos) {
        this.mod = mod;
        this.framePos = framePos;
    }

    @Override
    protected void onStart() {
        placed = false;
        setDebugState("Filling portal frame " + framePos);
    }

    @Override
    protected Task onTick() {
        if (placed) return null;

        // Already has eye?
        try {
            var state = mod.getWorld().getBlockState(framePos);
            if (state.isOf(Blocks.END_PORTAL_FRAME)
                    && state.contains(EndPortalFrameBlock.EYE)
                    && state.get(EndPortalFrameBlock.EYE)) {
                placed = true;
                return null;
            }
        } catch (Exception ignored) {}

        if (StorageHelper.getItemCount(mod, Items.ENDER_EYE) < 1) {
            setDebugState("No eyes left to place");
            return null;
        }

        // Equip eye
        mod.getSlotHandler().forceEquipItem(Items.ENDER_EYE);

        // Look at frame and interact
        Vec3d hit = Vec3d.ofCenter(framePos);
        LookHelper.lookAt(mod, framePos);

        // Right-click the frame
        BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, framePos, false);
        mod.getClientBaritone().getInputOverrideHandler().clearAllKeys();
        // Use interaction manager if available
        try {
            mod.getPlayer().swingHand(Hand.MAIN_HAND);
            if (mod.getWorld() != null && mod.getControllerExtras() != null) {
                // Fallback: many forks expose interact via player or input
            }
            // Standard client interact
            net.minecraft.client.MinecraftClient.getInstance().interactionManager.interactBlock(
                    mod.getPlayer(),
                    Hand.MAIN_HAND,
                    bhr
            );
        } catch (Exception e) {
            setDebugState("Interact failed: " + e.getMessage());
        }

        // Re-check next tick
        try {
            var state = mod.getWorld().getBlockState(framePos);
            if (state.contains(EndPortalFrameBlock.EYE) && state.get(EndPortalFrameBlock.EYE)) {
                placed = true;
            }
        } catch (Exception ignored) {}

        return null;
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FillEndPortalFrameTask
                && ((FillEndPortalFrameTask) other).framePos.equals(this.framePos);
    }

    @Override
    protected String toDebugString() {
        return "FillEndPortalFrameTask(" + framePos + ")";
    }

    @Override
    public boolean isFinished() {
        if (placed) return true;
        try {
            var state = mod.getWorld().getBlockState(framePos);
            return state.isOf(Blocks.END_PORTAL_FRAME)
                    && state.contains(EndPortalFrameBlock.EYE)
                    && state.get(EndPortalFrameBlock.EYE);
        } catch (Exception e) {
            return false;
        }
    }
}
