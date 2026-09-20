package adris.altoclef.threat;

import adris.altoclef.AltoClef;
import adris.altoclef.knowledge.KnowledgeFact;
import adris.altoclef.knowledge.WorldKnowledge;
import adris.altoclef.trackers.EntityTracker;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Reads existing AltoClef / WorldKnowledge / EntityTracker signals into
 * {@link ThreatSignals}. Does not reimplement MobDefense / WorldSurvival logic.
 */
public final class ThreatSignalCollector {

    private ThreatSignalCollector() {}

    public static ThreatSignals collect(AltoClef mod) {
        ThreatSignals.Builder b = ThreatSignals.builder();
        if (mod == null || !AltoClef.inGame()) {
            return b.build();
        }

        ClientPlayerEntity player = mod.getPlayer();
        if (player == null) {
            return b.build();
        }

        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        WorldKnowledge wk = mod.getWorldKnowledge();
        if (wk != null) {
            KnowledgeFact<Float> hf = wk.playerHealthFact();
            if (hf != null && hf.isKnown() && hf.getValue() != null) {
                health = hf.getValue();
            }
        }

        b.health(health)
                .maxHealth(maxHealth > 0 ? maxHealth : ThreatSignals.DEFAULT_MAX_HEALTH)
                .foodLevel(player.getHungerManager().getFoodLevel())
                .playerDead(player.isDead() || health <= 0f)
                .inLava(player.isInLava())
                .onFire(player.isOnFire())
                .drowning(isDrowning(player));

        EntityTracker entities = mod.getEntityTracker();
        if (entities != null) {
            List<LivingEntity> hostiles = entities.getHostiles();
            if (hostiles != null && !hostiles.isEmpty()) {
                Vec3d pos = player.getPos();
                double closest = ThreatSignals.NO_HOSTILE;
                int count = 0;
                for (LivingEntity h : hostiles) {
                    if (h == null || !h.isAlive()) {
                        continue;
                    }
                    count++;
                    double d = h.squaredDistanceTo(pos);
                    if (d < closest * closest || closest == ThreatSignals.NO_HOSTILE) {
                        closest = Math.sqrt(d);
                    }
                }
                b.nearbyHostileCount(count);
                if (count > 0 && Double.isFinite(closest)) {
                    b.closestHostileDistance(closest);
                }
            }
        }

        // Optional: last MobDefense priority if chain already ticked this frame.
        // We do NOT call getPriority() here (would re-enter defense logic).
        // Hint left unused unless a future accessor exposes cached priority safely.

        return b.build();
    }

    private static boolean isDrowning(ClientPlayerEntity player) {
        if (player == null) {
            return false;
        }
        // Mirror WorldSurvivalChain drowning interest: in water and air depleting
        return player.isTouchingWater()
                && player.getAir() < player.getMaxAir() / 2;
    }
}
