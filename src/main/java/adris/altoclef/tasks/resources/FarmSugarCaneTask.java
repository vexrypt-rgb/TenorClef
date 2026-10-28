package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.DoToClosestBlockTask;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.Set;

/**
 * Ostinato: sugar cane as a farm, not a mine. The old catalogue entry mined whole stalks (base included), so every patch
 * was gone after one visit and each maze room hunted a new one 60-70 blocks out (paper skipped in run86/88).
 * <ul>
 *     <li>Harvest only the block above a base (everything above drops with it); the stalk regrows.</li>
 *     <li>A lone cane is only taken when no tall one is loaded, and its spot is owed a replant.</li>
 *     <li>While harvesting, plant up to a few canes per visit (owed replants first, then new spots beside water) while a
 *     tall cane nearby can pay each one back, so the patch grows every visit instead of shrinking.</li>
 * </ul>
 */
public class FarmSugarCaneTask extends ResourceTask {

    private static final Block[] GROUND = {Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT,
            Blocks.MYCELIUM, Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.SAND, Blocks.RED_SAND, Blocks.SUSPICIOUS_SAND};
    // Ostinato: cane this bot planted this session (shared across tasks, so a later ask knows the farm size).
    private static final Set<BlockPos> FARM = new HashSet<>();
    private static final int FARM_MAX = 16, PER_VISIT = 3, NEAR = 16, PLANT_RADIUS = 12;

    private final int count;
    private final Set<BlockPos> replant = new HashSet<>();
    private int farmOwed;
    private BlockPos plantSpot;
    private int plantTicks, scanCooldown;

    public FarmSugarCaneTask(int count) {
        super(Items.SUGAR_CANE, count);
        this.count = count;
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
        farmOwed = Math.max(0, Math.min(PER_VISIT, FARM_MAX - FARM.size()));
        plantSpot = null;
        scanCooldown = 0;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        // Ostinato: planting is interleaved with the harvest (a crafting parent drops this task the tick the ask is met,
        // so a "farm after the ask" phase would never run inside the paper chain).
        if (farmOwed > 0 && held(mod) > 0) {
            Task f = farmStep(mod);
            if (f != null) return f;
        }
        return super.onTick();
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        // Harvest the block above a base: the base stays and regrows.
        if (mod.getBlockScanner().anyFound(FarmSugarCaneTask::harvestable, Blocks.SUGAR_CANE)) {
            setDebugState("Harvesting sugar cane above the base");
            return new DoToClosestBlockTask(DestroyBlockTask::new, FarmSugarCaneTask::harvestable, Blocks.SUGAR_CANE);
        }
        // Only lone canes loaded: take one (bootstrap) and owe its spot a replant from spare cane.
        if (mod.getBlockScanner().anyFound(Blocks.SUGAR_CANE)) {
            setDebugState("Taking a lone sugar cane (spot owed a replant)");
            return new DoToClosestBlockTask(p -> {
                replant.add(p.toImmutable());
                return new DestroyBlockTask(p);
            }, Blocks.SUGAR_CANE);
        }
        setDebugState("Searching for sugar cane");
        return new TimeoutWanderTask();
    }

    /** One plant action, or null to keep harvesting this tick. */
    private Task farmStep(AltoClef mod) {
        if (plantSpot != null) {
            Block at = mod.getWorld().getBlockState(plantSpot).getBlock();
            if (at == Blocks.SUGAR_CANE) {
                FARM.add(plantSpot);
                replant.remove(plantSpot);
                farmOwed--;
                System.out.println("MAZETEST FARM cane planted " + plantSpot.toShortString() + " farm=" + FARM.size());
                plantSpot = null;
                return null;
            }
            if (at != Blocks.AIR || !plantable(mod, plantSpot.down()) || ++plantTicks > 400) {
                replant.remove(plantSpot);
                plantSpot = null;
                return null;
            }
        } else {
            if (scanCooldown-- > 0) return null;
            scanCooldown = 40;
            replant.removeIf(p -> !mod.getWorld().getBlockState(p).isAir() || !plantable(mod, p.down()));
            if (!replant.isEmpty()) plantSpot = replant.iterator().next();
            else {
                // A new spot spends one cane: only when a tall cane close by pays it back, so the ask never recedes.
                BlockPos me = mod.getPlayer().getBlockPos();
                boolean payback = mod.getBlockScanner().anyFound(p -> harvestable(p) && p.getSquaredDistance(me) < NEAR * NEAR, Blocks.SUGAR_CANE);
                if (!payback && held(mod) <= count) return null;
                plantSpot = findSpot(mod);
            }
            plantTicks = 0;
            if (plantSpot == null) return null;
        }
        setDebugState("Planting sugar cane (farm " + FARM.size() + "/" + FARM_MAX + ")");
        return new InteractWithBlockTask(new ItemTarget(Items.SUGAR_CANE, 1), Direction.UP, plantSpot.down(), true);
    }

    private static int held(AltoClef mod) {
        return mod.getItemStorage().getItemCount(Items.SUGAR_CANE);
    }

    private static boolean harvestable(BlockPos p) {
        AltoClef mod = AltoClef.getInstance();
        return mod.getWorld().getBlockState(p).getBlock() == Blocks.SUGAR_CANE
                && mod.getWorld().getBlockState(p.down()).getBlock() == Blocks.SUGAR_CANE
                && mod.getWorld().getBlockState(p.down(2)).getBlock() != Blocks.SUGAR_CANE;
    }

    /** Ground that takes sugar cane: a valid block with water beside it (vanilla SugarCaneBlock.canPlaceAt). */
    private static boolean plantable(AltoClef mod, BlockPos ground) {
        Block g = mod.getWorld().getBlockState(ground).getBlock();
        boolean ok = false;
        for (Block b : GROUND) if (b == g) { ok = true; break; }
        if (!ok) return false;
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos w = ground.offset(d);
            if (mod.getWorld().getBlockState(w).getBlock() == Blocks.WATER || mod.getWorld().getBlockState(w).getBlock() == Blocks.FROSTED_ICE) return true;
        }
        return false;
    }

    private static BlockPos findSpot(AltoClef mod) {
        BlockPos c = mod.getPlayer().getBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -PLANT_RADIUS; dx <= PLANT_RADIUS; dx++) {
            for (int dz = -PLANT_RADIUS; dz <= PLANT_RADIUS; dz++) {
                for (int dy = -4; dy <= 3; dy++) {
                    BlockPos spot = c.add(dx, dy, dz);
                    if (!mod.getWorld().getBlockState(spot).isAir() || !plantable(mod, spot.down())) continue;
                    double d = spot.getSquaredDistance(c);
                    if (d < bestD) { bestD = d; best = spot.toImmutable(); }
                }
            }
        }
        return best;
    }

    /** Cane plants this bot has planted this session (for harness logs). */
    public static int farmSize() {
        return FARM.size();
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof FarmSugarCaneTask t && t.count == count;
    }

    @Override
    protected String toDebugStringName() {
        return "Farming " + count + " sugar cane";
    }
}
