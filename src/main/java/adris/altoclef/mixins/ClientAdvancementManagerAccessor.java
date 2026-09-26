package adris.altoclef.mixins;

import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.client.network.ClientAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Client-side advancement progress. Key is Advancement (1.16) or AdvancementEntry (1.20.2+),
 * so it is typed Object here; AdvancementProbe reads the id per version.
 */
@Mixin(ClientAdvancementManager.class)
public interface ClientAdvancementManagerAccessor {
    @Accessor("advancementProgresses")
    Map<Object, AdvancementProgress> altoClefGetProgresses();
}
