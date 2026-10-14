package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;

/** Hit-test helpers and command dispatch for {@link T2MenuScreen}. */
final class T2MenuActions {
    private T2MenuActions() {}

    static void rebuild(T2MenuScreen s) {
        s.rebuild();
    }

    /** Registers a painted button: its hit rectangle, its label and the command a click runs. */
    static void button(T2MenuScreen s, int x, int y, int w, int h, String label, String cmd) {
        s.hits.add(new int[]{x, y, w, h});
        s.hitCmd.add(cmd);
        s.hitLab.add(label);
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

    static TextFieldWidget textField(T2MenuScreen s, int x, int y, int w, int h, String value) {
        return s.addField(x, y, w, h, value);
    }

    static String fieldText(TextFieldWidget box) {
        return box == null ? "" : box.getText();
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

    static void setBox(TextFieldWidget box, String value) {
        if (box != null && value != null) box.setText(value);
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
        if (mc != null) mc.setScreen(null);
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
