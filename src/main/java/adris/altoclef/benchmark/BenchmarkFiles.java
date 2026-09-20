package adris.altoclef.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Live benchmark run folder under {@code <gameDir>/altoclef/bench/}
 * (falls back to {@code altoclef/bench} offline). Uses reflection for the
 * Minecraft game dir so this class stays offline-compilable.
 */
public final class BenchmarkFiles {

    private static volatile Path dirOverride;
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private BenchmarkFiles() {}

    /** Tests only — bypass Minecraft game dir. */
    public static void setDirOverrideForTests(Path path) {
        dirOverride = path;
    }

    public static Path dir() {
        if (dirOverride != null) {
            return dirOverride;
        }
        Path game = tryGameDir();
        if (game != null) {
            return game.resolve("altoclef").resolve("bench");
        }
        Path primary = Path.of("altoclef", "bench");
        if (Files.isDirectory(primary) || !Files.exists(Path.of("bench-out"))) {
            return primary;
        }
        return Path.of("bench-out");
    }

    private static Path tryGameDir() {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.MinecraftClient");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            if (mc == null) {
                return null;
            }
            Object runDir = mcClass.getField("runDirectory").get(mc);
            if (runDir instanceof java.io.File file) {
                return file.toPath();
            }
        } catch (Throwable ignored) {
            // offline / no client
        }
        return null;
    }

    public static void ensure() {
        try {
            Files.createDirectories(dir());
        } catch (IOException ignored) {
        }
    }

    /** Sanitize run name and build {@code <dir>/<ts>-<name>.json}. */
    public static Path nextRunPath(String name) {
        ensure();
        String safe = sanitize(name);
        String stamp = LocalDateTime.now().format(TS);
        return dir().resolve(stamp + "-" + safe + ".json");
    }

    static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "run";
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String s = sb.toString();
        return s.isBlank() ? "run" : s;
    }
}
