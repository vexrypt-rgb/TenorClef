package adris.altoclef.swarm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SwarmRuntime over a fake Ostinato link: routing and the lead-only rule. No Minecraft. */
class SwarmRuntimeLinkTest {

    static final class FakeLink implements SwarmLink {
        final List<String> sent = new ArrayList<>();
        String lead = "alice";
        String problem;

        @Override public String self() { return "alice"; }
        @Override public String leader() { return lead; }
        @Override public boolean leads() { return true; }
        @Override public boolean send(String to, SwarmMessage m) { sent.add(to + " " + m.op); return true; }
        @Override public String problem() { return problem; }
        @Override public void onReceive(TriConsumer<String, String, String> handler) { }
        @Override public String leadOf(String group) { return "alice"; }
    }

    @AfterEach
    void reset() {
        SwarmRuntime.leave();
        SwarmRuntime.useLink(new SwarmLink.Ostinato());
    }

    @Test
    void leaderOffersOverTheLink() {
        FakeLink link = new FakeLink();
        SwarmRuntime.useLink(link);
        SwarmCoordinator leader = SwarmRuntime.lead(null);
        SwarmRuntime.onWire("bob", "crew", SwarmMessage.of("reg", "caps", Capability.encode(EnumSet.allOf(Capability.class))).encode());
        leader.submit(leader.create(Objective.acquire("oak_log", 3), 5, EnumSet.noneOf(Capability.class)), System.currentTimeMillis());
        assertTrue(link.sent.contains("bob offer"), link.sent.toString());
    }

    @Test
    void leadingFailsWithAReasonWhenTheLinkIsDown() {
        FakeLink link = new FakeLink();
        link.problem = "Ostinato swarm link is not running";
        SwarmRuntime.useLink(link);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> SwarmRuntime.lead(null));
        assertTrue(e.getMessage().contains("not running"));
        assertNull(SwarmRuntime.leader());
    }

    @Test
    void workerBoundMessagesFromANonLeadAreIgnored() {
        FakeLink link = new FakeLink();
        SwarmRuntime.useLink(link);
        // No worker joined: an offer from anyone is dropped without touching the leader role.
        SwarmRuntime.onWire("mallory", "crew", SwarmMessage.of("offer", "a", "x", "item", "oak_log", "n", "1").encode());
        assertTrue(link.sent.isEmpty());
        assertNull(SwarmRuntime.worker());
    }
}
