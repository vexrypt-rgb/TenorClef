package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.multiversion.DrawContextWrapper;
import adris.altoclef.tasks.speedrun.testrun2.SpeedrunClock;
import adris.altoclef.tasks.speedrun.testrun2.util.Splits;
import net.minecraft.client.MinecraftClient;

/** Run clock and last splits, top-right. Named Segno (score jump mark). */
public final class SegnoOverlay {

    private static boolean on = true;

    private SegnoOverlay() {}

    public static void setEnabled(boolean v) {
        on = v;
    }

    public static void render(DrawContextWrapper context) {
        if (!on || context == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        // No run has started a clock yet: nothing to show.
        if (SpeedrunClock.millis() == 0) return;
        int w = mc.getWindow().getScaledWidth();
        int y = 6;
        for (String line : Splits.hudLines()) {
            int x = Math.max(4, w - mc.textRenderer.getWidth(line) - 6);
            context.drawText(mc.textRenderer, line, x, y, 0xFFE8C96A, true);
            y += 10;
        }
    }
}
