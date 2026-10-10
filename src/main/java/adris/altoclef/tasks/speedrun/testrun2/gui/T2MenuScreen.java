package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.Debug;
import adris.altoclef.multiversion.ScreenVer;
import adris.altoclef.multiversion.TextVer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractCommandBlockScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * TenorClef control panel. Same floating-card language as Ostinato
 * (centered glass, sidebar, header chip, drop shadow) but a warm
 * amber/rose palette so the two menus stay distinct in a paired
 * session. Widgets are only the Agent text fields; everything else
 * is painted and hit-tested here.
 */
public class T2MenuScreen extends Screen {

    private static final String TITLE = "TenorClef";

    private static int tab = Integer.getInteger("tenorclef.menu.tab", 0);
    private static int pendingOpen;

    static int tab() { return tab; }
    static void setTab(int v) { tab = v; }

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

    // TEMPORARY showcase tab: commands added since the vexrypt/Ostinato work began.
    private static final String[][] TAB_SHOW_L = {
            {"Everything  all demos in a row", "show everything"},
            {"parkour  4/3/2-gap run", "show parkour"},
            {"swim  surface lanes", "show swim"},
            {"dive  roofed + air pocket", "show dive"},
            {"boat  place, sail, collect", "show boat"},
            {"ladder  climb a tower", "show ladder"},
            {"melee  4 zombies, sword + shield", "show melee"},
            {"ranged  3 skeletons, bow", "show ranged"},
            {"horde  mixed mobs, full kit", "show horde"},
    };
    private static final String[][] TAB_SHOW_R = {
            {"kinematic  slalom", "show kinematic"},
            {"physics  sim-driven slalom", "show physics"},
            {"pillar  build a tower", "show pillar"},
            {"bridge  span a void gap", "show bridge"},
            {"tunnel  mine through", "show tunnel"},
            {"off  stop demo", "show off"},
            {"stop", "stop"},
    };

    TextFieldWidget keyBox;
    TextFieldWidget urlBox;
    TextFieldWidget modelBox;
    TextFieldWidget bindBox;
    TextFieldWidget cmpName;
    TextFieldWidget sigName;
    TextFieldWidget sigPaste;
    TextFieldWidget sigMsg;
    TextFieldWidget swItem;
    TextFieldWidget swCount;
    TextFieldWidget swSchem;
    //#if MC >= 12002
    private net.minecraft.client.gui.widget.EditBoxWidget editor;
    //#endif
    boolean dropProv;
    boolean dropModel;
    int dropStart = Integer.MAX_VALUE;
    final List<int[]> hits = new ArrayList<>();
    final List<String> hitCmd = new ArrayList<>();
    final List<String> hitLab = new ArrayList<>();

    int px0, py0, px1, py1, headerB, sideR, contentX, contentY, contentW, footerT;

    public T2MenuScreen() {
        super(TextVer.literal(TITLE));
    }

    public static void open() {
        pendingOpen = 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        Runnable show = () -> {
            try {
                if (ScreenVer.current(mc) instanceof T2MenuScreen) return;
                mc.setScreen(new T2MenuScreen());
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

    public static void openSoon() {
        pendingOpen = 12;
        Debug.logMessage("T2MENU queued");
    }

    public static void poll() {
        if (pendingOpen <= 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        Screen current = ScreenVer.current(mc);
        // The command that queued the menu was typed into one of these; wait for it to close.
        if (current instanceof ChatScreen || current instanceof AbstractCommandBlockScreen) return;
        if (current instanceof T2MenuScreen) {
            pendingOpen = 0;
            return;
        }
        pendingOpen--;
        if (pendingOpen <= 0) open();
    }

    private String[][] tabLeft() {
        if (tab == 1) return TAB_LINK_L;
        if (tab == 2) return TAB_MEDIA_L;
        if (tab == 3) return TAB_AGENT_L;
        if (tab == 4 || tab == 6 || tab == 7 || tab == 8) return new String[0][];
        if (tab == 5) return TAB_SHOW_L;
        return TAB_TASKS_L;
    }

    private String[][] tabRight() {
        if (tab == 1) return TAB_LINK_R;
        if (tab == 2) return TAB_MEDIA_R;
        if (tab == 3) return TAB_AGENT_R;
        if (tab == 4 || tab == 6 || tab == 7 || tab == 8) return new String[0][];
        if (tab == 5) return TAB_SHOW_R;
        return TAB_TASKS_R;
    }

    void layout() {
        boolean wide = tab == T2CompositionTab.ID;
        int pw = Math.min(wide ? 780 : 560, Math.max(420, this.width - 32));
        int ph = Math.min(wide ? 420 : tab == 3 ? 292 : 268, this.height - 16);
        if (ph < 200) ph = Math.max(188, this.height - 16);
        px0 = (this.width - pw) / 2;
        py0 = (this.height - ph) / 2;
        px1 = px0 + pw;
        py1 = py0 + ph;
        headerB = py0 + 28;
        footerT = py1 - 24;
        sideR = px0 + (pw >= 500 ? 118 : 100);
        contentX = sideR + 10;
        contentY = headerB + 8;
        contentW = px1 - contentX - 10;
    }

    @Override
    public void init() {
        super.init();
        hits.clear();
        hitCmd.clear();
        hitLab.clear();
        dropStart = Integer.MAX_VALUE;
        layout();
        T2MenuActions.button(this, px1 - 22, py0 + 7, 14, 14, "x", null);
        String[] nav = {"Tasks", "Link", "Media", "Agent", "Faults", "Showcase", "SIGIL", "Compose", "Swarm"};
        int[] navId = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        int iy = headerB + 8;
        for (int i = 0; i < nav.length; i++) {
            T2MenuActions.button(this, px0 + 6, iy, sideR - px0 - 12, 16, nav[i], "TAB:" + navId[i]);
            iy += 18;
        }
        String[][] L = tabLeft();
        String[][] R = tabRight();
        int colW = Math.max(80, (contentW - 8) / 2);
        int bh = 20;
        if (tab == T2SigilTab.ID) {
            T2SigilTab.layout(this);
            return;
        }
        if (tab == T2SwarmTab.ID) {
            T2SwarmTab.layout(this);
            return;
        }
        if (tab == T2CompositionTab.ID) {
            T2CompositionTab.layout(this);
            return;
        }
        if (tab == 3) {
            AgentConfig cfg = AgentConfig.cached();
            AgentPresets.Preset preset = AgentPresets.byId(cfg.provider);
            int top = contentY;
            T2MenuActions.button(this, contentX, top, colW, 18, "API  " + preset.label, "DROP:PROV");
            T2MenuActions.button(this, contentX + colW + 8, top, colW, 18, "Model  " + cfg.model, "DROP:MODEL");
            int y = contentY + 44;
            for (String[] row : L) {
                T2MenuActions.button(this, contentX, y, colW, bh, row[0], row[1]);
                y += 22;
            }
            y = contentY + 44;
            for (String[] row : R) {
                T2MenuActions.button(this, contentX + colW + 8, y, colW, bh, row[0], row[1]);
                y += 22;
            }
            int fx = contentX;
            int fw = contentW;
            int fy = footerT - 62;
            keyBox = T2MenuActions.textField(this, fx, fy, fw, 14, cfg.apiKey);
            urlBox = T2MenuActions.textField(this, fx, fy + 15, fw, 14, cfg.url);
            modelBox = T2MenuActions.textField(this, fx, fy + 30, fw / 2 - 4, 14, cfg.model);
            bindBox = T2MenuActions.textField(this, fx + fw / 2 + 4, fy + 30, fw / 2 - 4, 14, cfg.bind);
            T2MenuActions.button(this, px1 - 212, footerT + 2, 96, 18, "save api", "SAVECFG");
            T2MenuActions.button(this, px1 - 108, footerT + 2, 96, 18, "close", null);
            // Dropdowns last: hits paint in order and click-test in reverse, so they sit on top.
            dropStart = hits.size();
            if (dropProv) {
                int py = top + 20;
                for (AgentPresets.Preset p : AgentPresets.ALL) {
                    T2MenuActions.button(this, contentX, py, colW, 16, p.label, "PROV:" + p.id);
                    py += 17;
                }
            }
            if (dropModel) {
                int py = top + 20;
                for (String m : preset.models) {
                    T2MenuActions.button(this, contentX + colW + 8, py, colW, 16, m, "MODEL:" + m);
                    py += 17;
                }
            }
            return;
        }
        int y = contentY;
        for (String[] row : L) {
            T2MenuActions.button(this, contentX, y, colW, bh, row[0], row[1]);
            y += 22;
        }
        y = contentY;
        for (String[] row : R) {
            T2MenuActions.button(this, contentX + colW + 8, y, colW, bh, row[0], row[1]);
            y += 22;
        }
        T2MenuActions.button(this, px1 - 108, footerT + 2, 96, 18, "close", null);
    }

    /** Drops every widget and lays the current tab out again. */
    void rebuild() {
        //#if MC >= 11700
        clearChildren();
        //#else
        //$$ children.clear();
        //$$ buttons.clear();
        //#endif
        setFocused(null);
        init();
    }

    TextFieldWidget addField(int x, int y, int w, int h, String value) {
        TextFieldWidget box = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x, y, w, h, TextVer.literal(TITLE));
        box.setMaxLength(256);
        box.setText(value == null ? "" : value);
        //#if MC >= 11700
        return addDrawableChild(box);
        //#else
        //$$ return addButton(box);
        //#endif
    }

    /** Adds the multi-line editor. False before 1.20.2, which has no such widget. */
    boolean addEditor(int x, int y, int w, int h, String text) {
        //#if MC >= 12111
        //$$ editor = net.minecraft.client.gui.widget.EditBoxWidget.builder().x(x).y(y)
        //$$         .build(MinecraftClient.getInstance().textRenderer, w, h, TextVer.literal(TITLE));
        //#elseif MC >= 12002
        Text title = TextVer.literal(TITLE);
        editor = new net.minecraft.client.gui.widget.EditBoxWidget(MinecraftClient.getInstance().textRenderer, x, y, w, h, title, title);
        //#endif
        //#if MC >= 12002
        editor.setText(text == null ? "" : text);
        addDrawableChild(editor);
        return true;
        //#else
        //$$ return false;
        //#endif
    }

    String editorText() {
        //#if MC >= 12002
        return editor == null ? "" : editor.getText();
        //#else
        //$$ return "";
        //#endif
    }

    public boolean shouldPause() {
        return false;
    }

    //#if MC >= 260000
    //$$ @Override
    //$$ public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    //$$     adris.altoclef.multiversion.DrawContextWrapper g = adris.altoclef.multiversion.DrawContextWrapper.of(context);
    //$$     paintUi(g, mouseX, mouseY);
    //$$     super.extractRenderState(context, mouseX, mouseY, delta);
    //$$     if (dropStart < hits.size()) T2MenuLook.paintHits(this, g, mouseX, mouseY, dropStart);
    //$$ }
    //$$
    //$$ // The panel supplies its own backdrop; skip the blurred menu background.
    //$$ @Override
    //$$ public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    //$$ }
    //#elseif MC >= 12000
    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        adris.altoclef.multiversion.DrawContextWrapper g = adris.altoclef.multiversion.DrawContextWrapper.of(context);
        paintUi(g, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        // Text-field widgets draw after the panel; repaint open dropdowns over them.
        if (dropStart < hits.size()) T2MenuLook.paintHits(this, g, mouseX, mouseY, dropStart);
    }

    //#if MC >= 12002
    // super.render() draws the background first, and on 1.21+ that applies the menu blur,
    // which would blur the panel painted above. The panel supplies its own backdrop.
    @Override
    public void renderBackground(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
    }
    //#endif
    //#else
    //$$ @Override
    //$$ public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
    //$$     paintUi(adris.altoclef.multiversion.DrawContextWrapper.of(matrices), mouseX, mouseY);
    //$$     com.mojang.blaze3d.systems.RenderSystem.enableTexture();
    //$$     super.render(matrices, mouseX, mouseY, delta);
    //$$ }
    //#endif

    private void paintUi(adris.altoclef.multiversion.DrawContextWrapper g, int mx, int my) {
        T2MenuLook.paint(this, g, mx, my);
    }

    //#if MC >= 12111
    //$$ @Override
    //$$ public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
    //$$     double mx = click.x();
    //$$     double my = click.y();
    //$$     int button = click.button();
    //$$     if (button == 0) {
    //$$         for (int i = hits.size() - 1; i >= 0; i--) {
    //$$             int[] b = hits.get(i);
    //$$             if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
    //$$                 T2MenuActions.runCmd(this, i < hitCmd.size() ? hitCmd.get(i) : null);
    //$$                 return true;
    //$$             }
    //$$         }
    //$$     }
    //$$     try {
    //$$         return super.mouseClicked(click, doubled);
    //$$     } catch (Throwable t) {
    //$$         return false;
    //$$     }
    //$$ }
    //#else
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                int[] b = hits.get(i);
                if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                    T2MenuActions.runCmd(this, i < hitCmd.size() ? hitCmd.get(i) : null);
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
    //#endif
}
