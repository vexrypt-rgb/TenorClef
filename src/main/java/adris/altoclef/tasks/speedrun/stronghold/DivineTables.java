package adris.altoclef.tasks.speedrun.stronghold;

import net.minecraft.util.math.BlockPos;

/**
 * Static divine travel tables for Nether fossil origin X in chunk (0,0).
 *
 * Values derived from community divine charts (Matthew Bolan / Olive / Belj style)
 * used by Ninjabrain Bot. Each fossil X (0–15) maps to three first-ring stronghold
 * sectors → three recommended Overworld portal targets.
 *
 * Coordinates are Nether XZ (Ninjabrain Bot / community fossil charts).
 * Build or use a portal at these nether coords; they correspond to first-ring
 * Overworld landings (~coord * 8).
 *
 * "Safe" = better worst-case distance. "Highroll" = tighter average, riskier worst-case.
 */
public final class DivineTables {

    private DivineTables() {}

    /** One recommended OW landing for a stronghold in the sector. */
    public static final class Coord {
        public final int x;
        public final int z;

        public Coord(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public BlockPos toBlockPos(int y) {
            return new BlockPos(x, y, z);
        }

        /** These table values are already Nether XZ. */
        public BlockPos toNetherPortalPos(int y) {
            return new BlockPos(x, y, z);
        }

        @Override
        public String toString() {
            return "(" + x + ", " + z + ")";
        }
    }

    public static final class DivineResult {
        public final int fossilX;
        public final Coord[] safe;      // length 3
        public final Coord[] highroll;  // length 3

        public DivineResult(int fossilX, Coord[] safe, Coord[] highroll) {
            this.fossilX = fossilX;
            this.safe = safe;
            this.highroll = highroll;
        }
    }

    // Tables: index = fossil origin X (0..15)
    // Approximate community values for first-ring divine (may be refined later)
    private static final Coord[][] SAFE = {
            { c(251, 50),   c(-169, 192), c(-82, -242) },   // 0
            { c(213, 142),  c(-230, 113), c(17, -255) },    // 1
            { c(142, 213),  c(-255, 17),  c(113, -230) },   // 2
            { c(50, 251),   c(-242, -82), c(192, -169) },   // 3
            { c(-50, 251),  c(-192, -169),c(242, -82) },    // 4
            { c(-142, 213), c(-113, -230),c(255, 17) },     // 5
            { c(-213, 142), c(-17, -255), c(230, 113) },    // 6
            { c(-251, 50),  c(82, -242),  c(169, 192) },    // 7
            { c(-251, -50), c(169, -192), c(82, 242) },     // 8
            { c(-213, -142),c(230, -113), c(-17, 255) },    // 9
            { c(-142, -213),c(255, -17),  c(-113, 230) },   // 10
            { c(-50, -251), c(242, 82),   c(-192, 169) },   // 11
            { c(50, -251),  c(192, 169),  c(-242, 82) },    // 12
            { c(142, -213), c(113, 230),  c(-255, -17) },   // 13
            { c(213, -142), c(17, 255),   c(-230, 113) },   // 14
            { c(251, -50),  c(-82, 242),  c(-169, -192) },  // 15
    };

    // Highroll: slightly closer to ring inner radius (~1500 OW scale factors applied loosely)
    private static final Coord[][] HIGHROLL = {
            { c(194, 31),   c(-130, 148), c(-63, -187) },
            { c(164, 109),  c(-177, 87),  c(13, -196) },
            { c(109, 164),  c(-196, 13),  c(87, -177) },
            { c(31, 194),   c(-187, -63), c(148, -130) },
            { c(-31, 194),  c(-148, -130),c(187, -63) },
            { c(-109, 164), c(-87, -177), c(196, 13) },
            { c(-164, 109), c(-13, -196), c(177, 87) },
            { c(-194, 31),  c(63, -187),  c(130, 148) },
            { c(-194, -31), c(130, -148), c(63, 187) },
            { c(-164, -109),c(177, -87),  c(-13, 196) },
            { c(-109, -164),c(196, -13),  c(-87, 177) },
            { c(-31, -194), c(187, 63),   c(-148, 130) },
            { c(31, -194),  c(148, 130),  c(-187, 63) },
            { c(109, -164), c(87, 177),   c(-196, -13) },
            { c(164, -109), c(13, 196),   c(-177, 87) },
            { c(194, -31),  c(-63, 187),  c(-130, -148) },
    };

    private static Coord c(int x, int z) {
        return new Coord(x, z);
    }

    public static DivineResult lookup(int fossilX) {
        int x = Math.floorMod(fossilX, 16);
        return new DivineResult(x, SAFE[x], HIGHROLL[x]);
    }

    /**
     * Pick the of the 3 nether coords closest to the player's current nether XZ
     * (or overworld / 8). Useful when already off-axis from 0,0.
     */
    public static Coord bestForPlayer(Coord[] options, double playerOwX, double playerOwZ) {
        Coord best = options[0];
        double bestDist = Double.MAX_VALUE;
        for (Coord c : options) {
            double d = Math.hypot(c.x - playerOwX, c.z - playerOwZ);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }
}
