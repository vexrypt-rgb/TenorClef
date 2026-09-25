package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.speedrun.testrun2.util.GameFiles;
import adris.altoclef.tasksystem.Task;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * High-frequency movement / decision / inventory trace for post-mortem analysis.
 *
 * <p>One {@code MOVE} line per tick (decision + position + velocity-ish state) plus an
 * {@code INV} snapshot of the whole inventory with slot indices every second and whenever
 * the inventory signature changes. Output goes to {@code <runDir>/altoclef/trace.log} so
 * it does not flood the chat/console; grep/tail it after a stall.
 *
 * <p>Why this exists: a 20-minute stall looks the same whether the child can never finish
 * (S156) or two children keep replacing each other (S157). The existing {@code T2 [NOW]}
 * heartbeat only fires once a second and omits inventory slots, so "the bot kept pillaring
 * up then digging down while collecting iron" was invisible. This trace records every tick.
 *
 * <p>Hooked from {@link ModernSpeedrunTask#onTick()} and reset from {@code onStart()}.
 */
public final class T2Trace {

    private static final String FILE = "trace.log";
    private static final int INV_EVERY_TICKS = 20;   // 1 heartbeat/s
    private static final int FLUSH_EVERY_TICKS = 100; // flush ~5s so a kill loses <=5s

    private static final String[] ARMOR = {"BOOT", "LEGS", "CHST", "HELM"}; // index 0..3

    private static BufferedWriter out;
    private static int tick;
    private static int sinceFlush;
    private static String lastInvSig = "";
    private static int lastInvTick = -INV_EVERY_TICKS;

    private T2Trace() {}

    /** Truncate the log for a fresh session. Call from onStart(). */
    public static void reset() {
        try {
            if (out != null) { out.close(); }
        } catch (Throwable ignored) {}
        out = null;
        tick = 0;
        sinceFlush = 0;
        lastInvSig = "";
        lastInvTick = -INV_EVERY_TICKS;
        // Truncate now so a half-written previous session does not leave a stale head.
        // Each S159 reroll calls this again, so the file only ever holds the CURRENT
        // attempt — stamp it with the reroll number so it is identifiable after the run.
        // The rejected attempts are summarised by the S159 lines in the fault log.
        try {
            Path dir = GameFiles.dir();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(FILE),
                    "# T2Trace reset — session start, spawn reroll #" + SpawnScout.rerolls() + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Throwable ignored) {}
    }

    /**
     * Emit one MOVE line for this tick plus (once per second / on change) an INV line.
     *
     * @param live   the child the chain is currently running (its {@code active} field)
     * @param wanted the task the driver returned this tick (what it decided to do)
     */
    public static void tick(AltoClef mod, String phase, Task live, Task wanted) {
        if (mod == null || mod.getPlayer() == null) return;
        tick++;
        int t = tick;
        double x, y, z;
        float yaw = 0f, pitch = 0f;
        boolean onGround = false, wet = false;
        int hp = -1, hunger = -1, sel = -1;
        try {
            var p = mod.getPlayer();
            x = p.getX(); y = p.getY(); z = p.getZ();
            try { yaw = p.getYaw(); pitch = p.getPitch(); } catch (Throwable ignored) {}
            try { onGround = p.isOnGround(); } catch (Throwable ignored) {}
            try { wet = p.isSubmergedInWater(); } catch (Throwable ignored) {}
            try { hp = (int) p.getHealth(); } catch (Throwable ignored) {}
            try { hunger = p.getHungerManager().getFoodLevel(); } catch (Throwable ignored) {}
            try { sel = p.getInventory().selectedSlot; } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            // player vanished mid-tick; record a minimal line so the gap is visible
            x = Double.NaN; y = Double.NaN; z = Double.NaN;
        }
        String liveName = live == null ? "-" : live.getClass().getSimpleName();
        String wantName = wanted == null ? "-" : wanted.getClass().getSimpleName();
        String swapTag = "";
        if (live != null && wanted != null && live.getClass() != wanted.getClass()) {
            swapTag = " SWAP->" + wantName;
        } else if (live == null && wanted != null) {
            swapTag = " SWAP->" + wantName;
        }
        write(String.format(
                "MOVE t=%d clk=%s ph=%s @%.1f,%.1f,%.1f yaw=%.0f,%.0f g=%b wet=%b hp=%d hun=%d sel=%d child=%s want=%s%s",
                t, SpeedrunClock.now(), phase, x, y, z, yaw, pitch, onGround, wet, hp, hunger, sel,
                liveName, wantName, swapTag));

        if (t - lastInvTick >= INV_EVERY_TICKS) {
            String inv = inventorySnapshot(mod);
            // Always stamp the tick so an unchanged 1s heartbeat still lets you correlate
            // a MOVE line to the nearest INV. Suppress only identical consecutive bodies.
            if (!inv.equals(lastInvSig)) {
                write("INV  t=" + t + " clk=" + SpeedrunClock.now() + " ph=" + phase + " " + inv);
                lastInvSig = inv;
            }
            lastInvTick = t;
        }
    }

    /** Full inventory with slot indices: H0..H8 hotbar, M9..M35 main, ARMOR, OFF. */
    private static String inventorySnapshot(AltoClef mod) {
        StringBuilder sb = new StringBuilder(200);
        int total = 0;
        try {
            var inv = mod.getPlayer().getInventory();
            for (int i = 0; i < 36; i++) {
                ItemStack st;
                try { st = inv.getStack(i); } catch (Throwable e) { continue; }
                if (st == null || st.isEmpty()) continue;
                sb.append(' ').append(i < 9 ? "H" + i : "M" + i).append('=')
                  .append(name(st.getItem())).append('x').append(st.getCount());
                total++;
            }
            // armor: getArmorStack(0)=boots .. 3=helmet (mirrors StorageHelper)
            for (int k = 0; k < 4; k++) {
                ItemStack st;
                try { st = inv.getArmorStack(k); } catch (Throwable e) { st = ItemStack.EMPTY; }
                if (st == null || st.isEmpty()) continue;
                sb.append(' ').append(ARMOR[k]).append('=').append(name(st.getItem()));
                total++;
            }
            // offhand
            ItemStack off = ItemStack.EMPTY;
            try { off = inv.offHand.stream().findFirst().orElse(ItemStack.EMPTY); }
            catch (Throwable ignored) {}
            if (off != null && !off.isEmpty()) {
                sb.append(" OFF=").append(name(off.getItem())).append('x').append(off.getCount());
                total++;
            }
        } catch (Throwable ignored) {}
        return "n=" + total + sb;
    }

    /** Compact item id: "item.minecraft.stone_pickaxe" -> "stone_pickaxe". */
    private static String name(Item item) {
        if (item == null) return "?";
        try {
            String k = item.getTranslationKey();
            if (k != null) {
                int first = k.indexOf('.');
                int second = k.indexOf('.', first + 1);
                if (second > 0) return k.substring(second + 1);
                if (first >= 0) return k.substring(first + 1);
                return k;
            }
        } catch (Throwable ignored) {}
        return String.valueOf(item);
    }

    private static void write(String line) {
        BufferedWriter bw = ensure();
        if (bw != null) {
            try {
                bw.write(line);
                bw.newLine();
            } catch (Throwable ignored) {}
            if (++sinceFlush >= FLUSH_EVERY_TICKS) {
                sinceFlush = 0;
                try { bw.flush(); } catch (Throwable ignored) {}
            }
        } else {
            // last-resort: GameFiles opens/closes per line, but it works
            GameFiles.append(FILE, line);
        }
    }

    private static BufferedWriter ensure() {
        if (out != null) return out;
        try {
            Path dir = GameFiles.dir();
            Files.createDirectories(dir);
            out = Files.newBufferedWriter(dir.resolve(FILE), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (Throwable ignored) {
            out = null;
        }
        return out;
    }
}
