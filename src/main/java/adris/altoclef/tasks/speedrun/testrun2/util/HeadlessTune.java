package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.Debug;
import adris.altoclef.tasks.speedrun.testrun2.gui.SegnoOverlay;
import net.minecraft.client.MinecraftClient;

/**
 * Does not remove OpenGL. Caps FPS and view so a butler account
 * can sit on a VPS / under Xvfb without melting the GPU.
 */
public final class HeadlessTune {

    private static boolean on;

    private HeadlessTune() {}

    public static boolean on() {
        return on;
    }

    public static void apply() {
        on = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        //#if MC >= 11900
        mc.options.getViewDistance().setValue(2);
        mc.options.getMaxFps().setValue(10);
        mc.options.getEnableVsync().setValue(false);
        mc.options.getBobView().setValue(false);
        mc.options.getEntityShadows().setValue(false);
        //#else
        //$$ mc.options.viewDistance = 2;
        //$$ mc.options.maxFps = 10;
        //$$ mc.options.enableVsync = false;
        //$$ mc.options.bobView = false;
        //$$ mc.options.entityShadows = false;
        //#endif
        SegnoOverlay.setEnabled(false);
        Debug.logMessage("HEADLESS tune: view=2 fps=10 vsync=off  (still needs a window or Xvfb)");
    }
}
