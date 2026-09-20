package adris.altoclef.threat;

/**
 * Coarse survival/combat severity (Phase 8). Higher ordinal = worse.
 */
public enum ThreatLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** True if this level is at least as severe as {@code other}. */
    public boolean isAtLeast(ThreatLevel other) {
        if (other == null) {
            return true;
        }
        return this.ordinal() >= other.ordinal();
    }

    /** HIGH/CRITICAL interrupt strategic goals (pause or fail). */
    public boolean shouldInterruptGoals() {
        return this == HIGH || this == CRITICAL;
    }
}
