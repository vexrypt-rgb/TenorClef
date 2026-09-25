package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * S177 — finish and light a RUINED PORTAL instead of building one from scratch.
 *
 * <p>A ruined portal is a real obsidian frame with a few blocks missing. Filling the gaps
 * costs a handful of cobblestone and one flint-and-steel; it needs no lava lake, no water
 * bucket, no casting, and no vertical travel. The bucket build — dig down to lava, cast,
 * climb back up — is what produces the {@code S165L} deep-shaft fallback and its tax.
 *
 * <p>This exists because the spawn gate accepts seeds ON the ruined portal and the driver
 * then ignored it. {@code portal()} only looked for {@code Blocks.NETHER_PORTAL} (a
 * completed, lit portal), and a ruined frame's block id is OBSIDIAN, so it was invisible.
 * Runs T, U and X were all accepted as {@code village=false rp=true} at rpDist 33-64 and
 * all went digging for lava instead.
 *
 * <p>Design notes:
 * <ul>
 *   <li><b>The frame orientation is discovered, not assumed.</b> Ruined portals generate on
 *       either the X or Z axis, and a hardcoded frame would place blocks in the wrong plane.
 *       We read the actual obsidian ring around the lowest obsidian block and walk it.</li>
 *   <li><b>Only missing blocks are placed.</b> Re-placing obsidian that is already there is
 *       wasted ticks and can fail (the block is not replaceable).</li>
 *   <li><b>Interior must be clearable, corners are optional.</b> A functional 1.16 portal
 *       needs the 10-block ring plus a clear 2x3 interior; the four corners are cosmetic.</li>
 *   <li><b>Gives up cleanly.</b> If the frame cannot be walked within a budget, the caller
 *       falls through to the bucket build — this task must never become a stall.</li>
 * </ul>
 */
public class RuinedPortalFinishTask extends Task {

    /** How long to spend finishing a frame before handing back to the ordinary build. */
    private static final int MAX_TICKS = 20 * 60;

    private final BlockPos seed;

    private int ticks;
    private boolean done;
    private String why = "-";

    /** The two frame planes, and the ring of offsets we need in each. */
    private BlockPos frameOrigin;
    private boolean alongX;

    public RuinedPortalFinishTask(BlockPos seed) {
        this.seed = seed.toImmutable();
    }

    /** True once this instance has proven it cannot finish the frame. */
    public boolean gaveUp() {
        return done && !finished;
    }

    private boolean finished;

    @Override
    protected void onStart() {
        ticks = 0;
        done = false;
        finished = false;
        frameOrigin = null;
        AltoClef mod = AltoClef.getInstance();
        if (mod != null) {
            T2Log.force("S177", "finishing ruined portal near " + seed.getX() + ","
                    + seed.getY() + "," + seed.getZ() + " " + describe(mod));
        }
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getPlayer() == null || mod.getWorld() == null) {
            why = "no-world";
            done = true;
            return null;
        }
        if (++ticks >= MAX_TICKS) {
            why = "timeout " + (MAX_TICKS / 20) + "s";
            done = true;
            T2Log.warn("S177", "ruined portal unfinished after " + (MAX_TICKS / 20)
                    + "s (" + why + ") — falling back to the bucket build");
            return null;
        }

        // Already lit by someone else (or we lit it a moment ago): nothing to do.
        if (mod.getBlockScanner().anyFound(Blocks.NETHER_PORTAL)) {
            why = "portal exists";
            finished = true;
            done = true;
            return null;
        }

        // We need a flint and steel (or a fire charge) before the frame matters at all.
        if (!hasIgniter(mod)) {
            if (count(mod, Items.FLINT) >= 1) {
                setDebugState("crafting flint and steel for ruined portal");
                return TaskCatalogue.getItemTask(Items.FLINT_AND_STEEL, 1);
            }
            why = "no igniter";
            done = true;
            T2Log.warn("S177", "no flint and steel available — falling back to the bucket build");
            return null;
        }

        // Locate the frame once. Recomputing every tick re-paths and thrash-reads the world.
        if (frameOrigin == null && !findFrame(mod)) {
            why = "no usable frame";
            done = true;
            T2Log.warn("S177", "obsidian at " + seed.getX() + "," + seed.getY() + ","
                    + seed.getZ() + " is not a usable portal frame — falling back");
            return null;
        }

        // Walk to the frame if we are not near it yet.
        if (mod.getPlayer().getBlockPos().getSquaredDistance(frameOrigin) > 25) {
            setDebugState("walking to ruined portal");
            return new GetToBlockTask(frameOrigin, false);
        }

        List<BlockPos> missing = missingFrameBlocks(mod);
        if (!missing.isEmpty()) {
            BlockPos gap = missing.get(0);
            if (count(mod, Items.COBBLESTONE) < 1 && !hasPlaceable(mod)) {
                setDebugState("need cobblestone for the gap at " + shortPos(gap));
                return TaskCatalogue.getItemTask(Items.COBBLESTONE, 4);
            }
            setDebugState("filling " + shortPos(gap) + " (" + missing.size() + " left)");
            return new PlaceBlockTask(gap, Blocks.COBBLESTONE, Blocks.OBSIDIAN,
                    Blocks.NETHERRACK, Blocks.STONE, Blocks.DIRT);
        }

        // Frame complete — light it. The ignition point is the bottom-inside block.
        BlockPos ignite = interiorBottom(mod);
        if (ignite == null) {
            why = "no interior";
            done = true;
            T2Log.warn("S177", "frame has no clear interior to ignite");
            return null;
        }
        setDebugState("igniting ruined portal");
        return new InteractWithBlockTask(
                new ItemTarget(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE),
                Direction.UP, ignite, false);
    }

    /**
     * Find the frame plane from the seed obsidian. Ruined portals are exactly two blocks
     * wide inside, so from any frame block we can test which horizontal axis carries the
     * matching obsidian and adopt that plane.
     */
    private boolean findFrame(AltoClef mod) {
        // Start from the LOWEST obsidian in the neighbourhood: the bottom frame row is the
        // most reliably generated part of a ruined portal.
        BlockPos best = null;
        try {
            BlockPos p = seed;
            for (BlockPos c : BlockPos.iterate(p.add(-4, -3, -4), p.add(4, 3, 4))) {
                if (!isFrame(mod, c)) continue;
                if (best == null || c.getY() < best.getY()
                        || (c.getY() == best.getY() && c.getX() + c.getZ() < best.getX() + best.getZ())) {
                    best = c.toImmutable();
                }
            }
        } catch (Throwable ignored) {}
        if (best == null) best = seed;

        // Probe both orientations for a plausible 4-wide bottom row.
        for (boolean xAxis : new boolean[] {true, false}) {
            if (shapeScore(mod, best, xAxis) >= 6) {
                frameOrigin = best;
                alongX = xAxis;
                return true;
            }
        }
        // A genuine ruined portal always keeps a contiguous run of frame blocks, so require
        // at least 4 before adopting. Accepting 3 was wrong: a loot chest's obsidian item
        // dropped on the ground, a leftover bucket-cast block, or a natural 3-block blob all
        // score 3, and adopting one burns a give-up slot (only 2 are allowed) before the
        // driver falls back to the bucket build it should have gone straight to.
        int need = 4;
        int sx = shapeScore(mod, best, true);
        int sz = shapeScore(mod, best, false);
        if (Math.max(sx, sz) >= need) {
            frameOrigin = best;
            alongX = sx >= sz;
            return true;
        }
        return false;
    }

    /**
     * How many of the frame positions a full portal would need are already present.
     * A usable frame scores 10; a two-block stub scores ~2-3.
     */
    private int shapeScore(AltoClef mod, BlockPos origin, boolean xAxis) {
        int n = 0;
        for (BlockPos off : frameOffsets(xAxis)) {
            if (isFrame(mod, origin.add(off))) n++;
        }
        return n;
    }

    /**
     * The 10 frame blocks of a 4x5 portal, relative to the bottom-left frame corner.
     * The interior is the 2x3 opening between them.
     */
    private static List<BlockPos> frameOffsets(boolean xAxis) {
        List<BlockPos> out = new ArrayList<>(10);
        for (int dy = 0; dy < 3; dy++) {
            out.add(off(xAxis, 0, dy, 0));          // near side
            out.add(off(xAxis, 0, dy, 3));          // far side
        }
        out.add(off(xAxis, 0, 3, 1));               // top
        out.add(off(xAxis, 0, 3, 2));
        out.add(off(xAxis, 0, -1, 1));              // bottom
        out.add(off(xAxis, 0, -1, 2));
        return out;
    }

    /** Portal "width" runs along X, depth is 3 in X (one solid + 2 opening + one solid). */
    private static BlockPos off(boolean xAxis, int dx, int dy, int dz) {
        return xAxis ? new BlockPos(dx, dy, dz) : new BlockPos(dz, dy, dx);
    }

    private List<BlockPos> missingFrameBlocks(AltoClef mod) {
        List<BlockPos> out = new ArrayList<>(2);
        for (BlockPos off : frameOffsets(alongX)) {
            BlockPos p = frameOrigin.add(off);
            if (!isFrame(mod, p) && isReplaceable(mod, p)) {
                out.add(p.toImmutable());
            }
        }
        return out;
    }

    /** The bottom-centre interior block, used as the ignition target. */
    private BlockPos interiorBottom(AltoClef mod) {
        BlockPos c = frameOrigin.add(off(alongX, 0, 0, 1)).toImmutable();
        if (isReplaceable(mod, c)) return c;
        BlockPos c2 = frameOrigin.add(off(alongX, 0, 0, 2)).toImmutable();
        return isReplaceable(mod, c2) ? c2 : null;
    }

    private static boolean isFrame(AltoClef mod, BlockPos p) {
        try {
            var b = mod.getWorld().getBlockState(p).getBlock();
            return b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN
                    || b == Blocks.NETHER_PORTAL;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isReplaceable(AltoClef mod, BlockPos p) {
        try {
            var s = mod.getWorld().getBlockState(p);
            if (s == null || s.isAir()) return true;
            //#if MC >= 12001
            boolean solid = s.blocksMovement();
            //#else
            //$$ boolean solid = s.getMaterial().blocksMovement();
            //#endif
            return !solid || s.getBlock() == Blocks.LAVA || s.getBlock() == Blocks.WATER;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasIgniter(AltoClef mod) {
        return count(mod, Items.FLINT_AND_STEEL) >= 1 || count(mod, Items.FIRE_CHARGE) >= 1;
    }

    private static boolean hasPlaceable(AltoClef mod) {
        return count(mod, Items.COBBLESTONE) + count(mod, Items.DIRT) + count(mod, Items.STONE)
                + count(mod, Items.NETHERRACK) + count(mod, Items.OBSIDIAN) >= 1;
    }

    private static int count(AltoClef mod, net.minecraft.item.Item item) {
        try {
            return mod.getItemStorage().getItemCount(item);
        } catch (Throwable t) {
            return 0;
        }
    }

    private String describe(AltoClef mod) {
        StringBuilder sb = new StringBuilder("obsidian-nearby=");
        int n = 0;
        try {
            for (BlockPos c : BlockPos.iterate(seed.add(-4, -3, -4), seed.add(4, 3, 4))) {
                if (isFrame(mod, c)) n++;
            }
        } catch (Throwable ignored) {}
        sb.append(n).append('/').append(frameOffsets(true).size());
        return sb.toString();
    }

    private static String shortPos(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    @Override
    protected void onStop(Task interruptTask) {
        if (!finished && done) {
            // Route through T2Log too: this is the line that explains why the driver is
            // about to spend minutes on a bucket build, and it has to land in faults.log.
            try {
                T2Log.warn("S177", "gave up on ruined portal " + shortPos(seed) + ": " + why);
            } catch (Throwable ignored) {}
            Debug.logMessage("TESRUN2 S177 gave up: " + why);
        }
    }

    /**
     * Only finish when the task has genuinely resolved. Returning true while still working
     * would hand the child slot back to the driver mid-build (the S157 oscillator shape).
     */
    @Override
    public boolean isFinished() {
        return done;
    }

    /**
     * Never compare equal to a fresh instance while still working: {@code Task.tick()} keeps
     * a FINISHED instance and ticks it forever if the replacement compares equal.
     */
    @Override
    protected boolean isEqual(Task other) {
        if (!(other instanceof RuinedPortalFinishTask t)) return false;
        return t.seed.equals(seed) && done == t.done;
    }

    @Override
    protected String toDebugString() {
        return "T2 ruined-portal " + shortPos(seed) + " " + why;
    }
}
