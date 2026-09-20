package adris.altoclef.threat;

/**
 * Pure input snapshot for {@link ThreatAssessor} (Phase 8).
 * Filled from player state / EntityTracker / survival flags — no Minecraft types.
 */
public final class ThreatSignals {

    public static final float DEFAULT_MAX_HEALTH = 20f;
    public static final double NO_HOSTILE = Double.POSITIVE_INFINITY;

    private final float health;
    private final float maxHealth;
    private final int foodLevel;
    private final boolean inLava;
    private final boolean onFire;
    private final boolean drowning;
    private final boolean playerDead;
    private final int nearbyHostileCount;
    private final double closestHostileDistance;
    /** Optional MobDefenseChain last priority hint; negative = unused. */
    private final float defensePriorityHint;

    private ThreatSignals(Builder b) {
        this.health = b.health;
        this.maxHealth = b.maxHealth > 0 ? b.maxHealth : DEFAULT_MAX_HEALTH;
        this.foodLevel = b.foodLevel;
        this.inLava = b.inLava;
        this.onFire = b.onFire;
        this.drowning = b.drowning;
        this.playerDead = b.playerDead;
        this.nearbyHostileCount = Math.max(0, b.nearbyHostileCount);
        this.closestHostileDistance = b.closestHostileDistance;
        this.defensePriorityHint = b.defensePriorityHint;
    }

    public static Builder builder() {
        return new Builder();
    }

    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public int getFoodLevel() { return foodLevel; }
    public boolean isInLava() { return inLava; }
    public boolean isOnFire() { return onFire; }
    public boolean isDrowning() { return drowning; }
    public boolean isPlayerDead() { return playerDead; }
    public int getNearbyHostileCount() { return nearbyHostileCount; }
    public double getClosestHostileDistance() { return closestHostileDistance; }
    public float getDefensePriorityHint() { return defensePriorityHint; }

    public boolean hasHostiles() {
        return nearbyHostileCount > 0 && Double.isFinite(closestHostileDistance);
    }

    public static final class Builder {
        private float health = DEFAULT_MAX_HEALTH;
        private float maxHealth = DEFAULT_MAX_HEALTH;
        private int foodLevel = 20;
        private boolean inLava;
        private boolean onFire;
        private boolean drowning;
        private boolean playerDead;
        private int nearbyHostileCount;
        private double closestHostileDistance = NO_HOSTILE;
        private float defensePriorityHint = Float.NEGATIVE_INFINITY;

        public Builder health(float health) { this.health = health; return this; }
        public Builder maxHealth(float maxHealth) { this.maxHealth = maxHealth; return this; }
        public Builder foodLevel(int foodLevel) { this.foodLevel = foodLevel; return this; }
        public Builder inLava(boolean inLava) { this.inLava = inLava; return this; }
        public Builder onFire(boolean onFire) { this.onFire = onFire; return this; }
        public Builder drowning(boolean drowning) { this.drowning = drowning; return this; }
        public Builder playerDead(boolean playerDead) { this.playerDead = playerDead; return this; }
        public Builder nearbyHostileCount(int n) { this.nearbyHostileCount = n; return this; }
        public Builder closestHostileDistance(double d) { this.closestHostileDistance = d; return this; }
        public Builder defensePriorityHint(float p) { this.defensePriorityHint = p; return this; }

        public ThreatSignals build() {
            return new ThreatSignals(this);
        }
    }
}
