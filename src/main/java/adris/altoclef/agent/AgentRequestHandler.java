package adris.altoclef.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dispatches structured {@link AgentRequest}s into {@link AgentRuntime}
 * (GoalManager / TaskCatalogue / snapshot / cancel).
 */
public class AgentRequestHandler {

    public static final Set<String> SUPPORTED_ACTIONS = Set.of(
            "get", "acquire", "goal", "status", "snap", "snapshot", "cancel"
    );

    private final AgentRuntime runtime;

    public AgentRequestHandler(AgentRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public AgentRuntime getRuntime() {
        return runtime;
    }

    public AgentResponse handle(AgentRequest request) {
        if (request == null) {
            return AgentResponse.failure("", "null request");
        }
        String id = request.getId();
        String action = request.getAction();
        if (action == null || action.isBlank()) {
            return AgentResponse.failure(id, "missing action");
        }
        if (!SUPPORTED_ACTIONS.contains(action)) {
            return AgentResponse.failure(id, "unsupported action: " + action
                    + " (supported: " + String.join(", ", SUPPORTED_ACTIONS) + ")");
        }

        return switch (action) {
            case "get", "acquire" -> handleAcquire(id, request);
            case "goal" -> handleGoal(id, request);
            case "status", "snap", "snapshot" -> handleStatus(id);
            case "cancel" -> runtime.cancel(id);
            default -> AgentResponse.failure(id, "unsupported action: " + action);
        };
    }

    private AgentResponse handleAcquire(String id, AgentRequest request) {
        String item = request.paramFirst("item", "resource", "name");
        if (item == null || item.isBlank()) {
            return AgentResponse.failure(id, "parameters.item (or resource/name) required");
        }
        int count = request.paramInt("count", 1);
        if (count < 1) count = 1;
        return runtime.acquire(id, item, count);
    }

    private AgentResponse handleGoal(String id, AgentRequest request) {
        // Optional nested verb: parameters may omit item when action is goal
        String item = request.paramFirst("item", "resource", "name", "target");
        if (item == null || item.isBlank()) {
            // status-style goal query
            if ("status".equalsIgnoreCase(request.param("verb", ""))) {
                return handleStatus(id);
            }
            return AgentResponse.failure(id, "parameters.item required for goal");
        }
        int count = request.paramInt("count", 1);
        if (count < 1) count = 1;
        return runtime.goal(id, item, count);
    }

    private AgentResponse handleStatus(String id) {
        Map<String, String> snap = runtime.snapshot();
        if (snap == null) {
            snap = Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>(snap);
        String busy = runtime.isBusy() ? "true" : "false";
        result.putIfAbsent("busy", busy);
        AgentStatus status = runtime.isBusy() ? AgentStatus.RUNNING : AgentStatus.SUCCESS;
        return new AgentResponse(id, status, result, null);
    }
}
