package adris.altoclef.butler;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.speedrun.testrun2.util.QueueWatch;

import java.util.Locale;
import java.util.Set;

/** Rate limit + deny-list so a whisper cannot start @escape / @agent. */
public final class ButlerGuard {

    private static final Set<String> DENY = Set.of(
            "escape", "agent", "ado", "t2reset", "reset",
            "mapart", "aa", "testrun2"
    );

    private static String lastUser = "";
    private static long lastMs;
    private static int burst;

    private ButlerGuard() {}

    public static String rejectReason(AltoClef mod, String user, String message) {
        if (QueueWatch.blocked()) return "in queue";
        String msg = message == null ? "" : message.trim();
        if (msg.isEmpty()) return "empty";
        ButlerConfig cfg = ButlerConfig.getInstance();
        if (cfg.requirePrefixMsg) {
            String prefix = "@";
            try { prefix = mod.getModSettings().getCommandPrefix(); } catch (Throwable ignored) {}
            if (!msg.startsWith(prefix) && !msg.startsWith("@")) {
                return "need prefix " + prefix;
            }
        }
        String head = msg;
        if (head.startsWith("@")) head = head.substring(1);
        int sp = head.indexOf(' ');
        String cmd = (sp < 0 ? head : head.substring(0, sp)).toLowerCase(Locale.ROOT);
        if (DENY.contains(cmd)) return "command denied: " + cmd;
        long now = System.currentTimeMillis();
        if (user.equalsIgnoreCase(lastUser) && now - lastMs < 1500) {
            burst++;
            if (burst > 4) return "rate limit";
        } else {
            burst = 0;
        }
        lastUser = user;
        lastMs = now;
        return null;
    }
}
