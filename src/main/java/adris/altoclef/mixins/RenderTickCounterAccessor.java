package adris.altoclef.mixins;

import org.spongepowered.asm.mixin.Mixin;

//#if MC < 12003
//$$ import net.minecraft.client.render.RenderTickCounter;
//$$ import org.spongepowered.asm.mixin.gen.Accessor;
//#else
import net.minecraft.client.MinecraftClient;
//#endif

/** Reads RenderTickCounter.tickTime for ClientTimerWarpMixin (the redirect replaces the read). */
//#if MC < 12003
//$$ @Mixin(RenderTickCounter.class)
//$$ public interface RenderTickCounterAccessor {
//$$     @Accessor("tickTime")
//$$     float altoClefGetTickTime();
//$$ }
//#else
@Mixin(MinecraftClient.class)
public interface RenderTickCounterAccessor {
}
//#endif
