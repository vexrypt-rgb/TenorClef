package adris.altoclef.swarm;

import baritone.api.BaritoneAPI;


/**
 * TenorClef's view of Ostinato's swarm link. Objective messages travel as one sealed, signed,
 * roster-authenticated Ostinato message type ({@link #TYPE}); there is no transport of our own.
 * The roster, keyring and rate limits are Ostinato's ({@code #swarm}, {@code swarmEnabled}).
 */
public interface SwarmLink {
    /** Ostinato swarm message type carrying one encoded {@link SwarmMessage}. */
    String TYPE = "TCS";

    /** Our name on the link, or null if the link is down. */
    String self();

    /** Lead of the first roster group we belong to, or null. */
    String leader();

    /** True if we lead a roster group. */
    boolean leads();

    /** Send to a roster member. @return false if there is no shared group or the link is down. */
    boolean send(String to, SwarmMessage m);

    /** Why the link is unavailable, or null when it is up. */
    String problem();

    /** Install the inbound handler once; called with (sender, group, body). */
    void onReceive(TriConsumer<String, String, String> handler);

    /** Lead of {@code group}, or null. */
    String leadOf(String group);

    interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    /** The link over the primary Ostinato instance (typed driver API on every line). */
    static SwarmLink create() {
        return new Typed();
    }

    /** Ostinato: the link over the typed {@code IBaritone.getSwarm()} API, no reflection. */
    final class Typed implements SwarmLink {
        private static baritone.api.swarm.ISwarm swarm() {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getSwarm();
        }

        @Override
        public String problem() {
            baritone.api.swarm.ISwarm s = swarm();
            if (s.isOnline()) return null;
            return "Ostinato swarm link is not running (" + s.state()
                    + "). Set swarmEnabled true, add a roster and keyring; see the Ostinato swarm guide.";
        }

        @Override
        public String self() {
            return swarm().self();
        }

        @Override
        public String leader() {
            for (baritone.api.swarm.ISwarmGroup g : swarm().myGroups()) {
                if (g.lead() != null) return g.lead();
            }
            return null;
        }

        @Override
        public boolean leads() {
            baritone.api.swarm.ISwarm s = swarm();
            String me = s.self();
            if (me == null) return false;
            for (baritone.api.swarm.ISwarmGroup g : s.myGroups()) {
                if (me.equals(g.lead())) return true;
            }
            return false;
        }

        @Override
        public String leadOf(String group) {
            baritone.api.swarm.ISwarmGroup g = swarm().group(group);
            return g == null ? null : g.lead();
        }

        @Override
        public boolean send(String to, SwarmMessage m) {
            baritone.api.swarm.ISwarm s = swarm();
            for (baritone.api.swarm.ISwarmGroup g : s.myGroups()) {
                if (!g.has(to)) continue;
                return s.send(g.id(), to, TYPE, m.encode(), baritone.api.swarm.SwarmPriority.HIGH);
            }
            return false;
        }

        @Override
        public void onReceive(TriConsumer<String, String, String> handler) {
            // Re-registering replaces the handler for TYPE, so this is safe to call every tick and
            // picks up a restarted link.
            swarm().registerHandler(TYPE, handler::accept);
        }
    }
}
