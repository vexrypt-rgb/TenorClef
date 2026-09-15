package adris.altoclef.tasks.speedrun.testrun2.util;

import adris.altoclef.Debug;
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
        if (mc == null) return;
        try {
            Object opt = null;
            try { opt = mc.getClass().getField("options").get(mc); } catch (Throwable ignored) {}
            if (opt == null) {
                try { opt = mc.getClass().getMethod("getOptions").invoke(mc); } catch (Throwable ignored) {}
            }
            if (opt == null) return;
            setInt(opt, "viewDistance", 2);
            setInt(opt, "maxFps", 10);
            setBool(opt, "enableVsync", false);
            setBool(opt, "fancyGraphics", false);
            setBool(opt, "bobView", false);
            setBool(opt, "entityShadows", false);
            setBool(opt, "cloud", false);
            try {
                Object rd = opt.getClass().getField("viewDistance").get(opt);
                rd.getClass().getMethod("setValue", Object.class).invoke(rd, 2);
            } catch (Throwable ignored) {}
            SegnoOff();
            Debug.logMessage("HEADLESS tune: view=2 fps=10 vsync=off  (still needs a window or Xvfb)");
        } catch (Throwable t) {
            Debug.logWarning("HEADLESS " + t.getMessage());
        }
    }

    private static void SegnoOff() {
        try {
            Class.forName("adris.altoclef.tasks.speedrun.testrun2.gui.SegnoOverlay")
                    .getMethod("setEnabled", boolean.class)
                    .invoke(null, false);
        } catch (Throwable ignored) {}
    }

    private static void setInt(Object opt, String field, int v) {
        try {
            Object o = opt.getClass().getField(field).get(opt);
            if (o instanceof Integer) {
                opt.getClass().getField(field).setInt(opt, v);
                return;
            }
            o.getClass().getMethod("setValue", Object.class).invoke(o, v);
        } catch (Throwable ignored) {}
    }

    private static void setBool(Object opt, String field, boolean v) {
        try {
            Object o = opt.getClass().getField(field).get(opt);
            if (o instanceof Boolean) {
                opt.getClass().getField(field).setBoolean(opt, v);
                return;
            }
            o.getClass().getMethod("setValue", Object.class).invoke(o, v);
        } catch (Throwable ignored) {}
    }
}
