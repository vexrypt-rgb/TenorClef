package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.PlayerSlot;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Attacks an entity, but the target entity must be specified.
 */
public abstract class AbstractKillEntityTask extends AbstractDoToEntityTask {
    private static final double OTHER_FORCE_FIELD_RANGE = 2;

    // Not the "striking" distance, but the "ok we're close enough, lower our guard for other mobs and focus on this one" range.
    private static final double CONSIDER_COMBAT_RANGE = 10;

    /** Best-first sword order for combat equip (iron+ preferred; wood fallback). */
    private static final Item[] COMBAT_SWORDS = new Item[]{
            Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD,
            Items.GOLDEN_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD
    };

    /** Log "combat: equipping sword" only once per session. */
    private static boolean loggedCombatSwordEquip = false;

    protected AbstractKillEntityTask() {
        this(CONSIDER_COMBAT_RANGE, OTHER_FORCE_FIELD_RANGE);
    }

    protected AbstractKillEntityTask(double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
        super(combatGuardLowerRange, combatGuardLowerFieldRadius);
    }

    protected AbstractKillEntityTask(double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
        super(maintainDistance, combatGuardLowerRange, combatGuardLowerFieldRadius);
    }

    /**
     * Best sword in inventory for combat, or null if none.
     * Axes are never returned - combat must prefer swords.
     */
    public static Item bestWeapon(AltoClef mod) {
        for (Item item : COMBAT_SWORDS) {
            if (mod.getItemStorage().hasItem(item)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Equip best sword for combat. Returns true if a swap was performed.
     * Does not equip axes - caller may still punch/axe with current hand if no sword.
     */
    public static boolean equipWeapon(AltoClef mod) {
        Item bestWeapon = bestWeapon(mod);
        if (bestWeapon == null) {
            return false;
        }
        Item equippedWeapon = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot()).getItem();
        if (bestWeapon != equippedWeapon) {
            if (!loggedCombatSwordEquip) {
                Debug.logMessage("combat: equipping sword");
                loggedCombatSwordEquip = true;
            }
            mod.getSlotHandler().forceEquipItem(bestWeapon);
            return true;
        }
        return false;
    }

    @Override
    protected Task onEntityInteract(AltoClef mod, Entity entity) {
        // Equip sword when available; still attack if none (axe/fist fallback)
        equipWeapon(mod);
        float hitProg = mod.getPlayer().getAttackCooldownProgress(0);
        if (hitProg >= 1 && (mod.getPlayer().isOnGround() || mod.getPlayer().getVelocity().getY() < 0 || mod.getPlayer().isTouchingWater())) {
            LookHelper.lookAt(mod, entity.getEyePos());
            mod.getControllerExtras().attack(entity);
        }
        return null;
    }
}
