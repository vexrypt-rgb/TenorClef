package adris.altoclef.swarm;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;

/**
 * TenorClef's view of Ostinato's encrypted swarm link.
 * <p>
 * Ostinato owns the link: transport, sigil crypto, roster, frames and region builds. TenorClef
 * only flips its {@code swarm*} settings and forwards {@code #swarm} subcommands through the
 * Baritone command manager, so no Ostinato swarm class is referenced directly and stock Baritone
 * jars (no swarm settings) just report {@link #available()} as false.
 */
public final class SwarmAdapter {

    private static final String ENABLED = "swarmEnabled";

    private SwarmAdapter() {}

    /** Whether the loaded Baritone jar has the swarm settings and command. */
    public static boolean available() {
        try {
            return settings().containsKey(ENABLED.toLowerCase(Locale.ROOT));
        } catch (Throwable t) {
            return false;
        }
    }

    public static String detail() {
        return available() ? "Ostinato swarm link present" : "no swarm support in the loaded Baritone jar (needs Ostinato)";
    }

    public static boolean enabled() {
        return Boolean.TRUE.equals(get(ENABLED));
    }

    public static void setEnabled(boolean on) {
        set(ENABLED, String.valueOf(on));
    }

    /** Current value of a swarm setting, or null if absent. */
    public static Object get(String name) {
        Object setting = setting(name);
        if (setting == null) return null;
        try {
            return valueField(setting).get(setting);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String getString(String name) {
        Object v = get(name);
        return v == null ? "" : v.toString();
    }

    /**
     * Set a swarm setting from text, coerced to the setting's own type.
     * @return null on success, else the reason it was refused
     */
    public static String set(String name, String text) {
        Object setting = setting(name);
        if (setting == null) return "no such swarm setting: " + name;
        try {
            Field f = valueField(setting);
            Object cur = f.get(setting);
            Object next;
            String t = text == null ? "" : text.trim();
            if (cur instanceof Boolean) {
                if (!t.equalsIgnoreCase("true") && !t.equalsIgnoreCase("false")) return name + " wants true or false";
                next = Boolean.parseBoolean(t);
            } else if (cur instanceof Integer) {
                next = Integer.parseInt(t);
            } else if (cur instanceof Long) {
                next = Long.parseLong(t);
            } else if (cur instanceof Double) {
                next = Double.parseDouble(t);
            } else if (cur instanceof String || cur == null) {
                next = text == null ? "" : text;
            } else {
                return name + " has an unsupported type " + cur.getClass().getSimpleName();
            }
            f.set(setting, next);
            return null;
        } catch (NumberFormatException e) {
            return name + " wants a number";
        } catch (Throwable t) {
            return "could not set " + name + ": " + t.getMessage();
        }
    }

    /**
     * Run an Ostinato {@code #swarm} subcommand (status, ping [group], reload, build ..., stop).
     * Output appears in the Baritone chat log.
     * @return false when there is no Baritone or the command was not recognized
     */
    public static boolean run(String args) {
        try {
            if (!available()) return false;
            IBaritone b = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (b == null) return false;
            return b.getCommandManager().execute("swarm " + args);
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object setting(String name) {
        try {
            return settings().get(name.toLowerCase(Locale.ROOT));
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> settings() throws Exception {
        Object s = BaritoneAPI.getSettings();
        return (Map<String, Object>) s.getClass().getField("byLowerName").get(s);
    }

    private static Field valueField(Object setting) throws NoSuchFieldException {
        return setting.getClass().getField("value");
    }
}
