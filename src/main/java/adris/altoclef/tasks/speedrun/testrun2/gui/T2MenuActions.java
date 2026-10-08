package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Hit-test helpers and command dispatch for {@link T2MenuScreen}. */
final class T2MenuActions {
    private T2MenuActions() {}

    static void rebuild(T2MenuScreen s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // init(client, w, h) does not drop the old widgets: stale text fields would keep focus and input.
        try {
            Method clear = Screen.class.getDeclaredMethod("clearChildren");
            clear.setAccessible(true);
            clear.invoke(s);
        } catch (Throwable ignored) {}
        if (mc != null) {
            try {
                Screen.class.getMethod("init", MinecraftClient.class, int.class, int.class)
                        .invoke(s, mc, s.width, s.height);
                return;
            } catch (Throwable ignored) {}
        }
        s.init();
    }

    static void attach(T2MenuScreen s, Object btn) {
        if (btn == null) return;
        Class<?> c = s.getClass();
        // getDeclaredMethods() order is unspecified: try the drawable adders before addSelectableChild,
        // which registers the widget for input but never draws it (an invisible text field).
        for (String want : new String[]{"addDrawableChild", "addButton", "addSelectableChild"}) {
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods()) {
                    if (m.getParameterCount() != 1 || !m.getName().equals(want)) continue;
                    try {
                        m.setAccessible(true);
                        m.invoke(s, btn);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        }
        try {
            Field f = null;
            Class<?> sc = Screen.class;
            for (String name : new String[]{"buttons", "field_22786", "children"}) {
                try { f = sc.getDeclaredField(name); break; } catch (Throwable ignored) {}
            }
            if (f != null) {
                f.setAccessible(true);
                Object list = f.get(s);
                if (list instanceof List) {
                    ((List<Object>) list).add(btn);
                }
            }
        } catch (Throwable t) {
            Debug.logWarning("T2MENU attach failed");
        }
    }

    static Object button(T2MenuScreen s, int x, int y, int w, int h, String label, String cmd) {
        s.hits.add(new int[]{x, y, w, h});
        s.hitCmd.add(cmd);
        s.hitLab.add(label);
        return null;
    }

    static void runCmd(T2MenuScreen s, String cmd) {
        if (cmd != null && cmd.startsWith("TAB:")) {
            try { T2MenuScreen.setTab(Integer.parseInt(cmd.substring(4))); } catch (Throwable ignored) {}
            s.dropProv = false;
            s.dropModel = false;
            rebuild(s);
            return;
        }
        if (cmd != null && cmd.startsWith("CMP:")) {
            T2CompositionTab.run(s, cmd.substring(4));
            return;
        }
        if (cmd != null && cmd.startsWith("SWM:")) {
            T2SwarmTab.run(s, cmd.substring(4));
            return;
        }
        if (cmd != null && cmd.startsWith("SIG:")) {
            T2SigilTab.run(s, cmd.substring(4));
            return;
        }
        if (cmd != null && cmd.startsWith("DROP:")) {
            if (cmd.endsWith("PROV")) {
                s.dropProv = !s.dropProv;
                s.dropModel = false;
            } else {
                s.dropModel = !s.dropModel;
                s.dropProv = false;
            }
            rebuild(s);
            return;
        }
        if (cmd != null && cmd.startsWith("PROV:")) {
            applyProvider(s, cmd.substring(5));
            s.dropProv = false;
            rebuild(s);
            return;
        }
        if (cmd != null && cmd.startsWith("MODEL:")) {
            applyModel(s, cmd.substring(6));
            s.dropModel = false;
            rebuild(s);
            return;
        }
        if ("SAVECFG".equals(cmd)) {
            saveFields(s);
            return;
        }
        closeMe();
        if (cmd != null) exec(cmd);
    }

    static Object textField(T2MenuScreen s, int x, int y, int w, int h, String value) {
        try {
            Object tr = textRenderer(s);
            Class<?> tf = Class.forName("net.minecraft.client.gui.widget.TextFieldWidget");
            Object title = titleText();
            Object box = null;
            for (Constructor<?> c : tf.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 6 && p[1] == int.class) {
                    box = c.newInstance(tr, x, y, w, h, title);
                    break;
                }
                if (p.length == 5 && p[1] == int.class) {
                    box = c.newInstance(tr, x, y, w, h);
                    break;
                }
            }
            if (box == null) return null;
            try { box.getClass().getMethod("setMaxLength", int.class).invoke(box, 256); } catch (Throwable ignored) {}
            try { box.getClass().getMethod("setText", String.class).invoke(box, value == null ? "" : value); } catch (Throwable ignored) {}
            return box;
        } catch (Throwable t) {
            Debug.logWarning("T2MENU field: " + t.getClass().getSimpleName());
            return null;
        }
    }

    static Object textRenderer(T2MenuScreen s) {
        try {
            return s.getClass().getField("textRenderer").get(s);
        } catch (Throwable t) {
            return MinecraftClient.getInstance().textRenderer;
        }
    }

    static Text titleText() {
        try {
            return (Text) Text.class.getMethod("literal", String.class).invoke(null, "TenorClef");
        } catch (Throwable t) {
            try {
                return (Text) Class.forName("net.minecraft.text.LiteralText")
                        .getConstructor(String.class).newInstance("TenorClef");
            } catch (Throwable t2) {
                throw new IllegalStateException(t2);
            }
        }
    }

    static String fieldText(Object box) {
        if (box == null) return "";
        try {
            Object v = box.getClass().getMethod("getText").invoke(box);
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    static void applyProvider(T2MenuScreen s, String id) {
        AgentPresets.Preset p = AgentPresets.byId(id);
        AgentConfig cfg = AgentConfig.cached();
        cfg.provider = p.id;
        if (!p.url.isEmpty()) cfg.url = p.url;
        if (p.models.length > 0) cfg.model = p.models[0];
        setBox(s.urlBox, cfg.url);
        setBox(s.modelBox, cfg.model);
        Debug.logMessage("T2MENU provider=" + p.label + " model=" + cfg.model);
    }

    static void applyModel(T2MenuScreen s, String model) {
        AgentConfig cfg = AgentConfig.cached();
        cfg.model = model;
        setBox(s.modelBox, model);
        Debug.logMessage("T2MENU model=" + model);
    }

    static void setBox(Object box, String value) {
        if (box == null || value == null) return;
        try {
            box.getClass().getMethod("setText", String.class).invoke(box, value);
        } catch (Throwable ignored) {}
    }

    static void saveFields(T2MenuScreen s) {
        AgentConfig cfg = AgentConfig.cached();
        cfg.apiKey = fieldText(s.keyBox);
        cfg.url = fieldText(s.urlBox);
        cfg.model = fieldText(s.modelBox);
        String b = fieldText(s.bindBox);
        if (b != null && !b.isEmpty()) cfg.bind = b;
        cfg.save();
        Debug.logMessage("T2MENU saved key=" + (cfg.hasKey() ? "yes" : "no")
                + " model=" + cfg.model + " bind=" + cfg.bind);
    }

    static void closeMe() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        try {
            mc.getClass().getMethod("openScreen", Screen.class).invoke(mc, new Object[]{null});
        } catch (Throwable t) {
            try {
                mc.getClass().getMethod("setScreen", Screen.class).invoke(mc, new Object[]{null});
            } catch (Throwable ignored) {}
        }
    }

    static void exec(String name) {
        try {
            String prefix = "@";
            try {
                prefix = AltoClef.getCommandExecutor().getCommandPrefix();
            } catch (Throwable ignored) {}
            AltoClef.getCommandExecutor().executeWithPrefix(name);
            Debug.logMessage("T2MENU " + prefix + name);
        } catch (Throwable t) {
            Debug.logWarning("T2MENU exec " + t.getMessage());
        }
    }
}
