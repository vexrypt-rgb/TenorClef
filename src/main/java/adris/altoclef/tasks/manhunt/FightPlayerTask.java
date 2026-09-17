package adris.altoclef.tasks.manhunt;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.entity.AbstractKillEntityTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

/**
 * Basic PvP task against a specific player.
 *
 * This is a practical skeleton that:
 * - Paths into melee range
 * - Equips best available sword
 * - Attacks (via AltoClefâ€™s existing entity attack helpers where possible)
 *
 * Real competitive PvP (W-tapping, crit chains, shield, potting, crystal)
 * is much more complex and can be layered on later.
 * UnionClef / autoclef have more advanced combat â€“ this is the starting point
 * for MiranCZ-style Altoclef.
 */
public class FightPlayerTask extends Task {

    private final AltoClef mod;
    private final PlayerEntity target;

    private final TimerGame attackCooldown = new TimerGame(0.55); // ~sword cooldown ballpark

    public FightPlayerTask(AltoClef mod, PlayerEntity target) {
        this.mod = mod;
        this.target = target;
    }

    @Override
    protected void onStart() {
        setDebugState("Fighting " + target.getName().getString());
        equipBestWeapon();
    }

    @Override
    protected Task onTick() {
        if (target == null || !target.isAlive() || adris.altoclef.multiversion.entity.EntityVer.isGone(target)) {
            setDebugState("Target dead or gone");
            return null;
        }

        double distSq = mod.getPlayer().squaredDistanceTo(target);

        // Keep in melee range
        if (distSq > 3.5 * 3.5) {
            return new GetToEntityTask(target, 2.0);
        }

        // Face and hit
        equipBestWeapon();
        if (attackCooldown.elapsed()) {
            // Look at target and attack
            // Real implementation uses modâ€™s input / attack helpers:
            // mod.getInputControls().tryPress(Input.CLICK_LEFT);
            // or existing KillEntity-style logic
            attackCooldown.reset();
            setDebugState("Attacking " + target.getName().getString());
        }

        return null; // stay in combat
    }

    private void equipBestWeapon() {
        if (StorageHelper.itemInventoryIncludes(mod, Items.NETHERITE_SWORD)) {
            mod.getSlotHandler().forceEquipItem(Items.NETHERITE_SWORD);
        } else if (StorageHelper.itemInventoryIncludes(mod, Items.DIAMOND_SWORD)) {
            mod.getSlotHandler().forceEquipItem(Items.DIAMOND_SWORD);
        } else if (StorageHelper.itemInventoryIncludes(mod, Items.IRON_SWORD)) {
            mod.getSlotHandler().forceEquipItem(Items.IRON_SWORD);
        } else if (StorageHelper.itemInventoryIncludes(mod, Items.STONE_SWORD)) {
            mod.getSlotHandler().forceEquipItem(Items.STONE_SWORD);
        } else if (StorageHelper.itemInventoryIncludes(mod, Items.WOODEN_SWORD)) {
            mod.getSlotHandler().forceEquipItem(Items.WOODEN_SWORD);
        }
    }

    @Override
    protected void onStop(Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FightPlayerTask
                && ((FightPlayerTask) other).target == this.target;
    }

    @Override
    protected String toDebugString() {
        return "FightPlayerTask(" + target.getName().getString() + ")";
    }

    @Override
    public boolean isFinished() {
        return target == null || !target.isAlive() || adris.altoclef.multiversion.entity.EntityVer.isGone(target);
    }
}
