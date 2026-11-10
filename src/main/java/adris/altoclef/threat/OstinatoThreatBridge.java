package adris.altoclef.threat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Optional read-only boundary: older pinned Ostinato jars do not have the 26.3 threat service.
 * No physics, classification, goals or fight/flight policy belongs in this adapter. */
public final class OstinatoThreatBridge {
    private OstinatoThreatBridge() {}

    public static final class View {
        public final boolean available;
        public final String status;
        public final long tick;
        public final int highestLevel;
        public final List<String> sources;
        private View(boolean available, String status, long tick, int highestLevel, List<String> sources) {
            this.available = available; this.status = status; this.tick = tick; this.highestLevel = highestLevel;
            this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        }
    }

    /** Reflection is confined to the optional version boundary; callers get an immutable value view. */
    public static View read(Object baritone) {
        if (baritone == null) return unavailable("NO_BARITONE");
        try {
            Object service = call(baritone, "getThreatAssessment");
            Object snapshot = call(service, "latest");
            return fromSnapshot(snapshot);
        } catch (NoSuchMethodException e) {
            return unavailable("UNSUPPORTED_OSTINATO_VERSION");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return unavailable("THREAT_READ_FAILED_" + e.getClass().getSimpleName());
        }
    }

    public static View fromSnapshot(Object snapshot) throws ReflectiveOperationException {
        String status = String.valueOf(call(snapshot, "status"));
        List<String> lines = new ArrayList<>();
        for (Object source : (List<?>)call(snapshot, "mostImportant")) {
            lines.add(call(source, "entityType") + " " + call(source, "source") + " level=" + call(source, "level")
                    + " exposure=" + call(source, "exposure") + " confidence=" + call(source, "confidence")
                    + " reasons=" + call(source, "reasons") + " uncertainty=" + call(source, "uncertainties"));
        }
        lines.add("environment=" + call(snapshot, "environment") + " truncated=" + call(snapshot, "truncated")
                + " simultaneousImmediate=" + call(snapshot, "immediateSources"));
        return new View("OBSERVED".equals(status), status, ((Number)call(snapshot, "tick")).longValue(),
                ((Number)call(snapshot, "highestLevel")).intValue(), lines);
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }
    private static View unavailable(String why) { return new View(false, why, -1, 0, Collections.emptyList()); }
}
