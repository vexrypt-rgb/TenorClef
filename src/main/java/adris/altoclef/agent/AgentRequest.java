package adris.altoclef.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured agent request: {@code { id, action, parameters }}.
 */
public final class AgentRequest {

    private final String id;
    private final String action;
    private final Map<String, String> parameters;

    public AgentRequest(String id, String action, Map<String, String> parameters) {
        this.id = id != null ? id : "";
        this.action = action != null ? action.trim().toLowerCase() : "";
        if (parameters == null || parameters.isEmpty()) {
            this.parameters = Map.of();
        } else {
            this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        }
    }

    public static AgentRequest of(String id, String action) {
        return new AgentRequest(id, action, Map.of());
    }

    public static AgentRequest of(String id, String action, Map<String, String> parameters) {
        return new AgentRequest(id, action, parameters);
    }

    public String getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String param(String key) {
        return parameters.get(key);
    }

    public String param(String key, String defaultValue) {
        String v = parameters.get(key);
        return v != null ? v : defaultValue;
    }

    /** First non-blank among keys (e.g. item / resource / name). */
    public String paramFirst(String... keys) {
        if (keys == null) {
            return null;
        }
        for (String k : keys) {
            String v = parameters.get(k);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    public int paramInt(String key, int defaultValue) {
        String v = parameters.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentRequest that)) return false;
        return Objects.equals(id, that.id)
                && Objects.equals(action, that.action)
                && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, action, parameters);
    }

    @Override
    public String toString() {
        return "AgentRequest{id=" + id + ", action=" + action + ", parameters=" + parameters + "}";
    }
}
