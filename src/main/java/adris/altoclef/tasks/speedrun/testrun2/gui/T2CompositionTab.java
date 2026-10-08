package adris.altoclef.tasks.speedrun.testrun2.gui;

import adris.altoclef.AltoClef;
import adris.altoclef.compose.CompositionCompiler;
import adris.altoclef.compose.CompositionRun;
import adris.altoclef.compose.CompositionService;
import adris.altoclef.compose.CompositionStore;
import adris.altoclef.multiversion.DrawContextWrapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Compositions tab of {@link T2MenuScreen}: edit, compile and perform a Composition against the live world.
 * This class only talks to {@link CompositionService}; performing is the normal user-task path and the tab never
 * touches movement internals. Nothing runs until the Perform button is pressed.
 */
final class T2CompositionTab {
    private T2CompositionTab() {}

    static final int ID = 7;

    // Survive the rebuild that every click triggers.
    private static String selected = "";
    private static String nameText = "";
    private static String source = "";
    private static String status = "Pick a composition, or press New. Nothing runs until you press Perform.";
    private static int nodeSel = -1;
    private static boolean loadedOnce;
    private static boolean editorMissing;

    // Pane geometry computed in layout(), used by paint().
    private static int paneTop;
    private static int paneBottom;

    private static CompositionService svc() { return CompositionService.INSTANCE; }

    static void layout(T2MenuScreen s) {
        CompositionStore store = svc().store();
        if (!loadedOnce) {
            loadedOnce = true;
            List<String> names = store.list();
            if (!names.isEmpty()) select(names.get(0));
        }
        int bodyY = s.contentY + 24;
        int listW = 128;
        int edX = s.contentX + listW + 6;
        int edW = s.contentW - listW - 6;
        int avail = s.footerT - 4 - bodyY;
        int edH = Math.max(40, Math.min(140, avail - 44 - 76));

        s.cmpName = T2MenuActions.textField(s, s.contentX, bodyY, listW, 14, nameText);
        T2MenuActions.attach(s, s.cmpName);
        int ry = bodyY + 16;
        int rows = Math.max(1, (edH - 16) / 12);
        List<String> names = store.list();
        for (int i = 0; i < names.size() && i < rows; i++) {
            T2MenuActions.button(s, s.contentX, ry, listW, 12, names.get(i), "CMP:sel:" + names.get(i));
            ry += 12;
        }

        s.cmpEdit = editBox(s, edX, bodyY, edW, edH, source);
        editorMissing = s.cmpEdit == null;
        if (s.cmpEdit != null) T2MenuActions.attach(s, s.cmpEdit);

        int by = bodyY + edH + 4;
        int bw = (s.contentW - 5 * 4) / 6;
        String[][] rowsB = {
                {"Compile", "CMP:compile", "Perform", "CMP:perform", "Stop", "CMP:stop",
                        "Save", "CMP:save", "Reload", "CMP:reload", "Reset", "CMP:reset"},
                {"New", "CMP:new", "Dupe", "CMP:dup", "Re-run", "CMP:reperform",
                        "Clear", "CMP:clear", "Copy log", "CMP:copylog", "Copy err", "CMP:copystack"},
        };
        for (String[] r : rowsB) {
            for (int c = 0; c < 6; c++) {
                T2MenuActions.button(s, s.contentX + c * (bw + 4), by, bw, 18, r[c * 2], r[c * 2 + 1]);
            }
            by += 20;
        }
        paneTop = by + 2;
        paneBottom = s.footerT - 4;
        T2MenuActions.button(s, s.contentX + s.contentW - 30, paneTop + 1, 13, 11, "<", "CMP:prev");
        T2MenuActions.button(s, s.contentX + s.contentW - 15, paneTop + 1, 13, 11, ">", "CMP:next");
    }

    /** Multi-line editor via reflection: builder on 1.21.11+, constructor on 1.20.2-1.21.x, absent before that. */
    private static Object editBox(T2MenuScreen s, int x, int y, int w, int h, String text) {
        try {
            Object tr = T2MenuActions.textRenderer(s);
            Class<?> ebw = Class.forName("net.minecraft.client.gui.widget.EditBoxWidget");
            Object title = T2MenuActions.titleText();
            Object box = null;
            try {
                Object b = ebw.getMethod("builder").invoke(null);
                b = b.getClass().getMethod("x", int.class).invoke(b, x);
                b = b.getClass().getMethod("y", int.class).invoke(b, y);
                for (Method m : b.getClass().getMethods()) {
                    if (m.getName().equals("build") && m.getParameterCount() == 4) {
                        box = m.invoke(b, tr, w, h, title);
                        break;
                    }
                }
            } catch (NoSuchMethodException noBuilder) {
                for (Constructor<?> c : ebw.getConstructors()) {
                    Class<?>[] p = c.getParameterTypes();
                    if (p.length == 7 && p[1] == int.class) {
                        box = c.newInstance(tr, x, y, w, h, title, title);
                        break;
                    }
                }
            }
            if (box == null) return null;
            box.getClass().getMethod("setText", String.class).invoke(box, text == null ? "" : text);
            return box;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void select(String name) {
        try {
            source = svc().store().load(name);
            selected = name;
            nameText = name;
            nodeSel = -1;
        } catch (IOException e) {
            status = "Cannot read " + name + ": " + e.getMessage();
        }
    }

    private static void capture(T2MenuScreen s) {
        if (s.cmpEdit != null) source = T2MenuActions.fieldText(s.cmpEdit);
        nameText = T2MenuActions.fieldText(s.cmpName);
    }

    private static String currentName() {
        String n = nameText.isBlank() ? selected : nameText;
        return CompositionStore.sanitize(n);
    }

    private static boolean save() {
        String n = currentName();
        CompositionStore st = svc().store();
        if (!n.equals(selected) && st.exists(n)) {
            status = "A composition named " + n + " already exists. Pick another name or select it.";
            return false;
        }
        try {
            st.save(n, source);
            selected = n;
            nameText = n;
            status = "Saved " + n + " (" + st.dir().resolve(n + ".java") + ")";
            return true;
        } catch (IOException e) {
            status = "Save failed: " + e.getMessage();
            return false;
        }
    }

    static void run(T2MenuScreen s, String act) {
        capture(s);
        CompositionService svc = svc();
        AltoClef mod = AltoClef.getInstance();
        CompositionStore st = svc.store();
        if (act.startsWith("sel:")) {
            select(act.substring(4));
            T2MenuActions.rebuild(s);
            return;
        }
        switch (act) {
            case "new" -> {
                String n = nameText.isBlank() ? "Comp" : CompositionStore.sanitize(nameText);
                String fresh = n;
                for (int i = 2; st.exists(fresh); i++) fresh = n + i;
                try {
                    st.save(fresh, CompositionStore.template(fresh));
                    select(fresh);
                    status = "Created " + fresh + ".";
                } catch (IOException e) {
                    status = "Create failed: " + e.getMessage();
                }
            }
            case "save" -> save();
            case "reload" -> {
                if (selected.isEmpty()) status = "Nothing selected.";
                else {
                    select(selected);
                    status = "Reloaded " + selected + " from disk.";
                }
            }
            case "dup" -> {
                if (selected.isEmpty()) { status = "Nothing selected."; break; }
                if (!save()) break;
                try {
                    String copy = st.duplicate(selected);
                    select(copy);
                    status = "Duplicated to " + copy + ".";
                } catch (IOException e) {
                    status = "Duplicate failed: " + e.getMessage();
                }
            }
            case "compile" -> {
                CompositionCompiler.Result r = svc.compile(currentName(), source);
                status = r.ok() ? "Compiled " + currentName() + " OK. Not performed."
                        : "COMPILE FAILURE: " + (r.diagnostics.isEmpty() ? r.error : r.diagnostics.size() + " error(s), see the log pane.");
            }
            case "perform" -> {
                if (!save()) break;
                String why = svc.perform(mod, currentName(), source);
                status = why != null ? "Perform refused: " + why : "Performing " + currentName() + ".";
                nodeSel = -1;
            }
            case "reperform" -> {
                String why = svc.reperform(mod);
                status = why != null ? "Re-perform refused: " + why : "Re-performing the last compile.";
                nodeSel = -1;
            }
            case "stop" -> {
                svc.stop(mod);
                status = "Stop requested.";
            }
            case "reset" -> {
                status = svc.reset() ? "Reset to IDLE." : "Stop the running composition first.";
                nodeSel = -1;
            }
            case "clear" -> {
                CompositionRun r = svc.current();
                if (r != null) r.clearEvents();
                svc.clearCompile();
                status = "Output cleared.";
            }
            case "copylog" -> {
                T2SigilTab.clipboard(report(false));
                status = "Log copied to the clipboard.";
            }
            case "copystack" -> {
                T2SigilTab.clipboard(report(true));
                status = "Diagnostics and stack traces copied to the clipboard.";
            }
            case "prev" -> step(-1);
            case "next" -> step(1);
            default -> status = "Unknown action " + act;
        }
        T2MenuActions.rebuild(s);
    }

    private static void step(int d) {
        CompositionRun r = svc().current();
        if (r == null) return;
        int n = r.nodes().size();
        if (n == 0) return;
        int cur = nodeSel < 0 ? n - 1 : nodeSel;
        nodeSel = Math.floorMod(cur + d, n);
    }

    /** Plain-text dump for the clipboard. stacksOnly keeps just diagnostics and stack traces. */
    private static String report(boolean stacksOnly) {
        StringBuilder sb = new StringBuilder();
        CompositionCompiler.Result c = svc().lastCompile();
        if (c != null && !c.ok()) {
            sb.append("COMPILE FAILURE ").append(svc().lastCompiledName()).append('\n');
            for (CompositionCompiler.Diagnostic d : c.diagnostics) sb.append("  ").append(d).append('\n');
            if (c.error != null) sb.append("  ").append(c.error).append('\n');
        }
        CompositionRun r = svc().current();
        if (r != null) {
            if (!stacksOnly) {
                sb.append(r.compositionName).append(" -> ").append(r.state()).append(' ').append(r.failureKind())
                        .append(' ').append(r.failureText()).append("\n\n").append(r.formatLog()).append('\n')
                        .append(r.formatTree(System.currentTimeMillis()));
            } else if (!r.stackTrace().isEmpty()) {
                sb.append(r.failureKind()).append(": ").append(r.failureText()).append('\n').append(r.stackTrace());
            } else if (r.failureKind() != CompositionRun.FailureKind.NONE) {
                sb.append(r.failureKind()).append(": ").append(r.failureText()).append('\n');
            }
        }
        return sb.length() == 0 ? "(nothing to report)" : sb.toString();
    }

    // ---- painting ----

    static void paintRow(DrawContextWrapper g, int[] b, String label, String cmd, boolean hover) {
        boolean on = cmd.substring(8).equals(selected);
        if (on) {
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x22E8C96A);
            g.fill(b[0], b[1] + 1, b[0] + 2, b[1] + b[3] - 1, T2MenuLook.C_ACCENT);
        } else if (hover) {
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x14FFFFFF);
        }
        g.drawText(MinecraftClient.getInstance().textRenderer, label, b[0] + 6, b[1] + 2,
                on ? T2MenuLook.C_TEXT : T2MenuLook.C_MUTED, false);
    }

    private static int resultColor(String r) {
        return switch (r) {
            case "SUCCESS", "SUCCEEDED" -> 0xFF7EE787;
            case "FAILURE", "FAILED" -> 0xFFFF8A84;
            case "CANCELLED" -> 0xFFE8C96A;
            case "RETRY", "BLOCKED" -> 0xFFFF9E64;
            default -> T2MenuLook.C_TEXT;
        };
    }

    private static int eventColor(CompositionRun.EventType t) {
        return switch (t) {
            case TASK_SUCCEEDED, MOVEMENT_SUCCEEDED -> 0xFF7EE787;
            case TASK_FAILED, TASK_EXCEPTION, COMPILE_ERROR, MOVEMENT_FAILED -> 0xFFFF8A84;
            case TASK_CANCELLED, MOVEMENT_CANCELLED -> 0xFFE8C96A;
            case TASK_RETRY, TASK_BLOCKED, TASK_RECOVERY -> 0xFFFF9E64;
            case MOVEMENT_STARTED -> 0xFF7AA2F7;
            case COMPOSITION_STARTED, COMPOSITION_FINISHED -> T2MenuLook.C_ACCENT;
            default -> T2MenuLook.C_TEXT;
        };
    }

    static void paint(T2MenuScreen s, DrawContextWrapper g) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        AltoClef mod = AltoClef.getInstance();
        CompositionService svc = svc();
        if (mod != null) svc.reconcile(mod);
        CompositionRun run = svc.current();
        long now = System.currentTimeMillis();
        int x = s.contentX;
        int w = s.contentW;

        // header: lifecycle state, then the last action message
        String state = run == null ? "IDLE" : run.state().name();
        String head = "COMPOSITION  " + state
                + (run != null ? "  " + run.compositionName + "  " + (run.durationMs() / 100) / 10.0 + "s" : "")
                + (run != null && run.failureKind() != CompositionRun.FailureKind.NONE
                ? "  [" + run.failureKind() + "] " + run.failureText() : "");
        if (run != null && run.state() == CompositionRun.State.PERFORMING) {
            g.fill(x - 2, s.contentY - 2, x + w, s.contentY + 10, 0x40E5534B);
            head = "ACTIVE  " + head + "   (Stop to cancel)";
        }
        g.drawText(tr, T2MenuLook.trim(s, head, w), x, s.contentY, resultColor(state), false);
        g.drawText(tr, T2MenuLook.trim(s, status, w), x, s.contentY + 11, T2MenuLook.C_MUTED, false);
        if (editorMissing) {
            g.drawText(tr, "No multi-line editor widget on this version: edit altoclef/compositions/" + currentName()
                    + ".java elsewhere, then Reload.", s.contentX + 134, s.contentY + 40, 0xFFFF9E64, false);
        }

        // panes
        int ph = paneBottom - paneTop;
        if (ph < 24) return;
        int leftW = (w - 6) * 2 / 5;
        int rightX = x + leftW + 6;
        int rightW = w - leftW - 6;
        pane(g, x, paneTop, leftW, ph);
        g.drawText(tr, "task tree", x + 4, paneTop + 2, T2MenuLook.C_ACCENT, false);
        int insH = Math.min(ph / 2, 8 * 10 + 14);
        pane(g, rightX, paneTop, rightW, insH);
        g.drawText(tr, "inspector", rightX + 4, paneTop + 2, T2MenuLook.C_ACCENT, false);
        int logY = paneTop + insH + 2;
        pane(g, rightX, logY, rightW, paneBottom - logY);
        g.drawText(tr, "log", rightX + 4, logY + 2, T2MenuLook.C_ACCENT, false);

        List<CompositionRun.Node> nodes = run == null ? List.of() : run.nodes();
        int rows = Math.max(1, (ph - 16) / 10);
        for (int i = 0; i < nodes.size() && i < rows; i++) {
            CompositionRun.Node n = nodes.get(i);
            boolean sel = nodeSel >= 0 ? nodeSel == i : i == nodes.size() - 1;
            String line = "  ".repeat(Math.min(n.depth, 6)) + n.name.trim();
            if (sel) g.fill(x + 2, paneTop + 13 + i * 10, x + leftW - 2, paneTop + 23 + i * 10, 0x22E8C96A);
            g.drawText(tr, T2MenuLook.trim(s, line, leftW - 8), x + 4, paneTop + 14 + i * 10,
                    resultColor(n.result.name()), false);
        }
        if (nodes.size() > rows) {
            g.drawText(tr, "+" + (nodes.size() - rows) + " more", x + leftW - 50, paneTop + 2, T2MenuLook.C_DIM, false);
        }

        // inspector
        if (!nodes.isEmpty()) {
            int idx = nodeSel >= 0 && nodeSel < nodes.size() ? nodeSel : nodes.size() - 1;
            CompositionRun.Node n = nodes.get(idx);
            boolean leafLive = run.state() == CompositionRun.State.PERFORMING && !n.ended && n.children.isEmpty();
            String movement = leafLive && mod != null ? svc.movementStatus(mod) : "-";
            List<String> lines = new ArrayList<>();
            lines.add(n.className.substring(n.className.lastIndexOf('.') + 1) + "  " + n.result
                    + (n.ended ? " (ended)" : " (live)"));
            lines.add("goal: " + n.name.trim());
            lines.add("duration " + n.durationMs(now) + "ms  ticks " + n.ticks);
            lines.add("parent: " + (n.parent == null ? "-" : n.parent.name.trim()));
            lines.add("children: " + n.children.size());
            lines.add("failure: " + (n.failure == null ? "-" : n.failure.toString()));
            lines.add("recovery: " + (n.recovery == null ? "-" : n.recovery.toString()));
            lines.add("movement: " + movement);
            for (int i = 0; i < lines.size() && 14 + i * 10 + 9 <= insH; i++) {
                g.drawText(tr, T2MenuLook.trim(s, lines.get(i), rightW - 10), rightX + 4, paneTop + 14 + i * 10,
                        i == 0 ? resultColor(n.result.name()) : T2MenuLook.C_TEXT, false);
            }
        }

        // log: compile diagnostics when relevant, else the run's events (tail)
        List<String> texts = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        CompositionCompiler.Result c = svc.lastCompile();
        if (c != null && !c.ok()) {
            texts.add("COMPILE FAILURE " + svc.lastCompiledName());
            cols.add(0xFFFF8A84);
            for (CompositionCompiler.Diagnostic d : c.diagnostics) {
                texts.add("  " + d);
                cols.add(0xFFFF8A84);
            }
            if (c.error != null) {
                texts.add("  " + c.error);
                cols.add(0xFFFF8A84);
            }
        }
        if (run != null && run.failureKind() != CompositionRun.FailureKind.COMPILE_FAILURE) {
            for (CompositionRun.Event e : run.events()) {
                texts.add(String.format("+%.1fs %s %s", (e.timeMs - run.startMs) / 1000.0, e.type, e.detail));
                cols.add(eventColor(e.type));
            }
            if (!run.stackTrace().isEmpty()) {
                for (String l : run.stackTrace().split("\\R")) {
                    texts.add(l.replace("\t", "  "));
                    cols.add(0xFFFF8A84);
                }
            }
        }
        int logRows = Math.max(0, (paneBottom - logY - 14) / 10);
        int from = Math.max(0, texts.size() - logRows);
        for (int i = from; i < texts.size(); i++) {
            g.drawText(tr, T2MenuLook.trim(s, texts.get(i), rightW - 10), rightX + 4, logY + 14 + (i - from) * 10,
                    cols.get(i), false);
        }
    }

    private static void pane(DrawContextWrapper g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, T2MenuLook.C_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xC0080A0F);
    }
}
