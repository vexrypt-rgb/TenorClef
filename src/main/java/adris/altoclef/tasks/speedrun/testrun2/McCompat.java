package adris.altoclef.tasks.speedrun.testrun2;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 1.16–1.21 field/method differences without ReplayMod #if.
 * Missing items/blocks resolve to null. Callers must skip nulls.
 */
public final class McCompat {

    private McCompat() {}

    /** 16 for 1.16.x, 17 for 1.17.x, … 21 for 1.21.x. 0 if unknown. */
    public static int gameMinor() {
        try {
            Object ver = Class.forName("net.minecraft.SharedConstants")
                    .getMethod("getGameVersion").invoke(null);
            String name = String.valueOf(ver.getClass().getMethod("getName").invoke(ver));
            if (name.startsWith("1.16")) return 16;
            if (name.startsWith("1.17")) return 17;
            if (name.startsWith("1.18")) return 18;
            if (name.startsWith("1.19")) return 19;
            if (name.startsWith("1.20")) return 20;
            if (name.startsWith("1.21")) return 21;
            if (name.startsWith("1.22")) return 22;
            if (name.startsWith("25.")) return 21;
            if (name.startsWith("26.")) return 26;
        } catch (Throwable ignored) {}
        if (item("SULFUR") != null || item("CINNABAR") != null) return 26;
        if (item("IRON_SPEAR") != null || item("SPEAR") != null) return 21;
        if (item("DRIED_GHAST") != null || item("CREAKING_HEART") != null) return 21;
        if (item("MACE") != null) return 21;
        if (item("SNIFFER_EGG") != null) return 20;
        if (item("ECHO_SHARD") != null) return 19;
        if (item("MUSIC_DISC_OTHERSIDE") != null) return 18;
        if (item("AXOLOTL_BUCKET") != null) return 17;
        if (item("NETHERITE_INGOT") != null) return 16;
        return 16;
    }

    public static Item item(String name) {
        try {
            return (Item) Items.class.getField(name).get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Block block(String name) {
        try {
            return (Block) Blocks.class.getField(name).get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean gone(Entity e) {
        if (e == null) return true;
        try {
            if (!e.isAlive()) return true;
        } catch (Throwable ignored) {}
        try {
            Object v = e.getClass().getMethod("isRemoved").invoke(e);
            if (v instanceof Boolean && (Boolean) v) return true;
        } catch (Throwable ignored) {
            try {
                if (e.getClass().getField("removed").getBoolean(e)) return true;
            } catch (Throwable ignored2) {}
        }
        return false;
    }

    public static Object mc() {
        try {
            return Class.forName("net.minecraft.client.MinecraftClient")
                    .getMethod("getInstance").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object player() {
        Object mc = mc();
        if (mc == null) return null;
        try {
            Object p = mc.getClass().getMethod("player").invoke(mc);
            if (p != null) return p;
        } catch (Throwable ignored) {}
        try {
            return mc.getClass().getField("player").get(mc);
        } catch (Throwable t) {
            return null;
        }
    }

    public static float playerYaw() {
        Object p = player();
        if (p == null) return 0f;
        try { return p.getClass().getField("yaw").getFloat(p); } catch (Throwable ignored) {}
        try { return (Float) p.getClass().getMethod("getYaw").invoke(p); } catch (Throwable ignored) {}
        return 0f;
    }

    public static void setYaw(float yaw) {
        Object p = player();
        if (p == null) return;
        try { p.getClass().getField("yaw").setFloat(p, yaw); } catch (Throwable ignored) {}
        try { p.getClass().getMethod("setYaw", float.class).invoke(p, yaw); } catch (Throwable ignored) {}
    }

    public static void setMove(boolean forward, boolean jump) {
        Object p = player();
        if (p != null) {
            try {
                Object input = p.getClass().getField("input").get(p);
                if (input != null) {
                    try { input.getClass().getField("pressingForward").setBoolean(input, forward); } catch (Throwable ignored) {}
                    try { input.getClass().getField("jumping").setBoolean(input, jump); } catch (Throwable ignored) {}
                    try { input.getClass().getField("movementForward").setFloat(input, forward ? 1f : 0f); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            try { p.getClass().getMethod("setSprinting", boolean.class).invoke(p, forward); } catch (Throwable ignored) {}
        }
        try {
            Object mc = mc();
            if (mc == null) return;
            Object options = null;
            try { options = mc.getClass().getField("options").get(mc); } catch (Throwable ignored) {}
            if (options == null) {
                try { options = mc.getClass().getMethod("getOptions").invoke(mc); } catch (Throwable ignored) {}
            }
            if (options == null) return;
            Object keyFwd = null;
            Object keyJump = null;
            try { keyFwd = options.getClass().getField("keyForward").get(options); } catch (Throwable ignored) {}
            try { keyJump = options.getClass().getField("keyJump").get(options); } catch (Throwable ignored) {}
            if (keyFwd != null) {
                try { keyFwd.getClass().getMethod("setPressed", boolean.class).invoke(keyFwd, forward); } catch (Throwable ignored) {}
            }
            if (keyJump != null) {
                try { keyJump.getClass().getMethod("setPressed", boolean.class).invoke(keyJump, jump); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public static void cancelPathing() {
        try {
            Object mod = Class.forName("adris.altoclef.AltoClef").getMethod("getInstance").invoke(null);
            if (mod == null) return;
            Object bari = mod.getClass().getMethod("getClientBaritone").invoke(mod);
            if (bari == null) return;
            Object path = bari.getClass().getMethod("getPathingBehavior").invoke(bari);
            if (path != null) {
                try { path.getClass().getMethod("cancelEverything").invoke(path); } catch (Throwable ignored) {}
            }
            try {
                Object in = bari.getClass().getMethod("getInputOverrideHandler").invoke(bari);
                if (in != null) in.getClass().getMethod("clearAllKeys").invoke(in);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    public static void closeScreen() {
        try {
            Object mc = Class.forName("net.minecraft.client.MinecraftClient")
                    .getMethod("getInstance").invoke(null);
            if (mc == null) return;
            Object player = mc.getClass().getMethod("player").invoke(mc);
            if (player == null) {
                try { player = mc.getClass().getField("player").get(mc); } catch (Throwable ignored) {}
            }
            if (player != null) {
                try {
                    player.getClass().getMethod("closeHandledScreen").invoke(player);
                    return;
                } catch (Throwable ignored) {}
                try {
                    player.getClass().getMethod("closeScreen").invoke(player);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public static String biomePath(World world, BlockPos pos) {
        if (world == null || pos == null) return "?";
        try {
            Object biome = world.getClass().getMethod("getBiome", BlockPos.class).invoke(world, pos);
            if (biome == null) return "?";
            try {
                Object opt = biome.getClass().getMethod("getKey").invoke(biome);
                if (opt instanceof java.util.Optional) {
                    java.util.Optional<?> o = (java.util.Optional<?>) opt;
                    if (o.isPresent()) {
                        Object key = o.get();
                        Object val = key.getClass().getMethod("getValue").invoke(key);
                        return String.valueOf(val.getClass().getMethod("getPath").invoke(val));
                    }
                }
            } catch (Throwable ignored) {}
            return biome.getClass().getSimpleName();
        } catch (Throwable t) {
            return "?";
        }
    }
}
