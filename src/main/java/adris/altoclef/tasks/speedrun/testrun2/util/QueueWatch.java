package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.GameMessageEvent;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

/**
 * 2b2t (and similar) queue: do not path, mine, or build until the
 * real world is loaded. Detected from the server's chat lines + idle timeouts.
 */
public final class QueueWatch {

    private static boolean queued;
    private static boolean announced;
    private static int lastQueueTick;
    private static int tick;
    private static int worldTicks;
    private static boolean listening;

    private QueueWatch() {}

    public static boolean blocked() {
        return queued;
    }

    public static void tick(AltoClef mod) {
        tick++;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        if (!listening) {
            listening = true;
            EventBus.subscribe(GameMessageEvent.class, evt -> onServerLine(evt.message));
        }

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

    /** A line the server wrote in chat. */
    static void onServerLine(String line) {
        if (line == null) return;
        String s = strip(line).toLowerCase(Locale.ROOT);
        if (isQueueLine(s)) {
            queued = true;
            lastQueueTick = tick;
        }
        if (isJoinLine(s)) release("chat");
    }

    static boolean isQueueLine(String s) {
        return s.contains("position in queue") || s.contains("in queue:")
                || s.contains("queue position")
                || (s.contains("estimated time") && s.contains("queue"));
    }

    static boolean isJoinLine(String s) {
        return s.contains("connecting to the server") || s.contains("sending you to")
                || s.contains("you are being sent") || s.contains("entering the server")
                || s.contains("connected to the server");
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
