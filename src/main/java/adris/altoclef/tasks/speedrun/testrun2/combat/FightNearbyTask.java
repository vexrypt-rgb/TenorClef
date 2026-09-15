package adris.altoclef.tasks.speedrun.testrun2.combat;

import adris.altoclef.tasksystem.Task;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PiglinEntity;

import java.util.function.Predicate;

/** Convenience constructors for the speedrun. */
public final class FightNearbyTask {

    private FightNearbyTask() {}

    public static Task hostiles() {
        return new AnyWeaponCombatTask(e -> e instanceof HostileEntity && !(e instanceof PiglinEntity p && !p.isAttacking()));
    }

    public static Task of(Class<? extends LivingEntity> type) {
        return new AnyWeaponCombatTask(type::isInstance);
    }

    public static Task matching(Predicate<LivingEntity> filter) {
        return new AnyWeaponCombatTask(filter);
    }
}
