package adris.altoclef.mixins;

import adris.altoclef.Debug;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.ClientTickEvent;
import adris.altoclef.util.AutoWorldState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Changed this from player to client, I hope this doesn't break anything.
@Mixin(MinecraftClient.class)
public final class ClientTickMixin {
    // Proves the mixin subsystem actually injected into MinecraftClient.tick().
    // When the mixin config compatibilityLevel is lower than the compiled class
    // file version, Mixin silently drops EVERY mixin in this config and the mod
    // appears to "run" while nothing is hooked. This one-time marker makes that
    // failure loud in latest.log instead of invisible.
    private static boolean altoClefKtickProven = false;
    // S174: throttle + one-shot log for the reroll drive below. Kept as private statics on
    // the mixin (allowed) rather than on AutoWorldState, because they are transport detail,
    // not state another class needs to read.
    private static int altoClefRerollPoll = 0;
    private static boolean altoClefRerollLogged = false;

    @Inject(
            method = "tick",
            at = @At("HEAD")
    )
    private void clientTick(CallbackInfo ci) {
        if (!altoClefKtickProven) {
            altoClefKtickProven = true;
            System.out.println("TENORCLEF: MIXIN OK ClientTickMixin injected into MinecraftClient.tick()");
        }
        // S174 — drive the reroll from HERE, not from TitleScreen.tick().
        //
        // The reroll used to depend on the title screen appearing after mc.disconnect().
        // Run W proved it does not: a correct S165 reset at 23:35:27 re-armed the state and
        // then the client sat in the old world for 13+ minutes, saving every ten minutes,
        // with no title screen and no create call. MinecraftClient.tick() runs on every
        // screen (and in-world), so polling it is the only trigger guaranteed to fire.
        //
        // Keep the TitleScreen path too: when the disconnect DOES reach the title screen,
        // AutoWorldCreateMixin handles creation itself and clears the flag via setCreated()
        // before this poll can act. This is a backstop, not a replacement.
        if (AutoWorldState.needsRerollDrive()) {
            MinecraftClient self = MinecraftClient.getInstance();
            if (self == null) {
                AutoWorldState.clearRerollDrive();
            } else if (self.currentScreen instanceof TitleScreen) {
                // The normal path will pick it up; stop polling this tick.
                AutoWorldState.clearRerollDrive();
            } else if (self.world == null && self.getLevelStorage() != null) {
                // No world at all and not on the title screen: the client is between worlds
                // after the disconnect. Kick creation here.
                if (!altoClefRerollLogged) {
                    altoClefRerollLogged = true;
                    Debug.logHarness("AUTOWORLD: reroll drive — client has no world and no "
                            + "TitleScreen; forcing a fresh-world create from the client tick");
                }
                AutoWorldState.clearRerollDrive();
                AutoWorldState.setCreated(false);
                AutoWorldState.setDelay(0);
            } else if (++altoClefRerollPoll > 20 * 45) {
                // 45s and the client is still sitting in the old world: the disconnect never
                // took effect. Say so loudly rather than hanging silently for 40 minutes —
                // that silence is what made run W look like a harness fault instead of a bug.
                AutoWorldState.clearRerollDrive();
                Debug.logHarness("AUTOWORLD: reroll drive gave up after 45s — client still in "
                        + "a world (screen=" + (self.currentScreen == null
                        ? "null" : self.currentScreen.getClass().getSimpleName())
                        + "), the reset cannot proceed. Screen and world are both stuck.");
            }
        }
        // S175 — client-thread liveness probe. This mixin is the only hook guaranteed to
        // fire on EVERY client tick regardless of screen or world state, which makes it the
        // right place to prove the client thread is still turning. The actual stall
        // detection runs on a separate daemon thread (see T2Deadman) because a thread that
        // is blocked cannot observe its own blockage.
        //
        // Keep this call FIRST in the body and free of any dependency on game state: it
        // must succeed even when the world, the player, or AltoClef itself is unavailable.
        try {
            adris.altoclef.tasks.speedrun.testrun2.T2Deadman.clientTickAlive();
        } catch (Throwable ignored) {}
        EventBus.publish(new ClientTickEvent());
    }
}