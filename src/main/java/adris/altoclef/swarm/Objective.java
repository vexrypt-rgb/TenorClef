package adris.altoclef.swarm;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What an assignment is trying to achieve, and how the swarm checks it from evidence.
 * Only ACQUIRE exists: it is the one objective TenorClef can already execute and verify end to end
 * (AcquireItemGoal on the agent). New kinds belong here with their own verify().
 */
public final class Objective {
    public final String item;
    public final int count;

    private Objective(String item, int count) {
        this.item = item;
        this.count = count;
    }

    public static Objective acquire(String item, int count) {
        if (item == null || item.isBlank()) throw new IllegalArgumentException("item required");
        return new Objective(item.trim().toLowerCase(Locale.ROOT), Math.max(1, count));
    }

    public String describe() { return "acquire " + item + " x" + count; }

    /** Capabilities usually needed for this objective; the submitter may override them on the Assignment. */
    public Set<Capability> defaultCapabilities() {
        Set<Capability> caps = EnumSet.noneOf(Capability.class);
        if (item.endsWith("_log") || item.endsWith("_ore") || item.contains("cobblestone") || item.equals("obsidian")
                || item.equals("coal") || item.equals("diamond")) {
            caps.add(Capability.CAN_MINE);
        } else if (item.equals("blaze_rod") || item.equals("ender_pearl") || item.equals("leather")
                || item.startsWith("cooked_") || item.equals("bone") || item.equals("string")) {
            caps.add(Capability.CAN_FIGHT);
        } else {
            caps.add(Capability.CAN_CRAFT);
        }
        return caps;
    }

    /** The swarm's own check of a SUCCEEDED report: the agent must have observed the full count. */
    public boolean verify(Map<String, String> evidence) {
        if (evidence == null) return false;
        String have = evidence.get("have");
        if (have == null) return false;
        try {
            return Integer.parseInt(have) >= count;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
