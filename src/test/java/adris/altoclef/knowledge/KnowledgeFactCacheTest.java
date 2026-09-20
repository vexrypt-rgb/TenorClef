package adris.altoclef.knowledge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Phase 5: bounded in-memory fact cache.
 */
public class KnowledgeFactCacheTest {

    @Test
    void putGetAndUnknownMiss() {
        KnowledgeFactCache cache = new KnowledgeFactCache(4);
        Assertions.assertFalse(cache.get("missing").isKnown());
        cache.put("player.pos", KnowledgeFact.of("here", 10L, 1.0, KnowledgeSource.SENSOR));
        Assertions.assertEquals("here", cache.get("player.pos").getValue());
        Assertions.assertEquals(1, cache.size());
    }

    @Test
    void unknownPutRemoves() {
        KnowledgeFactCache cache = new KnowledgeFactCache(4);
        cache.put("k", KnowledgeFact.of(1, 1L, 1.0, KnowledgeSource.SENSOR));
        cache.put("k", KnowledgeFact.unknown());
        Assertions.assertFalse(cache.contains("k"));
        Assertions.assertEquals(0, cache.size());
    }

    @Test
    void lruEvictsEldest() {
        KnowledgeFactCache cache = new KnowledgeFactCache(2);
        cache.put("a", KnowledgeFact.of("A", 1L, 1.0, KnowledgeSource.SENSOR));
        cache.put("b", KnowledgeFact.of("B", 2L, 1.0, KnowledgeSource.SENSOR));
        cache.get("a"); // touch a so b is eldest
        cache.put("c", KnowledgeFact.of("C", 3L, 1.0, KnowledgeSource.SENSOR));
        Assertions.assertTrue(cache.contains("a"));
        Assertions.assertTrue(cache.contains("c"));
        Assertions.assertFalse(cache.contains("b"));
        Assertions.assertEquals(2, cache.size());
    }

    @Test
    void clearEmpties() {
        KnowledgeFactCache cache = new KnowledgeFactCache();
        cache.put("x", KnowledgeFact.of(true, 0L, 1.0, KnowledgeSource.SCANNER));
        cache.clear();
        Assertions.assertEquals(0, cache.size());
    }
}
