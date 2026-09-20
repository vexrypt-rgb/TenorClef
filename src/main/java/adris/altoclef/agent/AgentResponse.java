package adris.altoclef.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured agent response: {@code { id, status, result?, error? }}.
 */
public final class AgentResponse {

    private final String id;
    private final AgentStatus status;
    private final Map<String, String> result;
    private final String error;

    public AgentResponse(String id, AgentStatus status, Map<String, String> result, String error) {
        this.id = id != null ? id : "";
        this.status = status != null ? status : AgentStatus.FAILURE;
        if (result == null || result.isEmpty()) {
            this.result = Map.of();
        } else {
            this.result = Collections.unmodifiableMap(new LinkedHashMap<>(result));
        }
        this.error = error;
    }

    public static AgentResponse of(String id, AgentStatus status) {
        return new AgentResponse(id, status, null, null);
    }

    public static AgentResponse accepted(String id, Map<String, String> result) {
        return new AgentResponse(id, AgentStatus.ACCEPTED, result, null);
    }

    public static AgentResponse running(String id, Map<String, String> result) {
        return new AgentResponse(id, AgentStatus.RUNNING, result, null);
    }

    public static AgentResponse success(String id, Map<String, String> result) {
        return new AgentResponse(id, AgentStatus.SUCCESS, result, null);
    }

    public static AgentResponse failure(String id, String error) {
        return new AgentResponse(id, AgentStatus.FAILURE, null, error);
    }

    public static AgentResponse blocked(String id, String error) {
        return new AgentResponse(id, AgentStatus.BLOCKED, null, error);
    }

    public static AgentResponse cancelled(String id, Map<String, String> result) {
        return new AgentResponse(id, AgentStatus.CANCELLED, result, null);
    }

    public String getId() {
        return id;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public Map<String, String> getResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    public boolean hasError() {
        return error != null && !error.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentResponse that)) return false;
        return Objects.equals(id, that.id)
                && status == that.status
                && Objects.equals(result, that.result)
                && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, status, result, error);
    }

    @Override
    public String toString() {
        return "AgentResponse{id=" + id + ", status=" + status
                + ", result=" + result + ", error=" + error + "}";
    }
}
