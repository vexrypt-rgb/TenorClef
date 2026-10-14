package adris.altoclef.swarm;

import baritone.api.BaritoneAPI;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

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

    /**
     * The real link, backed by the primary Ostinato instance. It talks to Ostinato by reflection so every
     * Minecraft-version module still compiles against older Ostinato jars; those report a clear
     * {@link #problem()} instead of failing to build.
     */
    final class Ostinato implements SwarmLink {
        private Object installedOn;

        private static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
            Method m = o.getClass().getMethod(name, types);
            m.setAccessible(true);
            return m.invoke(o, args);
        }

        private static Object call(Object o, String name) throws Exception {
            return call(o, name, new Class<?>[0]);
        }

        private static Object behavior() throws Exception {
            return call(BaritoneAPI.getProvider().getPrimaryBaritone(), "getSwarmBehavior");
        }

        /** The SwarmControl, or null when the link is not running. */
        private static Object control() {
            try {
                return call(behavior(), "control");
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public String problem() {
            try {
                Object b = behavior();
                if (call(b, "control") != null) {
                    call(control(), "roster");
                    Class.forName("baritone.swarm.SwarmControl").getMethod("registerHandler", String.class, Consumer.class);
                    return null;
                }
                return "Ostinato swarm link is not running (" + call(b, "state")
                        + "). Set swarmEnabled true, add a roster and keyring; see the Ostinato swarm guide.";
            } catch (NoSuchMethodException e) {
                return "this Ostinato build is too old for TenorClef swarms (needs registerHandler); update Ostinato";
            } catch (Throwable t) {
                return "this Ostinato build has no swarm link";
            }
        }

        private static String selfOf(Object c) throws Exception {
            return (String) call(call(c, "endpoint"), "selfId");
        }

        @SuppressWarnings("unchecked")
        private static List<Object> myGroups(Object c) throws Exception {
            Object roster = call(c, "roster");
            return (List<Object>) call(roster, "groupsOf", new Class<?>[] {String.class}, selfOf(c));
        }

        @Override
        public String self() {
            try {
                Object c = control();
                return c == null ? null : selfOf(c);
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public String leader() {
            try {
                Object c = control();
                if (c == null) return null;
                for (Object g : myGroups(c)) {
                    String lead = (String) call(g, "lead");
                    if (lead != null) return lead;
                }
            } catch (Throwable t) {
                // fall through
            }
            return null;
        }

        @Override
        public boolean leads() {
            try {
                Object c = control();
                if (c == null) return false;
                String me = selfOf(c);
                for (Object g : myGroups(c)) {
                    if (me.equals(call(g, "lead"))) return true;
                }
            } catch (Throwable t) {
                // fall through
            }
            return false;
        }

        @Override
        public String leadOf(String group) {
            try {
                Object c = control();
                if (c == null) return null;
                Object g = call(call(c, "roster"), "group", new Class<?>[] {String.class}, group);
                return g == null ? null : (String) call(g, "lead");
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public boolean send(String to, SwarmMessage m) {
            try {
                Object c = control();
                if (c == null) return false;
                for (Object g : myGroups(c)) {
                    if (!(Boolean) call(g, "has", new Class<?>[] {String.class}, to)) continue;
                    Class<?> prio = Class.forName("baritone.swarm.transport.SwarmPriority");
                    Object high = prio.getField("HIGH").get(null);
                    call(call(c, "endpoint"), "send",
                            new Class<?>[] {String.class, String.class, String.class, String.class, prio},
                            call(g, "id"), to, TYPE, m.encode(), high);
                    return true;
                }
            } catch (Throwable t) {
                // fall through
            }
            return false;
        }

        @Override
        public void onReceive(TriConsumer<String, String, String> handler) {
            try {
                Object c = control();
                if (c == null || c == installedOn) return;
                Consumer<Object> h = msg -> {
                    try {
                        handler.accept((String) call(msg, "from"), (String) call(msg, "group"), (String) call(msg, "body"));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                };
                call(c, "registerHandler", new Class<?>[] {String.class, Consumer.class}, TYPE, h);
                installedOn = c;
            } catch (Throwable t) {
                // problem() explains it
            }
        }
    }
}
