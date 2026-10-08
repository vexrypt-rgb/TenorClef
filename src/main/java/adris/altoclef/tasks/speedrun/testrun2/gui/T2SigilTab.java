package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.butler.ButlerConfig;
import adris.altoclef.sigil.Sigil;
import adris.altoclef.sigil.SigilKeyring;
import adris.altoclef.sigil.SigilService;
import adris.altoclef.util.helpers.ConfigHelper;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * SIGIL tab of {@link T2MenuScreen}: keyring management, seal/open and the Butler toggles.
 * Every PBKDF2/ECDH step runs on {@link SigilService}'s worker; the render thread only reads cached strings.
 * Plaintext and passphrases are shown on screen only, never logged.
 */
final class T2SigilTab {
    private T2SigilTab() {}

    static final int ID = 6;

    // Field contents survive the rebuild that every click triggers.
    private static String name = "";
    private static String paste = "";
    private static String message = "";
    private static volatile String status = "Keys live in altoclef/sigil/ (treat it like a password file).";
    private static String summary = "";

    static String status() { return status; }
    static String summary() { return summary; }
    static String[] fieldLabels() { return new String[]{"name", "paste", "message"}; }

    private static void refresh() {
        SigilKeyring kr = SigilService.get().keyring();
        ButlerConfig cfg = ButlerConfig.getInstance();
        summary = "circles " + names(kr.circles().stream().map(Sigil.Circle::name).toList())
                + "  signets " + names(kr.signets().stream().map(Sigil.Signet::name).toList())
                + "  contacts " + kr.contacts().size();
    }

    private static String names(List<String> l) {
        return l.isEmpty() ? "-" : String.join(",", l);
    }

    static void layout(T2MenuScreen s) {
        refresh();
        int cy = s.contentY + 26;
        int fx = s.contentX + 52;
        int fw = s.contentW - 52;
        s.sigName = T2MenuActions.textField(s, fx, cy, fw, 14, name);
        s.sigPaste = T2MenuActions.textField(s, fx, cy + 16, fw, 14, paste);
        s.sigMsg = T2MenuActions.textField(s, fx, cy + 32, fw, 14, message);
        T2MenuActions.attach(s, s.sigName);
        T2MenuActions.attach(s, s.sigPaste);
        T2MenuActions.attach(s, s.sigMsg);

        ButlerConfig cfg = ButlerConfig.getInstance();
        int cols = 3;
        int bw = (s.contentW - 8) / cols;
        String[][] rows = {
                {"new circle", "SIG:newcircle", "new signet", "SIG:newsignet", "add contact", "SIG:addcontact"},
                {"open token", "SIG:open", "seal + send", "SIG:send", "seal > clip", "SIG:clip"},
                {"copy circle", "SIG:copycircle", "copy signet", "SIG:copysignet", "reload", "SIG:reload"},
                {"auto-decrypt  " + onOff(cfg.sigilAutoDecrypt), "SIG:auto",
                        "require sealed  " + onOff(cfg.sigilRequireSealed), "SIG:req",
                        "seal replies  " + onOff(cfg.sigilReplySealed), "SIG:reply"},
                {"circle  " + dflt(cfg.sigilCircle), "SIG:cycc", "signet  " + dflt(cfg.sigilSignet), "SIG:cycs"},
        };
        int y = cy + 54;
        for (String[] r : rows) {
            for (int c = 0; c * 2 + 1 < r.length; c++) {
                T2MenuActions.attach(s, T2MenuActions.button(s, s.contentX + c * (bw + 4), y, bw, 18, r[c * 2], r[c * 2 + 1]));
            }
            y += 20;
        }
        T2MenuActions.attach(s, T2MenuActions.button(s, s.px1 - 108, s.footerT + 2, 96, 18, "close", null));
    }

    private static String onOff(boolean b) { return b ? "on" : "off"; }

    private static String dflt(String v) { return v == null || v.isEmpty() ? "auto" : v; }

    private static void capture(T2MenuScreen s) {
        name = T2MenuActions.fieldText(s.sigName);
        paste = T2MenuActions.fieldText(s.sigPaste);
        message = T2MenuActions.fieldText(s.sigMsg);
    }

    private static void saveButler() {
        ConfigHelper.saveConfig("configs/butler.json", ButlerConfig.getInstance());
    }

    private static void say(String m) { status = m; }

    private static void clipboard(String text) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Object kb = MinecraftClient.class.getField("keyboard").get(mc);
            kb.getClass().getMethod("setClipboard", String.class).invoke(kb, text);
        } catch (Throwable ignored) {
            // no clipboard: the text is still in the status line
        }
    }

    /** Next option, wrapping through "" (= automatic). */
    private static String cycle(List<String> options, String current) {
        if (options.isEmpty()) return "";
        int i = options.indexOf(current);
        if (i < 0) return options.get(0);
        return i + 1 >= options.size() ? "" : options.get(i + 1);
    }

    static void run(T2MenuScreen s, String act) {
        capture(s);
        SigilService svc = SigilService.get();
        SigilKeyring kr = svc.keyring();
        ButlerConfig cfg = ButlerConfig.getInstance();
        switch (act) {
            case "newcircle" -> {
                if (name.isBlank()) { say("Give the circle a name first."); break; }
                if (kr.circle(name) != null) { say("Circle " + name + " exists; replacing it would lose its passphrase. Pick another name."); break; }
                String pass = paste.isBlank() ? Sigil.dicePhrase(5) : paste;
                String n = name;
                say("Deriving key for " + n + " ...");
                svc.async(() -> {
                    Sigil.Circle c = kr.addCircle(n, pass, "tenorclef gui");
                    return "Circle " + c.name() + " ready, fingerprint " + c.fingerprint() + " ("
                            + Sigil.speakFingerprint(c.fingerprint()) + "). Passphrase: " + pass;
                }, m -> { say(m); paste = ""; rebuildIfOpen(s); }, m -> say("Circle failed: " + m));
            }
            case "newsignet" -> {
                if (name.isBlank()) { say("Give the signet a name first."); break; }
                if (kr.signet(name) != null) { say("Signet " + name + " exists; its key would be replaced. Pick another name."); break; }
                String n = name;
                svc.async(() -> {
                    Sigil.Signet sg = kr.createSignet(n);
                    return "Signet " + sg.name() + " (" + sg.shortId() + ") created. Use copy signet to share it.";
                }, m -> { say(m); rebuildIfOpen(s); }, m -> say("Signet failed: " + m));
            }
            case "addcontact" -> {
                Sigil.Contact c = Sigil.parseSignetAnnouncement(paste);
                if (c == null) { say("Paste an S1+PK... announcement into the paste box."); break; }
                svc.async(() -> {
                    kr.addContact(c);
                    return "Contact " + c.alias() + " (" + c.shortId() + ") added.";
                }, m -> { say(m); paste = ""; rebuildIfOpen(s); }, m -> say("Contact failed: " + m));
            }
            case "open" -> {
                if (!SigilService.looksSealed(paste)) { say("Paste an S1C./S1K./S1E. token into the paste box."); break; }
                say("Opening ...");
                svc.open("gui", paste,
                        o -> say("[" + o.mode() + " " + o.label() + "] " + (o.from() == null || o.from().isEmpty() ? "" : o.from() + ": ") + o.plaintext()),
                        m -> say("Could not open: " + m));
            }
            case "send" -> {
                if (name.isBlank() || message.isBlank()) { say("Fill name (circle or contact) and message."); break; }
                T2MenuActions.exec("seal " + name.trim() + " " + message);
                say("Sealing for " + name.trim() + " ...");
            }
            case "clip" -> {
                if (name.isBlank() || message.isBlank()) { say("Fill name (circle or contact) and message."); break; }
                svc.seal(name.trim(), message, Sigil.MAX_LINE, lines -> {
                    clipboard(String.join("\n", lines));
                    say(lines.size() + " sealed line(s) copied to the clipboard.");
                }, m -> say("Seal failed: " + m));
            }
            case "copycircle" -> {
                Sigil.Circle c = kr.circle(name.isBlank() ? cfg.sigilCircle : name);
                if (c == null && kr.circles().size() == 1) c = kr.circles().get(0);
                if (c == null) { say("Name a circle (or set a default) first."); break; }
                clipboard(Sigil.announceCircle(c));
                say("Circle announcement copied (no passphrase in it). Say the fingerprint aloud: " + Sigil.speakFingerprint(c.fingerprint()));
            }
            case "copysignet" -> {
                Sigil.Signet sg = kr.signet(name.isBlank() ? cfg.sigilSignet : name);
                if (sg == null && !kr.signets().isEmpty()) sg = kr.signets().get(0);
                if (sg == null) { say("Create a signet first."); break; }
                clipboard(Sigil.announceSignet(sg));
                say("Signet announcement copied.");
            }
            case "reload" -> { kr.reload(); say("Keyring reloaded."); }
            case "auto" -> { cfg.sigilAutoDecrypt = !cfg.sigilAutoDecrypt; saveButler(); }
            case "req" -> { cfg.sigilRequireSealed = !cfg.sigilRequireSealed; saveButler(); }
            case "reply" -> { cfg.sigilReplySealed = !cfg.sigilReplySealed; saveButler(); }
            case "cycc" -> {
                cfg.sigilCircle = cycle(kr.circles().stream().map(Sigil.Circle::name).toList(), cfg.sigilCircle);
                saveButler();
            }
            case "cycs" -> {
                cfg.sigilSignet = cycle(kr.signets().stream().map(Sigil.Signet::name).toList(), cfg.sigilSignet);
                saveButler();
            }
            default -> say("Unknown action " + act);
        }
        T2MenuActions.rebuild(s);
    }

    private static void rebuildIfOpen(T2MenuScreen s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && adris.altoclef.multiversion.ScreenVer.current(mc) == s) T2MenuActions.rebuild(s);
    }
}
