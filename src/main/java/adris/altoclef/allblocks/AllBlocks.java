package adris.altoclef.allblocks;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ostinato: {@code #allblocks} / {@code @allblocks}. Entry points shared by the TenorClef command and the Ostinato
 * command (through {@link AllBlocksBridge}), plus the layout.
 * <p>
 * Layout, relative to the origin O (the block the player stands in at the first start, saved with the progress):
 * <ul>
 *   <li>Chest k at O + (2·(k mod 16), 0, 3·(k div 16)): single chests in rows of 16, a gap between chests so they never
 *   join into double chests. Chest k holds the 27 items with plan index 27k..27k+26.</li>
 *   <li>Its label sign at chest + (0, 0, 1): "ALLBLOCKS k", the first and the last item id it holds.</li>
 *   <li>Grid cell n at O + (2·(n mod 32), 0, 12 + 2·(n div 32)): one block per cell, a gap between cells. Each cell
 *   gets a floor block under it (any solid block; dirt, sand, soul sand or end stone when the block needs it).</li>
 * </ul>
 */
public final class AllBlocks {

    public static final int CHESTS_PER_ROW = 16;
    public static final int CELLS_PER_ROW = 32;
    public static final int GRID_Z = 12;

    private static AllBlocksTask current;

    private AllBlocks() {}

    public static BlockPos chestPos(BlockPos o, int k) {
        return o.add(2 * (k % CHESTS_PER_ROW), 0, 3 * (k / CHESTS_PER_ROW));
    }

    public static BlockPos signPos(BlockPos o, int k) {
        return chestPos(o, k).add(0, 0, 1);
    }

    public static BlockPos cellPos(BlockPos o, int n) {
        return o.add(2 * (n % CELLS_PER_ROW), 0, GRID_Z + 2 * (n / CELLS_PER_ROW));
    }

    /**
     * Picks the origin when none is given: the closest spot (8-block steps up to 64 blocks around the player) whose
     * chest rows and first 8 grid rows (64 wide, 28 deep) are loaded, dry, open (at most 6 sampled tree columns) and
     * within 6 blocks of height, sampled every
     * 4 blocks. Lakes and rivers are the usual trouble: a chest in water cannot be reached. Later grid rows are not
     * checked (they are often not loaded yet); a cell that turns out to be in water is skipped. The origin is one
     * block above the most common ground height. Null when there is none.
     */
    static BlockPos pickOrigin(AltoClef mod, int gridRows) {
        BlockPos feet = mod.getPlayer().getBlockPos();
        int width = 2 * CELLS_PER_ROW, depth = GRID_Z + 2 * Math.min(gridRows, 8);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -64; dx <= 64; dx += 8) {
            for (int dz = -64; dz <= 64; dz += 8) {
                double dist = dx * dx + dz * dz;
                if (dist >= bestDist) continue;
                Integer y = footprintY(mod, feet.getX() + dx, feet.getZ() + dz, width, depth);
                if (y == null) continue;
                best = new BlockPos(feet.getX() + dx, y, feet.getZ() + dz);
                bestDist = dist;
            }
        }
        if (best == null) {
            Debug.logMessage("ALLBLOCKS no dry flat site within 64 blocks (rejected: " + siteRejects[0] + " not loaded, "
                    + siteRejects[1] + " water, " + siteRejects[2] + " uneven, " + siteRejects[3] + " forest)");
            java.util.Arrays.fill(siteRejects, 0);
            return null;
        }
        Debug.logMessage("ALLBLOCKS site " + best.toShortString() + " (dry, flat), " + (int) Math.sqrt(bestDist) + " blocks from the player");
        return best;
    }

    private static final int[] siteRejects = new int[4];

    private static boolean isTree(BlockState s) {
        String id = Registries.BLOCK.getId(s.getBlock()).getPath();
        return id.endsWith("_log") || id.endsWith("_leaves") || id.endsWith("_wood") || id.endsWith("_stem")
                || id.contains("mushroom_block") || id.contains("vine");
    }

    /** Most common ground height + 1 over the footprint, or null when it is wet, too uneven or not loaded. */
    private static Integer footprintY(AltoClef mod, int x0, int z0, int width, int depth) {
        java.util.Map<Integer, Integer> heights = new java.util.HashMap<>();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, trees = 0, cmin = Integer.MAX_VALUE, cmax = Integer.MIN_VALUE;
        for (int x = x0; x <= x0 + width; x += 4) {
            for (int z = z0 - 2; z <= z0 + depth; z += 4) {
                BlockPos column = new BlockPos(x, 0, z);
                if (!mod.getChunkTracker().isChunkLoaded(column)) {
                    siteRejects[0]++;
                    return null;
                }
                int top = mod.getWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                // Look through tree trunks (they would make every forest "uneven"), but count them: under a canopy it
                // stays dark enough for mobs by day, and the first run in a taiga died at its chest every evening.
                int before = top;
                while (top > 0 && isTree(mod.getWorld().getBlockState(new BlockPos(x, top - 1, z)))) top--;
                if (top != before && ++trees > 6) {
                    siteRejects[3]++;
                    return null;
                }
                BlockPos ground = new BlockPos(x, top - 1, z);
                if (!mod.getWorld().getBlockState(ground).getFluidState().isEmpty()
                        || !mod.getWorld().getBlockState(ground.up()).getFluidState().isEmpty()) {
                    siteRejects[1]++;
                    return null;
                }
                min = Math.min(min, top);
                max = Math.max(max, top);
                // The chest rows must be truly flat (a sunken chest in a hillside could not be reached); the grid
                // may roll a little.
                if (z < z0 + GRID_Z && x <= x0 + 2 * (CHESTS_PER_ROW - 1)) {
                    cmin = Math.min(cmin, top);
                    cmax = Math.max(cmax, top);
                    if (cmax - cmin > 2) {
                        siteRejects[2]++;
                        return null;
                    }
                }
                if (max - min > 6) {
                    siteRejects[2]++;
                    return null;
                }
                heights.merge(top, 1, Integer::sum);
            }
        }
        return heights.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(null);
    }

    static String[] labelLines(int chest, Map<String, Integer> chestOf) {
        String first = null, last = null;
        for (Map.Entry<String, Integer> e : chestOf.entrySet()) {
            if (e.getValue() != chest) continue;
            if (first == null) first = e.getKey();
            last = e.getKey();
        }
        return new String[]{"ALLBLOCKS " + chest, first == null ? "" : first, "to", last == null ? "" : last};
    }

    /** "sp-&lt;world name&gt;" in singleplayer, "mp-&lt;address&gt;" on a server. */
    static String worldKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getCurrentServerEntry() != null) return "mp-" + mc.getCurrentServerEntry().address;
        if (mc.getServer() != null) return "sp-" + mc.getServer().getSaveProperties().getLevelName();
        return "unknown";
    }

    // ---------------------------------------------------------------- commands

    public static String start(String args) {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || !AltoClef.inGame()) return "allblocks: not in a world";
        BlockPos origin = null;
        boolean retry = false;
        String[] p = args == null ? new String[0] : args.trim().split("\\s+");
        List<String> nums = new ArrayList<>();
        for (String s : p) {
            if (s.isEmpty()) continue;
            if (s.equalsIgnoreCase("retry")) retry = true;
            else nums.add(s);
        }
        if (nums.size() == 3) {
            try {
                origin = new BlockPos(Integer.parseInt(nums.get(0)), Integer.parseInt(nums.get(1)), Integer.parseInt(nums.get(2)));
            } catch (NumberFormatException e) {
                return "allblocks: start [x y z] [retry]";
            }
        } else if (!nums.isEmpty()) {
            return "allblocks: start [x y z] [retry]";
        }
        current = new AllBlocksTask(origin, retry);
        AllBlocksTask task = current;
        mod.runUserTask(task, () -> {
            if (current == task && task.isFinished()) Debug.logMessage("ALLBLOCKS finished");
        });
        return "allblocks: started" + (origin != null ? " at " + origin.toShortString() : "") + (retry ? " (retrying stuck items)" : "")
                + ". Progress: altoclef/allblocks/" + worldKey() + ".json";
    }

    public static String stop() {
        AltoClef mod = AltoClef.getInstance();
        if (mod != null && current != null && current.isActive()) {
            String status = current.statusLine();
            mod.stopTasks();
            Debug.logMessage("ALLBLOCKS stopped. " + status);
            return "allblocks: stopped (progress kept; start resumes)";
        }
        return "allblocks: not running";
    }

    /** Walks to the chest-room and grid views and holds each for 10 seconds (for screenshots). */
    public static String tour() {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || !AltoClef.inGame()) return "allblocks: not in a world";
        if (running()) return "allblocks: stop the run first";
        AllBlocksProgress p = loadSaved();
        if (p == null || p.origin == null) return "allblocks: no saved origin for " + worldKey();
        int chests = 0;
        for (int k : p.chestOf.values()) chests = Math.max(chests, k + 1);
        int cells = Math.max(p.nextCell, 1);
        mod.runUserTask(new AllBlocksTourTask(new BlockPos(p.origin[0], p.origin[1], p.origin[2]), Math.min(chests, 64), cells));
        return "allblocks: touring the chest room and the grid";
    }

    public static boolean running() {
        return current != null && current.isActive() && !current.isFinished();
    }

    public static String status() {
        if (current != null && current.progress() != null && current.isActive()) return current.statusLine();
        AllBlocksProgress p = loadSaved();
        if (p == null) return "allblocks: no progress saved for " + worldKey();
        return summarize(AllBlocksPlan.build(), p, "not running");
    }

    public static String report(int max) {
        AllBlocksProgress p = current != null && current.progress() != null ? current.progress() : loadSaved();
        List<AllBlocksPlan.Entry> plan = current != null && current.plan() != null ? current.plan() : AllBlocksPlan.build();
        List<String> lines = reportLines(plan, p);
        StringBuilder sb = new StringBuilder("allblocks report (" + lines.size() + " entries):");
        for (int i = 0; i < Math.min(max, lines.size()); i++) sb.append("\n").append(lines.get(i));
        if (lines.size() > max) sb.append("\n... ").append(lines.size() - max).append(" more in the progress file");
        return sb.toString();
    }

    public static String reset() {
        if (running()) return "allblocks: stop the run first";
        try {
            Path f = AllBlocksProgress.file(MinecraftClient.getInstance().runDirectory.toPath(), worldKey());
            Files.deleteIfExists(f);
            return "allblocks: progress for " + worldKey() + " deleted (chests and grid stay in the world)";
        } catch (Exception e) {
            return "allblocks: reset failed: " + e;
        }
    }

    private static AllBlocksProgress loadSaved() {
        Path f = AllBlocksProgress.file(MinecraftClient.getInstance().runDirectory.toPath(), worldKey());
        return Files.exists(f) ? AllBlocksProgress.load(f, worldKey()) : null;
    }

    // ---------------------------------------------------------------- summaries

    static String summarize(List<AllBlocksPlan.Entry> plan, AllBlocksProgress p, String now) {
        int candidates = 0, excluded = 0, unsupported = 0, stuck = 0, placeable = 0, placeableUnsupported = 0;
        for (AllBlocksPlan.Entry e : plan) {
            if (!e.candidate()) {
                excluded++;
                continue;
            }
            candidates++;
            boolean unsup = e.unsupportedReason() != null;
            if (e.placeable()) {
                placeable++;
                if (unsup) placeableUnsupported++;
            }
            if (p.stored.containsKey(e.id)) continue;
            if (unsup) unsupported++;
            else if (p.failures.containsKey(e.id) && p.failures.get(e.id).stuck) stuck++;
        }
        int obtained = p.stored.size();
        int remaining = candidates - obtained - unsupported - stuck;
        return "allblocks: obtained " + obtained + "/" + candidates
                + " | placed " + p.placed.size() + "/" + placeable
                + " | skipped " + (excluded + p.placeSkipped.size()) + " (" + excluded + " items by rule, " + p.placeSkipped.size() + " blocks)"
                + " | unsupported " + unsupported + " | stuck " + stuck
                + " | remaining " + remaining
                + (now != null ? " | now: " + now : "");
    }

    /** One line per entry that will not complete on its own: rule skips, unsupported, stuck, placement skips. */
    static List<String> reportLines(List<AllBlocksPlan.Entry> plan, AllBlocksProgress p) {
        List<String> stuck = new ArrayList<>(), unsupported = new ArrayList<>(), placeSkip = new ArrayList<>(), rule = new ArrayList<>();
        for (AllBlocksPlan.Entry e : plan) {
            if (!e.candidate()) {
                rule.add("SKIP " + e.id + ": " + e.excluded);
                continue;
            }
            if (p != null && p.stored.containsKey(e.id)) {
                if (p.placeSkipped.containsKey(e.id)) placeSkip.add("PLACE-SKIP " + e.id + ": " + p.placeSkipped.get(e.id));
                continue;
            }
            String why = e.unsupportedReason();
            if (why != null) {
                unsupported.add("UNSUPPORTED " + e.id + ": " + why);
            } else if (p != null && p.failures.containsKey(e.id) && p.failures.get(e.id).stuck) {
                stuck.add("STUCK " + e.id + ": " + p.failures.get(e.id).last);
            }
        }
        List<String> all = new ArrayList<>(stuck);
        all.addAll(placeSkip);
        all.addAll(unsupported);
        all.addAll(rule);
        return all;
    }
}
