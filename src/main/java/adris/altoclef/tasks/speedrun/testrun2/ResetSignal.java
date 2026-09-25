package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;

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
        // Ask the headless world creator for a fresh seed. Without this the client just
        // disconnects to the title screen and the harness burns the rest of its timeout on
        // a dead client — a rejected spawn would cost a whole run instead of one retry.
        // State lives on AutoWorldState, not on the mixin: a mixin class may not expose
        // non-private static methods (Mixin fails at apply time, not compile time).
        try {
            adris.altoclef.util.AutoWorldState.rearm();
        } catch (Throwable t) {
            Debug.logWarning("TESRUN2 could not re-arm world creation: " + t);
        }
        // S192: the driver stops on purpose now; world creation blocks ticks for 10-40s.
        // The next @testrun2 onStart re-arms the watchdog.
        try {
            T2Deadman.disarm();
        } catch (Throwable ignored) {}
        // Leave the world the way the vanilla "Save and Quit" button does. Two things matter:
        //
        // 1. send(), never execute(). fire() is called from inside the client tick, and
        //    execute() runs the task INLINE when already on the client thread. disconnect()
        //    then enters its waitForServer render loop from inside the tick.
        // 2. world.disconnect() BEFORE client.disconnect(). Closing the connection is what
        //    makes the integrated server stop; client.disconnect() loops
        //    `while (!server.isStopping()) render()` and without it spins forever.
        //    Observed live (sim run base1): S159 reject at 0:27 → client frozen 90s+ with
        //    DEADMAN stack ResetSignal → disconnect → render → limitDisplayFPS. This is the
        //    S174 "reroll path has never worked" failure.
        //
        // Landing on TitleScreen lets AutoWorldCreateMixin create the next seed.
        if (pendingDisconnect) return;
        pendingDisconnect = true;
        try {
            mc.send(() -> {
                pendingDisconnect = false;
                try {
                    if (mc.world == null) return;
                    T2Log.force("S188", "reroll: leaving world (queued Save-and-Quit disconnect)");
                    //#if MC >= 12111
                    //$$ mc.world.disconnect(net.minecraft.text.Text.empty());
                    //$$ mc.disconnect(new TitleScreen(), false);
                    //#else
                    mc.world.disconnect();
                    mc.disconnect();
                    mc.setScreen(new TitleScreen());
                    //#endif
                } catch (Throwable t) {
                    Debug.logWarning("TESRUN2 reset disconnect failed: " + t);
                }
            });
        } catch (Throwable t) {
            pendingDisconnect = false;
            Debug.logWarning("TESRUN2 could not schedule reset disconnect: " + t);
        }
    }

    /** One queued disconnect at a time; several guards can fire in the same tick. */
    private static volatile boolean pendingDisconnect = false;
}
