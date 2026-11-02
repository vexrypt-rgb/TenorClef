// Ostinato: 2026-11-02. Pool water, farmland, wheat and sugar cane.
// The ceiling clicker equips a block item and mines the cell first. A water cell has no block item
// (the bucket places the fluid), and a crop click has to land on the soil under the cell.
package adris.altoclef.maze;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.speedrun.testrun2.McCompat;
import adris.altoclef.tasksystem.Task;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Ostinato: 2026-11-02. One pool or farm cell from a backrooms furnish step.
 * {@code water} is the air cell that should hold the source. {@code farmland} is the soil cell.
 * {@code wheat} and {@code sugar_cane} are the crop cell, the air above the soil.
 */
public class MazeFeaturePlaceTask extends Task {

    private final BlockPos target;
    private final String feature;
    private BlockPos going;
    private int ticks;
    private boolean logged;

    public MazeFeaturePlaceTask(BlockPos target, String feature) {
        this.target = target;
        this.feature = feature;
    }

    /** The world cell already matches the feature. Water accepts a source or a flow. */
    public static boolean satisfied(BlockState state, String feature) {
        if (state == null || feature == null) return false;
        if ("water".equals(feature)) {
            Fluid fluid = state.getFluidState().getFluid();
            return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
        }
        return feature.equals(Registries.BLOCK.getId(state.getBlock()).getPath());
    }

    @Override
    protected void onStart() {
        going = null;
        ticks = 0;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        // Ostinato: same world guard as the ceiling task. A tick with no world must not throw out of the client tick.
        if (mod == null || mod.getWorld() == null || mod.getPlayer() == null) {
            setDebugState("No world");
            return null;
        }
        BlockState state = mod.getWorld().getBlockState(target);
        if (satisfied(state, feature)) return null;
        if (!logged) {
            logged = true;
            System.out.println("MAZETEST FURNISH feature " + feature + " " + target.getX() + " " + target.getY() + " " + target.getZ()
                    + " was=" + Registries.BLOCK.getId(state.getBlock()).getPath());
        }
        Task prep = prepare(mod, state);
        if (prep != null) return prep;
        // Ostinato: the click budget starts once the item is in hand. A dirt or seed gather keeps its own timer.
        if (++ticks > 400) {
            setDebugState("Giving the step budget " + feature);
            return null;
        }
        BlockPos stand = stand(mod);
        Vec3d eye = mod.getPlayer().getEyePos();
        boolean there = Math.hypot(eye.x - (stand.getX() + 0.5), eye.z - (stand.getZ() + 0.5)) < 0.9
                && Math.abs(mod.getPlayer().getBlockPos().getY() - stand.getY()) <= 1;
        if (!there) {
            if (!stand.equals(going)) {
                going = stand;
                mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(stand.getX(), stand.getY(), stand.getZ()));
            }
            setDebugState("Walking to " + feature + " " + target.toShortString());
            return null;
        }
        mod.getClientBaritone().getCustomGoalProcess().onLostControl();
        mod.getClientBaritone().getPathingBehavior().cancelEverything();
        click(mod, state);
        setDebugState("Placing " + feature + " " + target.toShortString());
        return null;
    }

    /** Gather the item, hoe soil, or put dirt down. Null when the click can happen. */
    private Task prepare(AltoClef mod, BlockState state) {
        if ("water".equals(feature)) {
            if (!mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
                setDebugState("Gathering water_bucket");
                return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
            }
            return null;
        }
        if ("farmland".equals(feature)) {
            if (tillable(state)) {
                if (!hasHoe(mod)) return TaskCatalogue.getItemTask(hoeAsk(), 1);
                return null;
            }
            if (!mod.getItemStorage().hasItem(Items.DIRT)) {
                setDebugState("Gathering dirt for farmland");
                return TaskCatalogue.getItemTask(Items.DIRT, 1);
            }
            setDebugState("Placing dirt to till");
            return new PlaceBlockTask(target, Blocks.DIRT);
        }
        BlockPos soil = target.down();
        BlockState under = mod.getWorld().getBlockState(soil);
        if ("wheat".equals(feature) && tillable(under)) {
            if (!hasHoe(mod)) return TaskCatalogue.getItemTask(hoeAsk(), 1);
            return null; // click hoes the dirt, then the next tick plants
        }
        Item seed = "wheat".equals(feature) ? Items.WHEAT_SEEDS : Items.SUGAR_CANE;
        if (!mod.getItemStorage().hasItem(seed)) {
            setDebugState("Gathering " + Registries.ITEM.getId(seed).getPath());
            return TaskCatalogue.getItemTask(seed, 1);
        }
        return null;
    }

    private void click(AltoClef mod, BlockState state) {
        Item[] hand = hand(mod);
        if (hand.length == 0 || !mod.getSlotHandler().forceEquipItem(hand, true)) return;
        BlockPos against;
        Direction face;
        BlockState soil = mod.getWorld().getBlockState(target.down());
        if ("farmland".equals(feature)) {
            against = target;
            face = Direction.UP;
        } else if ("water".equals(feature)) {
            // The bucket fills the air cell past the face that was clicked.
            if (solid(mod, target.down())) {
                against = target.down();
                face = Direction.UP;
            } else {
                against = sideSupport(mod);
                if (against.equals(target.down())) return;
                face = directionInto(against);
            }
        } else if ("wheat".equals(feature) && tillable(soil)) {
            against = target.down(); // hoe the dirt; the next tick plants the seed
            face = Direction.UP;
        } else {
            against = target.down();
            face = Direction.UP;
        }
        Vec3d hit = Vec3d.ofCenter(against).add(face.getOffsetX() * 0.49, face.getOffsetY() * 0.49, face.getOffsetZ() * 0.49);
        look(mod, hit);
        HitResult lookHit = MinecraftClient.getInstance().crosshairTarget;
        if (!(lookHit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(against) || bhr.getSide() != face) return;
        if (ticks % 4 != 0) return;
        ActionResult res = MinecraftClient.getInstance().interactionManager.interactBlock(mod.getPlayer(), Hand.MAIN_HAND, bhr);
        if (res == ActionResult.SUCCESS) mod.getPlayer().swingHand(Hand.MAIN_HAND);
        else if (ticks == 80) {
            System.out.println("MAZETEST FURNISH feature-refused " + feature + " " + target.toShortString() + " res=" + res);
        }
    }

    private Item[] hand(AltoClef mod) {
        if ("water".equals(feature)) return new Item[]{Items.WATER_BUCKET};
        if ("farmland".equals(feature) || ("wheat".equals(feature) && tillable(mod.getWorld().getBlockState(target.down())))) {
            return hoes();
        }
        if ("wheat".equals(feature)) return new Item[]{Items.WHEAT_SEEDS};
        return new Item[]{Items.SUGAR_CANE};
    }

    private BlockPos stand(AltoClef mod) {
        BlockPos feetNow = mod.getPlayer().getBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        // Ostinato: the ring is the reach, not the room. A 5-wide and an 11-wide room use the same stand.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos feet = target.add(dx, dy, dz);
                    if (feet.equals(target) || feet.up().equals(target)) continue;
                    if (!open(mod, feet) || !open(mod, feet.up()) || !solid(mod, feet.down())) continue;
                    Vec3d eye = new Vec3d(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
                    if (!MazeReach.reaches(eye.x, eye.y, eye.z, target.getX(), target.getY(), target.getZ())) continue;
                    double d = feet.getSquaredDistance(feetNow);
                    if (d < bestD) {
                        bestD = d;
                        best = feet;
                    }
                }
            }
        }
        if (best != null) return best;
        return target.offset(Direction.NORTH);
    }

    private BlockPos sideSupport(AltoClef mod) {
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos n = target.offset(d);
            if (solid(mod, n)) return n;
        }
        return target.down();
    }

    /** The face of {@code against} that points at {@code target}. */
    private Direction directionInto(BlockPos against) {
        int dx = target.getX() - against.getX();
        int dy = target.getY() - against.getY();
        int dz = target.getZ() - against.getZ();
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        if (dz > 0) return Direction.SOUTH;
        if (dz < 0) return Direction.NORTH;
        if (dy > 0) return Direction.UP;
        return Direction.DOWN;
    }

    private static boolean tillable(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.DIRT_PATH || b == Blocks.COARSE_DIRT;
    }

    private static boolean solid(AltoClef mod, BlockPos p) {
        BlockState s = mod.getWorld().getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(mod.getWorld(), p).isEmpty();
    }

    private static boolean open(AltoClef mod, BlockPos p) {
        BlockState s = mod.getWorld().getBlockState(p);
        return s.isAir() || s.getCollisionShape(mod.getWorld(), p).isEmpty();
    }

    private static boolean hasHoe(AltoClef mod) {
        Item[] hoes = hoes();
        return hoes.length > 0 && mod.getItemStorage().hasItem(hoes);
    }

    /** Copper, then iron, then the rest. A missing hoe is asked as copper when this version has one. */
    private static Item[] hoes() {
        java.util.List<Item> out = new java.util.ArrayList<>();
        for (String id : new String[]{"copper_hoe", "iron_hoe", "diamond_hoe", "netherite_hoe", "stone_hoe", "wooden_hoe"}) {
            Item it = McCompat.item(id);
            if (it != null) out.add(it);
        }
        return out.toArray(new Item[0]);
    }

    private static String hoeAsk() {
        return TaskCatalogue.taskExists("copper_hoe") ? "copper_hoe" : "wooden_hoe";
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

    @Override
    protected void onStop(Task interruptTask) {
    }

    @Override
    public boolean isFinished() {
        AltoClef mod = AltoClef.getInstance();
        return mod != null && mod.getWorld() != null && satisfied(mod.getWorld().getBlockState(target), feature);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof MazeFeaturePlaceTask t && t.target.equals(target) && t.feature.equals(feature);
    }

    @Override
    protected String toDebugString() {
        return "Feature " + feature + " at " + target.toShortString();
    }
}
