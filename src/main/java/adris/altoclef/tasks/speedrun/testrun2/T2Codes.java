package adris.altoclef.tasks.speedrun.testrun2;

/**
 * Stable codes. Grep the log for {@code T2 [Exxx]} / {@code T2 [Ixx]}.
 *
 *  I0x  heartbeat / phase
 *  E1x  water
 *  E2x  combat
 *  E3x  stall
 *  E4x  closer / fallback
 *  E5x  death
 *  E6x  portal / nether entry
 *  E7x  inventory
 *  E8x  task lifecycle
 *  E9x  movement / craft table
 *  E10x sensor (jump, GUI, starve, wrong child)
 */
public final class T2Codes {

    public static final String I02_PULSE = "I02";
    public static final String I03_PHASE = "I03";
    public static final String I04_CHILD = "I04";

    public static final String E10_WATER = "E10";
    public static final String E102_WATER_STILL = "E102";

    public static final String E20_FIGHT = "E20";
    public static final String E110_PIGLIN_NAKED = "E110";

    public static final String E30_STALL = "E30";
    public static final String E118_CHILD_STALE = "E118";

    public static final String E40_CLOSER = "E40";

    public static final String E50_DEATH = "E50";

    public static final String E60_PORTAL = "E60";
    public static final String E105_LAVA_NO_WATER = "E105";
    public static final String E107_NO_FLINT = "E107";
    public static final String E119_PORTAL_IGNORED = "E119";

    public static final String E80_RESTART = "E80";
    public static final String E106_UNSTICK_CHILD = "E106";
    public static final String E111_NULL_CHILD = "E111";
    public static final String E112_PHASE_FLAP = "E112";

    public static final String E90_CAVE = "E90";
    public static final String E91_TABLE = "E91";
    public static final String E97_WOOD_JUMP = "E97";
    public static final String E98_XZ_FREEZE = "E98";
    public static final String E99_WALKOFF_FAIL = "E99";
    public static final String E100_JUMP_PLACE = "E100";
    public static final String E101_BOOT_FREEZE = "E101";
    public static final String E103_STARVE = "E103";
    public static final String E104_GUI = "E104";
    public static final String E108_TABLE_FEET = "E108";
    public static final String E109_NO_TOOL = "E109";
    public static final String E116_HOLE = "E116";
    public static final String E131_PILLAR_THRASH = "E131";
    public static final String S130_PILLAR = "S130";
    public static final String S131_PILLAR_FAIL = "S131";
    public static final String S132_PILLAR_CLEAR = "S132";
    public static final String S133_PILLAR_COOL_ARM = "S133";
    public static final String S134_PILLAR_COOL_END = "S134";
    public static final String S135_PILLAR_SUPPRESS = "S135";
    public static final String S136_PILLAR_END = "S136";
    public static final String S137_PILLAR_RECOOL = "S137";
    public static final String S138_PILLAR_BAN_CLEAR = "S138";
    public static final String E132_SHAFT_STUCK = "E132";

    private T2Codes() {}

    public static String glossary() {
        return String.join("\n",
                "T2 codes:",
                "  I02 pulse every 30s",
                "  E10  submerged â€” WaterBail once",
                "  E102 standing in water, velocity ~0",
                "  E20  combat overlay",
                "  E110 piglin in range, no gold helmet",
                "  E30  phase stall 2 min no inv change",
                "  E118 same child 90s no inv change",
                "  E40  closer / fallback path",
                "  E50  death recycle",
                "  E60  portal watchdog",
                "  E105 Construct running with lava and no water",
                "  E107 portal phase, no flint / steel",
                "  E119 nether portal exists, phase not PORTAL",
                "  E80  parent onStart ignored",
                "  E106 UnstickWalk is a child (should not happen)",
                "  E111 wanted child is null in a live phase",
                "  E112 phase flipped twice in <2s",
                "  E90  bootstrap underground",
                "  E91  crafting table under feet",
                "  E97  wood punch same XZ",
                "  E98  XZ freeze overlay (bootstrap only)",
                "  E99  walk-off failed 3x",
                "  E100 jumping in place (onGround flip, same XZ)",
                "  E101 bootstrap freeze",
                "  E103 hunger 0 and no food",
                "  E104 inventory/craft GUI open >8s",
                "  E108 table at feet + jumping",
                "  E109 mining with no pick",
                "  E116 fell 8+ blocks in overworld",
                "  E131 HolePillar<->CollectIron thrash at same xz",
                "  E132 shaft stuck re-cool loop",
                "SOLVE S130 pillar-out of 1x1 shaft",
                "SOLVE S131 pillar fail / no rise / no blocks",
                "SOLVE S132 pillar clear (risen enough)",
                "SOLVE S133 pillar failCool armed",
                "SOLVE S134 pillar failCool expired",
                "SOLVE S135 S130 suppressed (cool/busy)",
                "SOLVE S136 HolePillarTask finished (reason)",
                "SOLVE S137 pillar re-cool still boxed at ban xz",
                "SOLVE S138 shaft ban cleared (left xz)",
                "  E132 shaft stuck: re-cool x3+ still boxed",
                "SOLVE S100 stop jump + walk",
                "SOLVE S102 swim forward",
                "SOLVE S103 get bread",
                "SOLVE S104 close GUI",
                "SOLVE S105 get water before lava cast",
                "SOLVE S108 step off table",
                "SOLVE S110 gold helmet vs piglin",
                "SOLVE S111 missing portal child"
        );
    }
}
