package adris.altoclef.tasksystem;

import java.lang.reflect.Method;

/** Optional version boundary: task policy only, never movement or physics. Client thread only. */
public final class GraceNoteTaskGate {
    public enum Decision { RUN, WAIT, REPLAN }

    private long pendingEpisode = -1;
    private String status = "Grace Note unavailable";
    private Class<?> baritoneClass;
    private Method getter;

    public String status() { return status; }

    public Decision poll(Object baritone) {
        try {
            if (baritone == null) return unavailable("No Baritone");
            if (baritoneClass != baritone.getClass()) {
                baritoneClass = baritone.getClass();
                getter = null;
                try {
                    getter = baritoneClass.getMethod("getGraceNote");
                } catch (NoSuchMethodException olderVersion) {
                    return unavailable("Grace Note unsupported by this Ostinato version");
                }
            }
            if (getter == null) return unavailable("Grace Note unsupported by this Ostinato version");
            Object service = getter.invoke(baritone);
            if (Boolean.TRUE.equals(call(service, "holdsControl"))) {
                if (pendingEpisode < 0) pendingEpisode = Long.MAX_VALUE;
                Object view = call(service, "status");
                pendingEpisode = number(view, "episode");
                status = "Grace Note holds movement (episode " + pendingEpisode + ")";
                return Decision.WAIT;
            }
            if (pendingEpisode < 0) return Decision.RUN;
            Object outcome = call(service, "lastOutcome");
            if (outcome == null || number(outcome, "episode") < pendingEpisode) {
                status = "Grace Note is verifying the outcome";
                return Decision.WAIT;
            }
            if (number(outcome, "episode") != pendingEpisode || number(outcome, "released") < 0) {
                status = "Grace Note release unverified; task remains paused";
                return Decision.WAIT;
            }
            status = "Grace Note " + call(outcome, "result") + "; re-evaluating task after verified release";
            pendingEpisode = -1;
            return Decision.REPLAN;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            status = "Grace Note read failed: " + failure.getClass().getSimpleName();
            return Decision.WAIT;
        }
    }

    private Decision unavailable(String why) {
        status = why;
        // Loss of an optional API is harmless before a takeover, never proof of its release.
        return pendingEpisode < 0 ? Decision.RUN : Decision.WAIT;
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static long number(Object target, String method) throws ReflectiveOperationException {
        return ((Number) call(target, method)).longValue();
    }
}
