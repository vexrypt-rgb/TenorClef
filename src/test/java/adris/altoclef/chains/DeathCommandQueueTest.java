package adris.altoclef.chains;

import adris.altoclef.chains.DeathCommandQueue.PlayerState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The death-command lifecycle that replaced the render-thread {@code while (player.isAlive());} spin. */
class DeathCommandQueueTest {

    @Test
    void deadPlayerRunsCommandsOnTheSchedulingTickLikeBefore() {
        DeathCommandQueue q = new DeathCommandQueue();
        List<String> ran = new ArrayList<>();
        q.schedule(List.of("@get log", "/say hi"));
        assertEquals("ran", q.tick(PlayerState.DEAD, ran::add));
        assertEquals(List.of("@get log", "/say hi"), ran);
        assertEquals("idle", q.tick(PlayerState.DEAD, ran::add));
        assertEquals(2, ran.size(), "commands run exactly once");
    }

    @Test
    void alivePlayerOnTheDeathScreenNeverBlocksTheTick() {
        // The freeze: the old loop spun forever on the render thread while the player read alive.
        DeathCommandQueue q = new DeathCommandQueue();
        List<String> ran = new ArrayList<>();
        q.schedule(List.of("@stop"));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            for (int i = 1; i < DeathCommandQueue.MAX_HOLD_TICKS; i++) {
                assertEquals("held", q.tick(PlayerState.ALIVE, ran::add));
            }
        });
        assertTrue(ran.isEmpty(), "nothing runs while held");
        assertEquals("ran-after-hold", q.tick(PlayerState.ALIVE, ran::add));
        assertEquals(List.of("@stop"), ran);
        for (int i = 0; i < 100; i++) q.tick(PlayerState.ALIVE, ran::add);
        assertEquals(1, ran.size(), "no duplicate run after the hold");
    }

    @Test
    void deathWhileHeldRunsAtOnce() {
        DeathCommandQueue q = new DeathCommandQueue();
        List<String> ran = new ArrayList<>();
        q.schedule(List.of("a"));
        assertEquals("held", q.tick(PlayerState.ALIVE, ran::add));
        assertEquals("ran", q.tick(PlayerState.DEAD, ran::add));
        assertEquals(List.of("a"), ran);
    }

    @Test
    void leavingTheWorldDiscardsPendingCommands() {
        DeathCommandQueue q = new DeathCommandQueue();
        List<String> ran = new ArrayList<>();
        q.schedule(List.of("a"));
        q.tick(PlayerState.ALIVE, ran::add);
        assertEquals("discarded", q.tick(PlayerState.NO_PLAYER, ran::add));
        assertFalse(q.hasPending());
        assertEquals("idle", q.tick(PlayerState.DEAD, ran::add));
        assertTrue(ran.isEmpty());
    }

    @Test
    void aSecondRespawnReplacesAnUnrunBatch() {
        DeathCommandQueue q = new DeathCommandQueue();
        List<String> ran = new ArrayList<>();
        q.schedule(List.of("first"));
        q.tick(PlayerState.ALIVE, ran::add);
        q.schedule(List.of("second"));
        q.tick(PlayerState.DEAD, ran::add);
        assertEquals(List.of("second"), ran);
    }

    @Test
    void emptyScheduleIsIdle() {
        DeathCommandQueue q = new DeathCommandQueue();
        q.schedule(List.of());
        assertFalse(q.hasPending());
        assertEquals("idle", q.tick(PlayerState.ALIVE, s -> fail("nothing to run")));
    }

    @Test
    void aCommandThatReschedulesDoesNotRunTwice() {
        DeathCommandQueue q = new DeathCommandQueue();
        List<String> ran = new ArrayList<>();
        q.schedule(List.of("a"));
        q.tick(PlayerState.DEAD, c -> { ran.add(c); q.schedule(List.of()); });
        assertEquals(List.of("a"), ran);
        assertFalse(q.hasPending());
    }
}
