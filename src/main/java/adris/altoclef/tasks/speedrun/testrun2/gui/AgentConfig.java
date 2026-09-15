package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.tasks.speedrun.testrun2.util.GameFiles;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Disk config. Never write the key to chat. */
public final class AgentConfig {

    public static final String FILE = "agent_config.txt";

    public String apiKey = "";
    public String url = "https://api.x.ai/v1/chat/completions";
    public String model = "grok-4";
    public String bind = "RSHIFT";
    public String provider = "xai";

    private static AgentConfig cache;

    public static AgentConfig cached() {
        if (cache == null) cache = load();
        return cache;
    }

    public static void invalidate() {
        cache = null;
    }

    public static AgentConfig load() {
        AgentConfig c = new AgentConfig();
        String raw = GameFiles.read(FILE);
        if (raw == null) return c;
        for (String line : raw.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String v = line.substring(eq + 1).trim();
            switch (k) {
                case "key", "apikey", "api_key" -> c.apiKey = v;
                case "url", "base", "endpoint" -> c.url = v;
                case "model" -> c.model = v;
                case "bind", "keybind" -> c.bind = v;
                case "provider", "api" -> c.provider = v;
            }
        }
        return c;
    }

    public void save() {
        cache = this;
        GameFiles.write(FILE,
                "key=" + apiKey + "\n"
                        + "url=" + url + "\n"
                        + "model=" + model + "\n"
                        + "bind=" + bind + "\n"
                        + "provider=" + provider + "\n");
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public int glfwKey() {
        String b = bind == null ? "" : bind.trim().toUpperCase(Locale.ROOT);
        return switch (b) {
            case "RSHIFT", "RIGHT_SHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "LSHIFT", "LEFT_SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RCTRL", "RIGHT_CONTROL" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "GRAVE", "BACKTICK", "`" -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case "O" -> GLFW.GLFW_KEY_O;
            case "M" -> GLFW.GLFW_KEY_M;
            case "K" -> GLFW.GLFW_KEY_K;
            case "F8" -> GLFW.GLFW_KEY_F8;
            case "F9" -> GLFW.GLFW_KEY_F9;
            default -> GLFW.GLFW_KEY_RIGHT_SHIFT;
        };
    }
}
