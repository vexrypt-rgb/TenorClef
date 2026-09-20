package adris.altoclef.agent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Phase 9: parse / serialize AgentRequest and AgentResponse.
 */
public class AgentJsonTest {

    @Test
    void roundTripRequest() {
        AgentRequest req = AgentRequest.of("r1", "get", Map.of(
                "item", "cobblestone",
                "count", "64"
        ));
        String json = AgentJson.toJson(req);
        AgentRequest back = AgentJson.parseRequest(json);
        Assertions.assertEquals("r1", back.getId());
        Assertions.assertEquals("get", back.getAction());
        Assertions.assertEquals("cobblestone", back.param("item"));
        Assertions.assertEquals(64, back.paramInt("count", 1));
    }

    @Test
    void parseNumericCount() {
        AgentRequest req = AgentJson.parseRequest(
                "{\"id\":\"2\",\"action\":\"acquire\",\"parameters\":{\"item\":\"dirt\",\"count\":8}}");
        Assertions.assertEquals(8, req.paramInt("count", 1));
        Assertions.assertEquals("dirt", req.param("item"));
    }

    @Test
    void topLevelConvenienceKeys() {
        AgentRequest req = AgentJson.parseRequest(
                "{\"id\":\"3\",\"action\":\"goal\",\"item\":\"oak_log\",\"count\":16}");
        Assertions.assertEquals("oak_log", req.param("item"));
        Assertions.assertEquals(16, req.paramInt("count", 1));
    }

    @Test
    void roundTripResponse() {
        AgentResponse resp = AgentResponse.accepted("r1", Map.of("item", "bread", "count", "1"));
        String json = AgentJson.toJson(resp);
        AgentResponse back = AgentJson.parseResponse(json);
        Assertions.assertEquals(AgentStatus.ACCEPTED, back.getStatus());
        Assertions.assertEquals("bread", back.getResult().get("item"));
        Assertions.assertFalse(back.hasError());
    }

    @Test
    void failureIncludesError() {
        String json = AgentJson.toJson(AgentResponse.failure("x", "boom"));
        Assertions.assertTrue(json.contains("\"error\":\"boom\""));
        Assertions.assertTrue(json.contains("\"status\":\"failure\""));
    }

    @Test
    void looksLikeJsonRequest() {
        Assertions.assertTrue(AgentJson.looksLikeJsonRequest(
                "{\"id\":\"1\",\"action\":\"status\"}"));
        Assertions.assertTrue(AgentJson.looksLikeJsonRequest(
                "@agent json {\"id\":\"1\",\"action\":\"cancel\"}"));
        Assertions.assertFalse(AgentJson.looksLikeJsonRequest("xget bread"));
        Assertions.assertFalse(AgentJson.looksLikeJsonRequest("get cobblestone 64"));
    }

    @Test
    void missingActionThrows() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AgentJson.parseRequest("{\"id\":\"1\",\"parameters\":{}}"));
    }
}
