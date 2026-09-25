package adris.altoclef.tasks.speedrun.testrun2.fault;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class FaultBookTest {

    @Test
    void loopDetectorFlagsAnyAlternatingPair() {
        LoopDetector d = new LoopDetector(4, 60_000);
        Assertions.assertNull(d.feed("A", 0));
        Assertions.assertNull(d.feed("A", 1)); // repeats are not changes
        Assertions.assertNull(d.feed("B", 2));
        Assertions.assertNull(d.feed("A", 3));
        Assertions.assertNull(d.feed("B", 4));
        Assertions.assertEquals("A<->B x4", d.feed("A", 5));
        Assertions.assertNull(d.feed("B", 6)); // reported once, then re-armed
    }

    @Test
    void loopDetectorIgnoresSlowOrThreeWayChanges() {
        LoopDetector d = new LoopDetector(4, 1_000);
        for (int i = 0; i < 10; i++) Assertions.assertNull(d.feed(i % 2 == 0 ? "A" : "B", i * 5_000L));
        LoopDetector e = new LoopDetector(4, 60_000);
        String[] seq = {"A", "B", "C", "A", "B", "C", "A"};
        for (int i = 0; i < seq.length; i++) Assertions.assertNull(e.feed(seq[i], i));
    }

    @Test
    void episodesTrackSecondsLostAndSummaryIsSortedByCost() throws Exception {
        Path dir = Files.createTempDirectory("faultbook");
        FaultBook.configure(dir, () -> Map.of("phase", "IRON"), c -> "hint-" + c);
        FaultBook.reset(0);
        FaultBook.record("S200", "no \"progress\"", 1_000);
        FaultBook.record("E10", "water", 2_000);
        FaultBook.progress(11_000);   // S200 cost 10s, E10 cost 9s
        FaultBook.record("S200", "again", 20_000);
        FaultBook.progress(25_000);   // S200 +5s

        List<String> lines = Files.readAllLines(dir.resolve(FaultBook.EVENTS));
        Assertions.assertEquals(6, lines.size());
        Assertions.assertTrue(lines.get(0).contains("\"code\":\"S200\""));
        Assertions.assertTrue(lines.get(0).contains("\"phase\":\"IRON\""));
        Assertions.assertTrue(lines.get(0).contains("no \\\"progress\\\""));
        Assertions.assertTrue(lines.get(0).contains("\"sev\":\"RECOVERY\""));

        String sum = FaultBook.summaryJson(30_000, "test");
        Assertions.assertTrue(sum.contains("\"lost_s\":\"24.0\""), sum);
        Assertions.assertTrue(sum.indexOf("S200") < sum.indexOf("E10"), sum);
        Assertions.assertTrue(sum.contains("\"hint\":\"hint-E10\""), sum);
    }

    @Test
    void severityFromPrefix() {
        Assertions.assertEquals(FaultBook.Severity.ERROR, FaultBook.severityOf("E50"));
        Assertions.assertEquals(FaultBook.Severity.RECOVERY, FaultBook.severityOf("S200"));
        Assertions.assertEquals(FaultBook.Severity.WARN, FaultBook.severityOf("C210"));
    }
}
