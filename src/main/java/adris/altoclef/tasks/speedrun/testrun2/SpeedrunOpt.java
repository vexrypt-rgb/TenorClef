package adris.altoclef.tasks.speedrun.testrun2;

/**
 * Single place to tune the modern route.
 * Change numbers here — don't hunt through BeatMinecraftTask.
 */
public final class SpeedrunOpt {

    private SpeedrunOpt() {}

    /** Eyes of ender to craft. Humans use 12. Stock bot often farms extras. */
    public static final int EYES = 12;
    public static final int BLAZE_RODS = 6;
    public static final int PEARLS = 6;

    /** Iron ingots before we stop mining / looting for iron. Pick + bucket + flint leftovers. */
    public static final int IRON = 10; // pick 3 + flint&steel 1 + 2 buckets 6; 8 forced an underground iron trip in PORTAL

    /** Food units (half-shanks). Stock grind is 20–40. */
    public static final int FOOD = 12;

    /** Logs for table + sticks + emergency planks. */
    public static final int LOGS = 4;

    /** Beds for one-cycle. 0 = skip bed grind, shoot dragon. */
    public static final int BEDS = 0;

    /** Never start a diamond-armor task. */
    public static final boolean SKIP_DIAMOND_ARMOR = true;

    /** Skip iron armor pieces. Shield is separate — see GET_SHIELD_EARLY. */
    public static final boolean SKIP_IRON_ARMOR = true;

    /** 1 iron + 6 planks. Craft as soon as we can; blocks arrows and creeper chip. */
    public static final boolean GET_SHIELD_EARLY = true;

    /** Park shield offhand, eat under 7 shanks, water-clutch long falls. */
    public static final boolean SURVIVE_TICK = false;

    /** Stop the run if spawn is ocean with no village/RP/trees. Not a world resetter. */
    public static final boolean ABORT_BAD_SPAWN = true;

    /** One boat if logs already exist. No extra tree grind. */
    public static final boolean WANT_BOAT = false;

    /** Gold helmet only from gold already in the bag. */
    public static final boolean GOLD_PIGLIN_HEAD = true;

    /** Don't let generic combat steal the fortress task. */
    public static final boolean SKIP_COMBAT_IN_NETHER = true;

    /** Prefer chest loot over mining when a chest is within this block range. */
    public static final int LOOT_SCAN_RANGE = 80;

    /** Chests to open before forcing iron. Stops a full village tour. */
    public static final int LOOT_MAX_CHESTS = 3;

    /** Tungsten search budget for overworld travel (ms). Lower = less freeze, more replans. */
    public static final long TUNGSTEN_SEARCH_MS = 1500L;

    /** Give up a tungsten lock that hasn't closed 2 blocks in this many ms. */
    public static final long TUNGSTEN_STALE_MS = 8000L;

    /** If true, never path into water deeper than 1 block; WaterBailTask handles accidents. */
    public static final boolean AVOID_DEEP_WATER = true;

    /**
     * PvP attribute swap: charge cooldown on the fastest item, hit on the
     * highest-damage item the same tick. Extra vs mobs; some multiplayer
     * anticheats flag it.
     */
    public static final boolean ATTRIBUTE_SWAP = false;

    /** Use a mace smash if we have one and are falling far enough. */
    public static final boolean MACE_SMASH = true;
}
