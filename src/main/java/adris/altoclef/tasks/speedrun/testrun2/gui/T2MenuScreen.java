package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * In-game button panel. 1.16 addButton is protected — attach via
 * setAccessible. fill() without restoring textures makes widgets
 * invisible; renderBackground + painted labels stay readable.
 */
public class T2MenuScreen extends Screen {

    private static int tab;
    private static int pendingOpen;

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
    private final List<int[]> hits = new ArrayList<>();
    private final List<String> hitCmd = new ArrayList<>();
    private final List<String> hitLab = new ArrayList<>();

    public T2MenuScreen() {
        super(titleText());
    }

    public static void open() {
        pendingOpen = 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        Runnable show = () -> {
            try {
                if (mc.currentScreen instanceof T2MenuScreen) return;
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

    /** Chat closes the screen after the command. Wait it out. */
    public static void openSoon() {
        pendingOpen = 12;
        Debug.logMessage("T2MENU queued");
    }

    public static void poll() {
        if (pendingOpen <= 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        if (mc.currentScreen != null) {
            String n = mc.currentScreen.getClass().getSimpleName();
            if (n.contains("Chat") || n.contains("Command")) return;
            if (mc.currentScreen instanceof T2MenuScreen) {
                pendingOpen = 0;
                return;
            }
        }
        pendingOpen--;
        if (pendingOpen <= 0) open();
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
        hits.clear();
        hitCmd.clear();
        hitLab.clear();
        int bw = 200;
        int bh = 20;
        int left = Math.max(16, this.width / 2 - 220);
        int right = this.width / 2 + 20;
        int tabY = 28;
        attach(button(left, tabY, 78, 18, "Tasks", "TAB:0"));
        attach(button(left + 80, tabY, 78, 18, "Link", "TAB:1"));
        attach(button(left + 160, tabY, 78, 18, "Faults", "TAB:4"));
        attach(button(right, tabY, 100, 18, "Media", "TAB:2"));
        attach(button(right + 102, tabY, 100, 18, "Agent", "TAB:3"));
        String[][] L = TAB_TASKS_L;
        String[][] R = TAB_TASKS_R;
        if (tab == 1) { L = TAB_LINK_L; R = TAB_LINK_R; }
        if (tab == 2) { L = TAB_MEDIA_L; R = TAB_MEDIA_R; }
        if (tab == 3) { L = TAB_AGENT_L; R = TAB_AGENT_R; }
        if (tab == 4) { L = new String[0][]; R = new String[0][]; }
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
                "API  " + preset.label, "DROP:PROV"));
        attach(button(mid + 10, top, 210, 18,
                "Model  " + cfg.model, "DROP:MODEL"));
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

    public boolean shouldPause() {
        return false;
    }

    /**
     * Never Method.invoke Screen.render on this — that virtual-dispatches
     * back into this method and leaves BufferBuilder mid-quad.
     */
    //#if MC >= 12000
    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        paintUi(adris.altoclef.multiversion.DrawContextWrapper.of(context), mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
    }
    //#else
    //$$ @Override
    //$$ public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
    //$$     paintUi(adris.altoclef.multiversion.DrawContextWrapper.of(matrices), mouseX, mouseY);
    //$$     com.mojang.blaze3d.systems.RenderSystem.enableTexture();
    //$$     super.render(matrices, mouseX, mouseY, delta);
    //$$ }
    //#endif

    // Flat dark theme, ARGB.
    private static final int C_SCRIM = 0xC00B0D12;
    private static final int C_CARD = 0xE6161A22;
    private static final int C_BTN = 0xFF222834;
    private static final int C_BTN_HOVER = 0xFF2E3646;
    private static final int C_BORDER = 0xFF303848;
    private static final int C_ACCENT = 0xFF4FD1C5;
    private static final int C_DANGER = 0xFFE5534B;
    private static final int C_TEXT = 0xFFE6EAF0;
    private static final int C_MUTED = 0xFF8B93A3;

    /** Everything is painted here; widgets are only the text fields. */
    private void paintUi(adris.altoclef.multiversion.DrawContextWrapper g, int mx, int my) {
        if (g == null) return;
        g.fill(0, 0, this.width, this.height, C_SCRIM);
        // header bar
        g.fill(0, 0, this.width, 22, 0xF0101319);
        g.fill(0, 22, this.width, 23, C_BORDER);
        g.fill(8, 6, 11, 16, C_ACCENT);
        if (tab != 3) {
            g.drawText(this.textRenderer, "TenorClef", 16, 7, C_TEXT, false);
            String sub = "control panel";
            g.drawText(this.textRenderer, sub, this.width - 12 - this.textRenderer.getWidth(sub), 7, C_MUTED, false);
        }
        // card behind the two columns
        int left = Math.max(16, this.width / 2 - 220);
        int cardR = Math.min(this.width - 8, this.width / 2 + 20 + 212);
        int cardB = tab == 3 ? this.height - 104 : this.height - 32;
        g.fill(left - 8, 26, cardR, cardB, C_CARD);
        if (tab == 3) {
            g.fill(left - 8, this.height - 116, cardR, this.height - 30, C_CARD);
            g.drawText(this.textRenderer, "API key / URL / model / bind", left, this.height - 110, C_MUTED, false);
        }
        if (tab == 4) {
            long now = System.currentTimeMillis();
            String head = "time lost to faults: " + (adris.altoclef.tasks.speedrun.testrun2.fault.FaultBook.lostMs(now) / 1000)
                    + "s   (full detail: altoclef/faults.jsonl, run-summary.json)";
            g.drawText(this.textRenderer, head, left, 54, C_ACCENT, false);
            String[] lines = adris.altoclef.tasks.speedrun.testrun2.fault.FaultBook.recentText().split("\n");
            int maxW = cardR - left - 8;
            int rows = Math.max(1, (cardB - 72) / 11);
            int from = Math.max(0, lines.length - rows);
            int ly = 68;
            for (int i = lines.length - 1; i >= from; i--) { // newest first
                String s = lines[i];
                while (s.length() > 4 && this.textRenderer.getWidth(s) > maxW) s = s.substring(0, s.length() - 4) + "..";
                int col = s.contains(" E") ? 0xFFFF8A84 : C_TEXT;
                g.drawText(this.textRenderer, s, left, ly, col, false);
                ly += 11;
            }
        }
        for (int i = 0; i < hits.size(); i++) {
            int[] b = hits.get(i);
            String cmd = hitCmd.get(i);
            String label = hitLab.get(i);
            boolean hover = mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
            if (cmd != null && cmd.startsWith("TAB:")) {
                boolean on = cmd.equals("TAB:" + tab);
                if (hover && !on) g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x30FFFFFF);
                int tw = this.textRenderer.getWidth(label);
                g.drawText(this.textRenderer, label, b[0] + (b[2] - tw) / 2, b[1] + (b[3] - 8) / 2,
                        on ? C_TEXT : C_MUTED, false);
                g.fill(b[0] + 6, b[1] + b[3] - 2, b[0] + b[2] - 6, b[1] + b[3], on ? C_ACCENT : C_BORDER);
                continue;
            }
            boolean danger = "t2panic".equals(cmd) || "stop".equals(cmd);
            boolean primary = "SAVECFG".equals(cmd);
            int bg = hover ? C_BTN_HOVER : C_BTN;
            if (primary) bg = hover ? 0xFF5FE0D4 : C_ACCENT;
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], hover ? (danger ? C_DANGER : C_ACCENT) : C_BORDER);
            g.fill(b[0] + 1, b[1] + 1, b[0] + b[2] - 1, b[1] + b[3] - 1, bg);
            int strip = danger ? C_DANGER : C_ACCENT;
            boolean isCmd = cmd != null && !cmd.contains(":") && !primary;
            if (isCmd) g.fill(b[0] + 1, b[1] + 1, b[0] + 3, b[1] + b[3] - 1, strip);
            int fg = primary ? 0xFF0B0D12 : (danger ? 0xFFFF8A84 : C_TEXT);
            int ty = b[1] + (b[3] - 8) / 2;
            if (cmd == null || primary) {
                int tw = this.textRenderer.getWidth(label);
                g.drawText(this.textRenderer, label, b[0] + (b[2] - tw) / 2, ty, fg, false);
            } else {
                // "name  hint" -> name bright, hint muted
                int sp = label.indexOf("  ");
                String head = sp > 0 ? label.substring(0, sp) : label;
                String tail = sp > 0 ? label.substring(sp).trim() : "";
                int tx = b[0] + (isCmd ? 10 : 6);
                g.drawText(this.textRenderer, head, tx, ty, fg, false);
                if (!tail.isEmpty()) {
                    g.drawText(this.textRenderer, tail, tx + this.textRenderer.getWidth(head) + 6, ty, C_MUTED, false);
                }
                if (cmd.startsWith("DROP:")) {
                    g.drawText(this.textRenderer, "v", b[0] + b[2] - 10, ty, C_MUTED, false);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                int[] b = hits.get(i);
                if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                    runCmd(i < hitCmd.size() ? hitCmd.get(i) : null);
                    return true;
                }
            }
        }
        try {
            return super.mouseClicked(mx, my, button);
        } catch (Throwable t) {
            return false;
        }
    }

    private void rebuild() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            try {
                Screen.class.getMethod("init", MinecraftClient.class, int.class, int.class)
                        .invoke(this, mc, this.width, this.height);
                return;
            } catch (Throwable ignored) {}
        }
        this.init();
    }

    private void attach(Object btn) {
        if (btn == null) return;
        Class<?> c = this.getClass();
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() != 1) continue;
                String n = m.getName();
                if (!n.equals("addButton") && !n.equals("addDrawableChild") && !n.equals("addSelectableChild")) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    m.invoke(this, btn);
                    return;
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        // last resort: push onto Screen.buttons
        try {
            Field f = null;
            Class<?> s = Screen.class;
            for (String name : new String[]{"buttons", "field_22786", "children"}) {
                try { f = s.getDeclaredField(name); break; } catch (Throwable ignored) {}
            }
            if (f != null) {
                f.setAccessible(true);
                Object list = f.get(this);
                if (list instanceof List) {
                    ((List<Object>) list).add(btn);
                }
            }
        } catch (Throwable t) {
            Debug.logWarning("T2MENU attach failed");
        }
    }

    private Object button(int x, int y, int w, int h, String label, String cmd) {
        hits.add(new int[]{x, y, w, h});
        hitCmd.add(cmd);
        hitLab.add(label);
        // Painted in paintUi and clicked via hits; no vanilla widget.
        return null;
    }

    @SuppressWarnings("unused")
    private Object vanillaButton(int x, int y, int w, int h, String label, String cmd) {
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
        Runnable press = () -> runCmd(cmd);
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
            for (Constructor<?> ctor : bw.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 6 && p[0] == int.class) {
                    Object action = Proxy.newProxyInstance(bw.getClassLoader(), new Class<?>[]{p[5]},
                            (pr, m, a) -> {
                                if ("onPress".equals(m.getName()) || "press".equals(m.getName())) {
                                    press.run();
                                }
                                if ("toString".equals(m.getName())) return label;
                                return null;
                            });
                    return ctor.newInstance(x, y, w, h, text, action);
                }
            }
        } catch (Throwable t) {
            Debug.logWarning("T2MENU button: " + t.getMessage());
        }
        return null;
    }

    private void runCmd(String cmd) {
        if (cmd != null && cmd.startsWith("TAB:")) {
            try { tab = Integer.parseInt(cmd.substring(4)); } catch (Throwable ignored) {}
            dropProv = false;
            dropModel = false;
            rebuild();
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
            rebuild();
            return;
        }
        if (cmd != null && cmd.startsWith("PROV:")) {
            applyProvider(cmd.substring(5));
            dropProv = false;
            rebuild();
            return;
        }
        if (cmd != null && cmd.startsWith("MODEL:")) {
            applyModel(cmd.substring(6));
            dropModel = false;
            rebuild();
            return;
        }
        if ("SAVECFG".equals(cmd)) {
            saveFields();
            return;
        }
        closeMe();
        if (cmd != null) exec(cmd);
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
            return MinecraftClient.getInstance().textRenderer;
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
        if (b != null && !b.isEmpty()) cfg.bind = b;
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
            AltoClef.getCommandExecutor().executeWithPrefix(name);
            Debug.logMessage("T2MENU " + prefix + name);
        } catch (Throwable t) {
            Debug.logWarning("T2MENU exec " + t.getMessage());
        }
    }
}
