package adris.altoclef.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON codec for {@link AgentRequest} / {@link AgentResponse}.
 * Hand-rolled so offline unit tests need no Jackson on the classpath.
 * Supports flat string/number/boolean parameter values only.
 */
public final class AgentJson {

    private AgentJson() {}

    public static String toJson(AgentRequest req) {
        if (req == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        sb.append("\"id\":").append(quote(req.getId())).append(',');
        sb.append("\"action\":").append(quote(req.getAction())).append(',');
        sb.append("\"parameters\":");
        writeStringMap(sb, req.getParameters());
        sb.append('}');
        return sb.toString();
    }

    public static String toJson(AgentResponse resp) {
        if (resp == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        sb.append("\"id\":").append(quote(resp.getId())).append(',');
        sb.append("\"status\":").append(quote(resp.getStatus().wire()));
        if (resp.getResult() != null && !resp.getResult().isEmpty()) {
            sb.append(",\"result\":");
            writeStringMap(sb, resp.getResult());
        }
        if (resp.hasError()) {
            sb.append(",\"error\":").append(quote(resp.getError()));
        }
        sb.append('}');
        return sb.toString();
    }

    public static AgentRequest parseRequest(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty json");
        }
        String s = json.trim();
        if (s.startsWith("@agent")) {
            // tolerate "@agent json {...}" wrappers
            int brace = s.indexOf('{');
            if (brace < 0) {
                throw new IllegalArgumentException("no JSON object in: " + s);
            }
            s = s.substring(brace);
        }
        Map<String, Object> root = parseObject(s);
        String id = asString(root.get("id"));
        String action = asString(root.get("action"));
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action required");
        }
        Map<String, String> params = new LinkedHashMap<>();
        Object rawParams = root.get("parameters");
        if (rawParams instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null) continue;
                params.put(String.valueOf(e.getKey()), valueToString(e.getValue()));
            }
        }
        // Also allow top-level convenience keys as parameters
        for (String k : new String[]{"item", "resource", "name", "count", "mode"}) {
            if (!params.containsKey(k) && root.containsKey(k)) {
                params.put(k, valueToString(root.get(k)));
            }
        }
        return new AgentRequest(id != null ? id : "", action, params);
    }

    public static AgentResponse parseResponse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty json");
        }
        Map<String, Object> root = parseObject(json.trim());
        String id = asString(root.get("id"));
        AgentStatus status = AgentStatus.fromWire(asString(root.get("status")));
        if (status == null) {
            status = AgentStatus.FAILURE;
        }
        Map<String, String> result = new LinkedHashMap<>();
        Object rawResult = root.get("result");
        if (rawResult instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null) continue;
                result.put(String.valueOf(e.getKey()), valueToString(e.getValue()));
            }
        }
        String error = asString(root.get("error"));
        return new AgentResponse(id != null ? id : "", status, result, error);
    }

    /** True if the line looks like a JSON object request (not a legacy chat verb). */
    public static boolean looksLikeJsonRequest(String line) {
        if (line == null) return false;
        String t = line.trim();
        if (t.startsWith("{") && t.contains("\"action\"")) return true;
        if (t.regionMatches(true, 0, "@agent json", 0, 11)) return true;
        return t.startsWith("{") && t.contains("action");
    }

    // ---- tiny parser ----

    private static Map<String, Object> parseObject(String json) {
        Parser p = new Parser(json);
        Object v = p.parseValue();
        p.skipWs();
        if (!(v instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("expected JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) v;
        return map;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
            this.i = 0;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            char c = s.charAt(i);
            if (c == '{') return parseObj();
            if (c == '[') return parseArr();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBool();
            if (c == 'n') return parseNull();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            throw new IllegalArgumentException("unexpected char at " + i + ": " + c);
        }

        Map<String, Object> parseObj() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (peek('}')) {
                i++;
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                if (peek('}')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return map;
        }

        Object parseArr() {
            expect('[');
            skipWs();
            // Arrays are accepted but flattened as comma-joined strings if used as params
            StringBuilder joined = new StringBuilder();
            if (peek(']')) {
                i++;
                return joined.toString();
            }
            while (true) {
                Object v = parseValue();
                if (joined.length() > 0) joined.append(',');
                joined.append(valueToString(v));
                skipWs();
                if (peek(']')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return joined.toString();
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw new IllegalArgumentException("bad escape");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw new IllegalArgumentException("bad unicode");
                            int code = Integer.parseInt(s.substring(i, i + 4), 16);
                            sb.append((char) code);
                            i += 4;
                        }
                        default -> throw new IllegalArgumentException("bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("unterminated string");
        }

        Object parseNumber() {
            int start = i;
            if (peek('-')) i++;
            while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
            if (peek('.')) {
                i++;
                while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
            }
            String num = s.substring(start, i);
            if (num.contains(".")) {
                return Double.parseDouble(num);
            }
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                return Double.parseDouble(num);
            }
        }

        Boolean parseBool() {
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("expected boolean at " + i);
        }

        Object parseNull() {
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            throw new IllegalArgumentException("expected null at " + i);
        }

        void expect(char c) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != c) {
                throw new IllegalArgumentException("expected '" + c + "' at " + i);
            }
            i++;
        }

        boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
                else break;
            }
        }
    }

    private static void writeStringMap(StringBuilder sb, Map<String, String> map) {
        sb.append('{');
        boolean first = true;
        if (map != null) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(quote(e.getKey())).append(':').append(quote(e.getValue()));
            }
        }
        sb.append('}');
    }

    private static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String asString(Object o) {
        if (o == null) return null;
        return String.valueOf(o);
    }

    private static String valueToString(Object o) {
        if (o == null) return "";
        if (o instanceof Double d) {
            if (d == Math.rint(d) && !d.isInfinite() && !d.isNaN()) {
                return Long.toString(d.longValue());
            }
            return d.toString();
        }
        if (o instanceof Float f) {
            if (f == Math.rint(f) && !f.isInfinite() && !f.isNaN()) {
                return Long.toString(f.longValue());
            }
            return f.toString();
        }
        return String.valueOf(o);
    }
}
