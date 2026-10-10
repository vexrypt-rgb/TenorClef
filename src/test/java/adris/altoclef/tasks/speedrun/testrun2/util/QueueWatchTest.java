package adris.altoclef.tasks.speedrun.testrun2.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueueWatchTest {

    @Test
    void queueLinesAreRecognised() {
        assertTrue(QueueWatch.isQueueLine("position in queue: 142"));
        assertTrue(QueueWatch.isQueueLine("your queue position is 3"));
        assertTrue(QueueWatch.isQueueLine("estimated time in the queue: 12m"));
        assertFalse(QueueWatch.isQueueLine("estimated time: 12m"));
        assertFalse(QueueWatch.isQueueLine("welcome back"));
    }

    @Test
    void joinLinesAreRecognised() {
        assertTrue(QueueWatch.isJoinLine("connecting to the server..."));
        assertTrue(QueueWatch.isJoinLine("sending you to main"));
        assertFalse(QueueWatch.isJoinLine("position in queue: 1"));
    }

    @Test
    void aQueueLineFromTheServerBlocks() {
        assertFalse(QueueWatch.blocked());
        QueueWatch.onServerLine("§6Position in queue: §l57");
        assertTrue(QueueWatch.blocked());
    }
}
