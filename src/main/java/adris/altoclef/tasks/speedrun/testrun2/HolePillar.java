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
        release();
    }

    public static boolean boxed(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return false;
        BlockPos feet = mod.getPlayer().getBlockPos();
        // Geometry only. Sky light lies (caves, overhangs, night).
        // 4 walls at feet AND head = 1x1 shaft. A 3-wall cave tunnel is not a pit.
        // Also require sides below feet so a 1-deep surface dip does not arm S130.
        return wallCount(mod, feet) >= 4 && wallCount(mod, feet.add(0, 1, 0)) >= 4
                && wallCount(mod, feet.add(0, -1, 0)) >= 3;
    }

    public static boolean givingUp() {
        return failCool > 0;
    }

    public static boolean busy() {
        return holding || failCool > 0;
    }

    public static boolean holding() {
        return holding;
    }

    public static int failCoolLeft() {
        return failCool;
    }

    public static int startY() {
        return startY;
    }

    public static String lastEndReason() {
        return lastEndReason;
    }

    /** Climbed far enough from pillar start (1-block hop is not enough). */
    public static boolean risenEnough(AltoClef mod) {
        if (startY == Integer.MIN_VALUE || mod == null || mod.getPlayer() == null) return false;
        int y = mod.getPlayer().getBlockY();
        if (y >= startY + 3) return true;
        // Opened out after rising - treat as clear (not a boxed flicker at startY).
        return y >= startY + 2 && !boxed(mod);
    }

    public static void coolTick() {
        if (failCool > 0) {
            failCool--;
            coolWasOn = true;
            if (failCool == 0) {
                T2Log.force("S134", "pillar cool expire after " + lastArmReason
                        + " end=" + lastEndReason);
                coolWasOn = false;
            }
        } else if (coolWasOn) {
            coolWasOn = false;
        }
    }

    /** Dense one-liner: walls/sky/place/hold/cool/y. */
    public static String snap(AltoClef mod) {
        if (mod == null || mod.getPlayer() == null || mod.getWorld() == null) {
            return "pos=? walls=?/?/? sky=? place=? hold=" + holding + " cool=" + failCool;
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
                + " startY=" + (startY == Integer.MIN_VALUE ? "-" : String.valueOf(startY))
                + " step=" + step;
    }

    /** Why S130 armed. Call once when entering HolePillarTask. */
    public static void logStart(AltoClef mod, String phase, String prevChild, String trigger) {
        lastArmReason = trigger;
        T2Log.force("S130", "START trigger=" + trigger
                + " ph=" + phase
                + " prev=" + prevChild
                + " " + snap(mod));
        T2History.note("PILLAR START " + trigger + " prev=" + prevChild);
    }

    /** Why escape ended. Sets failCool when armCoolTicks > 0. */
    public static void logEnd(AltoClef mod, String reason, int armCoolTicks) {
        lastEndReason = reason;
        if (armCoolTicks > 0) {
            failCool = armCoolTicks;
            T2Log.force("S133", "cool arm ticks=" + armCoolTicks + " reason=" + reason
                    + " " + snap(mod));
        }
        T2Log.force("S136", "END reason=" + reason
                + " risen=" + risenEnough(mod)
                + " giveUp=" + givingUp()
                + " " + snap(mod));
        T2History.note("PILLAR END " + reason);
    }

    /** S130 blocked because cool/busy. At most once per 2s. */
    public static void logSuppress(AltoClef mod, String why) {
        long now = System.currentTimeMillis();
        if (now - lastSuppressMs < 2000) return;
        lastSuppressMs = now;
        T2Log.force("S135", "S130 suppressed why=" + why
                + " cool=" + failCool
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
        int x = mod.getPlayer().getBlockX();
        int z = mod.getPlayer().getBlockZ();
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
                        + " @" + x + "," + mod.getPlayer().getBlockY() + "," + z
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
        int y = mod.getPlayer().getBlockY();
        if (holding && risenEnough(mod)) {
            logEnd(mod, "risen y=" + y + " startY=" + startY, 20 * 4);
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
                try {
                    st = mod.getPlayer().inventory.getStack(i);
                } catch (Throwable t) {
                    st = mod.getPlayer().getInventory().getStack(i);
                }
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
            return s != null && !s.isAir() && s.getMaterial().isSolid();
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
            MinecraftClient.getInstance().player.pitch = 90f;
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