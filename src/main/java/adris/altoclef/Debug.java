package adris.altoclef;

import net.minecraft.client.MinecraftClient;
import adris.altoclef.multiversion.TextVer;
import net.minecraft.text.Text;

// TODO: Debug library or use Minecraft's built in debugger
public class Debug {

    private static final int DEBUG_LOG_LEVEL = 0;
    private static final int WARN_LOG_LEVEL = 1;
    private static final int ERROR_LOG_LEVEL = 2;

    public static void logInternal(String message) {
        if (canLog(DEBUG_LOG_LEVEL)) {
            System.out.println("TENORCLEF: " + message);
        }
    }

    public static void logInternal(String format, Object... args) {
        logInternal(String.format(format, args));
    }

    /**
     * Unconditional stdout marker for the headless SIM harness.
     * <p>
     * {@link #logInternal} is gated by the {@code logLevel} setting: under the default
     * {@code NORMAL}, {@code canLog(DEBUG_LOG_LEVEL)} is FALSE, so every
     * {@code logInternal} call vanishes once {@code AltoClef} exists (i.e. exactly when
     * the harness needs it most). Markers the harness greps from {@code latest.log}
     * must therefore never be gated.
     * <p>
     * Prefix is {@code HARNESS:} so a run can be reconstructed with one grep.
     */
    public static void logHarness(String message) {
        System.out.println("TENORCLEF: HARNESS: " + message);
    }

    public static void logHarness(String format, Object... args) {
        logHarness(String.format(format, args));
    }

    private static String getLogPrefix() {
        AltoClef altoClef = AltoClef.getInstance();
        if (altoClef != null) {
            String p = altoClef.getModSettings().getChatLogPrefix();
            if (p != null && (p.contains("Alto") || p.contains("alto"))) {
                return "[TenorClef] ";
            }
            if (p != null && !p.isBlank()) return p;
        }
        return "[TenorClef] ";
    }

    public static void logMessage(String message, boolean prefix) {
        if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().player != null) {
            if (prefix) {
                message = "\u00A72\u00A7l\u00A7o" + getLogPrefix() + "\u00A7r" + message;
            }
            MinecraftClient.getInstance().player.sendMessage(TextVer.literal(message), false);

        } else {
            logInternal(message);
        }
    }

    public static void logMessage(String message) {
        logMessage(message, true);
    }

    public static void logMessage(String format, Object... args) {
        logMessage(String.format(format, args));
    }

    public static void logWarning(String message) {
        if (canLog(WARN_LOG_LEVEL)) {
            System.out.println("ALTO CLEF: WARNING: " + message);
        }

        AltoClef altoClef = AltoClef.getInstance();
        if (altoClef != null && !altoClef.getModSettings().shouldHideAllWarningLogs()) {
            if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().player != null) {
                String msg = "\u00A72\u00A7l\u00A7o" + getLogPrefix() + "\u00A7c" + message + "\u00A7r";
                MinecraftClient.getInstance().player.sendMessage(TextVer.literal(msg), false);

            }
        }
    }

    public static void logWarning(String format, Object... args) {
        logWarning(String.format(format, args));
    }

    public static void logError(String message) {
        String stacktrace = getStack(2);

        if (canLog(ERROR_LOG_LEVEL)) {
            System.err.println(message);
            System.err.println("at:");
            System.err.println(stacktrace);
        }

        if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().player != null) {
            String msg = "\u00A72\u00A7l\u00A7c" + getLogPrefix() + "[ERROR] " + message + "\nat:\n" + stacktrace + "\u00A7r";
            MinecraftClient.getInstance().player.sendMessage(TextVer.literal(msg), false);
        }
    }

    public static void logError(String format, Object... args) {
        logError(String.format(format, args));
    }

    public static void logStack() {
        logInternal("STACKTRACE: \n" + getStack(2));
    }

    private static String getStack(int toSkip) {
        StringBuilder stacktrace = new StringBuilder();
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            if (toSkip-- <= 0) {
                stacktrace.append(ste.toString()).append("\n");
            }
        }
        return stacktrace.toString();
    }

    private static boolean canLog(int level) {
        if (AltoClef.getInstance() == null || AltoClef.getInstance().getModSettings() == null) return true;

        String enabledLogLevel = AltoClef.getInstance().getModSettings().getLogLevel();

        return switch (enabledLogLevel) {
            case "NONE" -> false;
            case "ALL" -> true;
            case "NORMAL" -> level == WARN_LOG_LEVEL || level == ERROR_LOG_LEVEL;
            case "WARN" -> level == WARN_LOG_LEVEL;
            case "ERROR" -> level == ERROR_LOG_LEVEL;
            default ->
                    // invalid log level, switch to default (NORMAL)
                    level != DEBUG_LOG_LEVEL;
        };

    }

}
