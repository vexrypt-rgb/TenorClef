package adris.altoclef.tasks.speedrun.testrun2.agent;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasks.speedrun.testrun2.SpeedrunClock;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.item.Items;

public final class AgentSnapshot {

    private AgentSnapshot() {}

    public static String json(AltoClef mod, String phase, Task child) {
        int x = 0, y = 0, z = 0, hp = 0, hun = 20;
        String dim = "?";
        try { dim = String.valueOf(WorldHelper.getCurrentDimension()); } catch (Throwable ignored) {}
        if (mod.getPlayer() != null) {
            x = mod.getPlayer().getBlockX();
            y = mod.getPlayer().getBlockY();
            z = mod.getPlayer().getBlockZ();
            hp = (int) mod.getPlayer().getHealth();
            try { hun = mod.getPlayer().getHungerManager().getFoodLevel(); } catch (Throwable ignored) {}
        }
        String ch = child == null ? "-" : child.getClass().getSimpleName();
        StringBuilder inv = new StringBuilder();
        addInv(inv, mod, "iron_ingot", Items.IRON_INGOT);
        addInv(inv, mod, "iron_pickaxe", Items.IRON_PICKAXE);
        addInv(inv, mod, "stone_pickaxe", Items.STONE_PICKAXE);
        addInv(inv, mod, "wooden_pickaxe", Items.WOODEN_PICKAXE);
        addInv(inv, mod, "cobblestone", Items.COBBLESTONE);
        addInv(inv, mod, "oak_log", Items.OAK_LOG);
        addInv(inv, mod, "bread", Items.BREAD);
        addInv(inv, mod, "water_bucket", Items.WATER_BUCKET);
        addInv(inv, mod, "lava_bucket", Items.LAVA_BUCKET);
        addInv(inv, mod, "ender_eye", Items.ENDER_EYE);
        addInv(inv, mod, "blaze_rod", Items.BLAZE_ROD);
        addInv(inv, mod, "ender_pearl", Items.ENDER_PEARL);
        if (inv.length() == 0) inv.append("");
        return "{"
                + "\"t\":\"" + SpeedrunClock.now() + "\","
                + "\"dim\":\"" + dim + "\","
                + "\"xyz\":[" + x + "," + y + "," + z + "],"
                + "\"hp\":" + hp + ","
                + "\"hunger\":" + hun + ","
                + "\"phase\":\"" + esc(phase) + "\","
                + "\"child\":\"" + esc(ch) + "\","
                + "\"goal\":\"" + esc(adris.altoclef.tasks.speedrun.testrun2.util.AgentGoal.get()) + "\","
                + "\"last_fault\":\"" + esc(adris.altoclef.tasks.speedrun.testrun2.T2Fault.recent()) + "\","
                + "\"eyes\":" + adris.altoclef.tasks.speedrun.testrun2.util.EyeGate.count(mod) + ","
                + "\"inv\":{" + inv + "}"
                + "}";
    }

    private static void addInv(StringBuilder sb, AltoClef mod, String name, net.minecraft.item.Item item) {
        int n = 0;
        try { n = mod.getItemStorage().getItemCount(item); } catch (Throwable ignored) {}
        if (n <= 0) return;
        if (sb.length() > 0) sb.append(',');
        sb.append('"').append(name).append("\":").append(n);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
