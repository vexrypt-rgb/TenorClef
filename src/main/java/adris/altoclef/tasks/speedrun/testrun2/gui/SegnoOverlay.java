package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.tasks.speedrun.testrun2.util.Splits;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/** IGT + last splits, top-left. Named Segno (score jump mark). */
public final class SegnoOverlay {

    private static boolean on = true;

    private SegnoOverlay() {}

    public static void setEnabled(boolean v) {
        on = v;
    }

    public static void render(Object context) {
        if (!on) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        List<String> lines = Splits.hudLines();
        int w = 320;
        try { w = mc.getWindow().getScaledWidth(); } catch (Throwable ignored) {}
        int y = 6;
        for (String line : lines) {
            int tw = 8 * line.length();
            try { tw = mc.textRenderer.getWidth(line); } catch (Throwable ignored) {}
            int x = Math.max(4, w - tw - 6);
            draw(context, mc, line, x, y, 0xFFE8C96A);
            y += 10;
        }
    }

    private static void draw(Object context, MinecraftClient mc, String text, int x, int y, int color) {
        if (context != null) {
            for (String name : new String[]{"drawText", "drawString", "drawCenteredText"}) {
                try {
                    context.getClass().getMethod(name, net.minecraft.client.font.TextRenderer.class,
                            String.class, int.class, int.class, int.class)
                            .invoke(context, mc.textRenderer, text, x, y, color);
                    return;
                } catch (Throwable ignored) {}
            }
        }
        try {
            mc.textRenderer.getClass()
                    .getMethod("draw", String.class, float.class, float.class, int.class)
                    .invoke(mc.textRenderer, text, (float) x, (float) y, color);
            return;
        } catch (Throwable ignored) {}
        try {
            Class<?> ms = Class.forName("net.minecraft.client.util.math.MatrixStack");
            Object stack = ms.getConstructor().newInstance();
            mc.textRenderer.getClass()
                    .getMethod("draw", ms, String.class, float.class, float.class, int.class)
                    .invoke(mc.textRenderer, stack, text, (float) x, (float) y, color);
        } catch (Throwable ignored) {}
    }
}
