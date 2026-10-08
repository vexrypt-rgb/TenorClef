package adris.altoclef.swarm;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One swarm protocol message: {@code s1 <op> k=v k=v ...}. Plain text carried as the body of one sealed
 * Ostinato swarm message (see {@link SwarmLink}); values are percent-escaped so they never contain spaces or '='.
 * <ul>
 *   <li>agent to leader: reg, hb, accept, reject, prog, result, obs, bye</li>
 *   <li>leader to agent: offer, cancel</li>
 * </ul>
 */
public final class SwarmMessage {
    public static final String VERSION = "s1";

    public final String op;
    private final Map<String, String> fields = new LinkedHashMap<>();

    public SwarmMessage(String op) {
        this.op = op.toLowerCase(Locale.ROOT);
    }

    public static SwarmMessage of(String op, String... kv) {
        SwarmMessage m = new SwarmMessage(op);
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    public SwarmMessage put(String k, String v) {
        if (v != null) fields.put(k, v);
        return this;
    }

    public SwarmMessage put(String k, Number v) {
        return put(k, String.valueOf(v));
    }

    public String get(String k) { return fields.get(k); }

    public String get(String k, String def) {
        String v = fields.get(k);
        return v == null ? def : v;
    }

    public int getInt(String k, int def) {
        try {
            return Integer.parseInt(fields.get(k));
        } catch (Exception e) {
            return def;
        }
    }

    public double getDouble(String k, double def) {
        try {
            return Double.parseDouble(fields.get(k));
        } catch (Exception e) {
            return def;
        }
    }

    public Map<String, String> fields() { return fields; }

    public String encode() {
        StringBuilder b = new StringBuilder(VERSION).append(' ').append(op);
        for (Map.Entry<String, String> e : fields.entrySet()) {
            b.append(' ').append(e.getKey()).append('=').append(escape(e.getValue()));
        }
        return b.toString();
    }

    /** @return null if the line is not a v1 swarm message. */
    public static SwarmMessage decode(String line) {
        if (line == null) return null;
        String[] p = line.trim().split("\\s+");
        if (p.length < 2 || !VERSION.equals(p[0])) return null;
        SwarmMessage m = new SwarmMessage(p[1]);
        for (int i = 2; i < p.length; i++) {
            int eq = p[i].indexOf('=');
            if (eq <= 0) continue;
            m.fields.put(p[i].substring(0, eq), unescape(p[i].substring(eq + 1)));
        }
        return m;
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '%' || c == ' ' || c == '=' || c == '\n') b.append('%').append(String.format("%02X", (int) c));
            else b.append(c);
        }
        return b.toString();
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length() + 0) {
                try {
                    b.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException ignored) {
                    // not an escape; keep the literal '%'
                }
            }
            b.append(c);
        }
        return b.toString();
    }

    @Override
    public String toString() { return encode(); }
}
