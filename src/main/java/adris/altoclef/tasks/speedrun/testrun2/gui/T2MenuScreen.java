package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * In-game button panel. Buttons are created in {@code init()} so 1.16
 * does not wipe them. Widget construction is reflective.
 */
public class T2MenuScreen extends Screen {

    private static int tab;

    private static final String[][] TAB_TASKS_L = {
            {"testrun2  RSG", "testrun2"},
            {"aa  advancements", "aa"},
            {"t2core  reserve test", "t2core"},
            {"escape  nether tunnel", "escape"},
            {"escape here", "escape here"},
            {"village", "village"},
            {"zerocycle  pillar", "zerocycle"},
            {"groundzero  fountain", "groundzero"},
    };
    private static final String[][] TAB_TASKS_R = {
            {"sethome", "sethome"},
            {"home", "home"},
            {"back  death", "back"},
            {"t2doctor", "t2doctor"},
            {"logdump", "logdump"},
            {"PANIC", "t2panic"},
            {"stop", "stop"},
    };
    private static final String[][] TAB_LINK_L = {
            {"butler status", "butler"},
            {"fleet list", "fleet"},
            {"fleet ping", "fleet ping"},
    };
    private static final String[][] TAB_LINK_R = {
            {"xget list", "xget list"},
    };
    private static final String[][] TAB_MEDIA_L = {
            {"mapart add image", "mapart add"},
            {"mapart convert inbox", "mapart convert"},
            {"mapart print last", "mapart print"},
    };
    private static final String[][] TAB_MEDIA_R = {
            {"dj play", "dj play"},
            {"dj stop", "dj stop"},
    };
    private static final String[][] TAB_AGENT_L = {
            {"agent on", "agent on"},
            {"agent off", "agent off"},
    };
    private static final String[][] TAB_AGENT_R = {
            {"t2doctor", "t2doctor"},
            {"stop", "stop"},
    };

    private Object keyBox;
    private Object urlBox;
    private Object modelBox;
    private Object bindBox;
    private boolean dropProv;
    private boolean dropModel;

    public T2MenuScreen() {
        super(titleText());
    }

    public static void open() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        Runnable show = () -> {
            try {
                T2MenuScreen screen = new T2MenuScreen();
                try {
                    mc.getClass().getMethod("openScreen", Screen.class).invoke(mc, screen);
                } catch (NoSuchMethodException e) {
                    mc.getClass().getMethod("setScreen", Screen.class).invoke(mc, screen);
                }
                Debug.logMessage("T2MENU opened");
            } catch (Throwable t) {
                Debug.logWarning("T2MENU open: " + t.getClass().getSimpleName() + " " + t.getMessage());
            }
        };
        try {
            mc.execute(show);
        } catch (Throwable t) {
            show.run();
        }
    }

    private static Text titleText() {
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

    @Override
    protected void init() {
        super.init();
        int bw = 200;
        int bh = 20;
        int left = Math.max(16, this.width / 2 - 220);
        int right = this.width / 2 + 20;
        int tabY = 28;
        attach(button(left, tabY, 100, 18, tab == 0 ? "[ Tasks ]" : "Tasks", "TAB:0"));
        attach(button(left + 102, tabY, 100, 18, tab == 1 ? "[ Link ]" : "Link", "TAB:1"));
        attach(button(right, tabY, 100, 18, tab == 2 ? "[ Media ]" : "Media", "TAB:2"));
        attach(button(right + 102, tabY, 100, 18, tab == 3 ? "[ Agent ]" : "Agent", "TAB:3"));
        String[][] L = TAB_TASKS_L;
        String[][] R = TAB_TASKS_R;
        if (tab == 1) { L = TAB_LINK_L; R = TAB_LINK_R; }
        if (tab == 2) { L = TAB_MEDIA_L; R = TAB_MEDIA_R; }
        if (tab == 3) { L = TAB_AGENT_L; R = TAB_AGENT_R; }
        int y = 52;
        for (String[] row : L) {
            attach(button(left, y, bw, bh, row[0], row[1]));
            y += 22;
        }
        y = 52;
        for (String[] row : R) {
            attach(button(right, y, bw, bh, row[0], row[1]));
            y += 22;
        }
        if (tab != 3) {
            attach(button(this.width / 2 - 50, this.height - 24, 100, 20, "close", null));
            return;
        }
        AgentConfig cfg = AgentConfig.cached();
        AgentPresets.Preset preset = AgentPresets.byId(cfg.provider);
        int top = 8;
        int mid = this.width / 2;
        attach(button(mid - 220, top, 210, 18,
                "API ▾  " + preset.label, "DROP:PROV"));
        attach(button(mid + 10, top, 210, 18,
                "Model ▾  " + cfg.model, "DROP:MODEL"));
        if (dropProv) {
            int py = top + 20;
            for (AgentPresets.Preset p : AgentPresets.ALL) {
                attach(button(mid - 220, py, 210, 16, p.label, "PROV:" + p.id));
                py += 17;
            }
        }
        if (dropModel) {
            int py = top + 20;
            for (String m : preset.models) {
                attach(button(mid + 10, py, 210, 16, m, "MODEL:" + m));
                py += 17;
            }
        }
        int fy = this.height - 96;
        int fx = Math.max(16, this.width / 2 - 220);
        int fw = Math.min(440, this.width - 32);
        keyBox = textField(fx, fy, fw, 16, cfg.apiKey);
        urlBox = textField(fx, fy + 18, fw, 16, cfg.url);
        modelBox = textField(fx, fy + 36, fw / 2 - 4, 16, cfg.model);
        bindBox = textField(fx + fw / 2 + 4, fy + 36, fw / 2 - 4, 16, cfg.bind);
        attach(keyBox);
        attach(urlBox);
        attach(modelBox);
        attach(bindBox);
        attach(button(this.width / 2 - 110, this.height - 24, 100, 20, "save api", "SAVECFG"));
        attach(button(this.width / 2 + 10, this.height - 24, 100, 20, "close", null));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 1.16+ */
    public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
        paint(matrices);
        try {
            Screen.class.getMethod("render",
                    net.minecraft.client.util.math.MatrixStack.class, int.class, int.class, float.class)
                    .invoke(this, matrices, mouseX, mouseY, delta);
        } catch (Throwable ignored) {}
    }

    /** pre-1.16 fallback */
    public void render(int mouseX, int mouseY, float delta) {
        paint(null);
        try {
            Screen.class.getMethod("render", int.class, int.class, float.class)
                    .invoke(this, mouseX, mouseY, delta);
        } catch (Throwable ignored) {}
    }

    private void paint(Object matrices) {
        try {
            if (matrices != null) {
                Screen.class.getMethod("fill",
                        net.minecraft.client.util.math.MatrixStack.class,
                        int.class, int.class, int.class, int.class, int.class)
                        .invoke(this, matrices, 0, 0, this.width, this.height, 0xC0101010);
            } else {
                Screen.class.getMethod("fill", int.class, int.class, int.class, int.class, int.class)
                        .invoke(this, 0, 0, this.width, this.height, 0xC0101010);
            }
        } catch (Throwable ignored) {}
        try {
            Object title = titleText();
            if (matrices != null) {
                this.textRenderer.getClass()
                        .getMethod("drawWithShadow",
                                net.minecraft.client.util.math.MatrixStack.class,
                                Class.forName("net.minecraft.text.Text"),
                                float.class, float.class, int.class)
                        .invoke(this.textRenderer, matrices, title, 16f, 8f, 0xFFFFFF);
            }
        } catch (Throwable ignored) {}
    }

    private void attach(Object btn) {
        if (btn == null) return;
        try {
            this.getClass().getMethod("addButton",
                    Class.forName("net.minecraft.client.gui.widget.AbstractButtonWidget"))
                    .invoke(this, btn);
            return;
        } catch (Throwable ignored) {}
        try {
            this.getClass().getMethod("addDrawableChild",
                    Class.forName("net.minecraft.client.gui.Element"))
                    .invoke(this, btn);
            return;
        } catch (Throwable ignored) {}
        for (Method m : Screen.class.getMethods()) {
            if (m.getParameterCount() == 1 && m.getName().startsWith("add")) {
                try {
                    m.invoke(this, btn);
                    return;
                } catch (Throwable ignored) {}
            }
        }
        Debug.logWarning("T2MENU no addButton on this Screen");
    }

    private Object button(int x, int y, int w, int h, String label, String cmd) {
        Object text;
        try {
            text = Text.class.getMethod("literal", String.class).invoke(null, label);
        } catch (Throwable t) {
            try {
                text = Class.forName("net.minecraft.text.LiteralText")
                        .getConstructor(String.class).newInstance(label);
            } catch (Throwable t2) {
                return null;
            }
        }
        Runnable press = () -> {
            if (cmd != null && cmd.startsWith("TAB:")) {
                try { tab = Integer.parseInt(cmd.substring(4)); } catch (Throwable ignored) {}
                dropProv = false;
                dropModel = false;
                this.init();
                return;
            }
            if (cmd != null && cmd.startsWith("DROP:")) {
                if (cmd.endsWith("PROV")) {
                    dropProv = !dropProv;
                    dropModel = false;
                } else {
                    dropModel = !dropModel;
                    dropProv = false;
                }
                this.init();
                return;
            }
            if (cmd != null && cmd.startsWith("PROV:")) {
                applyProvider(cmd.substring(5));
                dropProv = false;
                this.init();
                return;
            }
            if (cmd != null && cmd.startsWith("MODEL:")) {
                applyModel(cmd.substring(6));
                dropModel = false;
                this.init();
                return;
            }
            if ("SAVECFG".equals(cmd)) {
                saveFields();
                return;
            }
            closeMe();
            if (cmd != null) exec(cmd);
        };
        try {
            Class<?> bw = Class.forName("net.minecraft.client.gui.widget.ButtonWidget");
            try {
                Class<?> actionCl = Class.forName("net.minecraft.client.gui.widget.ButtonWidget$PressAction");
                Method builder = bw.getMethod("builder", Text.class, actionCl);
                Object action = Proxy.newProxyInstance(bw.getClassLoader(), new Class<?>[]{actionCl},
                        (p, m, a) -> {
                            if ("onPress".equals(m.getName())) press.run();
                            return null;
                        });
                Object b = builder.invoke(null, text, action);
                b.getClass().getMethod("dimensions", int.class, int.class, int.class, int.class)
                        .invoke(b, x, y, w, h);
                return b.getClass().getMethod("build").invoke(b);
            } catch (NoSuchMethodException ignored) {}
            for (Constructor<?> c : bw.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 6 && p[0] == int.class) {
                    Object action = Proxy.newProxyInstance(bw.getClassLoader(), new Class<?>[]{p[5]},
                            (pr, m, a) -> {
                                if ("onPress".equals(m.getName()) || "press".equals(m.getName())) {
                                    press.run();
                                }
                                if ("toString".equals(m.getName())) return label;
                                return null;
                            });
                    return c.newInstance(x, y, w, h, text, action);
                }
            }
        } catch (Throwable t) {
            Debug.logWarning("T2MENU button: " + t.getMessage());
        }
        return null;
    }

    private Object textField(int x, int y, int w, int h, String value) {
        try {
            Object tr = textRenderer();
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

    private Object textRenderer() {
        try {
            return this.getClass().getField("textRenderer").get(this);
        } catch (Throwable t) {
            try {
                return this.getClass().getField("client").get(this).getClass()
                        .getField("textRenderer").get(MinecraftClient.getInstance());
            } catch (Throwable t2) {
                return MinecraftClient.getInstance().textRenderer;
            }
        }
    }

    private static String fieldText(Object box) {
        if (box == null) return "";
        try {
            Object v = box.getClass().getMethod("getText").invoke(box);
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private void applyProvider(String id) {
        AgentPresets.Preset p = AgentPresets.byId(id);
        AgentConfig cfg = AgentConfig.cached();
        cfg.provider = p.id;
        if (!p.url.isEmpty()) cfg.url = p.url;
        if (p.models.length > 0) cfg.model = p.models[0];
        setBox(urlBox, cfg.url);
        setBox(modelBox, cfg.model);
        Debug.logMessage("T2MENU provider=" + p.label + " model=" + cfg.model);
    }

    private void applyModel(String model) {
        AgentConfig cfg = AgentConfig.cached();
        cfg.model = model;
        setBox(modelBox, model);
        Debug.logMessage("T2MENU model=" + model);
    }

    private static void setBox(Object box, String value) {
        if (box == null || value == null) return;
        try {
            box.getClass().getMethod("setText", String.class).invoke(box, value);
        } catch (Throwable ignored) {}
    }

    private void saveFields() {
        AgentConfig cfg = AgentConfig.cached();
        cfg.apiKey = fieldText(keyBox);
        cfg.url = fieldText(urlBox);
        cfg.model = fieldText(modelBox);
        String b = fieldText(bindBox);
        if (!b.isBlank()) cfg.bind = b;
        cfg.save();
        Debug.logMessage("T2MENU saved key=" + (cfg.hasKey() ? "yes" : "no")
                + " model=" + cfg.model + " bind=" + cfg.bind);
    }

    private static void closeMe() {
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

    private static void exec(String name) {
        try {
            String prefix = "@";
            try {
                prefix = AltoClef.getCommandExecutor().getCommandPrefix();
            } catch (Throwable ignored) {}
            String line = prefix + name;
            AltoClef.getCommandExecutor().executeWithPrefix(name);
            Debug.logMessage("T2MENU " + line);
        } catch (Throwable t) {
            Debug.logWarning("T2MENU exec " + t.getMessage());
        }
    }
}
