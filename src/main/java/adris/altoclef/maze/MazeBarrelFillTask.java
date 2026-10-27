package adris.altoclef.maze;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.slots.Slot;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Ostinato: run52/run53. Opens one placed barrel/chest in the maze room and shift-clicks the fill items into it.
 * StoreInContainerTask -> InteractWithBlockTask clicked, saw no open screen within 5s and fell back to
 * TimeoutWanderTask, whose explore goal was a 272-block path over snow, so fill stayed 0/1 two runs in a row.
 * This task never wanders: it walks to a real stand cell next to the container (feet + head open, solid floor),
 * releases sneak (a sneaking click places the held block instead of opening), right-clicks the visible face
 * directly, and moves items with QUICK_MOVE. Done when the server-side container is non-empty (what verify reads).
 */
public class MazeBarrelFillTask extends Task {

    private static final double REACH = 4.3;

    private final BlockPos target;
    private final ItemTarget[] toStore;
    private final List<BlockPos> badStands = new ArrayList<>();
    private BlockPos stand;
    private int standTicks, clickTicks, moveTicks, liveTicks;
    private boolean pathing;

    public MazeBarrelFillTask(BlockPos target, ItemTarget... toStore) {
        this.target = target;
        this.toStore = toStore;
    }

    @Override
    protected void onStart() {
        stand = null; standTicks = 0; clickTicks = 0; pathing = false;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (isFinished()) { closeIfOpen(mod); return null; }
        mod.getInputControls().release(Input.SNEAK);
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.SNEAK, false);

        // 1. Our container screen is open: shift-click every fill item in.
        if (containerOpen(mod)) {
            stopPath(mod);
            if (++moveTicks % 3 != 0) return stay("Filling " + target.toShortString());
            for (ItemTarget t : toStore) {
                List<Slot> slots = mod.getItemStorage().getSlotsWithItemPlayerInventory(false, t.getMatches());
                if (!slots.isEmpty()) {
                    mod.getSlotHandler().clickSlot(slots.get(0), 0, SlotActionType.QUICK_MOVE);
                    System.out.println("MAZETEST FURNISH fill-move " + Arrays.toString(t.getMatches()) + " into " + target.toShortString());
                    return stay("Moving fill item");
                }
            }
            return stay("No fill items left to move");
        }
        moveTicks = 0;

        // 2. In reach with a visible face: click it directly (no wander fallback).
        Vec3d eye = mod.getPlayer().getEyePos();
        Vec3d point = visiblePoint(mod, eye);
        if (point != null && eye.distanceTo(point) <= REACH) {
            stopPath(mod);
            look(mod, point);
            HitResult hr = MinecraftClient.getInstance().crosshairTarget;
            if (hr instanceof BlockHitResult bhr && bhr.getBlockPos().equals(target)) {
                if (++clickTicks % 10 == 1) {
                    mod.getSlotHandler().forceDeequipRightClickableItem();
                    MinecraftClient.getInstance().interactionManager.interactBlock(mod.getPlayer(), Hand.MAIN_HAND, bhr);
                    mod.getPlayer().swingHand(Hand.MAIN_HAND);
                    System.out.println("MAZETEST FURNISH fill-click " + target.toShortString() + " face=" + bhr.getSide() + " feet=" + mod.getPlayer().getBlockPos().toShortString());
                }
                if (clickTicks > 120) { // 6s of clicks without a screen: try another stand cell
                    if (stand != null) badStands.add(stand);
                    stand = null; clickTicks = 0;
                }
                return stay("Opening " + target.toShortString());
            }
            return stay("Aiming " + target.toShortString());
        }
        clickTicks = 0;

        // 3. Walk to a stand cell beside the container.
        if (stand == null || ++standTicks > 400) {
            if (stand != null) badStands.add(stand);
            stand = pickStand(mod);
            standTicks = 0; pathing = false;
            if (stand == null) { badStands.clear(); return stay("No stand cell next to " + target.toShortString()); }
            System.out.println("MAZETEST FURNISH fill-stand " + stand.toShortString() + " for " + target.toShortString());
        }
        if (!pathing) {
            mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(stand.getX(), stand.getY(), stand.getZ()));
            pathing = true;
        }
        return stay("Walking to " + stand.toShortString());
    }

    private BlockPos pickStand(AltoClef mod) {
        List<BlockPos> c = new ArrayList<>();
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos n = target.offset(d);
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos f = n.up(dy);
                if (open(mod, f) && open(mod, f.up()) && support(mod, f.down())) c.add(f);
            }
            BlockPos f2 = n.up(); // one up (barrel on the floor, standing on a ledge)
            if (open(mod, f2) && open(mod, f2.up()) && support(mod, f2.down())) c.add(f2);
        }
        if (open(mod, target.up()) && open(mod, target.up(2))) c.add(target.up()); // on top of it
        if (open(mod, target.down()) && open(mod, target.down(2)) && support(mod, target.down(3))) c.add(target.down(2));
        c.removeAll(badStands);
        BlockPos feet = mod.getPlayer().getBlockPos();
        c.sort((a, b) -> Double.compare(a.getSquaredDistance(feet), b.getSquaredDistance(feet)));
        return c.isEmpty() ? null : c.get(0);
    }

    private Vec3d visiblePoint(AltoClef mod, Vec3d eye) {
        Vec3d center = Vec3d.ofCenter(target);
        Vec3d best = null;
        for (Direction d : Direction.values()) {
            Vec3d p = center.add(d.getOffsetX() * 0.45, d.getOffsetY() * 0.45, d.getOffsetZ() * 0.45);
            HitResult hr = mod.getWorld().raycast(new RaycastContext(eye, p, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mod.getPlayer()));
            if (hr instanceof BlockHitResult bhr && bhr.getBlockPos().equals(target)) {
                if (best == null || eye.distanceTo(p) < eye.distanceTo(best)) best = p;
            }
        }
        return best;
    }

    private boolean containerOpen(AltoClef mod) {
        var h = mod.getPlayer().currentScreenHandler;
        if (h == null || h == mod.getPlayer().playerScreenHandler) return false;
        return h instanceof net.minecraft.screen.GenericContainerScreenHandler
                && mod.getPlayer().getEyePos().distanceTo(Vec3d.ofCenter(target)) < 6;
    }

    private void closeIfOpen(AltoClef mod) {
        var h = mod.getPlayer().currentScreenHandler;
        if (h != null && h != mod.getPlayer().playerScreenHandler) adris.altoclef.util.helpers.StorageHelper.closeScreen();
    }

    private static boolean open(AltoClef mod, BlockPos p) {
        BlockState s = mod.getWorld().getBlockState(p);
        return (s.isAir() || s.getCollisionShape(mod.getWorld(), p).isEmpty()) && s.getFluidState().isEmpty();
    }

    private static boolean support(AltoClef mod, BlockPos p) {
        BlockState s = mod.getWorld().getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(mod.getWorld(), p).isEmpty();
    }

    private static void look(AltoClef mod, Vec3d at) {
        Rotation rot = RotationUtils.calcRotationFromVec3d(
                mod.getClientBaritone().getPlayerContext().playerHead(), at,
                mod.getClientBaritone().getPlayerContext().playerRotations());
        mod.getClientBaritone().getLookBehavior().updateTarget(rot, true);
        mod.getPlayer().setYaw(rot.getYaw());
        mod.getPlayer().setPitch(rot.getPitch());
    }

    private void stopPath(AltoClef mod) {
        if (pathing) {
            mod.getClientBaritone().getCustomGoalProcess().onLostControl();
            mod.getClientBaritone().getPathingBehavior().cancelEverything();
            pathing = false;
        }
    }

    private Task stay(String why) {
        setDebugState(why);
        if (++liveTicks % 100 == 1) System.out.println("MAZETEST FURNISH fill-state " + target.toShortString() + " " + why);
        return null;
    }

    /** Server-side container non-empty: the same check MazeTest verify uses. */
    public static boolean filled(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.world.World w = mc.world;
        if (mc.getServer() != null && mc.world != null) {
            net.minecraft.server.world.ServerWorld sw = mc.getServer().getWorld(mc.world.getRegistryKey());
            if (sw != null) w = sw;
        }
        if (w == null) return false;
        return w.getBlockEntity(pos) instanceof net.minecraft.inventory.Inventory inv && !inv.isEmpty();
    }

    @Override
    public boolean isFinished() {
        return filled(target);
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef mod = AltoClef.getInstance();
        if (mod.getPlayer() != null) closeIfOpen(mod);
        if (pathing) mod.getClientBaritone().getCustomGoalProcess().onLostControl();
        pathing = false;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof MazeBarrelFillTask t && t.target.equals(target);
    }

    @Override
    protected String toDebugString() {
        return "Maze fill " + target.toShortString() + " " + Arrays.toString(toStore);
    }
}
