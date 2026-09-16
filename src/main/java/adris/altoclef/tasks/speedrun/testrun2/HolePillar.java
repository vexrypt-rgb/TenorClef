package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/**
 * 1-wide hole + blocks in hotbar → look down, jump, place under feet.
 * S100 no-jump is the opposite of this and is why the bot hops forever.
 */
public final class HolePillar {

    private static int step;
    private static int lastY = Integer.MIN_VALUE;
    private static int rose;
    private static int failCool;
    private static int startY = Integer.MIN_VALUE;
    private static boolean holding;

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
        return wallCount(mod, feet) >= 4 && wallCount(mod, feet.add(0, 1, 0)) >= 4;
    }

    public static boolean givingUp() {
        return failCool > 0;
    }

    public static boolean busy() {
        return holding || failCool > 0;
    }

    public static void coolTick() {
        if (failCool > 0) failCool--;
        if (failCool == 0 && !holding) { /* idle */ }
    }

    private static int wallCount(AltoClef mod, BlockPos feet) {
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
        if (failCool > 0) {
            failCool--;
            holding = false;
            release();
            return false;
        }
        if (!boxed(mod) || !hasPlace(mod)) {
            step = 0;
            holding = false;
            return false;
        }
        int y = mod.getPlayer().getBlockY();
        if (startY == Integer.MIN_VALUE) startY = y;
        lastY = y;
        // A 1-block hop is NOT progress. Need +3 from where pillar started.
        if (y >= startY + 3) {
            reset();
            release();
            return false;
        }
        if (step >= 30 && y <= startY + 1) {
            failCool = 20 * 12;
            T2Log.warn("S131", "pillar no rise startY=" + startY + " y=" + y + " — walk");
            reset();
            release();
            return false;
        }
        int slot = PlaceBlocks.equip(mod);
        if (slot < 0) {
            step = 0;
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
        if (step % 20 == 1) T2Log.warn("S130", "pillar-out y=" + y + " slot=" + slot);
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
