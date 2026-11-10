package adris.altoclef.tasksystem;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static adris.altoclef.tasksystem.GraceNoteTaskGate.Decision.*;

public class GraceNoteTaskGateTest {
    public static class BaritoneStub {
        final Service service = new Service();
        public Service getGraceNote() { return service; }
    }
    public static class Service {
        boolean held;
        Outcome outcome;
        public boolean holdsControl() { return held; }
        public View status() { return new View(); }
        public Outcome lastOutcome() { return outcome; }
    }
    public static class View { public long episode() { return 2; } }
    public static class BrokenBaritone {
        public Service getGraceNote() { throw new IllegalStateException("temporarily unavailable"); }
    }
    public static class Outcome {
        final long episode, released;
        Outcome(long episode, long released) { this.episode = episode; this.released = released; }
        public long episode() { return episode; }
        public long released() { return released; }
        public String result() { return "AVOIDED"; }
    }

    @Test public void olderVersionsRemainUsable() {
        GraceNoteTaskGate gate = new GraceNoteTaskGate();
        assertEquals(RUN, gate.poll(new Object()));
        assertEquals(RUN, gate.poll(new Object()));
    }

    @Test public void waitsForMatchingOutcomeAndVerifiedReleaseThenReplansOnce() {
        GraceNoteTaskGate gate = new GraceNoteTaskGate();
        BaritoneStub b = new BaritoneStub();
        b.service.held = true;
        assertEquals(WAIT, gate.poll(b));
        b.service.held = false;
        assertEquals(WAIT, gate.poll(b));
        b.service.outcome = new Outcome(1, 40);
        assertEquals(WAIT, gate.poll(b));
        b.service.outcome = new Outcome(2, -1);
        assertEquals(WAIT, gate.poll(b));
        b.service.outcome = new Outcome(2, 50);
        assertEquals(REPLAN, gate.poll(b));
        assertEquals(RUN, gate.poll(b));
    }

    @Test public void apiLossDuringTakeoverDoesNotResumeTasks() {
        GraceNoteTaskGate gate = new GraceNoteTaskGate();
        BaritoneStub b = new BaritoneStub();
        b.service.held = true;
        assertEquals(WAIT, gate.poll(b));
        assertEquals(WAIT, gate.poll(null));
        assertEquals(WAIT, gate.poll(new Object()));
    }

    @Test public void presentButBrokenServiceCannotAuthorizeTaskCommands() {
        GraceNoteTaskGate gate = new GraceNoteTaskGate();
        assertEquals(WAIT, gate.poll(new BrokenBaritone()));
        assertEquals(RUN, gate.poll(new BaritoneStub()));
    }
}
