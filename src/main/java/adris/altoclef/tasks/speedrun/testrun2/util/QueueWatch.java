package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;

/**
 * 2b2t (and similar) queue: do not path, mine, or build until the
 * real world is loaded. Detected from chat + idle timeouts.
 */
public final class QueueWatch {

    private static boolean queued;
    private static boolean announced;
    private static int lastQueueTick;
    private static int tick;
    private static int lastChatScan;
    private static int worldTicks;
    private static String lastNewest = "";

    private QueueWatch() {}

    public static boolean blocked() {
        return queued;
    }

    public static void tick(AltoClef mod) {
        tick++;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        scanChat(mc);

        if (queued) {
            worldTicks = 0;
            McIdle();
            if (!announced) {
                announced = true;
                Debug.logMessage("QUEUE detected — TenorClef idle until you are in the world");
                RunLog.line("queue wait");
            }
            // Queue messages usually refresh. If they stop and we have ground, we are in.
            if (tick - lastQueueTick > 20 * 25 && looksLikeWorld(mod)) {
                release("timeout+world");
            }
            return;
        }

        if (looksLikeWorld(mod)) worldTicks++;
        else worldTicks = 0;
    }

    private static void scanChat(MinecraftClient mc) {
        if (tick - lastChatScan < 10) return;
        lastChatScan = tick;
        try {
            Object hud = mc.inGameHud;
            if (hud == null) return;
            Object chat = null;
            try { chat = hud.getClass().getMethod("getChatHud").invoke(hud); } catch (Throwable ignored) {}
            if (chat == null) {
                try { chat = hud.getClass().getField("chatHud").get(hud); } catch (Throwable ignored) {}
            }
            if (chat == null) return;
            List<?> lines = null;
            for (String m : new String[]{"getVisibleMessages", "getMessages", "getMessageHistory"}) {
                try {
                    Object o = chat.getClass().getMethod(m).invoke(chat);
                    if (o instanceof List<?> l) {
                        lines = l;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            if (lines == null) {
                for (var f : chat.getClass().getDeclaredFields()) {
                    if (List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object o = f.get(chat);
                        if (o instanceof List<?> l && !l.isEmpty()) {
                            lines = l;
                            break;
                        }
                    }
                }
            }
            if (lines == null || lines.isEmpty()) return;
            String newest = strip(String.valueOf(lines.get(lines.size() - 1)));
            if (newest.equals(lastNewest)) return;
            lastNewest = newest;
            String s = newest.toLowerCase(Locale.ROOT);
            if (s.contains("position in queue") || s.contains("in queue:")
                    || s.contains("queue position")
                    || (s.contains("estimated time") && s.contains("queue"))) {
                queued = true;
                lastQueueTick = tick;
            }
            if (s.contains("connecting to the server") || s.contains("sending you to")
                    || s.contains("you are being sent") || s.contains("entering the server")
                    || s.contains("connected to the server")) {
                release("chat");
            }
        } catch (Throwable ignored) {}
    }

    private static boolean looksLikeWorld(AltoClef mod) {
        try {
            if (mod == null || mod.getPlayer() == null || mod.getWorld() == null) return false;
            if (!mod.getPlayer().isOnGround()) return false;
            var feet = mod.getPlayer().getBlockPos();
            var st = mod.getWorld().getBlockState(feet.down());
            return !st.isAir();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void release(String why) {
        if (!queued && worldTicks == 0 && !announced) return;
        if (queued || announced) {
            Debug.logMessage("QUEUE done (" + why + ") — starting tasks");
            RunLog.line("queue done " + why);
        }
        queued = false;
        announced = false;
    }

    private static void McIdle() {
        try {
            adris.altoclef.tasks.speedrun.testrun2.McCompat.setMove(false, false);
            adris.altoclef.tasks.speedrun.testrun2.McCompat.cancelPathing();
        } catch (Throwable ignored) {}
    }

    private static String strip(String s) {
        return s.replaceAll("§.", "").replaceAll("\\u00a7.", "");
    }
}
