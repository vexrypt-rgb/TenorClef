package adris.altoclef.mixins;

import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accessor for SelectWorldScreen's private world list (yarn field_3218). */
@Mixin(SelectWorldScreen.class)
public interface SelectWorldScreenAccessor {

    @Accessor("levelList")
    WorldListWidget altoGetLevelList();
}
