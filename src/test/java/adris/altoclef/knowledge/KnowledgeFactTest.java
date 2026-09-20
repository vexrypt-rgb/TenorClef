package adris.altoclef.knowledge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Phase 5: age / confidence / decay / merge rules (no Minecraft runtime).
 */
public class KnowledgeFactTest {

    @Test
    void unknownIsNotFreshOrKnown() {
        KnowledgeFact<String> u = KnowledgeFact.unknown();
        Assertions.assertFalse(u.isKnown());
        Assertions.assertFalse(u.isFreshEnough(100, 50));
        Assertions.assertEquals(0.0, u.decayedConfidence(100, 20), 1e-9);
        Assertions.assertTrue(u.valueOptional().isEmpty());
    }

    @Test
    void freshEnoughRespectsMaxAge() {
        KnowledgeFact<Integer> f = KnowledgeFact.of(1, 100L, 1.0, KnowledgeSource.SENSOR);
        Assertions.assertTrue(f.isFreshEnough(110, 20));
        Assertions.assertTrue(f.isFreshEnough(120, 20));
        Assertions.assertFalse(f.isFreshEnough(121, 20));
        Assertions.assertEquals(21L, f.ageTicks(121));
    }

    @Test
    void decayHalvesAtHalfLife() {
        KnowledgeFact<String> f = KnowledgeFact.of("x", 0L, 1.0, KnowledgeSource.SENSOR);
        Assertions.assertEquals(1.0, f.decayedConfidence(0, 40), 1e-9);
        Assertions.assertEquals(0.5, f.decayedConfidence(40, 40), 1e-9);
        Assertions.assertEquals(0.25, f.decayedConfidence(80, 40), 1e-9);
    }

    @Test
    void mergePrefersFresher() {
        KnowledgeFact<String> old = KnowledgeFact.of("old", 10L, 1.0, KnowledgeSource.SENSOR);
        KnowledgeFact<String> neu = KnowledgeFact.of("new", 100L, 0.4, KnowledgeSource.MEMORY);
        Assertions.assertEquals("new", KnowledgeFacts.merge(old, neu).getValue());
        Assertions.assertEquals("new", KnowledgeFacts.merge(neu, old).getValue());
    }

    @Test
    void mergeTiePrefersHigherConfidenceThenSource() {
        KnowledgeFact<String> a = KnowledgeFact.of("a", 50L, 0.9, KnowledgeSource.MEMORY);
        KnowledgeFact<String> b = KnowledgeFact.of("b", 52L, 0.5, KnowledgeSource.SENSOR);
        // Within tie window: higher confidence wins
        Assertions.assertEquals("a", KnowledgeFacts.merge(a, b).getValue());

        KnowledgeFact<String> c = KnowledgeFact.of("c", 50L, 0.8, KnowledgeSource.SENSOR);
        KnowledgeFact<String> d = KnowledgeFact.of("d", 51L, 0.8, KnowledgeSource.MEMORY);
        Assertions.assertEquals("c", KnowledgeFacts.merge(c, d).getValue());
    }

    @Test
    void mergeUnknownYields() {
        KnowledgeFact<String> known = KnowledgeFact.of("k", 1L, 1.0, KnowledgeSource.USER);
        Assertions.assertSame(known, KnowledgeFacts.merge(KnowledgeFact.unknown(), known));
        Assertions.assertSame(known, KnowledgeFacts.merge(known, KnowledgeFact.unknown()));
    }

    @Test
    void replaceIfKnown() {
        KnowledgeFact<String> prev = KnowledgeFact.of("prev", 1L, 1.0, KnowledgeSource.MEMORY);
        KnowledgeFact<String> next = KnowledgeFact.of("next", 2L, 0.5, KnowledgeSource.SENSOR);
        Assertions.assertEquals("next", KnowledgeFacts.replaceIfKnown(prev, next).getValue());
        Assertions.assertEquals("prev", KnowledgeFacts.replaceIfKnown(prev, KnowledgeFact.unknown()).getValue());
    }

    @Test
    void isReliableRequiresFreshnessAndDecayedConfidence() {
        KnowledgeFact<Boolean> live = KnowledgeFact.of(true, 100L, 1.0, KnowledgeSource.SENSOR);
        Assertions.assertTrue(KnowledgeFacts.isReliable(live, 100L, 40L, 20L, 0.35));
        Assertions.assertFalse(KnowledgeFacts.isReliable(live, 200L, 40L, 20L, 0.35)); // too old

        KnowledgeFact<Boolean> weak = KnowledgeFact.of(true, 100L, 0.2, KnowledgeSource.MEMORY);
        Assertions.assertFalse(KnowledgeFacts.isReliable(weak, 100L, 40L, 20L, 0.35));
    }

    @Test
    void clampConfidence() {
        KnowledgeFact<String> hi = KnowledgeFact.of("h", 0L, 2.0, KnowledgeSource.SENSOR);
        KnowledgeFact<String> lo = KnowledgeFact.of("l", 0L, -1.0, KnowledgeSource.SENSOR);
        Assertions.assertEquals(1.0, hi.getConfidence(), 1e-9);
        Assertions.assertEquals(0.0, lo.getConfidence(), 1e-9);
    }
}
