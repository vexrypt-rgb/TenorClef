package adris.altoclef.threat;

/**
 * Maps {@link ThreatSignals} → {@link ThreatAssessment} (Phase 8).
 * Pure Java — no Minecraft dependency.
 */
@FunctionalInterface
public interface ThreatEvaluator {
    ThreatAssessment evaluate(ThreatSignals signals);
}
