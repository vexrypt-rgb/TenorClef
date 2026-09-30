package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.Debug;
import adris.altoclef.multiversion.DrawContextWrapper;
import adris.altoclef.swarm.SwarmAdapter;
import net.minecraft.client.MinecraftClient;

/**
 * Swarm tab of {@link T2MenuScreen}: toggles and text fields for Ostinato's encrypted swarm link
 * (enable, signed senders, channel, roster, sigil home) and buttons for status, ping, reload and
 * group builds. Everything goes through {@link SwarmAdapter}; Ostinato owns the link itself.
 */
final class T2MenuSwarm {
    private T2MenuSwarm() {}

    private static final String[] CHANNELS = {"whisper", "global", "team"};

    /** Survive a rebuild of the screen (every button press re-inits it). */
    private static String groupText = "";
    private static String fileText = "";

    static void build(T2MenuScreen s, int colW, int bh) {
        int x2 = s.contentX + colW + 8;
        int y = s.contentY + 12;
        boolean ok = SwarmAdapter.available();
        T2MenuActions.attach(s, T2MenuActions.button(s, s.contentX, y, colW, bh,
                "swarm  " + (ok && SwarmAdapter.enabled() ? "ON" : "OFF"), "SW:TOGGLE"));
        T2MenuActions.attach(s, T2MenuActions.button(s, x2, y, colW, bh, "status", "SW:status"));
        y += 22;
        T2MenuActions.attach(s, T2MenuActions.button(s, s.contentX, y, colW, bh,
                "signed senders  " + (Boolean.FALSE.equals(SwarmAdapter.get("swarmRequireSignedSender")) ? "no" : "yes"), "SW:SIGNED"));
        T2MenuActions.attach(s, T2MenuActions.button(s, x2, y, colW, bh, "ping group", "SW:PING"));
        y += 22;
        T2MenuActions.attach(s, T2MenuActions.button(s, s.contentX, y, colW, bh,
                "channel  " + SwarmAdapter.getString("swarmChannel"), "SW:CHANNEL"));
        T2MenuActions.attach(s, T2MenuActions.button(s, x2, y, colW, bh, "reload roster", "SW:reload"));
        y += 22;
        T2MenuActions.attach(s, T2MenuActions.button(s, s.contentX, y, colW, bh, "build here", "SW:BUILD"));
        T2MenuActions.attach(s, T2MenuActions.button(s, x2, y, colW, bh, "stop build", "SW:stop"));

        int fy = s.footerT - 62;
        s.rosterBox = T2MenuActions.textField(s, s.contentX, fy, s.contentW, 14, SwarmAdapter.getString("swarmRosterFile"));
        s.homeBox = T2MenuActions.textField(s, s.contentX, fy + 15, s.contentW, 14, SwarmAdapter.getString("swarmSigilHome"));
        s.groupBox = T2MenuActions.textField(s, s.contentX, fy + 30, s.contentW / 3 - 4, 14, groupText);
        s.fileBox = T2MenuActions.textField(s, s.contentX + s.contentW / 3 + 4, fy + 30, s.contentW * 2 / 3 - 4, 14, fileText);
        T2MenuActions.attach(s, s.rosterBox);
        T2MenuActions.attach(s, s.homeBox);
        T2MenuActions.attach(s, s.groupBox);
        T2MenuActions.attach(s, s.fileBox);
        T2MenuActions.attach(s, T2MenuActions.button(s, s.px1 - 212, s.footerT + 2, 96, 18, "save swarm", "SW:SAVE"));
        T2MenuActions.attach(s, T2MenuActions.button(s, s.px1 - 108, s.footerT + 2, 96, 18, "close", null));
    }

    static void paint(T2MenuScreen s, DrawContextWrapper g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String head = SwarmAdapter.available()
                ? "roster / sigil home / group + schematic (build needs a group and a file)"
                : SwarmAdapter.detail();
        g.drawText(mc.textRenderer, T2MenuLook.trim(s, head, s.contentW), s.contentX, s.footerT - 74,
                SwarmAdapter.available() ? T2MenuLook.C_MUTED : 0xFFFF8A84, false);
        g.drawText(mc.textRenderer, "Ostinato encrypted swarm link", s.contentX, s.contentY, T2MenuLook.C_ACCENT, false);
    }

    static void run(T2MenuScreen s, String cmd) {
        if (!SwarmAdapter.available()) {
            Debug.logWarning("SWARM " + SwarmAdapter.detail());
            return;
        }
        groupText = T2MenuActions.fieldText(s.groupBox).trim();
        fileText = T2MenuActions.fieldText(s.fileBox).trim();
        switch (cmd) {
            case "TOGGLE" -> SwarmAdapter.setEnabled(!SwarmAdapter.enabled());
            case "SIGNED" -> SwarmAdapter.set("swarmRequireSignedSender",
                    String.valueOf(Boolean.FALSE.equals(SwarmAdapter.get("swarmRequireSignedSender"))));
            case "CHANNEL" -> {
                String cur = SwarmAdapter.getString("swarmChannel").toLowerCase();
                int i = 0;
                for (int k = 0; k < CHANNELS.length; k++) if (CHANNELS[k].equals(cur)) i = k + 1;
                SwarmAdapter.set("swarmChannel", CHANNELS[i % CHANNELS.length]);
            }
            case "SAVE" -> {
                report(SwarmAdapter.set("swarmRosterFile", T2MenuActions.fieldText(s.rosterBox).trim()));
                report(SwarmAdapter.set("swarmSigilHome", T2MenuActions.fieldText(s.homeBox).trim()));
                Debug.logMessage("SWARM saved roster=" + SwarmAdapter.getString("swarmRosterFile")
                        + " home=" + SwarmAdapter.getString("swarmSigilHome"));
            }
            case "PING" -> send(groupText.isEmpty() ? "ping" : "ping " + groupText);
            case "BUILD" -> {
                if (groupText.isEmpty() || fileText.isEmpty()) {
                    Debug.logWarning("SWARM build needs a group and a schematic file");
                } else {
                    send("build " + groupText + " " + fileText);
                }
            }
            default -> send(cmd);
        }
    }

    private static void send(String args) {
        if (!SwarmAdapter.run(args)) Debug.logWarning("SWARM '" + args + "' was not accepted");
    }

    private static void report(String err) {
        if (err != null) Debug.logWarning("SWARM " + err);
    }
}
