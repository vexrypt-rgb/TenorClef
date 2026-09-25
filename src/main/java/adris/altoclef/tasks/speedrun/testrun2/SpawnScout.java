package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

public final class SpawnScout {

    /**
     * @param villageDist blocks to the nearest village marker, or {@link Double#POSITIVE_INFINITY}
     *                    when none is tracked. Logged raw so the gate radius can be tuned from
     *                    a run log instead of guessed — "unseen" and "too far" must be
     *                    distinguishable or the gate cannot be tuned at all.
     * @param portalDist  same, for ruined-portal obsidian.
     */
    public record Result(String biome, int trees, boolean village, boolean portal, int lava,
                         boolean resetWorthy, double villageDist, double portalDist) {

        /** Compact "what did we actually see" tail for log lines. */
        public String dist() {
            return "vDist=" + fmt(villageDist) + " rpDist=" + fmt(portalDist);
        }

        private static String fmt(double d) {
            return Double.isInfinite(d) ? "unseen" : String.format("%.0f", d);
        }
    }

    private SpawnScout() {}

    /** How close a village / ruined portal must be to count as "at spawn". */
    public static final double SPAWN_LOOT_RADIUS = 160.0;

    /**
     * S169. Radius of the tree census. It used to be 16 (a 33x33 grid scanned every 2
     * blocks), which is small enough to miss an entire forest that starts on the next hill
     * — and a spawn with no wood is unplayable no matter what structure is next to it.
     *
     * Run U is the proof: `spawn ACCEPTED biome=BeachBiome village=false rp=true rpDist=33`
     * passed the gate, and then the heartbeat logged `logs=0` in all 82 samples across
     * 24 minutes while the bot wandered from 110,63,20 to 180,61,-85 with `woodpick=0`.
     * A beach has no trees by definition, and the 33x33 probe agreed with that — but the
     * gate only asked "is a ruined portal within 160 blocks", so a treeless beach with a
     * close portal was accepted as playable. Run P was the same shape (SnowyTundraBiome).
     *
     * 40 blocks (an 81x81 census, ~1.6k lookups instead of ~560) reaches over a beach's
     * dune line and into the forest behind it, so a forest biome is no longer scored as
     * "treeless" just because the trees are slightly uphill. The probe step is raised from
     * 2 to 4 so the wider grid does not cost 4x: a tree trunk is never narrower than a
     * 4-block stride along a plank of the census, and the census is a lower bound only —
     * it is used to REJECT, so it is deliberately generous.
     */
    public static final int TREE_SCAN_RADIUS = 40;
    private static final int TREE_SCAN_STEP = 4;

    /**
     * S169. Biomes where "no tree within {@link #TREE_SCAN_RADIUS} blocks" is expected
     * rather than suspicious. Anywhere else, a zero tree count is assumed to be a scan
     * failure and is not allowed to reject the seed on its own — the structures still can.
     */
    private static final String[] TREELESS_BIOMES = {
            "ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean", "cold_ocean",
            "deep_warm_ocean", "deep_lukewarm_ocean", "deep_cold_ocean", "frozen_ocean",
            "deep_frozen_ocean", "beach", "snowy_beach", "stone_shore", "desert",
            "desert_hills", "desert_lakes", "badlands", "badlands_plateau",
            "eroded_badlands", "wooded_badlands_plateau", "modified_badlands_plateau",
            "snowy_tundra", "snowy_mountains", "ice_spikes", "mountains", "mountain_edge",
            "gravelly_mountains", "modified_gravelly_mountains", "shattered_savanna",
            "snowy_taiga_mountains", "the_end", "the_void", "nether_wastes",
            "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas",
            "mushroom_field_shore", "mushroom_fields",
    };

    /**
     * Reroll budget. The gate depends on the block scanner having seen the spawn area, so
     * it must never be allowed to loop forever — after this many rerolls the bot accepts
     * whatever it got and says so loudly.
     */
    public static final int MAX_REROLLS = 12;

    private static int rerolls = 0;

    public static int rerolls() {
        return rerolls;
    }

    public static void noteReroll() {
        rerolls++;
    }

    public static Result scan(AltoClef mod) {
        Result empty = new Result("?", 0, false, false, 0, false,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        if (mod.getPlayer() == null || mod.getWorld() == null) return empty;
        BlockPos feet = mod.getPlayer().getBlockPos();

        int trees = 0;
        int lava = 0;
        int R = TREE_SCAN_RADIUS;
        try {
            for (int dx = -R; dx <= R; dx += TREE_SCAN_STEP) {
                for (int dz = -R; dz <= R; dz += TREE_SCAN_STEP) {
                    BlockPos p = feet.add(dx, 0, dz);
                    var top = mod.getWorld().getBlockState(p);
                    boolean woodHit = false;
                    for (int dy = 1; dy <= 8 && !woodHit; dy++) {
                        var wood = mod.getWorld().getBlockState(p.up(dy));
                        if (wood.isOf(Blocks.OAK_LOG) || wood.isOf(Blocks.BIRCH_LOG)
                                || wood.isOf(Blocks.SPRUCE_LOG) || wood.isOf(Blocks.JUNGLE_LOG)
                                || wood.isOf(Blocks.ACACIA_LOG) || wood.isOf(Blocks.DARK_OAK_LOG)
                                || isLog(wood, "CHERRY_LOG") || isLog(wood, "MANGROVE_LOG")) {
                            woodHit = true;
                        }
                    }
                    if (woodHit) { trees++; continue; }
                    var wood = mod.getWorld().getBlockState(p.up(3));
                    if (wood.isOf(Blocks.OAK_LOG) || wood.isOf(Blocks.BIRCH_LOG)
                            || wood.isOf(Blocks.SPRUCE_LOG) || wood.isOf(Blocks.JUNGLE_LOG)
                            || wood.isOf(Blocks.ACACIA_LOG) || wood.isOf(Blocks.DARK_OAK_LOG)
                            || isLog(wood, "CHERRY_LOG") || isLog(wood, "MANGROVE_LOG")) {
                        trees++;
                    }
                    if (top.isOf(Blocks.LAVA) || mod.getWorld().getBlockState(p.down()).isOf(Blocks.LAVA)) {
                        lava++;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Distance-bounded, not a bare "anywhere in the tracker" check: a village 600
        // blocks off is not a village spawn. distanceToClosest() returns +Infinity when
        // nothing is tracked, so "<= radius" is false both when absent and when unscanned.
        double villageDist = Double.POSITIVE_INFINITY;
        double portalDist = Double.POSITIVE_INFINITY;
        try {
            villageDist = mod.getBlockScanner().distanceToClosest(
                    Blocks.BELL, Blocks.HAY_BLOCK, Blocks.COMPOSTER);
            portalDist = mod.getBlockScanner().distanceToClosest(
                    Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN);
        } catch (Throwable ignored) {}
        boolean village = villageDist <= SPAWN_LOOT_RADIUS;
        boolean portal = portalDist <= SPAWN_LOOT_RADIUS;

        String biome = "?";
        try {
            biome = McCompat.biomePath(mod.getWorld(), feet);
        } catch (Throwable ignored) {}

        // The run only plays seeds that spawn near a village or a ruined portal. This used
        // to be `ocean && !village && !portal && trees < 3`, which could never fire for a
        // Forest or Plains spawn no matter how bad it was — run N spawned in open water in
        // a Forest, scored "playable", and burned the entire 38-minute budget with 0 items.
        // Biome name is no longer part of the decision; only the structure check is.
        //
        // WOOD IS A SEPARATE REQUIREMENT, and a ruined portal does not satisfy it.
        // Run P proved this: `spawn ACCEPTED biome=SnowyTundraBiome rp=true rpDist=57`
        // passed the gate, then the bot mined 8 iron, could not craft the iron pickaxe
        // (2 sticks, and a snowy tundra has no trees), wandered 160 blocks for logs, and
        // died — `ph=BOOTSTRAP n=0` at 14:38. A VILLAGE does supply wood (its buildings
        // are planks), so a village spawn is accepted on its own. A bare ruined portal in
        // a treeless biome is not playable no matter how close it is.
        //
        // S169: the census is now 81x81 instead of 33x33, so a forest just over a dune
        // line is seen. The biome check exists so the WIDER scan cannot create a new false
        // positive: in a forest biome a zero count means the scan failed (chunk not loaded,
        // trees out of range), which must not reject the seed on its own.
        boolean treeless = censusTreeless(mod, biome, trees);
        boolean reset = !(village || portal) || (treeless && !village);
        String why = !(village || portal) ? "no village / ruined portal in range"
                : "ruined portal but treeless (no wood for sticks)";

        Result r = new Result(biome, trees, village, portal, lava, reset, villageDist, portalDist);
        Debug.logMessage("TESRUN2 spawn biome=" + biome
                + " trees~=" + trees
                + " villageHint=" + village
                + " rpHint=" + portal
                + " surfaceLava=" + lava
                + " " + r.dist()
                + (reset ? "  *** RESET-WORTHY (" + why + ") ***" : "  playable"));
        return r;
    }

    /**
     * S169. Is the spawn short on wood?
     *
     * The census is intentionally one-sided: it exists to REJECT a seed, never to vouch for
     * one. So the rule is "zero trees is only believable in a biome that has none". In a
     * forest biome a zero count is a scan failure (trees outside {@link #TREE_SCAN_RADIUS},
     * or the chunks are not loaded yet) and must not throw the seed away — the structure
     * check still can. In a beach, tundra, ocean or desert, zero really is zero, and a
     * ruined portal next door does not make up for it.
     */
    private static boolean censusTreeless(AltoClef mod, String biome, int trees) {
        if (trees >= 3) return false;
        String b = biome == null ? "" : biome.toLowerCase(java.util.Locale.ROOT);
        for (String t : TREELESS_BIOMES) {
            if (b.contains(t)) return true;
        }
        return false;
    }

    private static boolean isLog(BlockState wood, String name) {
        var b = McCompat.block(name);
        return b != null && wood.isOf(b);
    }
}
