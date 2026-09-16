package adris.altoclef.tasks.speedrun.testrun2.gui;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Poll the configured key. Hooked from {@code AltoClef.onClientTick}.
 */
public final class T2MenuKeys {

    private static boolean wasDown;

    private T2MenuKeys() {}

    public static void tick() {
        T2MenuScreen.poll();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        if (mc.currentScreen != null) {
            wasDown = false;
            return;
        }
        long handle;
        try {
            Object win = mc.getWindow();
            handle = (Long) win.getClass().getMethod("getHandle").invoke(win);
        } catch (Throwable t) {
            return;
        }
        int key = AgentConfig.cached().glfwKey();
        boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        if (down && !wasDown) {
            T2MenuScreen.open();
        }
        wasDown = down;
    }
}
