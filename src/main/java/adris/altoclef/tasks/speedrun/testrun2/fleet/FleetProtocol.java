package adris.altoclef.tasks.speedrun.testrun2.fleet;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.ui.MessagePriority;
import java.util.Locale;

/**
 * Bot-to-bot whispers. Line after butler "` " :
 *   ~ ping
 *   ~ pong
 *   ~ do get oak_log 32
 *   ~ done oak_log
 */
public final class FleetProtocol {

    public static final String TAG = "~ ";

    private static String lastAssign;
    private static int lastCount;

    private FleetProtocol() {}

    /** @return true if consumed (not a normal butler reject). */
    public static boolean handle(String from, String message) {
        if (from == null || message == null) return false;
        String body = message;
        if (body.startsWith("`")) body = body.substring(1).trim();
        if (!body.startsWith("~")) return false;
        body = body.substring(1).trim();
        if (!Fleet.known(from)) {
            Debug.logMessage("FLEET ignore unknown " + from);
            return true;
        }
        String[] p = body.split("\\s+");
        if (p.length == 0) return true;
        String op = p[0].toLowerCase(Locale.ROOT);
        AltoClef mod = AltoClef.getInstance();
        switch (op) {
            case "ping" -> whisper(mod, from, "~ pong");
            case "pong" -> Debug.logMessage("FLEET pong from " + from);
            case "do" -> {
                if (mod != null && mod.getPlayer() != null
                        && from.equalsIgnoreCase(mod.getPlayer().getName().getString())) {
                    return true;
                }
                if (p.length < 3) return true;
                String kind = p[1];
                String item = p[2];
                int n = p.length >= 4 ? parse(p[3], 1) : 1;
                lastAssign = item;
                lastCount = n;
                Debug.logMessage("FLEET " + from + " assigned " + kind + " " + item + " x" + n);
                if ("get".equals(kind)) {
                    try {
                        var task = TaskCatalogue.getItemTask(item, n);
                        if (task != null && mod != null) {
                            mod.runUserTask(task, () -> whisper(mod, from, "~ done " + item));
                        } else {
                            whisper(mod, from, "~ fail " + item);
                        }
                    } catch (Throwable t) {
                        whisper(mod, from, "~ fail " + item);
                    }
                }
            }
            case "done" -> Debug.logMessage("FLEET " + from + " done " + (p.length > 1 ? p[1] : ""));
            case "fail" -> Debug.logMessage("FLEET " + from + " fail " + (p.length > 1 ? p[1] : ""));
            default -> Debug.logMessage("FLEET " + from + " " + body);
        }
        return true;
    }

    public static void broadcast(AltoClef mod, String line) {
        for (String name : Fleet.members()) {
            if (mod.getPlayer() != null && name.equalsIgnoreCase(mod.getPlayer().getName().getString())) {
                continue;
            }
            whisper(mod, name, line);
        }
    }

    public static void whisper(AltoClef mod, String user, String line) {
        try {
            mod.getMessageSender().enqueueWhisper(user, "` " + line, MessagePriority.TIMELY);
        } catch (Throwable t) {
            Debug.logWarning("FLEET whisper " + t.getMessage());
        }
    }

    private static int parse(String s, int d) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return d;
        }
    }
}
