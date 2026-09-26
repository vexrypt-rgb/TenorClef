package adris.altoclef.mixins;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;

//#if MC < 12003
//$$ import adris.altoclef.util.WarpClock;
//$$ import org.spongepowered.asm.mixin.injection.Constant;
//$$ import org.spongepowered.asm.mixin.injection.ModifyConstant;
//#endif

/**
 * Time warp, frame cap. render() runs at most Math.min(10, pendingTicks) client ticks per frame,
 * so under software rendering (a few fps on Xvfb) warp stalls at fps * 10 ticks/s. Scaling the
 * cap by the warp factor lets a slow frame catch up on every tick it owes.
 */
@Mixin(MinecraftClient.class)
public class ClientTickCapWarpMixin {
    //#if MC < 12003
    //$$ @ModifyConstant(method = "render", constant = @Constant(intValue = 10, ordinal = 0), require = 0)
    //$$ private int altoClefWarpTickCap(int cap) {
    //$$     return (int) Math.ceil(cap * WarpClock.get());
    //$$ }
    //#endif
}
