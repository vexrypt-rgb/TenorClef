package adris.altoclef.mixins;

import net.minecraft.client.gui.screen.world.WorldListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Accessor for the widget rows of a {@link WorldListWidget}.
 *
 * <p>Why not the {@code levels} field? On 1.16.1 {@code levels} is a
 * {@code List<LevelSummary>} — the raw save metadata. The clickable rows are
 * {@code WorldListWidget$Entry} objects, and only those expose {@code play()}.
 * {@link net.minecraft.client.gui.widget.EntryListWidget#children()} returns exactly
 * that typed list, so it is the correct thing to drive the headless auto-load.
 *
 * <p>History: v1 of this file exposed {@code levels} typed as
 * {@code List<WorldListWidget.Entry>}. Generics erase at the accessor boundary, so it
 * compiled cleanly and then threw
 * {@code ClassCastException: LevelSummary cannot be cast to WorldListWidget$Entry}
 * inside {@code SelectWorldScreen.init()} — see
 * crash-2026-09-20_13.39.36-client.txt.
 */
@Mixin(WorldListWidget.class)
public interface WorldListWidgetAccessor {

    /** The clickable rows, in display order. Empty when no saves exist. */
    @Invoker("children")
    List<WorldListWidget.Entry> altoGetEntries();
}
