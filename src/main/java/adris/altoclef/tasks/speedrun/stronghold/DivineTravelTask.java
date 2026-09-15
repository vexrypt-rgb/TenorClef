package adris.altoclef.tasks.speedrun.stronghold;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Opportunistic divine travel:
 * 1. If in Nether near 0,0, scan chunk (0,0) for fossil
 * 2. Compute divine nether coords from fossil origin X
 * 3. Path to the closest of the 3 nether portal spots
 *
 * Does NOT force a long detour if the player is far from 0,0 and no fossil is loaded.
 */
public class DivineTravelTask extends Task {

    private static final double SCAN_RADIUS = 128;

    private final AltoClef mod;
    private final boolean preferHighroll;

    private DivineFossilDetector.Detection detection;
    private DivineTables.Coord chosen;
    private Task pathTask;
    private boolean scanned = false;

    public DivineTravelTask(AltoClef mod) {
        this(mod, false);
    }

    public DivineTravelTask(AltoClef mod, boolean preferHighroll) {
        this.mod = mod;
        this.preferHighroll = preferHighroll;
    }

    @Override
    protected void onStart() {
        detection = null;
        chosen = null;
        pathTask = null;
        scanned = false;
        setDebugState("Divine – scanning chunk 0,0");
    }

    @Override
    protected Task onTick() {
        if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
            setDebugState("Divine – not in Nether, skip");
            scanned = true;
            return null;
        }

        if (!scanned) {
            double dist00 = Math.hypot(mod.getPlayer().getX(), mod.getPlayer().getZ());
            if (dist00 < SCAN_RADIUS && dist00 > 16) {
                setDebugState("Divine – approaching chunk 0,0 to scan");
                if (pathTask == null || pathTask.isFinished()) {
                    pathTask = new GetToBlockTask(new BlockPos(8, 64, 8), false);
                }
                return pathTask;
            }

            scanned = true;
            pathTask = null;
            Optional<DivineFossilDetector.Detection> det = DivineFossilDetector.detect(mod);
            if (det.isEmpty()) {
                setDebugState("Divine – no fossil in chunk 0,0");
                return null;
            }
            detection = det.get();
            setDebugState("Divine – " + detection);

            DivineTables.Coord[] options = preferHighroll
                    ? detection.result.highroll
                    : detection.result.safe;

            chosen = DivineTables.bestForPlayer(options, mod.getPlayer().getX(), mod.getPlayer().getZ());
            setDebugState("Divine – nether target " + chosen);
        }

        if (chosen == null) {
            return null;
        }

        BlockPos netherTarget = chosen.toNetherPortalPos(64);
        double distSq = mod.getPlayer().squaredDistanceTo(
                netherTarget.getX() + 0.5, mod.getPlayer().getY(), netherTarget.getZ() + 0.5
        );

        if (distSq < 12 * 12) {
            setDebugState("Divine – at portal coords " + chosen);
            return null;
        }

        setDebugState("Divine – pathing to nether " + netherTarget);
        if (pathTask == null || pathTask.isFinished()) {
            pathTask = new GetToBlockTask(netherTarget, false);
        }
        return pathTask;
    }

    public Optional<DivineFossilDetector.Detection> getDetection() {
        return Optional.ofNullable(detection);
    }

    public Optional<DivineTables.Coord> getChosenCoord() {
        return Optional.ofNullable(chosen);
    }

    public boolean hasDivine() {
        return detection != null && chosen != null;
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof DivineTravelTask;
    }

    @Override
    protected String toDebugString() {
        return "DivineTravelTask";
    }

    @Override
    public boolean isFinished() {
        if (!scanned) return false;
        if (detection == null || chosen == null) return true;
        BlockPos n = chosen.toNetherPortalPos(64);
        return mod.getPlayer().squaredDistanceTo(n.getX() + 0.5, mod.getPlayer().getY(), n.getZ() + 0.5) < 12 * 12;
    }
}
