package adris.altoclef.tasks.speedrun.testrun2.gui;

/**
 * Paint for {@link T2MenuScreen}. Ostinato card language, TenorClef amber/rose.
 */
final class T2MenuLook {

    static final int C_SCRIM_TOP = 0x8C120E0A;
    static final int C_SCRIM_BOT = 0xB4120E0A;
    static final int C_CARD = 0xF0161B26;
    static final int C_CARD_BOT = 0xF20E1118;
    static final int C_SIDE = 0x70080A0F;
    static final int C_BTN = 0xFF1C2230;
    static final int C_BTN_HOVER = 0xFF2A3242;
    static final int C_BORDER = 0xFF2B3344;
    static final int C_ACCENT = 0xFFE8C96A;
    static final int C_ACCENT2 = 0xFFFF9E64;
    static final int C_ACCENT3 = 0xFFF7768E;
    static final int C_DANGER = 0xFFE5534B;
    static final int C_TEXT = 0xFFE6EAF0;
    static final int C_MUTED = 0xFF8B93A3;
    static final int C_DIM = 0xFF5C6475;
    static final int[] LOGO = {C_ACCENT, C_ACCENT, C_ACCENT2, C_ACCENT2, C_ACCENT2, C_ACCENT3, C_ACCENT3, C_ACCENT3, C_ACCENT3};

    private T2MenuLook() {}

    static void paint(T2MenuScreen s, adris.altoclef.multiversion.DrawContextWrapper g, int mx, int my) {
        if (g == null) return;
        s.layout();
        fillV(g, 0, 0, s.width, s.height, C_SCRIM_TOP, C_SCRIM_BOT);
        g.fill(s.px0 + 4, s.py1, s.px1 + 5, s.py1 + 5, 0x50000000);
        g.fill(s.px1, s.py0 + 4, s.px1 + 5, s.py1 + 2, 0x40000000);
        g.fill(s.px0, s.py0, s.px1, s.py1, C_BORDER);
        fillV(g, s.px0 + 1, s.py0 + 1, s.px1 - 1, s.py1 - 1, C_CARD, C_CARD_BOT);
        g.fill(s.px0 + 3, s.py0 + 1, s.px1 - 3, s.py0 + 2, 0x33FFFFFF);

        g.fill(s.px0 + 11, s.py0 + 8, s.px0 + 14, s.py0 + 21, C_ACCENT);
        g.fill(s.px0 + 14, s.py0 + 8, s.px0 + 22, s.py0 + 11, C_ACCENT);
        g.fill(s.px0 + 14, s.py0 + 14, s.px0 + 20, s.py0 + 16, C_ACCENT2);
        drawLogo(s, g, "TenorClef", s.px0 + 26, s.py0 + 10);
        String chip = "control";
        int cx = s.px0 + 26 + net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth("TenorClef") + 8;
        int cw = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(chip) + 10;
        g.fill(cx, s.py0 + 9, cx + cw, s.py0 + 20, 0x22E8C96A);
        g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, chip, cx + 5, s.py0 + 11, C_ACCENT, false);
        g.fill(s.px0 + 1, s.headerB, s.px1 - 1, s.headerB + 1, 0x16FFFFFF);

        g.fill(s.px0 + 1, s.headerB + 1, s.sideR, s.footerT, C_SIDE);
        g.fill(s.sideR, s.headerB + 1, s.sideR + 1, s.footerT, 0x16FFFFFF);

        g.fill(s.px0 + 1, s.footerT, s.px1 - 1, s.py1 - 1, 0x80080A0F);
        g.fill(s.px0 + 1, s.footerT, s.px1 - 1, s.footerT + 1, 0x16FFFFFF);
        g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, "RSG \u00b7 AA \u00b7 fleet", s.px0 + 10, s.footerT + 8, C_DIM, false);

        int tab = T2MenuScreen.tab();
        if (tab == 3) {
            g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, "API key / URL / model / bind", s.contentX, s.footerT - 74, C_MUTED, false);
        }
        if (tab == 6) {
            T2MenuSwarm.paint(s, g);
        }
        if (tab == 4) {
            long now = System.currentTimeMillis();
            String head = "time lost to faults: "
                    + (adris.altoclef.tasks.speedrun.testrun2.fault.FaultBook.lostMs(now) / 1000)
                    + "s   (altoclef/faults.jsonl)";
            g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, trim(s, head, s.contentW), s.contentX, s.contentY, C_ACCENT, false);
            String[] lines = adris.altoclef.tasks.speedrun.testrun2.fault.FaultBook.recentText().split("\n");
            int rows = Math.max(1, (s.footerT - s.contentY - 20) / 11);
            int from = Math.max(0, lines.length - rows);
            int ly = s.contentY + 14;
            for (int i = lines.length - 1; i >= from; i--) {
                String str = trim(s, lines[i], s.contentW);
                int col = str.contains(" E") ? 0xFFFF8A84 : C_TEXT;
                g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, str, s.contentX, ly, col, false);
                ly += 11;
            }
        }
        paintHits(s, g, mx, my, 0);
    }

    static void paintHits(T2MenuScreen s, adris.altoclef.multiversion.DrawContextWrapper g, int mx, int my, int start) {
        int tab = T2MenuScreen.tab();
        for (int i = start; i < s.hits.size(); i++) {
            int[] b = s.hits.get(i);
            String cmd = s.hitCmd.get(i);
            String label = s.hitLab.get(i);
            boolean hover = mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
            if (cmd != null && cmd.startsWith("TAB:")) {
                boolean on = cmd.equals("TAB:" + tab);
                if (on) {
                    g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x22E8C96A);
                    g.fill(b[0], b[1] + 3, b[0] + 2, b[1] + b[3] - 3, C_ACCENT);
                } else if (hover) {
                    g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x14FFFFFF);
                }
                g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, label, b[0] + 10, b[1] + (b[3] - 8) / 2,
                        on ? C_TEXT : C_MUTED, false);
                continue;
            }
            boolean danger = "t2panic".equals(cmd) || "stop".equals(cmd);
            boolean primary = "SAVECFG".equals(cmd) || "SW:SAVE".equals(cmd);
            boolean close = cmd == null;
            int bg = hover ? C_BTN_HOVER : C_BTN;
            if (primary) bg = hover ? 0xFFFFD58A : C_ACCENT;
            if (close && label.equals("x")) {
                g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], hover ? 0x30E5534B : 0x14FFFFFF);
                g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, "x", b[0] + 4, b[1] + 3, hover ? C_TEXT : C_MUTED, false);
                continue;
            }
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], hover ? (danger ? C_DANGER : C_ACCENT) : C_BORDER);
            g.fill(b[0] + 1, b[1] + 1, b[0] + b[2] - 1, b[1] + b[3] - 1, bg);
            int strip = danger ? C_DANGER : C_ACCENT;
            boolean isCmd = cmd != null && !cmd.contains(":") && !primary;
            if (isCmd) g.fill(b[0] + 1, b[1] + 1, b[0] + 3, b[1] + b[3] - 1, strip);
            int fg = primary ? 0xFF120E0A : (danger ? 0xFFFF8A84 : C_TEXT);
            int ty = b[1] + (b[3] - 8) / 2;
            if (close || primary) {
                int tw = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(label);
                g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, label, b[0] + (b[2] - tw) / 2, ty, fg, false);
            } else {
                int sp = label.indexOf("  ");
                String head2 = sp > 0 ? label.substring(0, sp) : label;
                String tail = sp > 0 ? label.substring(sp).trim() : "";
                int tx = b[0] + (isCmd ? 10 : 6);
                g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, head2, tx, ty, fg, false);
                if (!tail.isEmpty()) {
                    g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, tail, tx + net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(head2) + 6, ty, C_MUTED, false);
                }
                if (cmd.startsWith("DROP:")) {
                    g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, "v", b[0] + b[2] - 10, ty, C_MUTED, false);
                }
            }
        }
    }

    static void fillV(adris.altoclef.multiversion.DrawContextWrapper g, int x1, int y1, int x2, int y2, int top, int bot) {
        int h = Math.max(1, y2 - y1);
        int bands = Math.min(12, h);
        for (int i = 0; i < bands; i++) {
            int ya = y1 + h * i / bands;
            int yb = y1 + h * (i + 1) / bands;
            g.fill(x1, ya, x2, yb, lerp(top, bot, i / (float) Math.max(1, bands - 1)));
        }
    }

    static int lerp(int a, int b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int out = 0;
        for (int s = 0; s <= 24; s += 8) {
            int x = (a >>> s) & 0xFF, y = (b >>> s) & 0xFF;
            out |= (Math.round(x + (y - x) * t) & 0xFF) << s;
        }
        return out;
    }

    static void drawLogo(T2MenuScreen s, adris.altoclef.multiversion.DrawContextWrapper g, String str, int x, int y) {
        int cx = x;
        for (int i = 0; i < str.length(); i++) {
            String ch = String.valueOf(str.charAt(i));
            int col = LOGO[Math.min(LOGO.length - 1, i)];
            g.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, ch, cx, y, col, true);
            cx += net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(ch);
        }
    }

    static String trim(T2MenuScreen s, String str, int maxW) {
        if (str == null) return "";
        if (net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(str) <= maxW) return str;
        String t = str;
        while (t.length() > 1 && net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(t + "..") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "..";
    }
}
