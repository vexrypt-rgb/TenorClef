package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.movement.GetOutOfWaterTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Tiny rule solver. Probe names the problem; this picks one fix.
 *
 * Overlay-first (close GUI, walk, swim) so we never replace
 * {@code ConstructNetherPortalBucketTask} with UnstickWalk.
 * A real child is only returned when the current goal is wrong
 * (no water for a lava cast, starving, piglin with no gold).
 */
public final class T2Solve {

    private static String lastFix = "";
    private static int cool;
    private static int sameXz;
    private static int lastX = Integer.MIN_VALUE;
    private static int lastZ;
    private static int flips;
    /**
     * Consecutive S100 nudges at the same column without the bot actually moving.
     *
     * S100 is a nudge, not a fix: it calls noJump()/walkTurn()/cancelPath() and then
     * returns null, which leaves the parent's child untouched. That works for a child
     * that is genuinely stuck walking, but a resource collector recomputes its target
     * every tick and paths straight back. Escalate to a real replacement task once the
     * nudge has demonstrably failed (see the S100 SOLVER comment in tick()).
     */
    private static int s100Escalations;
    /**
     * Ticks spent boxed in a shaft with no placeable blocks. Drives S145 (mine a wall
     * block to obtain cobblestone so the ordinary S130 pillar becomes possible).
     */
    private static int s145Ticks;
    private static boolean lastGround = true;
    private static int guiAge;
    /** Ticks the bot has mined while holding a non-pick with no blocks to place. */
    private static int wrongToolHits;

    private T2Solve() {}

    /** The item currently held in the main hand, or null. */
    private static net.minecraft.item.Item heldItem() {
        try {
            return adris.altoclef.util.helpers.StorageHelper
                    .getItemStackInSlot(adris.altoclef.util.slots.PlayerSlot.getEquipSlot())
                    .getItem();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isPick(net.minecraft.item.Item it) {
        return it == Items.WOODEN_PICKAXE || it == Items.STONE_PICKAXE
                || it == Items.IRON_PICKAXE || it == Items.GOLDEN_PICKAXE
                || it == Items.DIAMOND_PICKAXE || it == Items.NETHERITE_PICKAXE;
    }

    /** Best pick available anywhere in the inventory, or null. */
    private static net.minecraft.item.Item anyPick() {
        try {
            var store = AltoClef.getInstance().getItemStorage();
            if (store.getItemCount(Items.IRON_PICKAXE) > 0) return Items.IRON_PICKAXE;
            if (store.getItemCount(Items.STONE_PICKAXE) > 0) return Items.STONE_PICKAXE;
            if (store.getItemCount(Items.WOODEN_PICKAXE) > 0) return Items.WOODEN_PICKAXE;
            if (store.getItemCount(Items.DIAMOND_PICKAXE) > 0) return Items.DIAMOND_PICKAXE;
            if (store.getItemCount(Items.GOLDEN_PICKAXE) > 0) return Items.GOLDEN_PICKAXE;
            if (store.getItemCount(Items.NETHERITE_PICKAXE) > 0) return Items.NETHERITE_PICKAXE;
        } catch (Throwable ignored) {}
        return null;
    }

    public static void reset() {
        lastFix = "";
        cool = 0;
        sameXz = 0;
        lastX = Integer.MIN_VALUE;
        flips = 0;
        guiAge = 0;
        wrongToolHits = 0;
        s100Escalations = 0;
        s145Ticks = 0;
        try { adris.altoclef.tasks.speedrun.testrun2.core.T2Input.releaseAll(); } catch (Throwable ignored) {}
    }

    public static Task tick(AltoClef mod, String phase, Task child) {
        if (mod.getPlayer() == null) return null;
        HolePillar.beginTick();
        HolePillar.coolTick();
        if (HolePillar.consumeSameXzReset()) {
            sameXz = 0;
            flips = 0;
        }
        if (cool > 0) cool--;

        int x = mod.getPlayer().getBlockPos().getX();
        int y = mod.getPlayer().getBlockPos().getY();
        int z = mod.getPlayer().getBlockPos().getZ();
        boolean ground = true;
        boolean wet = false;
        try {
            ground = mod.getPlayer().isOnGround();
            wet = mod.getPlayer().isTouchingWater() || mod.getPlayer().isSubmergedInWater();
        } catch (Throwable ignored) {}
        if (x == lastX && z == lastZ) {
            sameXz++;
            if (ground != lastGround) flips++;
        } else {
            sameXz = 0;
            flips = 0;
        }
        lastX = x;
        lastZ = z;
        lastGround = ground;

        String childName = child == null ? "-" : child.getClass().getSimpleName();
        boolean portalWork = "PORTAL".equals(phase) && childName.contains("Construct");

        // 1. GUI. Craft/furnace stay open UNLESS we are jump-stuck on the table.
        if (guiOpen()) {
            guiAge++;
            if (workGui()) {
                // S184 — A FURNACE IS NOT A CRAFTING TABLE. Smelting one item takes 10s, and
                // the bot MUST stand still with the screen open for it to finish. The stall
                // test below (sameXz > 4s, flips >= 4) is right for an instantaneous craft but
                // catastrophic here: it closed the screen mid-smelt, the smelt aborted, the
                // parent re-opened the furnace, and the cycle repeated — the user's "the bot
                // spins in circles when it is in the furnace ui".
                //
                // Run AF: `S120 craft-gui jump` and `E104 FurnaceScreen open 8s` alternating
                // every ~6s at @69,66,18 for the final minute, with `E70 iron stall 6s`
                // alongside — the bot had the ore and the furnace and could never finish.
                //
                // A slow GUI gets a long, bounded budget and is NEVER closed by a same-xz
                // test, because standing still is the *correct* behaviour there.
                boolean slow = slowGui();
                boolean stuck = slow
                        ? (guiAge > 20 * 30)                                   // 30s of smelting
                        : (!wet && (flips >= 4 || sameXz > 20 * 4 || guiAge > 20 * 12));
                if (stuck) {
                    act(slow ? "S184" : "S120",
                            (slow ? "slow GUI" : "craft-gui jump") + " @" + x + "," + y + "," + z
                                    + " age=" + (guiAge / 20) + "s child=" + childName);
                    McCompat.closeScreen();
                    adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
                    adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
                    cancelPath(mod);
                    guiAge = 0;
                    flips = 0;
                }
                return null;
            }
            if (guiAge > 20 * 5) {
                act("S104", "close unknown GUI after 5s");
                McCompat.closeScreen();
                guiAge = 0;
            }
            return null;
        }
        guiAge = 0;

        // Only when jump-stuck ON the table. A standing wooden-pick craft
        // is same-XZ for >2s on purpose â€” do not cancel it.
        if (tableUnder(mod) && flips >= 4 && !wet
                && !childName.contains("StepOff")) {
            act("S108", "step off table @" + x + "," + y + "," + z);
            McCompat.closeScreen();
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
            cancelPath(mod);
            return new StepOffTableTask();
        }

        // 3. Water still / bob stall — escape to shore before mining resumes.
        // Critical: if already in GetOutOfWater / WaterBail, do NOT cancelPath or
        // re-nudge swim. cancelPath every tick kept spd≈0 and thrashed S102 forever.
        double spd = speed(mod);
        if (wet && spd < 0.03 && sameXz > 20 * 3) {
            boolean alreadyEscaping = childName.contains("GetOutOfWater")
                    || childName.contains("WaterBail");
            if (alreadyEscaping) {
                return null; // leave escape task running
            }
            act("S102", "escape water spd=" + String.format(java.util.Locale.ROOT, "%.3f", spd));
            cancelPath(mod);
            try {
                mod.getClientBaritone().getInputOverrideHandler().setInputForceState(
                        baritone.api.utils.input.Input.CLICK_LEFT, false);
            } catch (Throwable ignored) {}
            // No T2Input.swim() — fights GetOutOfWater pathing / worsens bob thrash.
            // S183: use WaterBailTask, not a raw GetOutOfWaterTask. The raw task reports
            // itself finished the moment the eyes clear the surface, so a bobbing bot ends
            // its own escape every few ticks and S102 re-fires forever. WaterBailTask wraps
            // the same pathing but requires a SUSTAINED dry period to finish.
            return new WaterBailTask();
        }

        // Thrash detector: CollectIron <-> HolePillar at same xz.
        HolePillar.noteChildFlip(mod, childName);

        // 3b. Mining with a block in hand. HolePillar/PlaceBlocks leave dirt or cobble
        // selected; if CollectIron then mines with it, every block break is ~10x slower.
        // Gate on the pillar/place machinery NOT being the active child — that is the only
        // time a non-pick in hand is legitimate. (An earlier draft required
        // PlaceBlocks.count()==0, which almost never holds in IRON since the bot carries
        // cobble, so S109 would never have fired.)
        boolean placingChild = childName.contains("HolePillar") || childName.contains("Place")
                || childName.contains("Bridge") || childName.contains("Construct")
                || childName.contains("Pillar");
        if (!"BOOTSTRAP".equals(phase) && !"LOOT".equals(phase) && !placingChild
                && (childName.contains("CollectIron") || childName.contains("Mine")
                    || childName.contains("Smelt"))
                && !childName.contains("Log")) {
            net.minecraft.item.Item held = heldItem();
            if (!isPick(held)) {
                net.minecraft.item.Item pick = anyPick();
                if (pick != null && ++wrongToolHits >= 6) {
                    act("S109", "equip pick for mining (was holding "
                            + (held == null || held == Items.AIR ? "empty" : held.toString()) + ")");
                    try {
                        mod.getSlotHandler().forceEquipItem(pick);
                    } catch (Throwable ignored) {}
                    wrongToolHits = 0;
                }
            } else {
                wrongToolHits = 0;
            }
        } else {
            wrongToolHits = 0;
        }

        // 4. 1x1 shaft only. Pause CollectIron so it cannot mine the pillar.
        // While holding OR on failCool, never hand control back / never re-arm S130.
        if (HolePillar.holding()) {
            return new HolePillarTask();
        }
        boolean walking = childName.contains("GetToBlock") || childName.contains("Wander") || childName.contains("HolePillar");
        boolean wouldPillar = !wet && !"PORTAL".equals(phase)
                && !childName.contains("Craft") && !childName.contains("StepOff")
                && !walking
                && HolePillar.boxed(mod) && HolePillar.hasPlace(mod)
                && (flips >= 3 || sameXz > 20 * 2);
        if (wouldPillar && HolePillar.busy()) {
            HolePillar.logSuppress(mod, "would-S130 but busy cool=" + HolePillar.failCoolLeft()
                    + " flips=" + flips + " sameXz=" + sameXz + " child=" + childName);
        }
        // S147 GUARD. A column that already ate a pillar ping-pong (E133) is refused for a
        // while. Without this the ordinary guard below re-arms S130 at the same column the
        // tick the sticky ban's cousin (banTicks) is cleared by a 2-block nudge, and the
        // bot burns pillar after pillar in the same hole. Observed run F: one column armed
        // S130 8 times, another 10 times across three different Y levels.
        if (wouldPillar && HolePillar.stickyBlocked(mod)) {
            HolePillar.logSuppress(mod, "would-S130 but sticky-banned @"
                    + HolePillar.stickyColumn() + " left=" + HolePillar.stickyTicksLeft());
        }
        // S158: the bot has pillared out of several DIFFERENT shafts recently. Every
        // column-scoped guard above is blind to that (see HolePillar noteHop), so without
        // this the bot just digs the neighbouring shaft and pillars again, forever.
        if (wouldPillar && HolePillar.hopBlocked(mod, sameXz)) {
            HolePillar.logSuppress(mod, "would-S130 but shaft-hop-banned "
                    + HolePillar.hopBanInfo() + " sameXz=" + sameXz);
        }
        if (!wet && !"PORTAL".equals(phase)
                && !childName.contains("Craft") && !childName.contains("StepOff")
                && !walking
                && !HolePillar.busy()
                && !HolePillar.stickyBlocked(mod)
                && !HolePillar.hopBlocked(mod, sameXz)
                && HolePillar.boxed(mod) && HolePillar.hasPlace(mod)
                && (flips >= 3 || sameXz > 20 * 2)) {
            String trig = flips >= 3 ? ("flips=" + flips) : ("sameXz=" + sameXz);
            HolePillar.logStart(mod, phase, childName, trig);
            act("S130", "pillar-out @" + x + "," + y + "," + z + " ph=" + phase + " " + trig);
            cool = 20 * 4;
            cancelPath(mod);
            return new HolePillarTask();
        }
        if ("S130".equals(lastFix) && cool > 0 && HolePillar.holding()) {
            return new HolePillarTask();
        }

        // 4b. Boxed but NO place blocks — mine one, then pillar.
        //
        // S145 SOLVER. Both pillar checks above require HolePillar.hasPlace(mod), so a
        // bot trapped in a 1x1 shaft with an empty inventory matches NEITHER branch:
        // no S130, no diagnostic, no recovery — it just spins (and S100/E100 fire on the
        // jump-in-place). Observed run D: `S133 cool arm ticks=240 reason=!hasPlace y=62`,
        // `S136 END reason=!hasPlace ... place=0 boxed=true` at -309,62,137, sky=15, with
        // a stone pickaxe in hand and solid stone all around.
        //
        // The escape is trivial: mine one block of the shaft wall to get cobblestone,
        // then the ordinary S130 pillar works. Escalate only after a couple of seconds of
        // no progress so this never preempts a pillar that is about to arm anyway.
        if (!wet && !"PORTAL".equals(phase) && !walking
                && HolePillar.boxed(mod) && !HolePillar.hasPlace(mod)
                && hasPick(mod) && !HolePillar.busy()
                && sameXz > 20 * 3) {
            s145Ticks++;
            if (s145Ticks % (20 * 5) == 1) {
                act("S145", "boxed, no place blocks — mine cobble @" + x + "," + y + "," + z
                        + " sameXz=" + sameXz);
            }
            // Dig a wall block at feet level. The parent's collector child may fight this,
            // which is why the escalation ladder below exists (S144 replaced it), but a
            // 1x1 shaft has no valid collector target anyway.
            BlockPos wall = shaftWall(mod);
            if (wall != null) {
                return new DestroyBlockTask(wall);
            }
        } else if (s145Ticks != 0) {
            s145Ticks = 0;
        }

        // 5. Jump in place - walk. Do not pillar a tunnel.
        if (!wet && flips >= 6 && sameXz > 20 * 2 && !"BOOTSTRAP".equals(phase)
                && !childName.contains("StepOff") && !walking) {
            // S147 guard applies here too - this is the second S130 arming site.
            if (HolePillar.stickyBlocked(mod) || HolePillar.hopBlocked(mod, sameXz)) {
                HolePillar.logSuppress(mod, "jump-pit " + (HolePillar.hopBlocked(mod, sameXz)
                        ? "shaft-hop-banned " + HolePillar.hopBanInfo() + " sameXz=" + sameXz
                        : "sticky-banned @" + HolePillar.stickyColumn()
                          + " left=" + HolePillar.stickyTicksLeft()));
                flips = 0;
                adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
                if (childName.contains("Craft") || childName.contains("Collect") || childName.contains("Mine")) {
                    McCompat.closeScreen();
                }
                adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
                cancelPath(mod);
                sameXz = 0;
                return null;
            }
            if (HolePillar.hasPlace(mod) && HolePillar.boxed(mod) && !HolePillar.busy()) {
                HolePillar.logStart(mod, phase, childName, "jump-pit flips=" + flips);
                act("S130", "jump-pit pillar @" + x + "," + y + "," + z);
                cool = 20 * 4;
                cancelPath(mod);
                return new HolePillarTask();
            }
            if (HolePillar.busy()) {
                HolePillar.logSuppress(mod, "jump-pit-busy child=" + childName);
            }
            act("S100", "stop jump-walk @" + x + "," + z + " ph=" + phase + " child=" + childName
                    + " esc=" + s100Escalations);
            adris.altoclef.tasks.speedrun.testrun2.core.T2Input.noJump();
            flips = 0;
            if (childName.contains("Craft") || childName.contains("Collect") || childName.contains("Mine")) {
                McCompat.closeScreen();
                adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
                cancelPath(mod);
            } else if (!portalWork && !childName.contains("Construct")) {
                adris.altoclef.tasks.speedrun.testrun2.core.T2Input.walkTurn();
            }
            // S100 SOLVER. cancelPath() + walkTurn() is not enough when the child is a
            // resource collector: CollectIronIngotTask (and friends) recompute their
            // target on the very next tick and path straight back to the same block, so
            // the nudge is undone before it can move the bot. Observed run C: S100 fired
            // twice (8:02.6, 8:08.9) with the child still ConstructNetherPortalBucketTask
            // and the bot still at -279,222.
            //
            // Escalate: after two nudges at the same column in the same phase, stop
            // nudging and hand the tick to a real replacement task. The parent adopts it
            // (non-null return bypasses the class-equality reuse check), which is the
            // only way to break a collector's re-path loop from outside.
            if (!portalWork && !"PORTAL".equals(phase) && !"BOOTSTRAP".equals(phase)) {
                s100Escalations++;
                if (s100Escalations >= 2) {
                    act("S144", "jump-stuck collector — replace " + childName
                            + " with wander (esc=" + s100Escalations + ")");
                    s100Escalations = 0;
                    sameXz = 0;
                    cool = 20 * 3;
                    cancelPath(mod);
                    return new TimeoutWanderTask();
                }
                return null;
            }
            // In PORTAL/BOOTSTRAP, the construct/wood child must stay sticky; S111/S100
            // are overlay-only there by design. Reset the escalation so a later phase
            // does not inherit a stale count.
            s100Escalations = 0;
            return null;
        }

        // 5. Lava and no water during construct â€” log only. Do not swap the child.
        if (count(mod, Items.LAVA_BUCKET) >= 1 && count(mod, Items.WATER_BUCKET) < 1
                && ("PORTAL".equals(phase) || childName.contains("Construct"))) {
            act("S105", "need water (overlay only, not swapping construct)");
            return null;
        }

        // 6. Starve
        int hun = 20;
        try { hun = mod.getPlayer().getHungerManager().getFoodLevel(); } catch (Throwable ignored) {}
        if (hun <= 4 && !hasFood(mod) && !portalWork && !childName.contains("Craft")) {
            act("S103", "hun=" + hun + " starving (overlay only)");
            return null;
        }

        // 7. Piglin, no gold â€” log only. Replacing the tunnel with @get helm is how escape dies.
        if (WorldHelper.getCurrentDimension() == Dimension.NETHER
                && count(mod, Items.GOLDEN_HELMET) < 1
                && piglinNear(mod)
                && !childName.contains("Gold")) {
            act("S110", "piglin nearby, no gold helm (overlay only)");
            return null;
        }

        // 8. Null child in a live phase
        if (child == null && "PORTAL".equals(phase)) {
            act("S111", "portal child missing â€” parent must set construct");
            return null;
        }

        // 9. Actually replace the child. Overlay-only is why wood sat 90s.
        if (!portalWork && !"IRON".equals(phase) && !"PORTAL".equals(phase)
                && !childName.contains("Wander")
                && sameXz > holdFor(phase, childName)) {
            act("S140", "replace " + childName + " after " + (sameXz / 20) + "s same xz");
            sameXz = 0;
            flips = 0;
            cancelPath(mod);
            return new TimeoutWanderTask();
        }

        return null;
    }

    private static int holdFor(String phase, String child) {
        // S168: SurfaceBailTask gets its own hold, LONGER than its own timeout.
        //
        // It used to fall through to the 40s default while SurfaceBailTask.MAX_TICKS is
        // 120s, so S140 replaced the bail every 40s and the 120s timeout that the whole
        // give-up / S156 / S161 chain depends on could never be reached. Run T sat at
        // 29,31,-39 with an empty inventory, sky=0 and pick=0 while S140 fired 14 times over
        // five minutes, each replacement resetting the clock and discarding whatever
        // progress the escape had made. Two constants that describe the same task must not
        // disagree: hold 20s beyond the bail's own budget so it always gets to finish.
        //
        // S169: this MUST be the first branch. It used to sit below the phase checks, so a
        // SurfaceBailTask running during PORTAL got the PORTAL hold (90s) instead of 140s —
        // still shorter than MAX_TICKS (120s), i.e. the exact S168 starvation bug, just
        // relocated to a different phase. An escape task's budget comes from the escape
        // task, not from whatever phase happens to be running.
        if (child.contains("SurfaceBail") || child.contains("WaterBail")) return 20 * 140;
        if ("BOOTSTRAP".equals(phase) && (child.contains("Mine") || child.contains("Collect"))) return 20 * 8;
        if ("IRON".equals(phase) && child.contains("Collect")) return 20 * 25;
        if ("PORTAL".equals(phase)) return 20 * 90;
        return 20 * 40;
    }

    private static void cancelPath(AltoClef mod) {
        try {
            mod.getClientBaritone().getPathingBehavior().cancelEverything();
        } catch (Throwable ignored) {}
    }

    private static void act(String code, String plan) {
        if (code.equals(lastFix) && cool > 0) return;
        lastFix = code;
        cool = 20 * 6;
        T2Log.warn(code, plan);
        T2History.note("SOLVE " + code + " " + plan);
    }

    /**
     * S184: a GUI whose work takes real TIME, so standing still with it open is correct.
     *
     * <p>Smelting takes 10s per item; brewing takes 20s per potion. These are the opposite of
     * a crafting table, where a craft resolves in a tick and a motionless bot means something
     * is wrong. Treating them alike is what produced the furnace spin.
     */
    private static boolean slowGui() {
        try {
            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.currentScreen == null) return false;
            String n = mc.currentScreen.getClass().getSimpleName();
            return n.contains("Furnace") || n.contains("Brew");
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean workGui() {
        try {
            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.currentScreen == null) return false;
            String n = mc.currentScreen.getClass().getSimpleName();
            return n.contains("Craft") || n.contains("Inventor") || n.contains("Furnace")
                    || n.contains("Anvil") || n.contains("Chest") || n.contains("Barrel")
                    || n.contains("Shulker") || n.contains("Hopper") || n.contains("Merchant")
                    || n.contains("Enchant") || n.contains("Brew") || n.contains("Smith")
                    || n.contains("Grind") || n.contains("Loom") || n.contains("Container")
                    || n.contains("Handled");
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean guiOpen() {
        try {
            var mc = MinecraftClient.getInstance();
            if (mc == null || mc.currentScreen == null) return false;
            String n = mc.currentScreen.getClass().getSimpleName();
            if (n.contains("Chat") || n.contains("T2Menu") || n.contains("GameMenu")
                    || n.contains("Death") || n.contains("Title") || n.contains("Pause")) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean tableUnder(AltoClef mod) {
        try {
            BlockPos feet = mod.getPlayer().getBlockPos();
            return mod.getWorld().getBlockState(feet).isOf(Blocks.CRAFTING_TABLE)
                    || mod.getWorld().getBlockState(feet.down()).isOf(Blocks.CRAFTING_TABLE);
        } catch (Throwable t) {
            return false;
        }
    }

    private static double speed(AltoClef mod) {
        try {
            var v = mod.getPlayer().getVelocity();
            return Math.sqrt(v.x * v.x + v.z * v.z);
        } catch (Throwable t) {
            return 1;
        }
    }

    private static int count(AltoClef mod, net.minecraft.item.Item item) {
        try {
            return mod.getItemStorage().getItemCount(item);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean hasFood(AltoClef mod) {
        return count(mod, Items.BREAD) + count(mod, Items.APPLE)
                + count(mod, Items.COOKED_BEEF) + count(mod, Items.COOKED_PORKCHOP)
                + count(mod, Items.COOKED_CHICKEN) + count(mod, Items.CARROT) > 0;
    }

    private static boolean piglinNear(AltoClef mod) {
        try {
            var list = mod.getEntityTracker().getTrackedEntities(PiglinEntity.class);
            if (list == null || mod.getPlayer() == null) return false;
            var me = mod.getPlayer().getPos();
            for (var e : list) {
                if (e != null && e.isAlive() && me.distanceTo(e.getPos()) < 16) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Any pickaxe at all — needed before S145 will try to mine a wall block. */
    private static boolean hasPick(AltoClef mod) {
        return count(mod, Items.WOODEN_PICKAXE) >= 1
                || count(mod, Items.STONE_PICKAXE) >= 1
                || count(mod, Items.IRON_PICKAXE) >= 1
                || count(mod, Items.GOLDEN_PICKAXE) >= 1
                || count(mod, Items.DIAMOND_PICKAXE) >= 1
                || count(mod, Items.NETHERITE_PICKAXE) >= 1;
    }

    /**
     * Pick a solid, mineable wall block at feet level around the player so a trapped bot
     * can obtain cobblestone. Returns null if every side is already air (not a shaft).
     *
     * Deliberately avoids the block directly below the feet: digging down is what got the
     * bot into the shaft in the first place, and the wall block is just as good a source
     * of cobblestone.
     */
    private static BlockPos shaftWall(AltoClef mod) {
        try {
            if (mod.getPlayer() == null || mod.getWorld() == null) return null;
            BlockPos feet = mod.getPlayer().getBlockPos();
            BlockPos[] sides = new BlockPos[]{
                    feet.north(), feet.south(), feet.east(), feet.west(),
                    feet.add(0, 1, 0).north(), feet.add(0, 1, 0).south(),
                    feet.add(0, 1, 0).east(), feet.add(0, 1, 0).west(),
            };
            for (BlockPos p : sides) {
                var state = mod.getWorld().getBlockState(p);
                if (state == null || state.isAir()) continue;
                if (!state.getMaterial().blocksMovement()) continue;
                // Must be breakable with a pickaxe (not bedrock / obsidian).
                float hardness = state.getHardness(mod.getWorld(), p);
                if (hardness < 0 || hardness > 10.0f) continue;
                if (state.getBlock() == Blocks.BEDROCK) continue;
                return p.toImmutable();
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
