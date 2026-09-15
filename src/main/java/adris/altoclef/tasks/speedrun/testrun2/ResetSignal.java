package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.Debug;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Singleplayer-only abort signal. */
public final class ResetSignal {

    public static final String FLAG_NAME = "testrun2-reset.flag";

    private ResetSignal() {}

    public static void fire(String reason) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        if (mc.getCurrentServerEntry() != null) {
            Debug.logWarning("TESRUN2 reset skipped — multiplayer");
            return;
        }
        try {
            Path run = mc.runDirectory != null ? mc.runDirectory.toPath() : Path.of(".");
            Files.writeString(run.resolve(FLAG_NAME), reason == null ? "abort" : reason, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            Debug.logWarning("TESRUN2 could not write reset flag: " + t.getMessage());
        }
        Debug.logWarning("TESRUN2 RESET: " + reason);
        try {
            mc.execute(() -> {
                try {
                    mc.disconnect();
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }
}
