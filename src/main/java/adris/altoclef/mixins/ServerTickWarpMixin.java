package adris.altoclef.mixins;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

//#if MC < 12003
//$$ import adris.altoclef.util.WarpClock;
//$$ import org.spongepowered.asm.mixin.injection.Constant;
//$$ import org.spongepowered.asm.mixin.injection.ModifyConstant;
//#endif

/**
 * Time warp, server half. runServer() paces itself with a hardcoded 50L (ms per tick) both
 * for advancing timeReference and for the next-tick deadline; shrinking it makes the
 * integrated server tick N times per 50ms. 1.20.3+ has ServerTickManager / /tick rate, so
 * this is a no-op there (see WarpCommand).
 */
@Mixin(MinecraftServer.class)
public class ServerTickWarpMixin {
    //#if MC < 12003
    //$$ @ModifyConstant(method = "runServer", constant = @Constant(longValue = 50L), require = 0)
    //$$ private long altoClefWarpTick(long ms) {
    //$$     return WarpClock.scaleMillis(ms);
    //$$ }
    //#endif
}
