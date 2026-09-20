package adris.altoclef.agent;

/**
 * Facade for Phase 9 structured agent JSON protocol.
 * Parse → dispatch → serialize. No new networking — reuses AgentFiles / @agent.
 */
public class AgentProtocol {

    private final AgentRequestHandler handler;

    public AgentProtocol(AgentRequestHandler handler) {
        this.handler = handler;
    }

    public AgentProtocol(AgentRuntime runtime) {
        this(new AgentRequestHandler(runtime));
    }

    public AgentRequestHandler getHandler() {
        return handler;
    }

    public AgentResponse handle(AgentRequest request) {
        return handler.handle(request);
    }

    public AgentResponse handleJson(String json) {
        try {
            AgentRequest req = AgentJson.parseRequest(json);
            return handler.handle(req);
        } catch (IllegalArgumentException e) {
            return AgentResponse.failure("", "parse error: " + e.getMessage());
        }
    }

    public String handleJsonToJson(String json) {
        return AgentJson.toJson(handleJson(json));
    }

    /**
     * Process one inbox / chat line. Returns a response if the line was JSON
     * (or {@code @agent json ...}); otherwise null so legacy verbs still apply.
     */
    public AgentResponse tryHandleLine(String line) {
        if (!AgentJson.looksLikeJsonRequest(line)) {
            return null;
        }
        String payload = line.trim();
        if (payload.regionMatches(true, 0, "@agent json", 0, 11)) {
            payload = payload.substring(11).trim();
        }
        return handleJson(payload);
    }

    public static boolean isJsonLine(String line) {
        return AgentJson.looksLikeJsonRequest(line);
    }
}
