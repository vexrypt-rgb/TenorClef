package adris.altoclef.tasks.manhunt;

import adris.altoclef.AltoClef;
import adris.altoclef.movement.TungstenMovement;
import adris.altoclef.tasks.speedrun.SpeedrunBeatMinecraftTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

import java.util.Optional;

/**
 * Runner role for manhunt.
 *
 * Default behaviour: run the modern speedrun task (@testrun logic).
 * When a hostile player is nearby or attacking:
 *   - Equip best weapon / armor
 *   - Fight back (PvP) or kite depending on gear advantage
 *   - Resume speedrun when the threat is gone or dead
 *
 * Travel/chase hooks go through TungstenMovement (Baritone fallback). Speedrun mining stays on Baritone.
 */
public class RunnerManhuntTask extends Task {

    private final AltoClef mod;

    private Task speedrunTask;
    private Task combatTask;

    private final TimerGame threatCheckTimer = new TimerGame(0.5);
    private static final double THREAT_RANGE = 32.0;

    public RunnerManhuntTask(AltoClef mod) {
        this.mod = mod;
    }

    @Override
    protected void onStart() {
        setDebugState("Runner - starting speedrun; " + TungstenMovement.statusLine());
        speedrunTask = new SpeedrunBeatMinecraftTask(mod);
        combatTask = null;
    }

    @Override
    protected Task onTick() {
        // Periodically check for hunters
        if (threatCheckTimer.elapsed()) {
            threatCheckTimer.reset();
            Optional<PlayerEntity> threat = findNearestThreat();
            if (threat.isPresent()) {
                setDebugState("Hunter nearby – engaging / defending");
                if (combatTask == null || combatTask.isFinished()) {
                    combatTask = new FightPlayerTask(mod, threat.get());
                }
                return combatTask;
            } else {
                combatTask = null;
            }
        }

        // No immediate threat – continue speedrun
        if (speedrunTask == null || speedrunTask.isFinished()) {
            setDebugState("Runner – speedrun complete or restarting");
            speedrunTask = new SpeedrunBeatMinecraftTask(mod);
        }

        setDebugState("Runner – speedrunning (watching for hunters)");
        return speedrunTask;
    }

    private Optional<PlayerEntity> findNearestThreat() {
        return mod.getEntityTracker().getClosestEntity(
                mod.getPlayer().getPos(),
                e -> e instanceof PlayerEntity p
                        && p != mod.getPlayer()
                        && !p.isSpectator()
                        && !p.isCreative()
                        && mod.getPlayer().squaredDistanceTo(p) < THREAT_RANGE * THREAT_RANGE,
                PlayerEntity.class
        ).map(e -> (PlayerEntity) e);
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof RunnerManhuntTask;
    }

    @Override
    protected String toDebugString() {
        return "RunnerManhuntTask";
    }

    @Override
    public boolean isFinished() {
        // Runner “wins” when the dragon is dead / credits – handled inside speedrun task
        return speedrunTask != null && speedrunTask.isFinished();
    }
}
