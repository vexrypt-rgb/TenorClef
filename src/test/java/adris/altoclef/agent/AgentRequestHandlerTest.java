package adris.altoclef.agent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 9: dispatch with a fake AgentRuntime (no Minecraft).
 */
public class AgentRequestHandlerTest {

    private FakeRuntime fake;
    private AgentProtocol protocol;

    @BeforeEach
    void setUp() {
        fake = new FakeRuntime();
        protocol = new AgentProtocol(fake);
    }

    @Test
    void getDispatchesAcquire() {
        AgentResponse r = protocol.handle(AgentRequest.of("1", "get", Map.of(
                "item", "cobblestone", "count", "64")));
        Assertions.assertEquals(AgentStatus.ACCEPTED, r.getStatus());
        Assertions.assertEquals(List.of("acquire:cobblestone:64"), fake.acquires);
        Assertions.assertEquals("cobblestone", r.getResult().get("item"));
    }

    @Test
    void acquireAlias() {
        AgentResponse r = protocol.handleJson(
                "{\"id\":\"a\",\"action\":\"acquire\",\"parameters\":{\"resource\":\"dirt\",\"count\":4}}");
        Assertions.assertEquals(AgentStatus.ACCEPTED, r.getStatus());
        Assertions.assertEquals(List.of("acquire:dirt:4"), fake.acquires);
    }

    @Test
    void goalDispatchesGoal() {
        AgentResponse r = protocol.handle(AgentRequest.of("g", "goal", Map.of(
                "item", "iron_ingot", "count", "8")));
        Assertions.assertEquals(AgentStatus.ACCEPTED, r.getStatus());
        Assertions.assertEquals(List.of("goal:iron_ingot:8"), fake.goals);
    }

    @Test
    void statusReturnsSnapshot() {
        fake.busy = true;
        fake.snap.put("health", "18.0");
        fake.snap.put("goalStatus", "RUNNING");
        AgentResponse r = protocol.handleJson("{\"id\":\"s\",\"action\":\"status\"}");
        Assertions.assertEquals(AgentStatus.RUNNING, r.getStatus());
        Assertions.assertEquals("18.0", r.getResult().get("health"));
        Assertions.assertEquals("true", r.getResult().get("busy"));
    }

    @Test
    void snapAlias() {
        AgentResponse r = protocol.handle(AgentRequest.of("s2", "snap"));
        Assertions.assertEquals(AgentStatus.SUCCESS, r.getStatus());
        Assertions.assertEquals("false", r.getResult().get("busy"));
    }

    @Test
    void cancelDelegates() {
        AgentResponse r = protocol.handle(AgentRequest.of("c", "cancel"));
        Assertions.assertEquals(AgentStatus.CANCELLED, r.getStatus());
        Assertions.assertEquals(1, fake.cancels);
    }

    @Test
    void missingItemFails() {
        AgentResponse r = protocol.handle(AgentRequest.of("x", "get"));
        Assertions.assertEquals(AgentStatus.FAILURE, r.getStatus());
        Assertions.assertTrue(r.getError().contains("item"));
    }

    @Test
    void unsupportedActionFails() {
        AgentResponse r = protocol.handle(AgentRequest.of("x", "explode"));
        Assertions.assertEquals(AgentStatus.FAILURE, r.getStatus());
        Assertions.assertTrue(r.getError().contains("unsupported"));
    }

    @Test
    void tryHandleLineIgnoresLegacy() {
        Assertions.assertNull(protocol.tryHandleLine("xget bread"));
        AgentResponse r = protocol.tryHandleLine(
                "{\"id\":\"1\",\"action\":\"cancel\"}");
        Assertions.assertNotNull(r);
        Assertions.assertEquals(AgentStatus.CANCELLED, r.getStatus());
    }

    @Test
    void handleJsonToJson() {
        String out = protocol.handleJsonToJson(
                "{\"id\":\"z\",\"action\":\"status\"}");
        Assertions.assertTrue(out.contains("\"status\":\"success\""));
        Assertions.assertTrue(out.contains("\"id\":\"z\""));
    }

    /** Fake runtime recording calls. */
    static final class FakeRuntime implements AgentRuntime {
        final List<String> acquires = new ArrayList<>();
        final List<String> goals = new ArrayList<>();
        int cancels;
        boolean busy;
        final Map<String, String> snap = new LinkedHashMap<>();

        @Override
        public AgentResponse acquire(String requestId, String item, int count) {
            acquires.add("acquire:" + item + ":" + count);
            return AgentResponse.accepted(requestId, Map.of(
                    "item", item, "count", Integer.toString(count), "mode", "fake"));
        }

        @Override
        public AgentResponse goal(String requestId, String item, int count) {
            goals.add("goal:" + item + ":" + count);
            return AgentResponse.accepted(requestId, Map.of(
                    "item", item, "count", Integer.toString(count), "mode", "fake-goal"));
        }

        @Override
        public Map<String, String> snapshot() {
            return new LinkedHashMap<>(snap);
        }

        @Override
        public AgentResponse cancel(String requestId) {
            cancels++;
            return AgentResponse.cancelled(requestId, Map.of("ok", "true"));
        }

        @Override
        public boolean isBusy() {
            return busy;
        }
    }
}
