package adris.altoclef.tasks.manhunt;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.movement.TungstenMovement;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

import java.util.Optional;

/**
 * Hunter role for manhunt.
 *
 * 1. Acquire minimal combat gear if naked
 * 2. Track the target runner (by name or nearest player)
 * 3. Chase and fight until the runner is dead
 * 4. Re-acquire target after death / dimension changes
 *
 * Movement: TungstenMovement facade (physics A* when available, else Baritone GetToEntity).
 */
public class HunterManhuntTask extends Task {

    private final AltoClef mod;
    private final String preferredTargetName; // null = nearest player

    private PlayerEntity currentTarget;
    private Task gearTask;
    private Task combatTask;

    private final TimerGame retargetTimer = new TimerGame(2.0);

    public HunterManhuntTask(AltoClef mod, String targetName) {
        this.mod = mod;
        this.preferredTargetName = targetName;
    }

    @Override
    protected void onStart() {
        setDebugState("Hunter – searching for runner");
        currentTarget = null;
        combatTask = null;
        gearTask = null;
    }

    @Override
    protected Task onTick() {
        // Minimal gear check
        if (!hasBasicCombatGear()) {
            setDebugState("Hunter – grabbing basic gear");
            if (gearTask == null || gearTask.isFinished()) {
                // Simple: sword + some food. Expand later.
                gearTask = TaskCatalogue.getItemTask(Items.IRON_SWORD, 1);
            }
            return gearTask;
        }

        // Acquire / refresh target
        if (currentTarget == null || !currentTarget.isAlive() || retargetTimer.elapsed()) {
            retargetTimer.reset();
            currentTarget = findTarget().orElse(null);
        }

        if (currentTarget == null) {
            setDebugState("Hunter – no runner found, searching…");
            // Could add exploration / last-known-position later
            return null;
        }

        setDebugState("Hunter – chasing " + currentTarget.getName().getString());

        // Close enough → fight; otherwise path to them
        double distSq = mod.getPlayer().squaredDistanceTo(currentTarget);
        if (distSq < 6.0 * 6.0) {
            if (combatTask == null || combatTask.isFinished()) {
                combatTask = new FightPlayerTask(mod, currentTarget);
            }
            return combatTask;
        }

        // Chase via Tungsten when available, else Baritone
        return TungstenMovement.followEntity(currentTarget, 2.0);
    }

    private Optional<PlayerEntity> findTarget() {
        if (preferredTargetName != null && !preferredTargetName.isEmpty()) {
            return mod.getEntityTracker().getClosestEntity(
                    mod.getPlayer().getPos(),
                    e -> e instanceof PlayerEntity p
                            && p.getName().getString().equalsIgnoreCase(preferredTargetName),
                    PlayerEntity.class
            ).map(e -> (PlayerEntity) e);
        }

        // Nearest non-self survival player
        return mod.getEntityTracker().getClosestEntity(
                mod.getPlayer().getPos(),
                e -> e instanceof PlayerEntity p
                        && p != mod.getPlayer()
                        && !p.isSpectator()
                        && !p.isCreative(),
                PlayerEntity.class
        ).map(e -> (PlayerEntity) e);
    }

    private boolean hasBasicCombatGear() {
        return StorageHelper.itemInventoryIncludes(mod, Items.IRON_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.DIAMOND_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.NETHERITE_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.STONE_SWORD)
                || StorageHelper.itemInventoryIncludes(mod, Items.WOODEN_SWORD);
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof HunterManhuntTask
                && java.util.Objects.equals(((HunterManhuntTask) other).preferredTargetName, preferredTargetName);
    }

    @Override
    protected String toDebugString() {
        return "HunterManhuntTask(" + (preferredTargetName != null ? preferredTargetName : "nearest") + ")";
    }

    @Override
    public boolean isFinished() {
        // Hunter “wins” when target is dead and no longer exists – for continuous manhunt we rarely finish
        return false;
    }
}
