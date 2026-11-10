package adris.altoclef.fugue;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link FugueWorldView} over the live client: inventory counts through the catalogue's item matches, stations and
 * ore through the existing {@code BlockScanner} cache. Reads only; client thread only.
 */
public final class LiveFugueView implements FugueWorldView {

    /** Same reuse radius as {@code CraftInTableTask} / {@code SmeltInFurnaceTask#getCostToMakeNew}. */
    static final double STATION_REUSE_RANGE = 40;

    private static final Block[] LOG_BLOCKS = ItemHelper.itemsToBlocks(ItemHelper.LOG);

    private final AltoClef mod;

    public LiveFugueView(AltoClef mod) {
        this.mod = mod;
    }

    @Override
    public int count(String catalogueKey) {
        if (!TaskCatalogue.taskExists(catalogueKey)) return 0;
        Item[] matches = TaskCatalogue.getItemMatches(catalogueKey);
        if (matches == null || matches.length == 0) return 0;
        // Includes crafting/furnace input slots, so opening a crafting screen does not look like losing the items.
        return mod.getItemStorage().getItemCount(matches);
    }

    @Override
    public boolean stationNearby(String catalogueKey) {
        Block block = stationBlock(catalogueKey);
        if (block == null || mod.getPlayer() == null) return false;
        Optional<BlockPos> nearest = mod.getBlockScanner().getNearestBlock(mod.getPlayer().getPos(), WorldHelper::canReach, block);
        return nearest.isPresent() && nearest.get().isWithinDistance(mod.getPlayer().getPos(), STATION_REUSE_RANGE);
    }

    @Override
    public double distanceToSource(String catalogueKey) {
        Block[] blocks = sourceBlocks(catalogueKey);
        if (blocks == null || mod.getPlayer() == null) return Double.POSITIVE_INFINITY;
        Optional<BlockPos> nearest = mod.getBlockScanner().getNearestBlock(blocks);
        if (nearest.isEmpty()) return Double.POSITIVE_INFINITY;
        BlockPos p = nearest.get();
        double dx = p.getX() + 0.5 - mod.getPlayer().getX();
        double dy = p.getY() + 0.5 - mod.getPlayer().getY();
        double dz = p.getZ() + 0.5 - mod.getPlayer().getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public boolean navigationIdle() {
        return !mod.getClientBaritone().getPathingBehavior().isPathing();
    }

    /**
     * Radius (blocks) and cap of the zone excluded around the bot when the child had to escape water for a target. The
     * failure is the bot's spot, not the block: from a flooded spot every under-water target loops on the water escape,
     * and stock selection picks the nearest one next. Live (f11-smoke-C, C2, C3): excluding the target, its wet
     * neighbours, or all same-type blocks within 6 left the next-nearest resource under the same pond (adjacent stone,
     * then coal ore), and the bot stayed at the same spot for every one. A sphere around the spot (C4) still missed the
     * ores 17+ blocks under the pond. Excluding Fugue's gather resources in the column zone under and around the spot
     * makes the fresh child's nearest target lie outside it, so stock navigation walks the bot out.
     */
    static final int WET_RADIUS = 16;
    /** The zone reaches this far above the spot (a bank or tree beside the pond); below it is unbounded. */
    static final int WET_ABOVE = 8;
    static final int WET_CAP = 128;
    private static final Block[] GATHER_BLOCKS = {Blocks.STONE, Blocks.COAL_ORE, Blocks.IRON_ORE};

    @Override
    public List<String> exclude(String target, boolean wetNeighbours) {
        List<String> out = new ArrayList<>();
        BlockPos pos = parse(target);
        if (pos == null || mod.getWorld() == null || !markUnreachable(pos)) return out;
        out.add(target);
        if (!wetNeighbours || mod.getPlayer() == null) return out;
        BlockPos spot = mod.getPlayer().getBlockPos();
        List<BlockPos> known = new ArrayList<>(mod.getBlockScanner().getKnownLocations(LOG_BLOCKS));
        known.addAll(mod.getBlockScanner().getKnownLocations(GATHER_BLOCKS));
        // nearest first, so the cap keeps the part of the zone the fresh child would pick from
        known.sort(java.util.Comparator.comparingInt(p -> horizontalDistSq(p, spot)));
        int nearby = 0;
        for (BlockPos p : known) {
            if (out.size() >= WET_CAP || horizontalDistSq(p, spot) > WET_RADIUS * WET_RADIUS) break;
            if (p.getY() > spot.getY() + WET_ABOVE) continue;
            nearby++;
            if (markUnreachable(p)) out.add(p.getX() + "," + p.getY() + "," + p.getZ());
        }
        Debug.logMessage("FUGUE exclude " + target + " spot=" + spot.getX() + "," + spot.getY() + "," + spot.getZ()
                + " nearbyKnown=" + nearby + " excludedAround=" + (out.size() - 1));
        return out;
    }

    private static int horizontalDistSq(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private boolean markUnreachable(BlockPos pos) {
        if (mod.getBlockScanner().isUnreachable(pos)) return false;
        mod.getBlockScanner().requestBlockUnreachable(pos, 0);
        return mod.getBlockScanner().isUnreachable(pos);
    }

    @Override
    public void forget(String target) {
        BlockPos pos = parse(target);
        if (pos != null) mod.getBlockScanner().forgetUnreachable(pos);
    }

    /** "x,y,z" as written by {@link FugueTask#targetOf}. */
    static BlockPos parse(String target) {
        if (target == null) return null;
        String[] p = target.split(",");
        if (p.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Block stationBlock(String key) {
        switch (key) {
            case "crafting_table": return Blocks.CRAFTING_TABLE;
            case "furnace": return Blocks.FURNACE;
            default: return null;
        }
    }

    private static Block[] sourceBlocks(String key) {
        switch (key) {
            case "log": return LOG_BLOCKS;
            case "cobblestone": return new Block[]{Blocks.STONE};
            case "coal": return new Block[]{Blocks.COAL_ORE};
            case "raw_iron": return new Block[]{Blocks.IRON_ORE};
            default: return null;
        }
    }
}
