package adris.altoclef.mixins;

import adris.altoclef.Debug;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.TitleScreenEntryEvent;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes {@link TitleScreenEntryEvent} once per JVM.
 *
 * <p>The headless auto-world work lives in {@code AutoWorldCreateMixin}, which also
 * injects into {@code TitleScreen.init()} and runs at TAIL — after this HEAD hook, so
 * "Global Init" is always logged before any world is created.
 */
@Mixin(TitleScreen.class)
public class EntryMixin {

    @Unique
    private static boolean _initialized = false;

    @Inject(at = @At("HEAD"), method = "init()V")
    private void init(CallbackInfo info) {
        if (!_initialized) {
            _initialized = true;
            Debug.logMessage("Global Init");
            EventBus.publish(new TitleScreenEntryEvent());
        }
    }
}
