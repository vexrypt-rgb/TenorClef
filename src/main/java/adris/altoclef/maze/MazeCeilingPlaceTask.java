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
import net.minecraft.world.RaycastContext;
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
    private boolean sideStandLogged; // Ostinato
    private final java.util.Set<BlockPos> badStands = new java.util.HashSet<>(); // Ostinato: run58
    private int thereTicks; // Ostinato: run58

    private int walkTicks; private BlockPos walkStand; // Ostinato: run90
    private BlockPos equipLogged; // Ostinato: run89 diagnostic, once per cell

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
        BlockPos feetNow = mod.getPlayer().getBlockPos();
        // Ostinato: measure against the STAND column, not the target column. standUnder may pick a neighbouring
        // room column when the target cell is at head height (see there); measuring against the target then kept
        // the bot walking to a spot it could never stand in.
        double horiz = Math.hypot(eye.x - (stand.getX() + 0.5), eye.z - (stand.getZ() + 0.5));
        boolean sameCol = stand.getX() == target.getX() && stand.getZ() == target.getZ();
        // Feet stay in the stand column: from a neighbour the ray still lands on the target's face, but standing
        // in a third column makes the upward ray hit the wrong cell.
        // Ostinato: run52. feet at stand+1 puts the head inside the target cell, so every place click is rejected.
        boolean there = horiz < 0.75 && (sameCol
                ? feetNow.getY() <= target.getY() - 2 && feetNow.getY() >= stand.getY() - 1
                : feetNow.getY() == stand.getY());
        if (!there) {
            thereTicks = 0;
            release(mod);
            arrived = false;
            // Ostinato: run90. A stand inside a sealed room cannot be pathed to; it was walked at forever. 30s per stand.
            // (walkStand, not going: onStart clears going whenever the task tree is restarted.)
            if (!stand.equals(walkStand)) { walkStand = stand; walkTicks = 0; }
            if (++walkTicks > 600) {
                System.out.println("MAZETEST FURNISH ceiling-unreachable-stand " + stand.toShortString() + " for " + target.toShortString());
                badStands.add(stand); going = null; walkStand = null;
                return stay("New stand for " + target.toShortString());
            }
            if (!stand.equals(going)) {
                going = stand;
                mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(stand.getX(), stand.getY(), stand.getZ()));
            }
            return stay("Walking under ceiling " + target.toShortString());
        }
        // Ostinato: run58. A stand that sees no clickable face / cannot mine for 8s is dropped for the next one.
        if (++thereTicks > 160) {
            System.out.println("MAZETEST FURNISH ceiling-bad-stand " + stand.toShortString() + " for " + target.toShortString());
            badStands.add(stand); thereTicks = 0; going = null;
            return stay("New stand for " + target.toShortString());
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
            look(mod, visiblePoint(mod, target));
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
        Item[] placeItems = ItemHelper.blocksToItems(toPlace);
        // Ostinato: run89. 17 second-pass ceilings sat on "No ceiling block in hand" while the blocks were held. The plain
        // equip refuses while FoodChain.needsToEat(); the block is in reach and one click away, so equip it regardless.
        if (!mod.getSlotHandler().forceEquipItem(placeItems, true)) {
            if (equipLogged == null || !equipLogged.equals(target)) {
                equipLogged = target;
                StringBuilder held = new StringBuilder();
                for (Item it : placeItems) held.append(it).append('=').append(mod.getItemStorage().getItemCount(it)).append(' ');
                System.out.println("MAZETEST FURNISH ceiling-equip-fail " + target.toShortString() + " want=" + held
                        + "eat=" + mod.getFoodChain().needsToEat() + " hunger=" + mod.getPlayer().getHungerManager().getFoodLevel()
                        + " screen=" + mod.getItemStorage().getSlotsWithItemScreen(placeItems).size()
                        + " gridOrCursor=" + adris.altoclef.util.helpers.StorageHelper.isItemInaccessibleToContainer(mod, new adris.altoclef.util.ItemTarget(placeItems, 1)));
            }
            // Held but not equippable: hasItem also counts the crafting grid / cursor, the equip only the inventory.
            adris.altoclef.util.ItemTarget stuck = new adris.altoclef.util.ItemTarget(placeItems, 1);
            if (adris.altoclef.util.helpers.StorageHelper.isItemInaccessibleToContainer(mod, stuck)) {
                return new adris.altoclef.tasks.slot.MoveInaccessibleItemToInventoryTask(stuck);
            }
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
        if (actTicks > 40) thereTicks = Math.min(thereTicks, 120); // Ostinato: clicking a seen face counts as progress
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

    /**
     * Feet position where the player can stand and still see the target, with the target OUTSIDE the player's body.
     * Ostinato: BlockItem.canPlace passes the placing player's shape, so a cell the player occupies is never filled.
     * Standing in the target's own column with the target at head height is exactly that, which made every
     * floor-level ceiling insert and every wall insert over room air time out (run42: 20 skip-light why=timeout).
     */
    private BlockPos standUnder(AltoClef mod) {
        // Same column, but the head must stay under the target: feet <= target - 2.
        for (int y = target.getY() - 2; y >= target.getY() - 6 && y > mod.getWorld().getBottomY(); y--) {
            BlockPos feet = new BlockPos(target.getX(), y, target.getZ());
            if (standSees(mod, feet)) return feet; // Ostinato: run63
        }
        // Ostinato: a 2-high room (floor right under the hole) or a column already clad below has no such spot.
        // Stand in a neighbouring room column instead; clickFace still lands on the target's face from there.
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos c = target.offset(d);
            for (int y = target.getY() - 1; y >= target.getY() - 4 && y > mod.getWorld().getBottomY(); y--) {
                BlockPos feet = new BlockPos(c.getX(), y, c.getZ());
                if (standSees(mod, feet)) { // Ostinato: run63
                    if (!sideStandLogged) {
                        sideStandLogged = true;
                        System.out.println("MAZETEST FURNISH ceiling-side-stand " + target.toShortString() + " from " + feet.toShortString());
                    }
                    return feet;
                }
            }
        }
        // Ostinato: run58. Wall and corner cells (104,86,-85 / 107,87,-88) have solid shell under them and on most sides,
        // so neither search above finds a spot. Any standable room cell within reach that sees the target works: the
        // nearest one to the player whose eye has a clear line to the target or to a solid neighbour's face.
        BlockPos feetNow = mod.getPlayer().getBlockPos();
        BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = -5; dy <= 1; dy++) { // Ostinato: run63 wider ring
            BlockPos feet = target.add(dx, dy, dz);
            if (feet.equals(target) || feet.up().equals(target) || badStands.contains(feet)) continue;
            if (!(support(mod, feet.down()) && open(mod, feet) && open(mod, feet.up()))) continue;
            Vec3d eye = new Vec3d(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
            if (eye.distanceTo(Vec3d.ofCenter(target)) > REACH - 0.3) continue;
            if (!seesTargetOrFace(mod, eye)) continue;
            double d = feet.getSquaredDistance(feetNow);
            if (d < bestD) { bestD = d; best = feet; }
        }
        if (best != null) {
            if (!sideStandLogged) { sideStandLogged = true; System.out.println("MAZETEST FURNISH ceiling-ring-stand " + target.toShortString() + " from " + best.toShortString()); }
            return best;
        }
        if (!badStands.isEmpty()) { badStands.clear(); }
        return new BlockPos(target.getX(), target.getY() - 2, target.getZ());
    }

    // Ostinato: run63 FAIL light 132/133. Corner wall hole -152,80,13 (cladding above, below and beside it, only its
    // room-side face open) got stands in the 2-high nook under the wall cladding (-152,77,12..14): standable, but the
    // plank at -152,79,13 hid the hole, so every pick was a bad stand and the ring search that would have found the
    // room-floor spot -151,79,13 was never reached. A stand now counts only when its eye, within reach, can raycast
    // the target or a solid neighbour's face, so wall/corner holes fall through to a room stand that sees the opening.
    private boolean standSees(AltoClef mod, BlockPos feet) {
        if (badStands.contains(feet) || feet.equals(target) || feet.up().equals(target)) return false;
        if (!(support(mod, feet.down()) && open(mod, feet) && open(mod, feet.up()))) return false;
        Vec3d eye = new Vec3d(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
        return eye.distanceTo(Vec3d.ofCenter(target)) <= REACH - 0.3 && seesTargetOrFace(mod, eye);
    }

    // Ostinato: run58. True when this eye can raycast the target block itself (to mine it) or a solid neighbour's face (to place).
    private boolean seesTargetOrFace(AltoClef mod, Vec3d eye) {
        BlockState st = mod.getWorld().getBlockState(target);
        if (!st.isAir()) {
            Vec3d c = Vec3d.ofCenter(target);
            for (Direction d : Direction.values()) if (rayHitsBlock(mod, eye, c.add(d.getOffsetX() * 0.45, d.getOffsetY() * 0.45, d.getOffsetZ() * 0.45), target)) return true;
            return false;
        }
        for (Direction fromTarget : Direction.values()) {
            BlockPos against = target.offset(fromTarget);
            if (!support(mod, against)) continue;
            Direction face = fromTarget.getOpposite();
            Vec3d hit = facePoint(against, face, 0, 0);
            if (eye.distanceTo(hit) <= REACH && rayHitsFace(mod, eye, hit, against, face)) return true;
        }
        return false;
    }

    private static boolean open(AltoClef mod, BlockPos p) {
        BlockState s = mod.getWorld().getBlockState(p);
        return s.isAir() || s.getCollisionShape(mod.getWorld(), p).isEmpty();
    }

    private static boolean support(AltoClef mod, BlockPos p) {
        BlockState s = mod.getWorld().getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(mod.getWorld(), p).isEmpty();
    }

    /** A face of a solid neighbour that this eye can actually raycast. The underside is preferred only when it is visible. */
    private Click clickFace(AltoClef mod) {
        Vec3d eye = mod.getPlayer().getEyePos();
        Click fallback = null;
        // UP = the block above the hole (its down face). Sides next. DOWN = the block under the hole.
        for (Direction fromTarget : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
            BlockPos against = target.offset(fromTarget);
            if (!support(mod, against)) continue;
            Direction face = fromTarget.getOpposite();
            for (double ou : new double[]{0.0, -0.22, 0.22}) {
                for (double ov : new double[]{0.0, -0.22, 0.22}) {
                    Vec3d hit = facePoint(against, face, ou, ov);
                    if (eye.distanceTo(hit) > REACH) continue;
                    Click c = new Click(against, face, hit);
                    if (fallback == null) fallback = c;
                    if (rayHitsFace(mod, eye, hit, against, face)) return c;
                }
            }
        }
        return fallback;
    }

    // Ostinato: run52. The center of a dirt cell is often hidden from a side stand, so the click never landed.
    private Vec3d visiblePoint(AltoClef mod, BlockPos pos) {
        Vec3d eye = mod.getPlayer().getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        if (rayHitsBlock(mod, eye, center, pos)) return center;
        for (Direction d : Direction.values()) {
            Vec3d p = center.add(d.getOffsetX() * 0.4, d.getOffsetY() * 0.4, d.getOffsetZ() * 0.4);
            if (rayHitsBlock(mod, eye, p, pos)) return p;
        }
        return center;
    }

    private static Vec3d facePoint(BlockPos against, Direction face, double ou, double ov) {
        Vec3d center = Vec3d.ofCenter(against);
        double fx = face.getOffsetX();
        double fy = face.getOffsetY();
        double fz = face.getOffsetZ();
        // Two axes on the face, so the sample is inset from the edges and not on a shared corner.
        double ax;
        double ay;
        double az;
        double bx;
        double by;
        double bz;
        if (fy != 0) {
            ax = 1; ay = 0; az = 0; bx = 0; by = 0; bz = 1;
        } else if (fx != 0) {
            ax = 0; ay = 1; az = 0; bx = 0; by = 0; bz = 1;
        } else {
            ax = 1; ay = 0; az = 0; bx = 0; by = 1; bz = 0;
        }
        return center.add(fx * 0.48, fy * 0.48, fz * 0.48).add(ax * ou, ay * ou, az * ou).add(bx * ov, by * ov, bz * ov);
    }

    private static boolean rayHitsFace(AltoClef mod, Vec3d eye, Vec3d hit, BlockPos against, Direction face) {
        HitResult hr = mod.getWorld().raycast(new RaycastContext(eye, hit, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mod.getPlayer()));
        return hr instanceof BlockHitResult bhr && bhr.getBlockPos().equals(against) && bhr.getSide() == face;
    }

    private static boolean rayHitsBlock(AltoClef mod, Vec3d eye, Vec3d hit, BlockPos pos) {
        HitResult hr = mod.getWorld().raycast(new RaycastContext(eye, hit, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mod.getPlayer()));
        return hr instanceof BlockHitResult bhr && bhr.getBlockPos().equals(pos);
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

    public BlockPos target() {
        return target;
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
