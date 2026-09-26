package adris.altoclef.mixins;

import org.spongepowered.asm.mixin.Mixin;

//#if MC < 12003
//$$ import adris.altoclef.util.WarpClock;
//$$ import net.minecraft.client.render.RenderTickCounter;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//#else
import net.minecraft.client.MinecraftClient;
//#endif

/**
 * Time warp, client half. RenderTickCounter turns elapsed ms into ticks by dividing by
 * tickTime (50ms). Reading a smaller tickTime makes the client, and so the bot, the player
 * physics and the input loop, run N ticks per 50ms.
 *
 * Vanilla caps client ticks at 10 per frame, so max real speed is about fps * 10 / 20.
 * At 10 fps (HeadlessTune) that is 5x; unlock fps for more.
 *
 * On 1.20.3+ the client follows the server's /tick rate itself, so this is an empty mixin.
 */
//#if MC < 12003
//$$ @Mixin(RenderTickCounter.class)
//#else
@Mixin(MinecraftClient.class)
//#endif
public class ClientTimerWarpMixin {
    //#if MC < 12003
    //$$ @Redirect(
    //$$         method = "beginRenderTick",
    //$$         at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/RenderTickCounter;tickTime:F"),
    //$$         require = 0
    //$$ )
    //$$ private float altoClefWarpTickTime(RenderTickCounter self) {
    //$$     return WarpClock.scaleTickTime(((RenderTickCounterAccessor) self).altoClefGetTickTime());
    //$$ }
    //#endif
}
