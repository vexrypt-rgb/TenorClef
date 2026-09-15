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

    private HolePillar() {}

    public static void reset() {
        step = 0;
        lastY = Integer.MIN_VALUE;
        rose = 0;
        release();
    }

    public static boolean boxed(AltoClef mod) {
        if (mod.getPlayer() == null || mod.getWorld() == null) return false;
        BlockPos feet = mod.getPlayer().getBlockPos();
        if (!wall(mod, feet) || !wall(mod, feet.add(0, 1, 0))) return false;
        return solid(mod, feet.add(0, 2, 0));
    }

    private static boolean wall(AltoClef mod, BlockPos feet) {
        return solid(mod, feet.add(0, 0, -1)) && solid(mod, feet.add(0, 0, 1))
                && solid(mod, feet.add(1, 0, 0)) && solid(mod, feet.add(-1, 0, 0));
    }

    public static boolean hasPlace(AltoClef mod) {
        return slotOf(mod) >= 0;
    }

    /** One tick. Returns true if it took over inputs. */
    public static boolean tick(AltoClef mod) {
        if (mod.getPlayer() == null) return false;
        if (!boxed(mod) || !hasPlace(mod)) {
            step = 0;
            return false;
        }
        int y = mod.getPlayer().getBlockY();
        if (lastY != Integer.MIN_VALUE && y > lastY) rose++;
        lastY = y;
        if (rose >= 4) {
            // climbed out
            reset();
            release();
            return false;
        }
        int slot = slotOf(mod);
        if (slot >= 0) {
            try { mod.getPlayer().inventory.selectedSlot = slot; } catch (Throwable ignored) {
                try { mod.getPlayer().getInventory().selectedSlot = slot; } catch (Throwable ignored2) {}
            }
        }
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
