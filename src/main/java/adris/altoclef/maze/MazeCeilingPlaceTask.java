// Ostinato: interior ceiling cladding. Baritone's builder breaks the shell, then wanders,
// because the only clickable face left is the underside of the block above the hole.
// Stand on the floor under the cell, mine it if it is the wrong block, sneak-click that underside.
package adris.altoclef.maze;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.ItemHelper;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

public class MazeCeilingPlaceTask extends Task {

    private static final double REACH = 4.4;

    private final BlockPos target;
    private final Block[] toPlace;
    private BlockPos going;
    private boolean logged;
    private boolean arrived;
    private int actTicks;
    private int liveTicks;

    public MazeCeilingPlaceTask(BlockPos target, Block[] toPlace) {
        this.target = target;
        this.toPlace = toPlace;
    }

    @Override
    protected void onStart() {
        actTicks = 0;
        going = null;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (placed(mod)) {
            release(mod);
            return null;
        }
        if (mod.getItemStorage().getItemCount(ItemHelper.blocksToItems(toPlace)) <= 0) {
            setDebugState("No ceiling block in inventory");
            release(mod);
            return null;
        }
        mod.getClientBaritone().getBuilderProcess().onLostControl();
        BlockPos stand = standUnder(mod);
        Vec3d eye = mod.getClientBaritone().getPlayerContext().playerHead();
        double horiz = Math.hypot(eye.x - (target.getX() + 0.5), eye.z - (target.getZ() + 0.5));
        // Feet stay in this column. Standing in a neighbor makes the upward ray hit the wrong cell.
        boolean there = horiz < 0.75 && mod.getPlayer().getBlockPos().getY() <= stand.getY() + 1
                && mod.getPlayer().getBlockPos().getY() >= stand.getY() - 1;
        if (!there) {
            release(mod);
            arrived = false;
            if (!stand.equals(going)) {
                going = stand;
                mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(stand.getX(), stand.getY(), stand.getZ()));
            }
            return stay("Walking under ceiling " + target.toShortString());
        }
        if (!arrived) {
            mod.getClientBaritone().getCustomGoalProcess().onLostControl();
            mod.getClientBaritone().getPathingBehavior().cancelEverything();
            arrived = true;
        }
        BlockState state = mod.getWorld().getBlockState(target);
        if (!logged) {
            System.out.println("MAZETEST FURNISH ceiling " + target.getX() + " " + target.getY() + " " + target.getZ()
                    + " was=" + net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath());
            logged = true;
        }
        if (!state.isAir() && !ArrayUtils.contains(toPlace, state.getBlock())) {
            Item[] picks = {Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, Items.STONE_PICKAXE};
            // Hunger used to make forceEquip return false, so a pick in the inventory still logged "No pickaxe" and never swung.
            if (!mod.getItemStorage().hasItem(picks)) {
                mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                if (!logged) {
                    System.out.println("MAZETEST FURNISH ceiling-need-pick " + target.toShortString());
                    logged = true;
                }
                return adris.altoclef.TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
            }
            if (!mod.getSlotHandler().forceEquipItem(picks, true)) {
                mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                return stay("Equipping pickaxe " + target.toShortString());
            }
            look(mod, Vec3d.ofCenter(target));
            // Only swing once the crosshair is on this cell, so a neighbor ceiling is not opened into another hole.
            HitResult hit = MinecraftClient.getInstance().crosshairTarget;
            boolean onTarget = hit instanceof BlockHitResult bhr && bhr.getBlockPos().equals(target);
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, onTarget);
            return stay("Mining ceiling " + target.toShortString());
        }
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
        mod.getInputControls().release(Input.CLICK_LEFT);
        Click click = clickFace(mod);
        if (click == null) {
            return stay("No face for ceiling " + target.toShortString());
        }
        if (eye.distanceTo(click.hit) > REACH) {
            return stay("Too far to place ceiling " + target.toShortString());
        }
        if (!mod.getSlotHandler().forceEquipItem(ItemHelper.blocksToItems(toPlace))) {
            return stay("No ceiling block in hand " + target.toShortString());
        }
        look(mod, click.hit);
        // Same click path as PlaceBlockNearbyTask: the server accepts the crosshair ray, not a made-up hit.
        HitResult lookHit = MinecraftClient.getInstance().crosshairTarget;
        if (!(lookHit instanceof BlockHitResult bhr) || !bhr.getBlockPos().offset(bhr.getSide()).equals(target)) {
            return stay("Aiming ceiling " + target.toShortString());
        }
        if (++actTicks % 4 != 0) {
            return stay("Placing ceiling " + target.toShortString());
        }
        if (MinecraftClient.getInstance().interactionManager.interactBlock(mod.getPlayer(), Hand.MAIN_HAND, bhr) == ActionResult.SUCCESS) {
            mod.getPlayer().swingHand(Hand.MAIN_HAND);
        }
        return stay("Placing ceiling " + target.toShortString());
    }

    private Task stay(String why) {
        setDebugState(why);
        if (++liveTicks % 100 == 1) {
            System.out.println("MAZETEST FURNISH ceiling-state " + target.getX() + " " + target.getY() + " " + target.getZ() + " " + why);
        }
        return null;
    }

    private boolean placed(AltoClef mod) {
        return ArrayUtils.contains(toPlace, mod.getWorld().getBlockState(target).getBlock());
    }

    /** Feet position on the highest solid under the cell. */
    private BlockPos standUnder(AltoClef mod) {
        for (int y = target.getY() - 1; y >= target.getY() - 6 && y > mod.getWorld().getBottomY(); y--) {
            BlockPos feet = new BlockPos(target.getX(), y, target.getZ());
            if (support(mod, feet.down()) && open(mod, feet) && open(mod, feet.up())) {
                return feet;
            }
        }
        return new BlockPos(target.getX(), target.getY() - 2, target.getZ());
    }

    private static boolean open(AltoClef mod, BlockPos p) {
        BlockState s = mod.getWorld().getBlockState(p);
        return s.isAir() || s.getCollisionShape(mod.getWorld(), p).isEmpty();
    }

    private static boolean support(AltoClef mod, BlockPos p) {
        BlockState s = mod.getWorld().getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(mod.getWorld(), p).isEmpty();
    }

    /** Click the face that borders the hole. The underside of the block above is the one we can see from the floor. */
    private Click clickFace(AltoClef mod) {
        BlockPos up = target.up();
        if (support(mod, up)) {
            return new Click(up, Direction.DOWN, new Vec3d(target.getX() + 0.5, up.getY(), target.getZ() + 0.5));
        }
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos n = target.offset(d);
            if (support(mod, n)) {
                Vec3d hit = Vec3d.ofCenter(n).add(d.getOpposite().getOffsetX() * 0.5, d.getOpposite().getOffsetY() * 0.5, d.getOpposite().getOffsetZ() * 0.5);
                return new Click(n, d.getOpposite(), hit);
            }
        }
        return null;
    }

    private static void look(AltoClef mod, Vec3d at) {
        Rotation rot = RotationUtils.calcRotationFromVec3d(
                mod.getClientBaritone().getPlayerContext().playerHead(),
                at,
                mod.getClientBaritone().getPlayerContext().playerRotations());
        mod.getClientBaritone().getLookBehavior().updateTarget(rot, true);
        mod.getPlayer().setYaw(rot.getYaw());
        mod.getPlayer().setPitch(rot.getPitch());
    }

    private static void release(AltoClef mod) {
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
        mod.getInputControls().release(Input.CLICK_LEFT);
        mod.getInputControls().release(Input.SNEAK);
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef mod = AltoClef.getInstance();
        if (mod != null && mod.getPlayer() != null) {
            release(mod);
        }
    }

    @Override
    public boolean isFinished() {
        AltoClef mod = AltoClef.getInstance();
        return mod != null && mod.getWorld() != null && placed(mod);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof MazeCeilingPlaceTask t && t.target.equals(target) && Arrays.equals(t.toPlace, toPlace);
    }

    @Override
    protected String toDebugString() {
        return "Ceiling " + Arrays.toString(toPlace) + " at " + target.toShortString();
    }

    private record Click(BlockPos against, Direction face, Vec3d hit) {}
}
