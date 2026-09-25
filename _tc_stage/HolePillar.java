package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/**
 * 1-wide hole + blocks in hotbar -> look down, jump, place under feet.
 * S100 no-jump is the opposite of this and is why the bot hops forever.
 */
public final class HolePillar {

    private static int step;
    private static int lastY = Integer.MIN_VALUE;
    private static int rose;
    private static int failCool;
    private static int startY = Integer.MIN_VALUE;
    private static boolean holding;
    private static String lastEndReason = "-";
    private static String lastArmReason = "-";
    private static boolean coolWasOn;

    // Sustained-rise gate. A +2y hop with a one-tick collar opening is NOT an escape:
    // the block we just placed under our feet clears the collar for exactly that tick.
    // Require headroom above the shaft AND both x/z offsets below to stay clear, for
    // a minimum number of consecutive ticks, before we accept "risen".
    private static int riseStable;
    private static int riseStableAtY = Integer.MIN_VALUE;
    // risenEnough() is consulted by several callers per game tick; memoise per tick.
    private static long riseEvalTick;
    private static long riseLastEvalTick = -1;
    private static boolean riseLastEvalResult;

    // Ping-pong: START/END at the same column without ever leaving it.
    private static int pingX = Integer.MIN_VALUE;
    private static int pingZ;
    private static int pingStartCount;
    private static long pingFirstMs;
    private static int pingLastStartTick = -1;
    private static int pillarCycles;

    // After END, ban re-arming S130 on this xz until the player leaves the column.
    private static int banX = Integer.MIN_VALUE;
    private static int banZ;
    private static int banTicks;
    // S178: the y at which the ban was armed. The ban-clear test below is horizontal-only,
    // so a bot that DESCENDS inside the banned column keeps manh==0 and the ban can never
    // clear early — see the S178 note in T2Codes.
    private static int banY = Integer.MIN_VALUE;
    private static int lastCoolArmed;
    private static int reCoolCount;
    private static boolean sameXzResetNeeded;

    // The column this escape started in. The ban anchors HERE, not at the mid-hop pos.
    private static int shaftX = Integer.MIN_VALUE;
    private static int shaftZ;

    // STICKY COLUMN BAN (S147).
    //
    // The ordinary banX/banZ/banTicks ban is cleared in coolTick() the moment the player
    // reaches manhattan >= 2 from the banned column. Measured on run F: `S138 shaft ban
    // clear ... manh=2` was by far the dominant clear reason, and a pillar escape hops
    // 2 blocks sideways almost immediately. So a ban armed by E133 with banTicks=400 was
    // effectively cleared within a second or two, and S130 re-armed at the SAME column.
    //
    // Result at a handful of columns: -132,210 armed S130 8 times, -85,222 armed 10 times
    // across Y=53/56/60, -7,36 armed at Y=84 AND Y=78. Each cycle burns a pillar and a
    // chunk of the run. E133 fired 6 times and banTicks=400 never held once.
    //
    // This ban is different: it anchors on the column and survives nudges. It clears only
    // when the player is BOTH far enough away (manhattan >= STICKY_CLEAR_MANH) AND has
    // been away for STICKY_CLEAR_TICKS consecutive ticks - i.e. a genuine departure, not a
    // hop. While it is armed, logStart() refuses to arm S130 at that column.
    private static int stickyX = Integer.MIN_VALUE;
    private static int stickyZ;
    private static int stickyTicks;
    private static int stickyAwayTicks;
    private static int stickyArms;      // how many S130 arms this column has eaten

    /** Manhattan distance that counts as "actually left" rather than a hop. */
    private static final int STICKY_CLEAR_MANH = 6;
    /** Consecutive ticks that far away before the sticky ban lifts. */
    private static final int STICKY_CLEAR_TICKS = 60;

    // S158: CROSS-COLUMN SHAFT HOPPING.
    //
    // Every guard above is COLUMN-SCOPED: detectPingPong() resets when (x,z) changes,
    // noteChildFlip() resets on any column change, and stickyBlocked() is exact-column
    // equality. Run M proved the bot never re-uses a column — it dug a FRESH 1x1 shaft for
    // each new iron target (20 HolePillar<->CollectIron swaps across ~8 distinct columns
    // in 4 minutes, y oscillating 62->72->64->70...). Because every cycle was a new
    // column, pingStartCount never reached 4, E133 never fired, and neither the ban nor
    // the sticky ban was ever armed. That is precisely why S147 has never once appeared
    // in any run log.
    //
    // So: count S130 arms IRRESPECTIVE of column, and once the bot has pillared out of
    // HOP_TRIGGER shafts inside HOP_WINDOW_MS, ban an AREA (radius, not a column) so it is
    // forced to actually relocate rather than just digging the neighbouring shaft.
    private static int hopCount;
    private static long hopFirstMs;
    private static int hopSeenX0 = Integer.MIN_VALUE;
    private static int hopSeenZ0;
    private static int hopSeenX1 = Integer.MIN_VALUE;
    private static int hopSeenZ1;
    private static int hopEscalate;

    private static int hopBanX = Integer.MIN_VALUE;
    private static int hopBanZ;
    private static int hopBanTicks;
    private static int hopBanRadius;

    private static final long HOP_WINDOW_MS = 90_000L;
    private static final int HOP_TRIGGER = 4;
    private static final int HOP_BAN_TICKS = 20 * 45;
    private static final int HOP_BAN_RADIUS = 14;
    private static final int HOP_BAN_RADIUS_MAX = 28;

    // Thrash: CollectIron <-> HolePillar at same xz
    private static int thrashX = Integer.MIN_VALUE;
    private static int thrashZ;
    private static int thrashFlips;
    private static long thrashStartMs;
    private static String thrashPrevChild = "";
    private static String thrashLastA = "";
    private static String thrashLastB = "";
    private static long lastSuppressMs;

    private HolePillar() {}

    public static void reset() {
        step = 0;
        lastY = Integer.MIN_VALUE;
        rose = 0;
        startY = Integer.MIN_VALUE;
        holding = false;
        riseStable = 0;
        riseStableAtY = Integer.MIN_VALUE;
        // NOTE: shaftX/shaftZ are deliberately NOT cleared here — logEnd() runs before
        // reset() and needs the column to anchor the ban.
        release();
    }

    public static boolean boxed(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return false;
        BlockPos feet = mod.getPlayer().getBlockPos();
        // Geometry only. Sky light lies (caves, overhangs, night).
        // 4 walls at feet AND head = 1x1 shaft. A 3-wall cave tunnel is not a pit.
        // Also require sides below feet so a 1-deep surface dip does not arm S130.
        if (wallCount(mod, feet) < 4 || wallCount(mod, feet.add(0, 1, 0)) < 4
                || wallCount(mod, feet.add(0, -1, 0)) < 3) {
            return false;
        }
        // S173: geometry alone cannot tell "trapped in a pit" from "standing in the 1x1 ore
        // shaft I just dug at the surface" — and that second case is the pillaring bug the
        // standing directive names. Run W is the proof: S130 fired 854 times, and ALL 18 of
        // its START lines had `sky=15 boxed=true`. Full daylight, open sky above, and it was
        // climbing from y=59 to y=63, then walking back down to 59 to reach the iron it had
        // come for, then being told it was "boxed" again and climbing once more. It never got
        // the iron and never got a stone pickaxe (t=9:52, stonepick=0 iron=0).
        //
        // The discriminator is whether there is open sky DIRECTLY above the column. A real
        // pit has a roof of stone and sky 0; a self-dug surface shaft has sky 15 and an
        // unobstructed column. Requiring both conditions means a genuine pit (which is what
        // S130 exists for) still arms, while a surface shaft no longer does.
        if (SurfaceBailTask.sky(mod) >= 10 && openColumnAbove(mod, feet)) return false;
        return true;
    }

    /**
     * S173: is the column directly above {@code feet} unobstructed all the way to build
     * limit? A self-dug surface shaft has nothing above it; a covered pit has a roof. Checked
     * with a cheap walk rather than a full scan — the common case is blocked within a few
     * blocks, and "open the whole way" is the only answer that matters.
     */
    private static boolean openColumnAbove(AltoClef mod, BlockPos feet) {
        for (int dy = 2; dy <= 6; dy++) {
            var above = mod.getWorld().getBlockState(feet.add(0, dy, 0));
            if (!above.isAir()) return false;
        }
        return true;
    }

    public static boolean givingUp() {
        return failCool > 0;
    }

    public static boolean busy() {
        return holding || failCool > 0 || banTicks > 0;
    }

    public static boolean holding() {
        return holding;
    }

    public static int failCoolLeft() {
        return failCool;
    }

    public static int banTicksLeft() {
        return banTicks;
    }

    /**
     * S147: the player is standing in a column we already escaped from and which is still
     * under a sticky ban. logStart() must refuse to arm S130 here.
     *
     * This is the fix for the repeated re-arm at one column. It is deliberately a REFUSAL
     * to re-arm, not a suppression of the pillar: once the bot genuinely leaves the column
     * (STICKY_CLEAR_MANH away for STICKY_CLEAR_TICKS), S130 works normally again.
     */
    public static boolean stickyBlocked(AltoClef mod) {
        if (stickyTicks <= 0 || stickyX == Integer.MIN_VALUE) return false;
        if (mod == null || mod.getPlayer() == null) return false;
        return mod.getPlayer().getBlockPos().getX() == stickyX && mod.getPlayer().getBlockPos().getZ() == stickyZ;
    }

    public static int stickyTicksLeft() {
        return stickyTicks;
    }

    public static String stickyColumn() {
        return stickyX == Integer.MIN_VALUE ? "-" : (stickyX + "," + stickyZ);
    }

    /**
     * S158: count one S130 arm IRRESPECTIVE of which column it happened in. Call from
     * logStart(). Returns true when this arm tripped the shaft-hop area ban.
     */
    public static boolean noteHop(AltoClef mod) {
        if (mod == null || mod.getPlayer() == null) return false;
        int x = mod.getPlayer().getBlockPos().getX();
        int z = mod.getPlayer().getBlockPos().getZ();
        long now = System.currentTimeMillis();
        if (now - hopFirstMs > HOP_WINDOW_MS) {
            hopFirstMs = now;
            hopCount = 0;
            hopEscalate = 0;
        }
        hopCount++;
        // Remember the previous distinct column so the log shows the hop is real.
        if (x != hopSeenX0 || z != hopSeenZ0) {
            hopSeenX1 = hopSeenX0;
            hopSeenZ1 = hopSeenZ0;
            hopSeenX0 = x;
            hopSeenZ0 = z;
        }
        if (hopCount < HOP_TRIGGER) return false;
        hopCount = 0;
        hopEscalate++;
        int radius = Math.min(HOP_BAN_RADIUS_MAX, HOP_BAN_RADIUS * hopEscalate);
        hopBanX = x;
        hopBanZ = z;
        hopBanTicks = HOP_BAN_TICKS;
        hopBanRadius = radius;
        // Also arm the ordinary cool so the pillar cannot instantly re-arm next tick.
        failCool = Math.max(failCool, 20 * 20);
        lastCoolArmed = Math.max(lastCoolArmed, 20 * 20);
        banX = x;
        banZ = z;
        banY = mod.getPlayer() == null ? Integer.MIN_VALUE : mod.getPlayer().getBlockPos().getY();
        banTicks = Math.max(banTicks, 20 * 30);
        T2Log.force("S158", "shaft-hop n>=" + HOP_TRIGGER + " in " + (now - hopFirstMs)
                + "ms - area ban r=" + radius + " @" + x + "," + z
                + " prev=" + hopSeenX1 + "," + hopSeenZ1
                + " esc=" + hopEscalate + " " + snap(mod));
        return true;
    }

    /**
     * Ticks the bot must stay in one column before the hop ban stops suppressing S130.
     * See {@link #hopBlocked} — without this the ban converts an oscillation into a stall.
     */
    private static final int HOP_BAN_YIELD_TICKS = 20 * 5;

    /**
     * S158: the player is inside a shaft-hop AREA ban (radius, not a single column).
     *
     * <p><b>The ban must suppress hopping, never trap the bot.</b> A bot standing at the
     * bottom of a shaft it cannot pillar out of is not hopping — it is stuck, and refusing
     * its escape for the whole ban window would turn the oscillation this code was written
     * to fix into a 45-second stall (the S156 failure shape: one child that can never
     * finish). So the ban yields once the bot has been pinned in a single column for
     * {@link #HOP_BAN_YIELD_TICKS}: hopping means the column keeps changing, being stuck
     * means it does not, and that distinction is what separates the two cases.
     *
     * @param sameXzTicks how long the solver has seen the bot in the same column
     */
    public static boolean hopBlocked(AltoClef mod, int sameXzTicks) {
        if (hopBanTicks <= 0 || hopBanX == Integer.MIN_VALUE) return false;
        if (mod == null || mod.getPlayer() == null) return false;
        if (sameXzTicks >= HOP_BAN_YIELD_TICKS) return false;
        int dx = mod.getPlayer().getBlockPos().getX() - hopBanX;
        int dz = mod.getPlayer().getBlockPos().getZ() - hopBanZ;
        return dx * dx + dz * dz <= hopBanRadius * hopBanRadius;
    }

    public static String hopBanInfo() {
        if (hopBanTicks <= 0) return "-";
        return hopBanX + "," + hopBanZ + " r=" + hopBanRadius + " left=" + hopBanTicks;
    }

    public static int startY() {
        return startY;
    }

    public static String lastEndReason() {
        return lastEndReason;
    }

    /** True once after cool/ban logic wants T2Solve to zero sameXz. */
    public static boolean consumeSameXzReset() {
        if (!sameXzResetNeeded) return false;
        sameXzResetNeeded = false;
        return true;
    }

    /**
     * Climbed far enough from pillar start.
     * +2 alone is not enough while the shaft collar (walls below feet) is still closed —
     * that was the S130 re-arm thrash: END risen @+2y, cool 4s, fall back in, START again.
     *
     * HARDENED: the old +3 branch accepted a collar that the block-we-just-placed had
     * opened for a single tick. That produced an endless loop at one column: S130 START
     * (walls 4/4/4) -> hold -> S136 END risen (walls 1/0/4) -> cool -> fall back in.
     * Now a marginal rise must be SUSTAINED across ticks and must have real headroom.
     */
    public static boolean risenEnough(AltoClef mod) {
        if (startY == Integer.MIN_VALUE || mod == null || mod.getPlayer() == null) return false;
        int y = mod.getPlayer().getBlockPos().getY();
        // Idempotent within a tick: HolePillarTask.isFinished(), HolePillar.tick() and
        // T2Solve can all call this on the same game tick. Without this guard the
        // stability counter would advance 2-3x faster than real time.
        if (riseLastEvalTick == riseEvalTick) return riseLastEvalResult;
        riseLastEvalTick = riseEvalTick;
        riseLastEvalResult = evaluateRise(mod, y);
        return riseLastEvalResult;
    }

    /** Advance once per game tick. Called from T2Solve.tick(). */
    public static void beginTick() {
        riseEvalTick++;
    }

    private static boolean evaluateRise(AltoClef mod, int y) {
        if (y >= startY + 4) {
            // Clear-cut: 4+ blocks up. Always accept.
            riseStableAtY = y;
            riseStable = Math.max(riseStable, RISE_STABLE_TICKS);
            T2Log.force("S139", "rise confirmed +" + (y - startY) + "y y=" + y
                    + " startY=" + startY + " " + snap(mod));
            return true;
        }
        if (y < startY + 2) {
            riseStable = 0;
            riseStableAtY = Integer.MIN_VALUE;
            return false;
        }
        // Marginal (+2 or +3). An escape means the bot can MOVE FREELY — which is exactly
        // what !boxed() tests. It is NOT "there is no shaft beneath me".
        //
        // S180: the previous form also required `wallCount(feet-1) < 3` and
        // `!columnUnderneath(feet)`. Both are STRUCTURALLY IMPOSSIBLE for a bot that has
        // just pillared OUT of a shaft: the shaft it climbed is always directly beneath it,
        // and its walls are still there one level down. So a +2/+3 escape could never be
        // confirmed, and always fell through to the `stuck-low` give-up at step 50.
        //
        // Run AB is the proof. The bot dug a 2-deep hole at the surface; S130 fired; it
        // pillared 62 -> 64; and its own tick log showed `boxed=false walls=0/2/4 sky=11` —
        // genuinely free, standing on the rim with open space all round. The strict test
        // rejected that, logged `stuck-low ... roseReal=false giveUp=true`, armed a 12s
        // ban, and CollectIron immediately re-entered the same shaft. Dig down 2, pillar up
        // 2, repeat — the exact loop the standing directive names. S141 (the reject log)
        // never fired either, because riseStable was still 0, so the rejection was SILENT.
        //
        // Being lenient is the safe direction here: a false accept costs one cool period
        // (S130 simply re-fires if the escape was not real), whereas a false reject is a
        // livelock that never terminates. The RISE_STABLE_TICKS hold below still requires
        // the free position to persist for 12 ticks before it counts.
        BlockPos feet = mod.getPlayer().getBlockPos();
        boolean clear = !boxed(mod) && headroom(mod, feet);
        // Log once per hold, not once per tick: force() echoes to CHAT+STDOUT+HIST, so a
        // per-tick line here would be 3 lines/tick of pure spam.
        if (clear && riseStableAtY != y) {
            T2Log.force("S180", "marginal escape +" + (y - startY) + "y free at y=" + y
                    + " startY=" + startY + " " + snap(mod));
        }
        if (!clear) {
            if (riseStable > 0) {
                T2Log.force("S141", "hop not sustained +" + (y - startY) + "y cleared="
                        + riseStable + "t y=" + y + " startY=" + startY + " " + snap(mod));
            }
            riseStable = 0;
            riseStableAtY = Integer.MIN_VALUE;
            return false;
        }
        if (riseStableAtY != y) {
            riseStableAtY = y;
            riseStable = 1;
            T2Log.force("S139", "rise pending +" + (y - startY) + "y t=1 y=" + y
                    + " startY=" + startY);
            return false;
        }
        riseStable++;
        if (riseStable < RISE_STABLE_TICKS) {
            return false;
        }
        T2Log.force("S139", "rise confirmed +" + (y - startY) + "y held " + riseStable
                + "t y=" + y + " startY=" + startY + " " + snap(mod));
        return true;
    }

    /** How long a marginal rise must hold before it counts as an escape. */
    private static final int RISE_STABLE_TICKS = 12;

    // S180: `columnUnderneath()` lived here. It asked "is the shaft still directly below
    // me?" — which is TRUE for every bot that has just pillared out of a shaft, so it made
    // the +2/+3 escape path unreachable and turned shallow escapes into the dig-down /
    // pillar-up livelock. Deleted rather than kept: a predicate that is always true in the
    // situation it guards is dead weight, and leaving it invites someone to re-add the call.
    // The escape test is now `!boxed() && headroom()` — "can the bot move freely".

    /**
     * S182: can the bot occupy the space it is standing in? Checks the block ABOVE THE HEAD
     * (feet+1) — the bot's own body column — and deliberately NOT feet+2.
     *
     * <p>The first version required feet+2 to be air ("not a ceiling lip"). That is wrong for
     * any normal 2-high space: a 2-high tunnel or an overhang has feet and feet+1 free and
     * feet+2 solid, which is a perfectly walkable ceiling, not a trap. Run AC's last
     * `stuck-low` is the proof — `y=70 startY=68 walls=0/0/4 sky=12`, i.e. no walls at feet or
     * head and open sky, an obviously free bot, rejected purely because a block sat 2 above it.
     *
     * <p>`!boxed()` already establishes that there is an opening at feet level, which is what
     * "can move" means; this check only needs to confirm the bot is not suffocating.
     */
    private static boolean headroom(AltoClef mod, BlockPos feet) {
        return !solid(mod, feet.add(0, 1, 0));
    }

    public static void coolTick() {
        AltoClef mod = null;
        try { mod = AltoClef.getInstance(); } catch (Throwable ignored) {}

        // S147 sticky column ban decay. Independent of banX/banTicks above, which clear on
        // a 2-block nudge and are therefore useless for this purpose.
        if (stickyTicks > 0) {
            stickyTicks--;
            if (mod != null && mod.getPlayer() != null) {
                int sx = mod.getPlayer().getBlockPos().getX();
                int sz = mod.getPlayer().getBlockPos().getZ();
                int manh = Math.abs(sx - stickyX) + Math.abs(sz - stickyZ);
                if (manh >= STICKY_CLEAR_MANH) {
                    stickyAwayTicks++;
                    if (stickyAwayTicks >= STICKY_CLEAR_TICKS) {
                        T2Log.force("S148", "sticky ban lift @" + stickyX + "," + stickyZ
                                + " after " + stickyAwayTicks + "t away manh=" + manh
                                + " arms=" + stickyArms);
                        stickyTicks = 0;
                        stickyAwayTicks = 0;
                        stickyArms = 0;
                    }
                } else {
                    // Nudged back within range - the clock restarts. A pillar hop is not
                    // a departure.
                    stickyAwayTicks = 0;
                }
            }
            if (stickyTicks == 0 && stickyAwayTicks == 0) {
                // expired naturally without ever getting clear; keep the memory anyway so
                // a later re-arm at this column is at least visible in the log.
            }
        }

        // S158 shaft-hop area ban decay. Time-based only: it must NOT clear on a nudge,
        // or it becomes the same useless ban as banX/banTicks.
        if (hopBanTicks > 0) {
            hopBanTicks--;
            if (hopBanTicks == 0) {
                T2Log.force("S158", "shaft-hop area ban lifted @" + hopBanX + "," + hopBanZ
                        + " r=" + hopBanRadius + " esc=" + hopEscalate);
                hopBanRadius = 0;
            }
        }

        // Leaving the banned column clears the ban early.
        if (banTicks > 0 && mod != null && mod.getPlayer() != null) {
            int x = mod.getPlayer().getBlockPos().getX();
            int z = mod.getPlayer().getBlockPos().getZ();
            int y = mod.getPlayer().getBlockPos().getY();
            int manh = Math.abs(x - banX) + Math.abs(z - banZ);
            // S178 — THE DESCENDING-SHAFT TRAP.
            //
            // This test is HORIZONTAL-ONLY, but the bot does not always leave a shaft
            // sideways: while collecting iron it frequently TUNNELS STRAIGHT DOWN. In run Z
            // S135 fired 231 times with the bot descending a self-dug shaft in the same
            // column (`sky` 9->7->6->3, `place` 46->51), and because manh stayed 0 the ban
            // never cleared, so `busy()` stayed true and S130 was suppressed the whole way
            // down. The bot dug 40+ blocks it did not need to dig.
            //
            // A 5-block vertical move inside the same column means this is no longer the
            // same situation the ban was armed for — the bot has changed depth, the walls
            // around it are different rock, and the pillar decision must be re-made rather
            // than inherited. Sideways-exit clearing (manh >= 2) is kept as well.
            int dy = (banY == Integer.MIN_VALUE) ? 0 : Math.abs(y - banY);
            boolean descended = dy >= 5;
            if (manh >= 2 || descended) {
                T2Log.force("S138", "shaft ban clear " + (descended ? "left-y" : "left-xz")
                        + " ban=" + banX + "," + banY + "," + banZ
                        + " now=" + x + "," + y + "," + z
                        + " manh=" + manh + " dy=" + dy + " left=" + banTicks);
                banTicks = 0;
                reCoolCount = 0;
            } else {
                banTicks--;
            }
        }

        if (failCool > 0) {
            failCool--;
            coolWasOn = true;
            if (failCool == 0) {
                T2Log.force("S134", "pillar cool expire after " + lastArmReason
                        + " end=" + lastEndReason);
                coolWasOn = false;
                // Still in the same shaft column and still boxed -> re-cool escalate,
                // never hand S130 an instant re-arm with sameXz already huge.
                if (stillBannedBoxed(mod)) {
                    int next = Math.min(20 * 20, Math.max(20 * 8, lastCoolArmed * 2));
                    reCoolCount++;
                    failCool = next;
                    lastCoolArmed = next;
                    banTicks = Math.max(banTicks, next);
                    sameXzResetNeeded = true;
                    T2Log.force("S137", "re-cool still-boxed@" + banX + "," + banZ
                            + " n=" + reCoolCount + " ticks=" + next
                            + " end=" + lastEndReason + " " + snap(mod));
                    if (reCoolCount >= 3) {
                        T2Log.force("E132", "SHAFT_STUCK reCool=" + reCoolCount
                                + " @" + banX + "," + (mod.getPlayer() == null ? "?" : mod.getPlayer().getBlockPos().getY())
                                + "," + banZ + " " + snap(mod));
                    }
                    // E132 SOLVER — the re-cool ladder above is self-defeating.
                    //
                    // The ban clears only when we reach manhattan >= 2 from the banned
                    // column. Inside a 1x1 shaft the player CANNOT move horizontally at
                    // all, so the ban can never clear, and every re-cool just doubles the
                    // lock. Observed 2026-09-20: bot pinned at 101,55<->101,56,167,
                    // "S135 S130 suppressed why=would-S130 but busy ... boxed=true",
                    // oscillating forever with 16 iron already in the bag.
                    //
                    // Escape is only possible by pillaring OUT. Once we have re-cooled
                    // twice at the same still-boxed column, stop escalating: clear the
                    // ban and let S130 arm a genuine attempt. The sustained-rise gate in
                    // evaluateRise() (RISE_STABLE_TICKS) plus detectPingPong()/E133 is
                    // what prevents this from degenerating back into the old ping-pong;
                    // a blanket ban is not the right tool — it deadlocks by construction.
                    if (reCoolCount >= 2) {
                        T2Log.force("S142", "unban for real pillar attempt@" + banX + "," + banZ
                                + " reCool=" + reCoolCount + " " + snap(mod));
                        banTicks = 0;
                        failCool = 0;
                        lastCoolArmed = 0;
                        reCoolCount = 0;
                        coolWasOn = false;
                        sameXzResetNeeded = false;
                    }
                } else {
                    // Cool done and not boxed here — still keep a short ban so sameXz
                    // cannot instantly re-arm if they drop back in within ~3s.
                    if (banTicks <= 0 && banX != Integer.MIN_VALUE) {
                        banTicks = 20 * 3;
                    }
                    sameXzResetNeeded = true;
                }
            }
        } else if (coolWasOn) {
            coolWasOn = false;
        }
    }

    private static boolean stillBannedBoxed(AltoClef mod) {
        if (mod == null || mod.getPlayer() == null) return false;
        if (banX == Integer.MIN_VALUE) return false;
        if (mod.getPlayer().getBlockPos().getX() != banX || mod.getPlayer().getBlockPos().getZ() != banZ) return false;
        // S178: "still in the same shaft" must also mean "still at the same depth". Without
        // this the doubling re-cool (lastCoolArmed*2, capped 20x20) keeps escalating while
        // the bot tunnels downward, which is how run Z reached cool=197/ban=197 in a column
        // it had already left in every sense that matters.
        if (banY != Integer.MIN_VALUE
                && Math.abs(mod.getPlayer().getBlockPos().getY() - banY) >= 5) return false;
        return boxed(mod);
    }

    /** Dense one-liner: walls/sky/place/hold/cool/y. */
    public static String snap(AltoClef mod) {
        if (mod == null || mod.getPlayer() == null || mod.getWorld() == null) {
            return "pos=? walls=?/?/? sky=? place=? hold=" + holding + " cool=" + failCool
                    + " ban=" + banTicks;
        }
        BlockPos feet = mod.getPlayer().getBlockPos();
        int wf = wallCount(mod, feet);
        int wh = wallCount(mod, feet.add(0, 1, 0));
        int wb = wallCount(mod, feet.add(0, -1, 0));
        int sky = -1;
        try {
            sky = mod.getWorld().getLightLevel(net.minecraft.world.LightType.SKY, feet);
        } catch (Throwable ignored) {}
        int place = 0;
        try { place = PlaceBlocks.count(mod); } catch (Throwable ignored) {}
        return "pos=" + feet.getX() + "," + feet.getY() + "," + feet.getZ()
                + " walls=" + wf + "/" + wh + "/" + wb
                + " sky=" + sky
                + " place=" + place
                + " boxed=" + boxed(mod)
                + " hold=" + holding
                + " cool=" + failCool
                + " ban=" + banTicks
                + " startY=" + (startY == Integer.MIN_VALUE ? "-" : String.valueOf(startY))
                + " step=" + step;
    }

    /** Why S130 armed. Call once when entering HolePillarTask. */
    public static void logStart(AltoClef mod, String phase, String prevChild, String trigger) {
        lastArmReason = trigger;
        if (mod != null && mod.getPlayer() != null) {
            shaftX = mod.getPlayer().getBlockPos().getX();
            shaftZ = mod.getPlayer().getBlockPos().getZ();
            if (startY == Integer.MIN_VALUE) startY = mod.getPlayer().getBlockPos().getY();
            // S147: if we are standing in a sticky-banned column, these are re-arms at a
            // column we already escaped. Count them - it is the metric for whether the
            // fix is working.
            if (stickyTicks > 0 && stickyX == shaftX && stickyZ == shaftZ) {
                stickyArms++;
                T2Log.warn("S147", "re-arm in sticky column @" + stickyX + "," + stickyZ
                        + " arm#" + stickyArms + " left=" + stickyTicks
                        + " trig=" + trigger + " " + snap(mod));
            }
        }
        // S158: count this arm across columns. Must run BEFORE the S130 log line so the
        // hop ban is visible in the same tick it arms.
        noteHop(mod);
        pillarCycles = detectPingPong(mod);
        T2Log.force("S130", "START trigger=" + trigger
                + " ph=" + phase
                + " prev=" + prevChild
                + " cycle=" + pillarCycles
                + " " + snap(mod));
        T2History.note("PILLAR START " + trigger + " prev=" + prevChild);
    }

    /**
     * Count repeated S130 arms at the same column inside 60s. >=4 means we are
     * pillar-hopping in place and never actually leaving the shaft — the loop that
     * burned 154 IRON faults. Escalates the cool so the bot is forced to break out.
     */
    private static int detectPingPong(AltoClef mod) {
        if (mod == null || mod.getPlayer() == null) return 0;
        int x = mod.getPlayer().getBlockPos().getX();
        int z = mod.getPlayer().getBlockPos().getZ();
        long now = System.currentTimeMillis();
        if (x != pingX || z != pingZ || now - pingFirstMs > 60_000L) {
            pingX = x;
            pingZ = z;
            pingStartCount = 0;
            pingFirstMs = now;
        }
        pingStartCount++;
        if (pingStartCount >= 4) {
            T2Log.force("E133", "PILLAR_PINGPONG n=" + pingStartCount
                    + " @" + x + "," + z + " in " + (now - pingFirstMs) + "ms"
                    + " cycles=" + pillarCycles + " lastEnd=" + lastEndReason);
            // Force a long ban so S130 cannot re-arm here, and hand control back
            // so the parent can walk somewhere else.
            banX = x;
            banZ = z;
            banY = mod.getPlayer() == null ? Integer.MIN_VALUE : mod.getPlayer().getBlockPos().getY();
            banTicks = Math.max(banTicks, 20 * 20);
            failCool = Math.max(failCool, 20 * 20);
            lastCoolArmed = Math.max(lastCoolArmed, 20 * 20);
            // S147: the ordinary ban above clears on a 2-block nudge (observed run F:
            // `S138 shaft ban clear ... manh=2` was the dominant clear reason), which is
            // exactly what a pillar hop produces. Arm the sticky per-column ban as well so
            // this column is genuinely refused for a while. Timer scales with how many
            // times we have already bounced here.
            stickyX = x;
            stickyZ = z;
            stickyAwayTicks = 0;
            int sticky = Math.min(20 * 90, 20 * 20 * Math.max(1, stickyArms + 1));
            stickyTicks = Math.max(stickyTicks, sticky);
            T2Log.force("S147", "sticky ban arm @" + x + "," + z + " ticks=" + sticky
                    + " n=" + pingStartCount + " prevArms=" + stickyArms
                    + " lastEnd=" + lastEndReason);
            pingStartCount = 0;
            pingFirstMs = now;
        }
        return pingStartCount;
    }

    /** Why escape ended. Sets failCool when armCoolTicks > 0. */
    public static void logEnd(AltoClef mod, String reason, int armCoolTicks) {
        lastEndReason = reason;
        // Evaluate "did we rise" BEFORE clearing the stability counters, and report it
        // without calling risenEnough() again (that call mutates riseStable).
        boolean roseReal = riseStable >= RISE_STABLE_TICKS
                || (mod != null && mod.getPlayer() != null && startY != Integer.MIN_VALUE
                    && mod.getPlayer().getBlockPos().getY() >= startY + 4);
        if (mod != null && mod.getPlayer() != null) {
            // Anchor the ban to the SHAFT COLUMN, not the mid-hop position.
            // Observed bug: ban=-236,8 while the real shaft was -236,7 — S138 then saw a
            // 1-block manhattan nudge and cleared the ban, so S130 re-armed immediately.
            BlockPos shaft = shaftColumn();
            if (shaft != null) {
                banX = shaft.getX();
                banZ = shaft.getZ();
                // S178: use the shaft's own startY, so the dy check measures descent from
                // where this escape began rather than from an arbitrary later tick.
                banY = startY == Integer.MIN_VALUE ? shaft.getY() : startY;
            } else {
                banX = mod.getPlayer().getBlockPos().getX();
                banZ = mod.getPlayer().getBlockPos().getZ();
                banY = mod.getPlayer().getBlockPos().getY();
            }
        }
        riseStable = 0;
        riseStableAtY = Integer.MIN_VALUE;
        if (armCoolTicks > 0) {
            failCool = armCoolTicks;
            lastCoolArmed = armCoolTicks;
            banTicks = Math.max(banTicks, armCoolTicks);
            // S178b: armY must be stamped HERE too. The first S178 cut only set banY at the
            // three explicit ban sites, so this path (the ordinary post-pillar cool) armed
            // banTicks while banY kept a STALE value from an earlier column. Run AA is the
            // proof: END at 3:43.8 armed cool=240/ban=240 with the bot at y=69, it then
            // descended 69->67->66->63 in the same column, and the dy test compared against
            // a stale Y so the ban never cleared — S135 suppressed 3 more times
            // (cool=196/156/116) while the bot dug downward. Any site that arms banTicks
            // must stamp the Y it armed at, or the dy test measures the wrong thing.
            banY = mod.getPlayer() == null ? Integer.MIN_VALUE : mod.getPlayer().getBlockPos().getY();
            sameXzResetNeeded = true;
            T2Log.force("S133", "cool arm ticks=" + armCoolTicks + " reason=" + reason
                    + " ban=" + banX + "," + banY + "," + banZ
                    + " " + snap(mod));
        }
        T2Log.force("S136", "END reason=" + reason
                + " roseReal=" + roseReal
                + " giveUp=" + givingUp()
                + " " + snap(mod));
        T2History.note("PILLAR END " + reason);
    }

    /** The column the current escape started in, or null if we never armed. */
    private static BlockPos shaftColumn() {
        if (shaftX == Integer.MIN_VALUE) return null;
        return new BlockPos(shaftX, startY == Integer.MIN_VALUE ? 0 : startY, shaftZ);
    }

    /** S130 blocked because cool/busy. At most once per 2s. */
    public static void logSuppress(AltoClef mod, String why) {
        long now = System.currentTimeMillis();
        if (now - lastSuppressMs < 2000) return;
        lastSuppressMs = now;
        T2Log.force("S135", "S130 suppressed why=" + why
                + " cool=" + failCool
                + " ban=" + banTicks
                + " hold=" + holding
                + " " + snap(mod));
    }

    /**
     * Track CollectIron <-> HolePillar flips at same xz.
     * Call each tick from T2Solve with the live child name.
     */
    public static void noteChildFlip(AltoClef mod, String childName) {
        if (mod == null || mod.getPlayer() == null || childName == null) return;
        boolean pillar = childName.contains("HolePillar");
        boolean ironish = childName.contains("Collect") || childName.contains("Mine")
                || childName.contains("GetToBlock");
        if (!pillar && !ironish) {
            thrashPrevChild = childName;
            return;
        }
        int x = mod.getPlayer().getBlockPos().getX();
        int z = mod.getPlayer().getBlockPos().getZ();
        long now = System.currentTimeMillis();
        if (x != thrashX || z != thrashZ) {
            thrashX = x;
            thrashZ = z;
            thrashFlips = 0;
            thrashStartMs = now;
            thrashPrevChild = childName;
            thrashLastA = "";
            thrashLastB = "";
            return;
        }
        if (thrashPrevChild.isEmpty()) {
            thrashPrevChild = childName;
            return;
        }
        boolean prevPillar = thrashPrevChild.contains("HolePillar");
        boolean prevIron = thrashPrevChild.contains("Collect") || thrashPrevChild.contains("Mine")
                || thrashPrevChild.contains("GetToBlock");
        boolean flip = (pillar && prevIron) || (ironish && prevPillar);
        if (flip) {
            thrashFlips++;
            thrashLastA = thrashPrevChild;
            thrashLastB = childName;
            // 4+ flips within 8s at same xz = thrash
            if (thrashFlips >= 4 && (now - thrashStartMs) <= 8000) {
                T2Log.force("E131", "THRASH flips=" + thrashFlips
                        + " ms=" + (now - thrashStartMs)
                        + " @" + x + "," + mod.getPlayer().getBlockPos().getY() + "," + z
                        + " a=" + thrashLastA + " b=" + thrashLastB
                        + " end=" + lastEndReason
                        + " " + snap(mod));
                thrashFlips = 0;
                thrashStartMs = now;
            }
        }
        thrashPrevChild = childName;
    }

    public static int wallCount(AltoClef mod, BlockPos feet) {
        int n = 0;
        if (solid(mod, feet.add(0, 0, -1))) n++;
        if (solid(mod, feet.add(0, 0, 1))) n++;
        if (solid(mod, feet.add(1, 0, 0))) n++;
        if (solid(mod, feet.add(-1, 0, 0))) n++;
        return n;
    }

    private static boolean wall(AltoClef mod, BlockPos feet) {
        return solid(mod, feet.add(0, 0, -1)) && solid(mod, feet.add(0, 0, 1))
                && solid(mod, feet.add(1, 0, 0)) && solid(mod, feet.add(-1, 0, 0));
    }

    public static boolean hasPlace(AltoClef mod) {
        return PlaceBlocks.count(mod) > 0;
    }

    /** One tick. Returns true if it took over inputs. */
    public static boolean tick(AltoClef mod) {
        if (mod.getPlayer() == null) return false;
        // failCool decrements only in coolTick - do not double-count here.
        if (failCool > 0) {
            holding = false;
            release();
            return false;
        }
        int y = mod.getPlayer().getBlockPos().getY();
        if (holding && risenEnough(mod)) {
            // Full clear: short cool. Marginal paths should not reach here often.
            int coolTicks = (y >= startY + 4) ? (20 * 4) : (20 * 10);
            logEnd(mod, "risen y=" + y + " startY=" + startY, coolTicks);
            reset();
            release();
            return false;
        }
        // Stuck hopping at +1/+2 without a real escape â€” give up before infinite hold.
        if (holding && step >= 50 && y < startY + 4) {
            logEnd(mod, "stuck-low step=" + step + " y=" + y + " startY=" + startY, 20 * 12);
            reset();
            release();
            return false;
        }
        if (!hasPlace(mod)) {
            if (holding) {
                logEnd(mod, "!hasPlace y=" + y, 20 * 12);
                reset();
                release();
            }
            holding = false;
            return false;
        }
        // Mid-escape: a 1-block hop can flicker !boxed. Keep holding until risen or fail.
        if (!boxed(mod) && !holding) {
            step = 0;
            return false;
        }
        if (startY == Integer.MIN_VALUE) {
            startY = y;
            T2Log.force("S130", "HOLD begin " + snap(mod));
        }
        lastY = y;
        if (step >= 30 && y <= startY + 1) {
            logEnd(mod, "no-rise step=" + step + " y=" + y + " startY=" + startY, 20 * 12);
            reset();
            release();
            return false;
        }
        int slot = PlaceBlocks.equip(mod);
        if (slot < 0) {
            step = 0;
            if (holding) {
                logEnd(mod, "equip-fail y=" + y, 20 * 12);
                reset();
                release();
            }
            return false;
        }
        holding = true;
        lookDown();
        MinecraftClient mc = MinecraftClient.getInstance();
        try { mc.options.jumpKey.setPressed(true); } catch (Throwable ignored) {}
        step++;
        // place on the even ticks while airborne so the block goes under the player
        boolean air = true;
        try { air = !mod.getPlayer().isOnGround(); } catch (Throwable ignored) {}
        try { mc.options.useKey.setPressed(air && (step % 2 == 0)); } catch (Throwable ignored) {}
        if (step % 20 == 1) {
            T2Log.force("S130", "tick y=" + y + " slot=" + slot + " air=" + air + " " + snap(mod));
        }
        return true;
    }

    private static int slotOf(AltoClef mod) {
        Item[] want = {
                Items.COBBLESTONE, Items.DIRT, Items.NETHERRACK, Items.STONE,
                Items.OAK_PLANKS, Items.ANDESITE, Items.GRANITE, Items.DIORITE
        };
        try {
            for (int i = 0; i < 9; i++) {
                ItemStack st;
                st = mod.getPlayer().getInventory().getStack(i);
                if (st == null || st.isEmpty()) continue;
                for (Item it : want) {
                    if (st.getItem() == it) return i;
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static boolean solid(AltoClef mod, BlockPos p) {
        try {
            BlockState s = mod.getWorld().getBlockState(p);
            return s != null && !s.isAir() && s.isSolid();
        } catch (Throwable t) {
            try {
                return !mod.getWorld().getBlockState(p).isAir();
            } catch (Throwable t2) {
                return false;
            }
        }
    }

    private static void lookDown() {
        try {
            MinecraftClient.getInstance().player.setPitch(90f);
        } catch (Throwable ignored) {}
    }

    private static void release() {
        try {
            var opt = MinecraftClient.getInstance().options;
            opt.jumpKey.setPressed(false);
            opt.useKey.setPressed(false);
        } catch (Throwable ignored) {}
    }
}
