// Ostinato: interior ceiling cladding. Baritone's builder breaks the shell, then wanders,
// because the only clickable face left is the underside of the block above the hole.
// Stand on the floor under the cell, mine it if it is the wrong block, sneak-click that underside.
package adris.altoclef.maze;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.construction.DestroyBlockTask;
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
import java.util.function.Predicate;

public class MazeCeilingPlaceTask extends Task {

    // Ostinato: 2026-11-02. Vanilla survival reach. The old 4.4-with-centre check rejected the floor of a 6-high room.
    private static final double REACH = MazeReach.SURVIVAL;
    // Ostinato: 2026-11-02. Copper first. McCompat keeps a version without copper tools off this list.
    private static final Item[] PICKS = picks();

    private static Item[] picks() {
        java.util.List<Item> out = new java.util.ArrayList<>();
        for (String id : new String[]{"copper_pickaxe", "iron_pickaxe", "diamond_pickaxe", "netherite_pickaxe", "stone_pickaxe"}) {
            Item it = adris.altoclef.tasks.speedrun.testrun2.McCompat.item(id);
            if (it != null && !out.contains(it)) out.add(it);
        }
        return out.toArray(new Item[0]);
    }

    private final BlockPos target;
    private final Block[] toPlace;
    private BlockPos going;
    private boolean logged;
    // Ostinato: 2026-10-30. `logged` is set by the "was=<block>" line on the very first tick, so every later
    // `if (!logged)` guard in this task was dead: ceiling-need-pick could never print (0 hits in run106 while
    // 32 ceiling-bad-stand fired). The pickaxe give-up is the one diagnostic that explains a silent stall, so
    // it gets its own flag.
    private boolean needPickLogged;
    private boolean arrived;
    private int actTicks;
    private int liveTicks;
    private boolean sideStandLogged; // Ostinato
    // Ostinato: run118 room3 seal 67/72. badStands lived on the task instance and the no-stand fallback cleared it, so the
    // ceiling seals -17,85,4 / -17,85,5 picked stand -17,83,x, dropped it on no-progress, then picked it again until the
    // furnish bridge skipped the seal why=stuck. A rejected stand now stays rejected for that cell for the session, the
    // next candidate is tried, and when every ground stand is gone the bot pillars up to a scaffold stand (pillarStand).
    private static final java.util.Map<BlockPos, java.util.Set<BlockPos>> REJECTED = new java.util.HashMap<>();
    private final java.util.Set<BlockPos> badStands;
    private BlockPos pillarLogged; // Ostinato: run118

    // Ostinato: run138 lobby. Ceiling y=92 cells took ring stands 4-5 blocks off (-49,92,-11 for -47,92,-8) that the
    // flood fill called walkable but the bot never reached: each burned 12s as no-progress, then the next far stand, then
    // skip-seal SEAL-LEFT-OPEN. Once a cell has lost a stand to no-progress/timeout it escalates: pillar under/near the
    // cell first, ring stands only when no pillar column works. Kept per cell like REJECTED.
    private static final java.util.Set<BlockPos> ESCALATED = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    static synchronized java.util.Set<BlockPos> rejectedFor(BlockPos t) {
        if (REJECTED.size() > 4096) REJECTED.clear();
        return REJECTED.computeIfAbsent(t.toImmutable(), k -> java.util.Collections.synchronizedSet(new java.util.HashSet<>()));
    }
    private int thereTicks; // Ostinato: run58

    private int walkTicks; private BlockPos walkStand; // Ostinato: run90
    private double walkBest; private int walkIdle; // Ostinato: run97 walk progress toward the stand
    private BlockPos equipLogged; // Ostinato: run89 diagnostic, once per cell
    private int refusedClicks; private boolean nudging; // Ostinato: run93 refused place clicks / re-centering on the stand
    private final Predicate<BlockPos> stray; private BlockPos clearing; private int clears; // Ostinato: run100
    private boolean pocketMine; // Ostinato: run141. clearing is the lid or the column occluder, not a stray

    public MazeCeilingPlaceTask(BlockPos target, Block[] toPlace) {
        this(target, toPlace, p -> false);
    }

    public MazeCeilingPlaceTask(BlockPos target, Block[] toPlace, Predicate<BlockPos> stray) {
        this.target = target;
        this.toPlace = toPlace;
        this.stray = stray;
        this.thin = thin(toPlace);
        this.badStands = rejectedFor(target); // Ostinato: run118
    }

    // Ostinato: run113. A torch/lantern/candle cell: the click goes on the block under it, then a side; a torch never
    // takes a ceiling underside, and the cell is satisfied by the item in any form (torch or wall_torch).
    private final boolean thin;

    static boolean thin(Block[] blocks) {
        for (Block b : blocks) {
            String p = net.minecraft.registry.Registries.BLOCK.getId(b).getPath();
            if (p.contains("torch") || p.contains("lantern") || p.contains("candle") || p.equals("end_rod")) return true;
        }
        return false;
    }

    private Direction[] faceOrder() {
        return thin ? new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}
                : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
    }

    @Override
    protected void onStart() {
        actTicks = 0;
        going = null;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        // Ostinato: 2026-10-30. Every line below reads the world, and TaskRunner.tick() only checks
        // AltoClef.inGame() (player + network handler), not the world. isFinished() already guards the world;
        // onTick did not, so a tick that landed between setWorld(null) and the player being cleared would throw
        // out of the ClientTickEvent subscriber and out of taskRunner.tick(). Same guard, same answer as
        // isFinished(): do nothing this tick.
        if (mod == null || mod.getWorld() == null || mod.getPlayer() == null) {
            setDebugState("No world");
            return null;
        }
        if (placed(mod)) {
            stopNudge(mod);
            release(mod);
            return null;
        }
        if (mod.getItemStorage().getItemCount(ItemHelper.blocksToItems(toPlace)) <= 0) {
            setDebugState("No ceiling block in inventory");
            stopNudge(mod);
            release(mod);
            return null;
        }
        mod.getClientBaritone().getBuilderProcess().onLostControl();
        if (clearing != null) { // Ostinato: run100, mining scaffolding that hides the target (see strayInTheWay)
            // Ostinato: run141. A pocket opener (coal lid, or the cobble the old stand walked into) is not a stray.
            // The next tick used to drop it and clear badStands, so the same solid stand was walked at again.
            if (!support(mod, clearing)) { clearing = null; pocketMine = false; going = null; walkStand = null; }
            else if (stray.test(clearing) || pocketMine) { stopNudge(mod); release(mod); setDebugState("Clearing scaffolding " + clearing.toShortString()); return new DestroyBlockTask(clearing); }
            else { clearing = null; badStands.clear(); going = null; walkStand = null; }
        }
        // Ostinato: run141 mono_yellow seals. Furnish cladding boxed the bot in at -25,85,5 (Baritone open set 0, feet
        // unchanged for 67 seals): every stand was no-progress and every seal SEAL-LEFT-OPEN. Dig out toward the stand.
        if (exitDig != null) {
            if (!mod.getWorld().getBlockState(exitDig).isAir()) { stopNudge(mod); release(mod); setDebugState("Digging out " + exitDig.toShortString()); return new DestroyBlockTask(exitDig); }
            exitDig = null; going = null; walkStand = null;
        }
        BlockPos stand = standUnder(mod);
        if (clearing != null) { stopNudge(mod); release(mod); return new DestroyBlockTask(clearing); }
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
            thereTicks = 0; refusedClicks = 0;
            stopNudge(mod);
            release(mod);
            arrived = false;
            // Ostinato: run90. A stand inside a sealed room cannot be pathed to; it was walked at forever. 30s per stand.
            // (walkStand, not going: onStart clears going whenever the task tree is restarted.)
            if (!stand.equals(walkStand)) { walkStand = stand; walkTicks = 0; walkBest = Double.MAX_VALUE; walkIdle = 0; }
            // Ostinato: run97. -21,95,-20 and -19,95,-20 (pipes room) never reached their stands, and the 30s cap tied the
            // furnish stuck-defer (also 30s), so no other stand was tried before the defer. A walk that gains less than
            // half a block in 12s drops the stand now, leaving time for the side and ring stands.
            double dist = Math.sqrt(mod.getPlayer().getPos().squaredDistanceTo(Vec3d.ofBottomCenter(stand)));
            if (dist < walkBest - 0.5) { walkBest = dist; walkIdle = 0; } else walkIdle++;
            if (walkIdle > 60 && exitDigs < 12) {
                walkable(mod, stand); // refreshes `reach` from the feet
                if (reach != null && reach.size() <= 3) {
                    BlockPos e = boxExit(mod, stand);
                    if (e != null) {
                        exitDigs++; exitDig = e; walkIdle = 0; walkTicks = 0; going = null;
                        System.out.println("MAZETEST FURNISH boxed-in-dig " + e.toShortString() + " toward " + stand.toShortString() + " reach=" + reach.size()
                                + " was=" + net.minecraft.registry.Registries.BLOCK.getId(mod.getWorld().getBlockState(e).getBlock()).getPath());
                        return new DestroyBlockTask(e);
                    }
                }
            }
            if (++walkTicks > 600 || walkIdle > 240) {
                System.out.println("MAZETEST FURNISH ceiling-unreachable-stand " + stand.toShortString() + " for " + target.toShortString()
                        + (walkIdle > 240 ? " no-progress dist=" + String.format("%.1f", dist) : ""));
                badStands.add(stand); going = null; walkStand = null;
                if (ESCALATED.add(target.toImmutable())) System.out.println("MAZETEST FURNISH ceiling-stand-too-far " + target.toShortString() + " stand=" + stand.toShortString() + " escalate=pillar"); // Ostinato: run138
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
            badStands.add(stand); thereTicks = 0; going = null; refusedClicks = 0;
            stopNudge(mod);
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
            // Hunger used to make forceEquip return false, so a pick in the inventory still logged "No pickaxe" and never swung.
            if (!mod.getItemStorage().hasItem(PICKS)) {
                mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
                if (!needPickLogged) {
                    needPickLogged = true;
                    System.out.println("MAZETEST FURNISH ceiling-need-pick " + target.toShortString()
                            + " held=" + mod.getItemStorage().getItemCount(PICKS));
                }
                // Ostinato: 2026-11-02. Copper when this version has it. The string id keeps older lines compiling.
                String ask = adris.altoclef.TaskCatalogue.taskExists("copper_pickaxe") ? "copper_pickaxe" : "stone_pickaxe";
                return adris.altoclef.TaskCatalogue.getItemTask(ask, 1);
            }
            if (!mod.getSlotHandler().forceEquipItem(PICKS, true)) {
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
        // Ostinato: run93. From the side stand -88,73,-150 the bot clicked -87,74,-150 for 30s and nothing was placed. "there"
        // allows 0.75 off the stand centre, so the body can straddle into the target column, and BlockItem.canPlace refuses
        // a block that would intersect the placer. Sneak back to the stand centre before clicking.
        if (mod.getPlayer().getBoundingBox().intersects(new net.minecraft.util.math.Box(target))) {
            look(mod, new Vec3d(stand.getX() + 0.5, eye.y, stand.getZ() + 0.5));
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
            mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
            nudging = true;
            return stay("Centering on stand for " + target.toShortString());
        }
        stopNudge(mod);
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
        ActionResult res = MinecraftClient.getInstance().interactionManager.interactBlock(mod.getPlayer(), Hand.MAIN_HAND, bhr);
        if (res == ActionResult.SUCCESS) {
            mod.getPlayer().swingHand(Hand.MAIN_HAND);
            refusedClicks = 0;
        } else if (++refusedClicks == 10) {
            // Ostinato: run93. A stand whose clicks are all refused used to count as progress forever (only the outer
            // furnish timeout skipped the cell). Log why once, then let thereTicks drop this stand for the next one.
            System.out.println("MAZETEST FURNISH ceiling-place-refused " + target.toShortString() + " stand=" + stand.toShortString()
                    + " res=" + res + " against=" + bhr.getBlockPos().toShortString() + " " + bhr.getSide()
                    + " player=" + String.format("%.2f,%.2f,%.2f", mod.getPlayer().getX(), mod.getPlayer().getY(), mod.getPlayer().getZ()));
        }
        if (actTicks > 40 && refusedClicks < 10) thereTicks = Math.min(thereTicks, 120); // Ostinato: clicking a seen face counts as progress
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
        Block b = mod.getWorld().getBlockState(target).getBlock();
        if (ArrayUtils.contains(toPlace, b)) return true;
        return thin && b != net.minecraft.block.Blocks.AIR && ArrayUtils.contains(ItemHelper.blocksToItems(toPlace), b.asItem());
    }

    /**
     * Feet position where the player can stand and still see the target, with the target OUTSIDE the player's body.
     * Ostinato: BlockItem.canPlace passes the placing player's shape, so a cell the player occupies is never filled.
     * Standing in the target's own column with the target at head height is exactly that, which made every
     * floor-level ceiling insert and every wall insert over room air time out (run42: 20 skip-light why=timeout).
     */
    private BlockPos standUnder(AltoClef mod) {
        // Ostinato: run96. The column and side searches only look below the target, which is right for a ceiling cell
        // (room air under it, solid above). For a floor cell they found pits under the floor (9,88,-151 got stand
        // 10,86,-151, 9,88,-150 got 9,87,-149): unreachable, 30s each, then skip-light. Walls and floors take the ring.
        boolean ceiling = open(mod, target.down()) && !open(mod, target.up());
        // Ostinato: run138. Escalated cell: pillar-under-cell before any further ring/far stand.
        if (ESCALATED.contains(target)) {
            BlockPos pillar = pillarStand(mod);
            if (pillar != null) {
                if (!pillar.equals(pillarLogged)) { pillarLogged = pillar; System.out.println("MAZETEST FURNISH ceiling-pillar-stand " + target.toShortString() + " from " + pillar.toShortString() + " rejected=" + badStands.size() + " (escalated)"); }
                return pillar;
            }
            // Ostinato: run140 yellow_office seal -35,85,-9/-8. Escalated, but pillarStand found no clear column (a solid
            // block at y=82 under both cells), so the ring stands it had already failed were walked at again every
            // repair round. Mine a stray (scaffolding) block that closes the pillar column under/next to the target first.
            if (clears < 8) {
                BlockPos s = columnStray(mod);
                if (s != null) {
                    clears++; clearing = s;
                    System.out.println("MAZETEST FURNISH ceiling-clear-column " + s.toShortString() + " for " + target.toShortString()
                            + " was=" + net.minecraft.registry.Registries.BLOCK.getId(mod.getWorld().getBlockState(s).getBlock()).getPath());
                    return mod.getPlayer().getBlockPos();
                }
            }
            if (!target.equals(columnLogged)) { columnLogged = target; System.out.println("MAZETEST FURNISH ceiling-column-diag " + target.toShortString() + " " + columnDiag(mod)); }
        }
        // Same column, but the head must stay under the target: feet <= target - 2.
        // Ostinato: 2026-11-02. Down to the floor of a clear-height-6 room (feet 6 under the ceiling block), not a fixed 7-wide room.
        if (ceiling) for (int y = target.getY() - 2; y >= target.getY() - MazeReach.STAND_DOWN && y > mod.getWorld().getBottomY(); y--) {
            BlockPos feet = new BlockPos(target.getX(), y, target.getZ());
            if (standSees(mod, feet)) return feet; // Ostinato: run63
        }
        // Ostinato: a 2-high room (floor right under the hole) or a column already clad below has no such spot.
        // Stand in a neighbouring room column instead; clickFace still lands on the target's face from there.
        if (ceiling) for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
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
        BlockPos bestR = null; double bestRD = Double.MAX_VALUE; // Ostinato: run117, best stand the bot can walk to
        // Ostinato: 2026-11-02. Horizontal +-4 is the reach, not a room size. 5x5 and 11x11 place from the same local stand.
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = -MazeReach.STAND_DOWN; dy <= 1; dy++) {
            BlockPos feet = target.add(dx, dy, dz);
            // Ostinato: run93. -87,74,-150 got the ring stand -87,75,-150, i.e. standing ON the target: the "there" check
            // wants a same-column stand under the target, so the bot stood on it for 30s and called it unreachable.
            if (feet.equals(target) || feet.up().equals(target) || feet.down().equals(target) || badStands.contains(feet)) continue;
            if (!(support(mod, feet.down()) && open(mod, feet) && open(mod, feet.up()))) continue;
            Vec3d eye = new Vec3d(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
            if (!MazeReach.reaches(eye.x, eye.y, eye.z, target.getX(), target.getY(), target.getZ())) continue;
            if (!seesTargetOrFace(mod, eye)) continue;
            // Ostinato: run96. A stand below the bot's feet is usually a pit or a cave pocket under the floor; prefer
            // stands on the level the bot is working from.
            double d = feet.getSquaredDistance(feetNow) + 9 * Math.max(0, feetNow.getY() - feet.getY());
            // Ostinato: run138. A perch at ceiling height (feet >= target-1) beside a ceiling cell is a wall top / ledge the
            // bot rarely reaches; rank it behind floor stands, and favour stands whose eye is close to the cell.
            if (ceiling && feet.getY() > target.getY() - 2) d += 64;
            d += 4 * eye.squaredDistanceTo(Vec3d.ofCenter(target));
            if (d < bestD) { bestD = d; best = feet; }
            if (d < bestRD && walkable(mod, feet)) { bestRD = d; bestR = feet; }
        }
        if (bestR != null) best = bestR;
        // Ostinato: run120 room mono_yellow. Light -36,88,-5 / seal -37,84,-4 (7-high room edge) had no walk-reachable ring
        // stand, so the ring returned unwalkable perches on the shell (-34,85,-4, -35,86,-3 ...): each burned 12s as
        // no-progress, the pillar fallback below was never reached, and 3 repair rounds ended in FAIL. When no ring stand is
        // walkable, pillar up from the floor first; an unwalkable perch is only the last resort.
        else if (best != null) {
            BlockPos pillar = pillarStand(mod);
            if (pillar != null) {
                if (!pillar.equals(pillarLogged)) { pillarLogged = pillar; System.out.println("MAZETEST FURNISH ceiling-pillar-stand " + target.toShortString() + " from " + pillar.toShortString() + " rejected=" + badStands.size() + " (ring unwalkable)"); }
                return pillar;
            }
        }
        else if (best != null && !unwalkLogged) { unwalkLogged = true; System.out.println("MAZETEST FURNISH ceiling-ring-unwalkable " + target.toShortString() + " best=" + best.toShortString() + " reach=" + (reach == null ? 0 : reach.size())); }
        if (best != null) {
            if (!sideStandLogged) { sideStandLogged = true; System.out.println("MAZETEST FURNISH ceiling-ring-stand " + target.toShortString() + " from " + best.toShortString()); }
            return best;
        }
        // Ostinato: run100. Room 2's light cell -50,93,107 had only its room-side face open, and the pillar dirt left at
        // -51,92,107 / -51,93,107 covered it: no stand saw it, the fallback stand under it was unreachable, the cell was
        // skipped and the room failed verify. A solid block in a carved cell the plan does not fill is scaffolding, so
        // mine the one in the way and search again.
        if (clears < 8) {
            BlockPos s = strayInTheWay(mod);
            if (s != null) {
                clears++; clearing = s;
                System.out.println("MAZETEST FURNISH ceiling-clear-stray " + s.toShortString() + " for " + target.toShortString()
                        + " was=" + net.minecraft.registry.Registries.BLOCK.getId(mod.getWorld().getBlockState(s).getBlock()).getPath());
                return feetNow;
            }
        }
        // Ostinato: run118. No ground stand left: scaffold up. A walk-reachable floor cell near the target whose clear
        // column, k blocks up, puts the eye in reach of the target or a face. Baritone pillars to that air goal with
        // throwaway blocks (MovementPillar). Each pillar top that fails is rejected like any stand, so the next is tried.
        BlockPos pillar = pillarStand(mod);
        if (pillar != null) {
            if (!pillar.equals(pillarLogged)) {
                pillarLogged = pillar;
                System.out.println("MAZETEST FURNISH ceiling-pillar-stand " + target.toShortString() + " from " + pillar.toShortString()
                        + " rejected=" + badStands.size());
            }
            return pillar;
        }
        // Ostinato: run118. The old fallback cleared badStands here, which put the same unreachable stand back on top.
        BlockPos blind = new BlockPos(target.getX(), target.getY() - 2, target.getZ());
        // Ostinato: run141. Seal -35,94,-22 is air between dirt and coal. blind is the cobble at y=92, which is not a
        // stand, so this walked at a solid four times (no-progress dist=3.5) and skipped. Mine the face already in
        // reach (that cobble), then the dirt under the hole, and place against the coal.
        if (clears < 8 && mod.getWorld().getBlockState(target).isAir()
                && support(mod, target.down()) && support(mod, target.up())
                && !standSees(mod, blind)) {
            BlockPos opener = pocketOpener(mod);
            if (opener != null) {
                clears++;
                clearing = opener;
                pocketMine = true;
                System.out.println("MAZETEST FURNISH ceiling-open-pocket " + opener.toShortString() + " for " + target.toShortString()
                        + " was=" + net.minecraft.registry.Registries.BLOCK.getId(mod.getWorld().getBlockState(opener).getBlock()).getPath());
                return mod.getPlayer().getBlockPos();
            }
        }
        return blind;
    }

    // Ostinato: run141. Lid and side first when the eye can already hit a non-place block; otherwise the nearest
    // hittable solid in this column (the cobble the fallback used to path into).
    private BlockPos pocketOpener(AltoClef mod) {
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        java.util.List<Boolean> column = new java.util.ArrayList<>();
        cells.add(target.up()); column.add(false);
        cells.add(target.down()); column.add(false);
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            cells.add(target.offset(d)); column.add(false);
        }
        int feetY = mod.getPlayer().getBlockPos().getY();
        int lo = Math.min(feetY, target.getY() - 1);
        int hi = Math.max(feetY, target.getY() - 1);
        java.util.List<BlockPos> col = new java.util.ArrayList<>();
        for (int y = lo; y <= hi; y++) {
            if (y == target.getY()) continue;
            col.add(new BlockPos(target.getX(), y, target.getZ()));
        }
        col.sort(java.util.Comparator.comparingInt(p -> Math.abs(p.getY() - feetY)));
        for (BlockPos p : col) { cells.add(p); column.add(true); }
        int n = cells.size();
        boolean[] solid = new boolean[n], place = new boolean[n], hit = new boolean[n], bedrock = new boolean[n], colFlag = new boolean[n];
        for (int i = 0; i < n; i++) {
            BlockPos p = cells.get(i);
            colFlag[i] = column.get(i);
            if (p.equals(target)) continue;
            Block b = mod.getWorld().getBlockState(p).getBlock();
            solid[i] = support(mod, p);
            place[i] = ArrayUtils.contains(toPlace, b);
            bedrock[i] = b == net.minecraft.block.Blocks.BEDROCK;
            hit[i] = solid[i] && canHit(mod, p);
        }
        int pick = MazePocketOpen.choose(solid, place, hit, bedrock, colFlag);
        return pick < 0 ? null : cells.get(pick);
    }

    private boolean canHit(AltoClef mod, BlockPos pos) {
        Vec3d eye = mod.getPlayer().getEyePos();
        Vec3d p = visiblePoint(mod, pos);
        return eye.distanceTo(p) <= REACH && rayHitsBlock(mod, eye, p, pos);
    }

    private BlockPos exitDig; private static int exitDigs; // Ostinato: run141, static: shared across seal cells per session
    // Ostinato: run141. Solid feet/head block beside the bot, sides ordered by how well they face the stand.
    private BlockPos boxExit(AltoClef mod, BlockPos stand) {
        BlockPos f = mod.getPlayer().getBlockPos();
        double vx = stand.getX() - f.getX(), vz = stand.getZ() - f.getZ();
        Direction[] ds = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        java.util.Arrays.sort(ds, java.util.Comparator.comparingDouble(d -> -(d.getOffsetX() * vx + d.getOffsetZ() * vz)));
        for (Direction d : ds) {
            BlockPos n = f.offset(d);
            if (n.equals(target) || n.up().equals(target)) continue;
            if (!open(mod, n.up())) return n.up();
            if (!open(mod, n)) return n;
        }
        return null;
    }

    private BlockPos columnLogged; // Ostinato: run140
    // Ostinato: run140. Nearest stray in the target column (then the 8 around it), between the floor under the bot
    // and the target. Those are the blocks that stop pillarStand from finding a clear column.
    private BlockPos columnStray(AltoClef mod) {
        int floor = mod.getPlayer().getBlockPos().getY();
        BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = floor; y < target.getY(); y++) {
            BlockPos p = new BlockPos(target.getX() + dx, y, target.getZ() + dz);
            if (!stray.test(p)) continue;
            double d = (dx * dx + dz * dz) * 4 + (target.getY() - y);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private String columnDiag(AltoClef mod) {
        StringBuilder sb = new StringBuilder();
        for (int y = mod.getPlayer().getBlockPos().getY(); y <= target.getY() + 1; y++) {
            BlockPos p = new BlockPos(target.getX(), y, target.getZ());
            sb.append(y).append("=").append(net.minecraft.registry.Registries.BLOCK.getId(mod.getWorld().getBlockState(p).getBlock()).getPath())
                    .append(stray.test(p) ? "(stray)" : "").append(" ");
        }
        return sb.toString();
    }

    // Ostinato: run118. Scaffold/pillar fallback for ceiling seal and light cells.
    private BlockPos pillarStand(AltoClef mod) {
        BlockPos feetNow = mod.getPlayer().getBlockPos();
        walkable(mod, feetNow); // fills `reach`
        if (reach == null) return null;
        BlockPos best = null; double bestScore = Double.MAX_VALUE;
        for (BlockPos base : reach) {
            int dx = base.getX() - target.getX(), dz = base.getZ() - target.getZ();
            if (Math.abs(dx) > 3 || Math.abs(dz) > 3 || base.getY() > target.getY() - 2) continue;
            for (int k = 1; k <= 5; k++) {
                BlockPos top = base.up(k);
                if (top.getY() > target.getY() - 1) break;
                if (!(open(mod, top) && open(mod, top.up()))) break; // the column must be clear to jump through
                if (top.equals(target) || top.up().equals(target) || badStands.contains(top)) continue;
                // The player's box at the top must not cover the target cell (BlockItem.canPlace).
                if (dx == 0 && dz == 0 && top.getY() + 2 > target.getY()) continue;
                Vec3d eye = new Vec3d(top.getX() + 0.5, top.getY() + 1.62, top.getZ() + 0.5);
                if (!MazeReach.reaches(eye.x, eye.y, eye.z, target.getX(), target.getY(), target.getZ())) continue;
                if (!seesTargetOrFace(mod, eye)) continue;
                double score = 4.0 * k + Math.hypot(dx, dz) + 0.1 * base.getSquaredDistance(feetNow);
                if (score < bestScore) { bestScore = score; best = top; }
                break; // lowest working height in this column
            }
        }
        return best;
    }

    // Ostinato: run100. The stray block nearest the target that a standable cell's view of it hits first: a ray to the
    // target (when it must be mined) or to a solid neighbour's face (to place against). Failing that, a stray next to a
    // solid target, which covers one of its faces.
    private BlockPos strayInTheWay(AltoClef mod) {
        boolean solidTarget = !mod.getWorld().getBlockState(target).isAir();
        BlockPos best = null; double bestD = Double.MAX_VALUE;
        // Ostinato: 2026-11-02. Same floor depth as the stand ring, so a 6-high room can clear a stray from the floor.
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = -MazeReach.STAND_DOWN; dy <= 1; dy++) {
            BlockPos feet = target.add(dx, dy, dz);
            if (feet.equals(target) || feet.up().equals(target) || feet.down().equals(target)) continue;
            if (!(support(mod, feet.down()) && open(mod, feet) && open(mod, feet.up()))) continue;
            Vec3d eye = new Vec3d(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
            if (!MazeReach.reaches(eye.x, eye.y, eye.z, target.getX(), target.getY(), target.getZ())) continue;
            java.util.List<Vec3d> aims = new java.util.ArrayList<>();
            if (solidTarget) {
                Vec3d c = Vec3d.ofCenter(target);
                for (Direction d : Direction.values()) aims.add(c.add(d.getOffsetX() * 0.45, d.getOffsetY() * 0.45, d.getOffsetZ() * 0.45));
            } else {
                for (Direction fromTarget : Direction.values()) {
                    BlockPos against = target.offset(fromTarget);
                    if (support(mod, against)) aims.add(facePoint(against, fromTarget.getOpposite(), 0, 0));
                }
            }
            for (Vec3d aim : aims) {
                HitResult hr = mod.getWorld().raycast(new RaycastContext(eye, aim, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mod.getPlayer()));
                if (!(hr instanceof BlockHitResult bhr) || bhr.getBlockPos().equals(target) || !stray.test(bhr.getBlockPos())) continue;
                double d = bhr.getBlockPos().getSquaredDistance(target);
                if (d < bestD) { bestD = d; best = bhr.getBlockPos(); }
            }
        }
        if (best == null && solidTarget) for (Direction d : Direction.values()) {
            BlockPos n = target.offset(d);
            if (stray.test(n)) return n;
        }
        return best;
    }

    // Ostinato: run63 FAIL light 132/133. Corner wall hole -152,80,13 (cladding above, below and beside it, only its
    // room-side face open) got stands in the 2-high nook under the wall cladding (-152,77,12..14): standable, but the
    // plank at -152,79,13 hid the hole, so every pick was a bad stand and the ring search that would have found the
    // room-floor spot -151,79,13 was never reached. A stand now counts only when its eye, within reach, can raycast
    // the target or a solid neighbour's face, so wall/corner holes fall through to a room stand that sees the opening.
    private boolean standSees(AltoClef mod, BlockPos feet) {
        if (badStands.contains(feet) || feet.equals(target) || feet.up().equals(target) || feet.down().equals(target)) return false;
        if (!(support(mod, feet.down()) && open(mod, feet) && open(mod, feet.up()))) return false;
        Vec3d eye = new Vec3d(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
        return MazeReach.reaches(eye.x, eye.y, eye.z, target.getX(), target.getY(), target.getZ()) && seesTargetOrFace(mod, eye);
    }

    // Ostinato: run58. True when this eye can raycast the target block itself (to mine it) or a solid neighbour's face (to place).
    private boolean seesTargetOrFace(AltoClef mod, Vec3d eye) {
        BlockState st = mod.getWorld().getBlockState(target);
        if (!st.isAir()) {
            Vec3d c = Vec3d.ofCenter(target);
            // Ostinato: run115 lobby. A ring stand passed on the nearest-corner distance (4.46) while every mining aim point
            // was ~5 away: the crosshair never reached the stone, so each floor stand burned 8s as ceiling-bad-stand and the
            // 453-cell room timed out at 252. The aim point must be in reach, and so must a face to place on once it is mined.
            boolean mineable = false;
            for (Direction d : Direction.values()) {
                Vec3d p = c.add(d.getOffsetX() * 0.45, d.getOffsetY() * 0.45, d.getOffsetZ() * 0.45);
                if (eye.distanceTo(p) <= REACH - 0.05 && rayHitsBlock(mod, eye, p, target)) { mineable = true; break; }
            }
            if (!mineable) return false;
            for (Direction fromTarget : faceOrder()) {
                BlockPos against = target.offset(fromTarget);
                if (support(mod, against) && eye.distanceTo(facePoint(against, fromTarget.getOpposite(), 0, 0)) <= REACH) return true;
            }
            return false;
        }
        for (Direction fromTarget : faceOrder()) {
            BlockPos against = target.offset(fromTarget);
            if (!support(mod, against)) continue;
            Direction face = fromTarget.getOpposite();
            Vec3d hit = facePoint(against, face, 0, 0);
            if (eye.distanceTo(hit) <= REACH && rayHitsFace(mod, eye, hit, against, face)) return true;
        }
        return false;
    }

    // Ostinato: run117 room4. Seal 45,97..98,115 took ring stands 45,98,114 / 45,99,113 the pathfinder could not reach
    // (open set 0 at 605 nodes, no-progress dist=3.8 ten times), both seals were skipped and the room failed verify.
    // The ring prefers stands the bot can walk to now: a flood fill over 2-high standable cells (step up 1, drop up
    // to 3), cached 2s. Stands only Baritone digging could reach stay as the fallback.
    private java.util.Set<BlockPos> reach; private long reachTick = Long.MIN_VALUE / 2; private BlockPos reachFrom; private boolean unwalkLogged;
    private boolean walkable(AltoClef mod, BlockPos feet) {
        long now = mod.getWorld().getTime();
        BlockPos from = mod.getPlayer().getBlockPos();
        if (reach == null || now - reachTick > 40 || !from.equals(reachFrom)) {
            reachTick = now; reachFrom = from; reach = new java.util.HashSet<>();
            java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
            reach.add(from); q.add(from);
            while (!q.isEmpty() && reach.size() < 6000) {
                BlockPos c = q.poll();
                for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    BlockPos n = c.offset(d);
                    if (Math.abs(n.getX() - from.getX()) > 24 || Math.abs(n.getZ() - from.getZ()) > 24) continue;
                    BlockPos up = n.up();
                    if (open(mod, c.up(2)) && standable(mod, up)) { if (reach.add(up)) q.add(up); }
                    if (standable(mod, n)) { if (reach.add(n)) q.add(n); continue; }
                    if (!(open(mod, n) && open(mod, n.up()))) continue;
                    for (int k = 1; k <= 3; k++) {
                        BlockPos f = n.down(k);
                        if (!open(mod, f)) break;
                        if (support(mod, f.down())) { if (reach.add(f)) q.add(f); break; }
                    }
                }
            }
        }
        return reach.contains(feet);
    }

    private static boolean standable(AltoClef mod, BlockPos f) {
        return support(mod, f.down()) && open(mod, f) && open(mod, f.up());
    }

    private static boolean open(AltoClef mod, BlockPos p) {        BlockState s = mod.getWorld().getBlockState(p);
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
        for (Direction fromTarget : faceOrder()) {
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
        // Ostinato: run115. The first visible point could be past reach (crosshair misses); take the nearest visible one.
        Vec3d best = null; double bestD = Double.MAX_VALUE;
        java.util.List<Vec3d> pts = new java.util.ArrayList<>(); pts.add(center);
        for (Direction d : Direction.values()) pts.add(center.add(d.getOffsetX() * 0.45, d.getOffsetY() * 0.45, d.getOffsetZ() * 0.45));
        for (Vec3d p : pts) {
            double dd = eye.distanceTo(p);
            if (dd < bestD && rayHitsBlock(mod, eye, p, pos)) { bestD = dd; best = p; }
        }
        return best != null ? best : center;
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

    private void stopNudge(AltoClef mod) {
        if (!nudging) return;
        nudging = false;
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
        mod.getClientBaritone().getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
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
            stopNudge(mod);
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
