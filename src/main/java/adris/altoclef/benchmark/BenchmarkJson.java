package adris.altoclef.benchmark;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Hand-rolled JSON export for benchmark summaries (offline-testable; no Jackson).
 */
public final class BenchmarkJson {

    private BenchmarkJson() {}

    public static String toSummaryJson(BenchmarkHarness harness) {
        List<BenchmarkResult> results = harness.getResults();
        BenchmarkResult agg = harness.aggregateSummary("aggregate");
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"scenarioCount\":").append(results.size()).append(',');
        sb.append("\"successCount\":").append(harness.successCount()).append(',');
        sb.append("\"failureCount\":").append(harness.failureCount()).append(',');
        sb.append("\"totalDurationMs\":").append(harness.totalDurationMs()).append(',');
        sb.append("\"aggregate\":");
        writeResult(sb, agg);
        sb.append(',');
        sb.append("\"scenarios\":[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            writeResult(sb, results.get(i));
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    public static String toJson(BenchmarkResult result) {
        StringBuilder sb = new StringBuilder(128);
        writeResult(sb, result);
        return sb.toString();
    }

    public static Path writeSummary(Path path, BenchmarkHarness harness) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, toSummaryJson(harness), StandardCharsets.UTF_8);
        return path;
    }


    /** Single live-session JSON (post-phase-10). */
    public static String toLiveJson(LiveBenchmarkSession session) {
        BenchmarkResult r = session.toResult();
        StringBuilder sb = new StringBuilder(192);
        sb.append('{');
        sb.append("\"type\":\"live\",");
        sb.append("\"running\":").append(session.isRunning()).append(',');
        sb.append("\"peakThreat\":").append(quote(session.getPeakThreat().name())).append(',');
        sb.append("\"threatPauses\":").append(session.getThreatPauses()).append(',');
        sb.append("\"threatFails\":").append(session.getThreatFails()).append(',');
        sb.append("\"result\":");
        writeResult(sb, r);
        sb.append('}');
        return sb.toString();
    }

    public static Path writeLive(Path path, LiveBenchmarkSession session) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, toLiveJson(session), StandardCharsets.UTF_8);
        return path;
    }

    public static Path writeResult(Path path, BenchmarkResult result) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, toJson(result), StandardCharsets.UTF_8);
        return path;
    }

    private static void writeResult(StringBuilder sb, BenchmarkResult r) {
        sb.append('{');
        sb.append("\"name\":").append(quote(r.getName())).append(',');
        sb.append("\"success\":").append(r.isSuccess()).append(',');
        sb.append("\"durationMs\":").append(r.getDurationMs());
        if (r.getDeaths() != null) {
            sb.append(",\"deaths\":").append(r.getDeaths());
        }
        if (r.getReplans() != null) {
            sb.append(",\"replans\":").append(r.getReplans());
        }
        if (r.getPathFails() != null) {
            sb.append(",\"pathFails\":").append(r.getPathFails());
        }
        if (r.getNotes() != null) {
            sb.append(",\"notes\":").append(quote(r.getNotes()));
        }
        sb.append(",\"counters\":");
        writeCounters(sb, r.getCounters());
        sb.append('}');
    }

    private static void writeCounters(StringBuilder sb, BenchmarkCounters c) {
        sb.append('{');
        sb.append("\"deaths\":").append(c.getDeaths()).append(',');
        sb.append("\"replans\":").append(c.getReplans()).append(',');
        sb.append("\"pathFails\":").append(c.getPathFails()).append(',');
        sb.append("\"threatHigh\":").append(c.getThreatHigh()).append(',');
        sb.append("\"threatCritical\":").append(c.getThreatCritical()).append(',');
        sb.append("\"threatPauses\":").append(c.getThreatPauses()).append(',');
        sb.append("\"threatFails\":").append(c.getThreatFails()).append(',');
        sb.append("\"threatPeak\":").append(quote(c.getThreatPeak())).append(',');
        sb.append("\"taskResults\":");
        writeEnumIntMap(sb, c.getTaskResults());
        sb.append(',');
        sb.append("\"recoveryActions\":");
        writeEnumIntMap(sb, c.getRecoveryActions());
        sb.append(',');
        sb.append("\"failureReasons\":");
        writeEnumIntMap(sb, c.getFailureReasons());
        sb.append('}');
    }

    private static <E extends Enum<E>> void writeEnumIntMap(StringBuilder sb, Map<E, Integer> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<E, Integer> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(e.getKey().name())).append(':').append(e.getValue());
        }
        sb.append('}');
    }

    private static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder q = new StringBuilder(s.length() + 2);
        q.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"', '\\' -> q.append('\\').append(c);
                case '\n' -> q.append("\\n");
                case '\r' -> q.append("\\r");
                case '\t' -> q.append("\\t");
                default -> {
                    if (c < 0x20) {
                        q.append(String.format("\\u%04x", (int) c));
                    } else {
                        q.append(c);
                    }
                }
            }
        }
        q.append('"');
        return q.toString();
    }
}
