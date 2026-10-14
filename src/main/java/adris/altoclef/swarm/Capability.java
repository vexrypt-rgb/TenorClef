package adris.altoclef.swarm;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** What an agent can be asked to do. Assignments name capabilities, never implementations. */
public enum Capability {
    CAN_MINE, CAN_BUILD, CAN_EXPLORE, CAN_CRAFT, CAN_TRANSPORT, CAN_SCOUT, CAN_FIGHT;

    public static String encode(Set<Capability> caps) {
        StringBuilder b = new StringBuilder();
        for (Capability c : caps) {
            if (b.length() > 0) b.append(',');
            b.append(c.name());
        }
        return b.toString();
    }

    public static Set<Capability> decode(String raw) {
        Set<Capability> out = EnumSet.noneOf(Capability.class);
        if (raw == null || raw.isBlank()) return out;
        for (String s : raw.split(",")) {
            try {
                out.add(valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // unknown capability from a newer peer: ignore it rather than reject the agent
            }
        }
        return out;
    }
}
