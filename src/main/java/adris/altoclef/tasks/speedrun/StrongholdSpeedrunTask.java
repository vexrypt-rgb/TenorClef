package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.EnterNetherPortalTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Optional;

/**
 * Stronghold phase for modern 1.16 speedruns.
 *
 * Sub-phases:
 * 1. OVERWORLD     – leave Nether if still there
 * 2. CRAFT_EYES    – pearls + blaze powder → eyes of ender
 * 3. LOCATE        – throw eyes / path to stronghold
 * 4. FIND_PORTAL   – search portal room inside stronghold
 * 5. FILL_PORTAL   – place eyes in empty frames
 * 6. ENTER_END     – step into the portal
 *
 * Target: enough eyes to fill portal (up to 12) + a few extras for locating.
 * Pearls come from barter phase; rods from fortress phase.
 */
public class StrongholdSpeedrunTask extends Task {

    private enum SubPhase {
        OVERWORLD,
        CRAFT_EYES,
        LOCATE,
        FIND_PORTAL,
        FILL_PORTAL,
        ENTER_END,
        DONE
    }

    private final AltoClef mod;
    private final int eyesForLocate;   // extras for throwing
    private final int eyesForPortal;   // up to 12 frames

    private SubPhase sub = SubPhase.OVERWORLD;
    private Task active;
    private BlockPos portalCenter = null;
    private int divineFossilX = -1;

    public StrongholdSpeedrunTask(AltoClef mod) {
        this(mod, 3, 12, -1);
    }

    public StrongholdSpeedrunTask(AltoClef mod, int divineFossilX) {
        this(mod, 3, 12, divineFossilX);
    }

    public StrongholdSpeedrunTask(AltoClef mod, int eyesForLocate, int eyesForPortal) {
        this(mod, eyesForLocate, eyesForPortal, -1);
    }

    public StrongholdSpeedrunTask(AltoClef mod, int eyesForLocate, int eyesForPortal, int divineFossilX) {
        this.mod = mod;
        this.eyesForLocate = eyesForLocate;
        this.eyesForPortal = eyesForPortal;
        this.divineFossilX = divineFossilX;
    }

    @Override
    protected void onStart() {
        sub = SubPhase.OVERWORLD;
        active = null;
        portalCenter = null;
        setDebugState("Stronghold – starting");
    }

    @Override
    protected Task onTick() {
        // Already in the End → done
        if (WorldHelper.getCurrentDimension() == Dimension.END) {
            sub = SubPhase.DONE;
            setDebugState("Stronghold – entered The End");
            return null;
        }

        switch (sub) {
            case OVERWORLD:
                return handleOverworld();
            case CRAFT_EYES:
                return handleCraftEyes();
            case LOCATE:
                return handleLocate();
            case FIND_PORTAL:
                return handleFindPortal();
            case FILL_PORTAL:
                return handleFillPortal();
            case ENTER_END:
                return handleEnterEnd();
            default:
                return null;
        }
    }

    // --- Sub-phase handlers ---

    private Task handleOverworld() {
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            if (divineFossilX >= 0 && WorldHelper.getCurrentDimension() == Dimension.NETHER) {
                adris.altoclef.tasks.speedrun.stronghold.DivineTables.DivineResult divine =
                        adris.altoclef.tasks.speedrun.stronghold.DivineTables.lookup(divineFossilX);
                adris.altoclef.tasks.speedrun.stronghold.DivineTables.Coord target =
                        adris.altoclef.tasks.speedrun.stronghold.DivineTables.bestForPlayer(
                                divine.safe, mod.getPlayer().getX(), mod.getPlayer().getZ());
                BlockPos netherPortal = target.toNetherPortalPos(64);
                double distSq = mod.getPlayer().squaredDistanceTo(
                        netherPortal.getX() + 0.5, mod.getPlayer().getY(), netherPortal.getZ() + 0.5);
                if (distSq > 12 * 12) {
                    setDebugState("Stronghold – divine travel to nether " + target);
                    return new GetToBlockTask(netherPortal, false);
                }
            }
            setDebugState("Stronghold – returning to Overworld");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }
        sub = SubPhase.CRAFT_EYES;
        active = null;
        return null;
    }

    private Task handleCraftEyes() {
        int eyes = StorageHelper.getItemCount(mod, Items.ENDER_EYE);
        int need = eyesForLocate + eyesForPortal; // upper bound; we may use fewer

        // Can craft more?
        int pearls = StorageHelper.getItemCount(mod, Items.ENDER_PEARL);
        int powder = StorageHelper.getItemCount(mod, Items.BLAZE_POWDER);
        int rods = StorageHelper.getItemCount(mod, Items.BLAZE_ROD);
        int craftable = Math.min(pearls, powder + rods * 2);

        if (eyes >= eyesForLocate + 4) {
            // Enough to start locating (portal fill can craft/use remaining later)
            sub = SubPhase.LOCATE;
            active = null;
            setDebugState("Stronghold – eyes ready (" + eyes + "), locating");
            return null;
        }

        if (craftable + eyes < 4 && pearls < 4) {
            setDebugState("Stronghold – low on pearls, need more (barter fallback)");
            // Soft dependency: try to get more pearls if somehow short
            return TaskCatalogue.getItemTask(Items.ENDER_PEARL, 8);
        }

        setDebugState("Stronghold – crafting eyes (" + eyes + "/" + need + ")");
        return TaskCatalogue.getItemTask(Items.ENDER_EYE, Math.min(need, eyes + craftable));
    }

    private Task handleLocate() {
        // If we can already see portal frames, skip locate
        Optional<BlockPos> frame = findPortalFrame();
        if (frame.isPresent()) {
            portalCenter = frame.get();
            sub = SubPhase.FIND_PORTAL;
            active = null;
            return null;
        }

        setDebugState("Stronghold – locating stronghold");
        if (active == null || active.isFinished()) {
            active = createLocateTask();
        }

        // Transition: if locate task finished or we found frames while pathing
        if (active != null && active.isFinished()) {
            sub = SubPhase.FIND_PORTAL;
            active = null;
            return null;
        }

        // Opportunistic frame detection while locating
        frame = findPortalFrame();
        if (frame.isPresent()) {
            portalCenter = frame.get();
            sub = SubPhase.FIND_PORTAL;
            active = null;
            return null;
        }

        return active;
    }

    private Task handleFindPortal() {
        Optional<BlockPos> frame = findPortalFrame();
        if (frame.isPresent()) {
            portalCenter = frame.get();
            double distSq = mod.getPlayer().squaredDistanceTo(
                    portalCenter.getX() + 0.5,
                    portalCenter.getY() + 0.5,
                    portalCenter.getZ() + 0.5
            );
            if (distSq > 6 * 6) {
                setDebugState("Stronghold – pathing to portal room");
                return new GetToBlockTask(portalCenter, false);
            }
            sub = SubPhase.FILL_PORTAL;
            active = null;
            return null;
        }

        // Still searching inside stronghold – walk toward stone brick clusters
        Optional<BlockPos> bricks = mod.getBlockScanner().getNearestBlock(
                Blocks.STONE_BRICKS,
                Blocks.MOSSY_STONE_BRICKS,
                Blocks.CRACKED_STONE_BRICKS
        );
        if (bricks.isPresent()) {
            setDebugState("Stronghold – exploring for portal room");
            return new GetToBlockTask(bricks.get(), false);
        }

        setDebugState("Stronghold – portal not found, re-locate");
        sub = SubPhase.LOCATE;
        active = null;
        return null;
    }

    private Task handleFillPortal() {
        // Count empty frames
        Optional<BlockPos> empty = findEmptyPortalFrame();
        if (empty.isEmpty()) {
            // All filled (or no frames) – enter
            setDebugState("Stronghold – portal frames filled");
            sub = SubPhase.ENTER_END;
            active = null;
            return null;
        }

        int eyes = StorageHelper.getItemCount(mod, Items.ENDER_EYE);
        if (eyes < 1) {
            setDebugState("Stronghold – need more eyes to fill frames");
            sub = SubPhase.CRAFT_EYES;
            active = null;
            return null;
        }

        BlockPos framePos = empty.get();
        setDebugState("Stronghold – filling portal frame at " + framePos);

        // Approach then right-click eye into frame
        double distSq = mod.getPlayer().squaredDistanceTo(
                framePos.getX() + 0.5, framePos.getY() + 0.5, framePos.getZ() + 0.5
        );
        if (distSq > 3.5 * 3.5) {
            return new GetToBlockTask(framePos, false);
        }

        // Interact: equip eye and use on frame
        // Uses Altoclef-style interact if available; otherwise path + manual use
        return new FillEndPortalFrameTask(mod, framePos);
    }

    private Task handleEnterEnd() {
        Optional<BlockPos> portal = mod.getBlockScanner().getNearestBlock(Blocks.END_PORTAL);
        if (portal.isPresent()) {
            setDebugState("Stronghold – entering End portal");
            return new GetToBlockTask(portal.get(), false);
        }

        // Portal block not formed yet – recheck frames
        Optional<BlockPos> empty = findEmptyPortalFrame();
        if (empty.isPresent()) {
            sub = SubPhase.FILL_PORTAL;
            return null;
        }

        // Stand on frame center and wait / step in
        if (portalCenter != null) {
            return new GetToBlockTask(portalCenter.up(), false);
        }

        setDebugState("Stronghold – waiting for portal open");
        return null;
    }

    // --- Helpers ---

    private Task createLocateTask() {
        // Ninjabrain-style Bayesian / ray-chunk calculator
        adris.altoclef.tasks.speedrun.stronghold.NinjabrainLocateTask loc =
                new adris.altoclef.tasks.speedrun.stronghold.NinjabrainLocateTask(mod, 2, 0.50);
        if (divineFossilX >= 0) {
            loc.getCalculator().setDivineFossilX(divineFossilX);
        }
        return loc;
    }

    private Optional<BlockPos> findPortalFrame() {
        return mod.getBlockScanner().getNearestBlock(Blocks.END_PORTAL_FRAME);
    }

    private Optional<BlockPos> findEmptyPortalFrame() {
        // Scan known frames – BlockScanner may return nearest only;
        // check EYE property when possible
        Optional<BlockPos> nearest = mod.getBlockScanner().getNearestBlock(Blocks.END_PORTAL_FRAME);
        if (nearest.isEmpty()) return Optional.empty();

        BlockPos pos = nearest.get();
        try {
            var state = mod.getWorld().getBlockState(pos);
            if (state.contains(EndPortalFrameBlock.EYE) && !state.get(EndPortalFrameBlock.EYE)) {
                return Optional.of(pos);
            }
            // If nearest is filled, search a small volume around portal center
            if (portalCenter != null) {
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        BlockPos p = portalCenter.add(dx, 0, dz);
                        var s = mod.getWorld().getBlockState(p);
                        if (s.isOf(Blocks.END_PORTAL_FRAME)
                                && s.contains(EndPortalFrameBlock.EYE)
                                && !s.get(EndPortalFrameBlock.EYE)) {
                            return Optional.of(p);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Property access may differ slightly by mappings – treat nearest as target
            return nearest;
        }
        return Optional.empty();
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof StrongholdSpeedrunTask;
    }

    @Override
    protected String toDebugString() {
        return "StrongholdSpeedrunTask[" + sub + "]";
    }

    @Override
    public boolean isFinished() {
        return sub == SubPhase.DONE
                || WorldHelper.getCurrentDimension() == Dimension.END;
    }
}
